package de.devin.ccr.content.domain

import de.devin.ccr.content.domain.beehive.BeeHive
import de.devin.ccr.content.domain.job.BeeJob
import de.devin.ccr.content.domain.logistics.LogisticsPort
import de.devin.ccr.content.domain.network.BeeNetwork
import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.domain.task.TaskBatch
import de.devin.ccr.content.domain.task.TaskStatus
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Global Bee Job distribution pool.
 *
 */
object GlobalJobPool : SavedData() {
    private val networks = mutableListOf<BeeNetwork>()
    private val allPorts = mutableSetOf<LogisticsPort>()
    private val jobBacklog = mutableListOf<BeeJob>()

    val workers: Set<BeeHive> get() = networks.flatMap { it.hives }.toSet()

    fun getAllJobs(): List<BeeJob> = jobBacklog

    fun registerWorker(worker: BeeHive) {
        // 1. Find all networks that overlap with this hive's range
        val overlappingNetworks = networks.filter { network ->
            network.hives.any { existingHive ->
                areHivesConnected(worker, existingHive)
            }
        }

        if (overlappingNetworks.isEmpty()) {
            val newNetwork = BeeNetwork()
            newNetwork.addHive(worker)
            networks.add(newNetwork)
        } else {
            val primaryNetwork = overlappingNetworks.first()
            primaryNetwork.addHive(worker)

            // Merge other overlapping networks into the primary one
            if (overlappingNetworks.size > 1) {
                for (i in 1 until overlappingNetworks.size) {
                    val other = overlappingNetworks[i]
                    primaryNetwork.hives.addAll(other.hives)
                    primaryNetwork.ports.addAll(other.ports)
                    networks.remove(other)
                }
            }
        }

        updatePortsForNetworks()
        this.setDirty()
    }

    fun unregisterWorker(worker: BeeHive) {
        unregisterWorker(worker.sourceId)
    }

    fun unregisterWorker(sourceId: UUID) {
        for (network in networks.toList()) {
            network.hives.removeIf { it.sourceId == sourceId }
            if (network.hives.isEmpty()) {
                networks.remove(network)
            }
        }
        // After removing a hive, networks might need to be split.
        // For simplicity, we could re-calculate all networks, 
        // but that might be expensive if done frequently.
        // For now, let's just re-calculate.
        recalculateNetworks()
        this.setDirty()
    }

    private fun recalculateNetworks() {
        val hives = networks.flatMap { it.hives }.toList()

        networks.clear()

        val unassignedHives = hives.toMutableList()

        while (unassignedHives.isNotEmpty()) {
            val startHive = unassignedHives.removeAt(0)
            val network = BeeNetwork()
            network.addHive(startHive)
            networks.add(network)

            val toProcess = mutableListOf(startHive)
            while (toProcess.isNotEmpty()) {
                val current = toProcess.removeAt(0)
                val neighbors = unassignedHives.filter { areHivesConnected(current, it) }
                for (neighbor in neighbors) {
                    network.addHive(neighbor)
                    unassignedHives.remove(neighbor)
                    toProcess.add(neighbor)
                }
            }
        }

        updatePortsForNetworks()
    }

    fun registerPort(port: LogisticsPort) {
        allPorts.add(port)
        updatePortsForNetworks()
        this.setDirty()
    }

    fun unregisterPort(port: LogisticsPort) {
        allPorts.remove(port)
        for (network in networks) {
            network.removePort(port)
        }
        this.setDirty()
    }

    private fun updatePortsForNetworks() {
        for (network in networks) {
            network.ports.clear()
            for (port in allPorts) {
                if (port.sourceWorld == network.hives.firstOrNull()?.sourceWorld &&
                    network.isInLogisticsRange(port.sourcePosition)
                ) {
                    network.addPort(port)
                }
            }
        }
    }

    private fun areHivesConnected(h1: BeeHive, h2: BeeHive): Boolean {
        if (h1.sourceWorld != h2.sourceWorld) return false
        val distSq = h1.sourcePosition.distSqr(h2.sourcePosition)
        val range1 = h1.getWorkRange()
        val range2 = h2.getWorkRange()
        val combinedRange = range1 + range2
        return distSq <= combinedRange * combinedRange
    }

    fun getNetworkAt(level: Level, pos: BlockPos): BeeNetwork? {
        return networks.find { it.hives.firstOrNull()?.sourceWorld == level && it.isInRange(pos) }
    }

    fun findProviderFor(level: Level, stack: ItemStack, startPos: BlockPos): LogisticsPort? {
        val network = getNetworkAt(level, startPos)
        if (network != null) {
            return network.findProvider(stack)
        }

        // Fallback to searching all ports if not in a network (though usually it should be)
        return allPorts.filter { it.sourceWorld == level && it.isValidForPickup() && it.hasItemStack(stack) }
            .minByOrNull { it.sourcePosition.distSqr(startPos) }
    }

    @Synchronized
    fun workBacklog(beeHive: BeeHive): TaskBatch? {
        val network = networks.find { it.hives.contains(beeHive) } ?: return null

        val job = jobBacklog.filter { network.isInRange(it.centerPos) }
            .sortedBy { it.centerPos.distSqr(beeHive.sourcePosition) }
            .firstOrNull { it.getNextTask() != null } ?: return null

        val task = job.getNextTask() ?: return null

        task.status = TaskStatus.PICKED
        return TaskBatch(listOf(task), job)
    }

    @Synchronized
    fun dispatchNewJob(job: BeeJob) {
        val availableNetworks = networks.filter { network ->
            network.hives.firstOrNull()?.sourceWorld == job.level &&
                    network.isInRange(job.centerPos)
        }.sortedBy { network ->
            // Sort by distance of closest hive in network to job center
            network.hives.minOf { it.sourcePosition.distSqr(job.centerPos) }
        }

        if (availableNetworks.isEmpty()) {
            jobBacklog.add(job)
            return
        }

        val tasksToDistribute = job.tasks
            .filter { it.status == TaskStatus.PENDING }
            .sortedByDescending { it.priority }
            .toMutableList()

        for (network in availableNetworks) {
            if (tasksToDistribute.isEmpty()) break

            // Try to distribute to hives in this network
            val hivesInNetwork = network.hives.filter { it.getAvailableBeeCount() > 0 }
                .sortedBy { it.sourcePosition.distSqr(job.centerPos) }

            for (hive in hivesInNetwork) {
                if (tasksToDistribute.isEmpty()) break

                val capacity = minOf(hive.getAvailableBeeCount(), hive.getMaxContributionBees())
                var contributedThisLoop = 0

                while (contributedThisLoop < capacity && tasksToDistribute.isNotEmpty()) {
                    val task = tasksToDistribute.first()

                    task.status = TaskStatus.PICKED
                    val batch = TaskBatch(listOf(task), job)
                    if (hive.acceptBatch(batch)) {
                        job.addContribution(hive, 1)
                        tasksToDistribute.removeAt(0)
                        contributedThisLoop++
                    } else {
                        task.status = TaskStatus.PENDING
                        break
                    }
                }
            }
        }

        if (tasksToDistribute.isNotEmpty()) jobBacklog.add(job)

        this.setDirty()
    }

    override fun save(
        p0: CompoundTag,
        p1: HolderLookup.Provider
    ): CompoundTag {
        TODO("Not yet implemented")
    }
}