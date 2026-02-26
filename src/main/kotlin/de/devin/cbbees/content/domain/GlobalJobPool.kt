package de.devin.cbbees.content.domain

import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobStatus
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
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
import de.devin.cbbees.util.ServerSide

/**
 * Global Bee Job distribution pool.
 *
 */
@ServerSide
object GlobalJobPool : SavedData() {
    private val jobBacklog = mutableListOf<BeeJob>()

    fun clear() {
        jobBacklog.clear()
    }

    fun tick() {
        if (jobBacklog.removeIf { it.status == JobStatus.COMPLETED || it.status == JobStatus.CANCELLED }) {
            this.setDirty()
        }
    }

    val workers: Set<BeeHive> get() = ServerBeeNetworkManager.getNetworks().flatMap { it.hives }.toSet()

    fun getAllJobs(): List<BeeJob> = jobBacklog

    @Synchronized
    fun workBacklog(beeHive: BeeHive): TaskBatch? {
        val network = beeHive.network()

        // 1. Find batches already assigned to this network
        val assignedBatch = jobBacklog.flatMap { it.batches }
            .filter { it.status == TaskStatus.PENDING && it.assignedNetworkId == network.id }
            .minByOrNull { it.targetPosition.distSqr(beeHive.pos) }

        if (assignedBatch != null) {
            assignedBatch.status = TaskStatus.PICKED
            return assignedBatch
        }

        // 2. Fallback: try to find any unassigned batch that this network can do
        // This handles cases where a job was dispatched before the network was fully ready or split/merge events
        val job = jobBacklog.filter { network.isInRange(it.centerPos) }
            .sortedBy { it.centerPos.distSqr(beeHive.pos) }
            .firstOrNull { j -> j.batches.any { it.status == TaskStatus.PENDING && (it.assignedNetworkId == null || it.assignedNetworkId == network.id) } }
            ?: return null

        val batch =
            job.batches.firstOrNull { it.status == TaskStatus.PENDING && (it.assignedNetworkId == null || it.assignedNetworkId == network.id) }
                ?: return null

        // Verification
        if (!network.isInRange(batch.targetPosition)) return null

        batch.assignedNetworkId = network.id
        batch.status = TaskStatus.PICKED
        return batch
    }

    @Synchronized
    fun dispatchNewJob(job: BeeJob) {
        // Prevent duplicate active jobs with same uniqueness key
        if (job.uniquenessKey != null && jobBacklog.any { it.uniquenessKey == job.uniquenessKey && it.status != JobStatus.COMPLETED && it.status != JobStatus.CANCELLED }) {
            return
        }

        val batchesToDistribute = job.batches
            .filter { it.status == TaskStatus.PENDING }
            .sortedByDescending { it.priority }

        if (batchesToDistribute.isEmpty()) return

        val allNetworks = ServerBeeNetworkManager.getNetworks()

        for (batch in batchesToDistribute) {
            // Find networks that can do this batch
            val candidateNetworks = allNetworks.filter { network ->
                val firstComp = network.components.firstOrNull()
                firstComp != null && firstComp.world == job.level &&
                        network.isInRange(batch.targetPosition) &&
                        canNetworkProvideResources(network, batch)
            }.sortedBy { network ->
                // Prefer network with closest hive
                network.hives.minOf { it.pos.distSqr(batch.targetPosition) }
            }

            val targetNetwork = candidateNetworks.firstOrNull()
            if (targetNetwork != null) {
                batch.assignedNetworkId = targetNetwork.id
                targetNetwork.dispatchBatch(batch)
            }
        }

        if (!jobBacklog.contains(job)) jobBacklog.add(job)
        this.setDirty()
    }

    private fun canNetworkProvideResources(network: BeeNetwork, batch: TaskBatch): Boolean {
        val requiredItems = batch.tasks.flatMap { it.action.requiredItems }
        if (requiredItems.isEmpty()) return true

        return requiredItems.all { req ->
            val provider = network.findProvider(req)
            provider != null
        }
    }

    override fun save(
        tag: CompoundTag,
        registries: HolderLookup.Provider
    ): CompoundTag {
        return tag
    }
}