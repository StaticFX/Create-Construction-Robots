package de.devin.ccr.content.domain.bee

import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.domain.job.BeeJob
import de.devin.ccr.content.domain.GlobalJobPool
import de.devin.ccr.content.domain.beehive.BeeHive
import de.devin.ccr.content.domain.beehive.PlayerBeeHive
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator

/**
 * Manages bee contributions from multiple sources to jobs.
 * 
 * This manager tracks which sources are contributing to which jobs,
 * handles the aggregation of bee counts, and coordinates the spawning
 * of bees from multiple sources for a single job.
 */
object BeeContributionManager {
    
    /**
     * Registry of all known bee sources, keyed by their source ID.
     */
    private val sources = ConcurrentHashMap<UUID, BeeHive>()
    
    /**
     * Tracks active contributions: sourceId -> set of jobIds
     */
    private val activeContributions = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    
    /**
     * Registers a bee source so it can be found by the job system.
     */
    fun registerSource(source: BeeHive) {
        sources[source.sourceId] = source
    }
    
    /**
     * Unregisters a bee source.
     */
    fun unregisterSource(sourceId: UUID) {
        sources.remove(sourceId)
        
        // Clean up any active contributions from this source
        val contributions = activeContributions.remove(sourceId) ?: return
        for (jobId in contributions) {
            GlobalJobPool.getJob(jobId)?.removeContribution(sourceId)
        }
    }
    
    /**
     * Gets a registered source by ID.
     */
    fun getSource(sourceId: UUID): BeeHive? = sources[sourceId]
    
    /**
     * Finds all sources that can contribute to a job at the given position.
     * 
     * @param level The level the job is in.
     * @param jobPos The position of the job.
     * @return List of sources that are in range of the job.
     */
    fun findSourcesForJob(level: Level, jobPos: BlockPos): List<BeeHive> {
        return sources.values.filter { source ->
            source.sourceWorld == level && source.isInRange(jobPos)
        }
    }
    
    /**
     * Calculates the total number of bees that can be contributed to a job
     * from all available sources.
     * 
     * Example: 10 Bees in Backpack + 32 bees in beehive = 42 total
     * 
     * @param level The level the job is in.
     * @param jobPos The position of the job.
     * @return Total bee count from all sources in range.
     */
    fun calculateTotalBees(level: Level, jobPos: BlockPos): Int {
        return findSourcesForJob(level, jobPos).sumOf { source ->
            minOf(source.getAvailableBeeCount(), source.getMaxContributedBees())
        }
    }
    
    /**
     * Checks if a job can be started based on available bees from all sources.
     * 
     * @param job The job to check.
     * @param level The level the job is in.
     * @return true if enough bees are available to start the job.
     */
    fun canStartJob(job: BeeJob, level: Level): Boolean {
        val totalBees = calculateTotalBees(level, job.centerPos)
        return totalBees >= job.requiredBeeCount
    }
    
    /**
     * Contributes bees from all available sources to a job.
     * 
     * This method will draw bees from multiple sources until the job's
     * required bee count is met or all sources are exhausted.
     * 
     * @param job The job to contribute to.
     * @param level The level the job is in.
     * @return The total number of bees contributed.
     */
    fun contributeToJob(job: BeeJob, level: Level): Int {
        val sourcesInRange = findSourcesForJob(level, job.centerPos)
            .sortedBy { it is PlayerBeeHive } // Prioritize stationary beehives over players
        var totalContributed = 0
        
        // Target is to have enough bees for all tasks, but at least the required amount
        val maxTargetBees = maxOf(job.requiredBeeCount, job.tasks.size)
        
        for (source in sourcesInRange) {
            val remainingNeeded = maxTargetBees - job.contributedBees
            if (remainingNeeded <= 0 && job.tasks.isNotEmpty()) break
            
            val available = source.getAvailableBeeCount()
            val maxContribution = source.getMaxContributedBees()
            val currentContribution = job.getContribution(source.sourceId)
            
            var canContribute = minOf(available, maxContribution - currentContribution)
            
            // Limit by what the job actually needs
            if (job.tasks.isNotEmpty()) {
                canContribute = minOf(canContribute, remainingNeeded)
            }
            
            if (canContribute > 0) {
                job.addContribution(source.sourceId, canContribute)
                
                // Track this contribution
                activeContributions.computeIfAbsent(source.sourceId) { 
                    Collections.synchronizedSet(mutableSetOf()) 
                }.add(job.jobId)
                
                totalContributed += canContribute
            }
        }
        
        return totalContributed
    }
    
    
    /**
     * Removes a source's contribution from a job.
     * 
     * @param sourceId The source to remove.
     * @param jobId The job to remove from.
     */
    fun removeContribution(sourceId: UUID, jobId: UUID) {
        activeContributions[sourceId]?.remove(jobId)
        GlobalJobPool.getJob(jobId)?.removeContribution(sourceId)
    }
    
    /**
     * Gets all jobs a source is currently contributing to.
     */
    fun getJobsForSource(sourceId: UUID): Set<UUID> {
        return activeContributions[sourceId]?.toSet() ?: emptySet()
    }
    
    /**
     * Cleans up contributions for completed jobs.
     */
    fun cleanup() {
        for ((sourceId, jobIds) in activeContributions) {
            val toRemove = jobIds.filter { jobId ->
                val job = GlobalJobPool.getJob(jobId)
                job == null || job.status == BeeJob.JobStatus.COMPLETED || job.status == BeeJob.JobStatus.CANCELLED
            }
            jobIds.removeAll(toRemove.toSet())
        }
    }
    
    /**
     * Gets all registered sources.
     */
    fun getAllSources(): Collection<BeeHive> = sources.values
    
    /**
     * Clears all data (used for server shutdown/restart).
     */
    fun clear() {
        sources.clear()
        activeContributions.clear()
        MechanicalBeeEntity.Companion.activeBeesPerHome.clear()
        MechanicalBeeEntity.Companion.activeHomes.clear()
    }
}
