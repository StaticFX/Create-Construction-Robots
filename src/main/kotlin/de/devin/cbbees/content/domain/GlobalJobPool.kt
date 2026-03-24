package de.devin.cbbees.content.domain

import de.devin.cbbees.CreateBuzzyBeez
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
    private var watchdogCounter = 0
    /** Redispatch every 4 calls of tick() = every 4 seconds (tick() is called every 10 server ticks). */
    private const val REDISPATCH_INTERVAL = 4
    /** Watchdog runs every 20 calls = every 10 seconds. */
    private const val WATCHDOG_INTERVAL = 20
    /** Batches stuck in IN_PROGRESS/PICKED for longer than this are released (30 seconds). */
    private const val STALE_BATCH_TICKS = 600L

    fun clear() {
        jobBacklog.clear()
        redispatchCounter = 0
        watchdogCounter = 0
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

        watchdogCounter++
        if (watchdogCounter >= WATCHDOG_INTERVAL) {
            watchdogCounter = 0
            healStaleBatches(gameTime)
        }
    }

    /**
     * Self-healing watchdog: detects batches stuck in IN_PROGRESS or PICKED state
     * where the assigned bee no longer exists, and releases them for retry.
     */
    private fun healStaleBatches(gameTime: Long) {
        var healedCount = 0

        for (job in jobBacklog) {
            if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) continue

            for (batch in job.batches) {
                if (batch.status != TaskStatus.IN_PROGRESS && batch.status != TaskStatus.PICKED) continue
                if (batch.startedAtTick == 0L) continue // legacy batch without timestamp

                val elapsed = gameTime - batch.startedAtTick
                if (elapsed < STALE_BATCH_TICKS) continue

                // Check if the assigned bee still exists
                val beeId = batch.assignedBeeId
                val serverLevel = job.level as? net.minecraft.server.level.ServerLevel
                val beeAlive = if (beeId != null && serverLevel != null) {
                    serverLevel.getEntity(beeId)?.isAlive == true
                } else false

                if (!beeAlive) {
                    batch.release(gameTick = gameTime)
                    healedCount++
                }
            }
        }

        // Also clean up orphaned active bee tracking in hives
        cleanupOrphanedBees(gameTime)

        if (healedCount > 0) {
            CreateBuzzyBeez.LOGGER.debug("[Watchdog] Healed $healedCount stale batches")
        }
    }

    /**
     * Scans all hives and removes active bee entries for entities that no longer exist.
     * This prevents hives from thinking they have active bees when the entities are gone.
     */
    private fun cleanupOrphanedBees(gameTime: Long) {
        for (network in ServerBeeNetworkManager.getNetworks()) {
            for (hive in network.hives) {
                if (hive is de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity) {
                    hive.cleanupOrphanedBees()
                } else if (hive is de.devin.cbbees.content.domain.beehive.PortableBeeHive) {
                    hive.cleanupOrphanedBees()
                }
            }
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
        val gameTime = (beeHive.world as? net.minecraft.server.level.ServerLevel)?.gameTime ?: 0L

        fun isDispatchable(batch: TaskBatch): Boolean =
            batch.status == TaskStatus.PENDING && batch.canRetry() && batch.isCooldownElapsed(gameTime)

        // 1. Find batches already assigned to this network
        val assignedBatch = jobBacklog.flatMap { it.batches }
            .filter { isDispatchable(it) && it.assignedNetworkId == network.id }
            .minByOrNull { it.targetPosition.distSqr(beeHive.pos) }

        if (assignedBatch != null) {
            assignedBatch.status = TaskStatus.PICKED
            return assignedBatch
        }

        // 2. Fallback: try to find any unassigned batch that this network can do
        // This handles cases where a job was dispatched before the network was fully ready or split/merge events
        val job = jobBacklog.filter { network.isInRange(it.centerPos) }
            .sortedBy { it.centerPos.distSqr(beeHive.pos) }
            .firstOrNull { j -> j.batches.any { isDispatchable(it) && (it.assignedNetworkId == null || it.assignedNetworkId == network.id) } }
            ?: return null

        val batch =
            job.batches.firstOrNull { isDispatchable(it) && (it.assignedNetworkId == null || it.assignedNetworkId == network.id) }
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
        var assignedCount = 0
        var unassignedCount = 0

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
                assignedCount++
            } else {
                unassignedCount++
            }
        }

        CreateBuzzyBeez.LOGGER.debug("[JobPool] Dispatched job: ${batchesToDistribute.size} batches, $assignedCount assigned, $unassignedCount unassigned, ${allNetworks.size} networks available")

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