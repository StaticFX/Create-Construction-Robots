package de.devin.cbbees.content.domain

import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobStatus
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData
import de.devin.cbbees.util.ServerSide

/**
 * Global Bee Job distribution pool.
 *
 */
@ServerSide
object GlobalJobPool : SavedData() {
    private val jobBacklog = mutableListOf<BeeJob>()
    private var redispatchCounter = 0
    /** Redispatch every 4 calls of tick() = every 2 seconds (tick() is called every 10 server ticks). */
    private const val REDISPATCH_INTERVAL = 4

    fun clear() {
        jobBacklog.clear()
        redispatchCounter = 0
    }

    fun tick(gameTime: Long = 0L) {
        if (jobBacklog.removeIf { it.status == JobStatus.COMPLETED || it.status == JobStatus.CANCELLED }) {
            this.setDirty()
        }

        redispatchCounter++
        if (redispatchCounter >= REDISPATCH_INTERVAL) {
            redispatchCounter = 0
            redispatchPendingBatches(gameTime)
        }
    }

    /**
     * Scans for PENDING batches and dispatches them to networks that have available bees.
     * Handles both:
     * - Retrying failed/released batches
     * - Assigning work to newly available bees in hives
     */
    private fun redispatchPendingBatches(gameTime: Long) {
        val allNetworks = ServerBeeNetworkManager.getNetworks()
        if (allNetworks.isEmpty()) return

        for (job in jobBacklog) {
            if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) continue

            for (batch in job.batches) {
                if (batch.status != TaskStatus.PENDING) continue
                if (!batch.canRetry()) continue
                if (!batch.isCooldownElapsed(gameTime)) continue

                val candidateNetworks = allNetworks.filter { network ->
                    val firstComp = network.components.firstOrNull()
                    firstComp != null && firstComp.world == job.level &&
                            network.isInRange(batch.targetPosition) &&
                            network.hives.any { it.getAvailableBeeCount() > 0 }
                }.sortedBy { network ->
                    network.hives.minOfOrNull { it.pos.distSqr(batch.targetPosition) } ?: Double.MAX_VALUE
                }

                val targetNetwork = candidateNetworks.firstOrNull() ?: continue
                batch.assignedNetworkId = targetNetwork.id
                targetNetwork.dispatchBatch(batch)
            }
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
                        network.isInRange(batch.targetPosition)
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

    override fun save(
        tag: CompoundTag,
        registries: HolderLookup.Provider
    ): CompoundTag {
        return tag
    }
}