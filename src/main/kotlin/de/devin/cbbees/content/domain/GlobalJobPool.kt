package de.devin.cbbees.content.domain

import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.BeeNetworkManager
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
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
    private val jobBacklog = mutableListOf<BeeJob>()

    val workers: Set<BeeHive> get() = BeeNetworkManager.workers

    fun getAllJobs(): List<BeeJob> = jobBacklog

    @Synchronized
    fun workBacklog(beeHive: BeeHive): TaskBatch? {
        val network = BeeNetworkManager.getNetworkFor(beeHive) ?: return null

        val job = jobBacklog.filter { network.isInRange(it.centerPos) }
            .sortedBy { it.centerPos.distSqr(beeHive.sourcePosition) }
            .firstOrNull { it.getNextTask() != null } ?: return null

        val task = job.getNextTask() ?: return null

        // Verification: double check task is in range (already filtered by job center but good to be sure)
        if (!network.isInRange(task.targetPos)) return null

        task.status = TaskStatus.PICKED
        return TaskBatch(listOf(task), job)
    }

    @Synchronized
    fun dispatchNewJob(job: BeeJob) {
        val availableNetworks = BeeNetworkManager.getNetworks().filter { network ->
            network.hives.firstOrNull()?.sourceWorld == job.level &&
                    network.isInRange(job.centerPos)
        }.sortedBy { network ->
            // Sort by distance of closest hive in network to job center
            network.hives.minOf { it.sourcePosition.distSqr(job.centerPos) }
        }

        val tasksToDistribute = job.tasks
            .filter { it.status == TaskStatus.PENDING }
            .sortedByDescending { it.priority }
            .toMutableList()

        if (availableNetworks.isEmpty()) {
            if (!jobBacklog.contains(job)) jobBacklog.add(job)
            return
        }

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

                    // Only assign if task is within network range
                    if (!network.isInRange(task.targetPos)) {
                        // This task cannot be done by this network, move to next task
                        // (Usually job.centerPos being in range means tasks should be too, 
                        // but individual tasks might be outside if the job covers a large area)
                        tasksToDistribute.removeAt(0)
                        continue
                    }

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