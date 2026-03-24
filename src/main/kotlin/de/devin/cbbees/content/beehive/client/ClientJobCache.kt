package de.devin.cbbees.content.beehive.client

import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.content.domain.job.HiveSnapshot
import net.minecraft.core.BlockPos

object ClientJobCache {
    private val byHive = mutableMapOf<BlockPos, HiveSnapshot>()

    /** Incremented on every update so renderers can detect changes. */
    var version = 0L
        private set

    /** Cached job list — rebuilt only when version changes. */
    private var cachedJobs: List<ClientJobInfo> = emptyList()
    private var cachedJobsVersion = -1L

    /** Job IDs from the most recent player snapshot — used to detect cancelled/completed jobs. */
    private var knownPlayerJobIds: Set<java.util.UUID> = emptySet()

    fun update(pos: BlockPos, snapshot: HiveSnapshot) {
        if (pos == BlockPos.ZERO) {
            // Player snapshot is authoritative for player-owned jobs.
            // Purge jobs from stale hive snapshots that the player no longer owns
            // (cancelled or completed since the hive snapshot was last refreshed).
            val currentIds = snapshot.jobs.map { it.jobId }.toSet()
            val removedIds = knownPlayerJobIds - currentIds
            if (removedIds.isNotEmpty()) {
                for ((hivePos, hiveSnapshot) in byHive) {
                    if (hivePos == BlockPos.ZERO) continue
                    val filtered = hiveSnapshot.jobs.filter { it.jobId !in removedIds }
                    if (filtered.size != hiveSnapshot.jobs.size) {
                        byHive[hivePos] = HiveSnapshot(hiveSnapshot.networkInfo, filtered)
                    }
                }
            }
            knownPlayerJobIds = currentIds
        }
        byHive[pos] = snapshot
        version++
    }

    fun get(pos: BlockPos): HiveSnapshot? = byHive[pos]

    fun getAllJobs(): List<ClientJobInfo> {
        if (cachedJobsVersion != version) {
            cachedJobs = byHive.values.flatMap { it.jobs }.distinctBy { it.jobId }
            cachedJobsVersion = version
        }
        return cachedJobs
    }
}
