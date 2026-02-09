package de.devin.ccr.content.domain

import de.devin.ccr.content.domain.job.BeeJob
import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.domain.beehive.BeeHive
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Global registry of jobs that BeeSource instances can contribute to and draw from.
 * 
 * The GlobalJobPool manages all active jobs across the server, organized by dimension
 * and chunk position for efficient spatial queries. Multiple BeeSource instances can
 * find and contribute to jobs within their range.
 * 
 * ### Bee Work Hierarchy
 * The bee work system is organized into three levels:
 * 1. **JobPool** (GlobalJobPool): Manages all active jobs in the world and handles spatial lookups.
 * 2. **Job** (BeeJob): Represents a specific assignment (e.g., building a schematic) and 
 *    tracks contributions from multiple sources.
 * 3. **Tasks** (BeeTask): Individual atomic actions (e.g., placing a single block) that 
 *    make up a Job.
 */
object GlobalJobPool {
    
    /**
     * All registered jobs, keyed by job ID.
     */
    private val jobs = ConcurrentHashMap<UUID, BeeJob>()
    
    /**
     * Jobs organized by dimension and chunk position for spatial queries.
     * Structure: dimensionKey -> chunkPos -> set of job IDs
     */
    private val jobsByChunk = ConcurrentHashMap<String, ConcurrentHashMap<ChunkPos, MutableSet<UUID>>>()
    
    /**
     * Registers a new job in the pool.
     * 
     * @param job The job to register.
     * @param level The level/dimension this job is in.
     */
    fun registerJob(job: BeeJob, level: Level) {
        jobs[job.jobId] = job
        
        val dimKey = level.dimension().location().toString()
        val chunkPos = ChunkPos(job.centerPos)
        
        jobsByChunk.computeIfAbsent(dimKey) { ConcurrentHashMap() }
            .computeIfAbsent(chunkPos) { Collections.synchronizedSet(mutableSetOf()) }
            .add(job.jobId)
    }
    
    /**
     * Unregisters a job from the pool.
     * 
     * @param jobId The ID of the job to remove.
     */
    fun unregisterJob(jobId: UUID) {
        val job = jobs.remove(jobId) ?: return
        
        // Remove from chunk index
        jobsByChunk.values.forEach { chunkMap ->
            chunkMap.values.forEach { jobSet ->
                jobSet.remove(jobId)
            }
        }
    }
    
    /**
     * Gets a job by its ID.
     */
    fun getJob(jobId: UUID): BeeJob? = jobs[jobId]

    /**
     * Gets a job by its uniqueness key.
     */
    fun getJobByUniquenessKey(key: Any): BeeJob? {
        return jobs.values.find { it.uniquenessKey == key }
    }
    
    /**
     * Finds all jobs within range of a position.
     * 
     * @param level The level to search in.
     * @param pos The center position.
     * @param range The search range.
     * @return List of jobs within range.
     */
    fun findJobsInRange(level: Level, pos: BlockPos, range: Double): List<BeeJob> {
        val dimKey = level.dimension().location().toString()
        val chunkMap = jobsByChunk[dimKey] ?: return emptyList()
        
        val result = mutableListOf<BeeJob>()
        val chunkRange = (range / 16).toInt() + 1
        val centerChunk = ChunkPos(pos)
        
        // Search nearby chunks
        for (cx in -chunkRange..chunkRange) {
            for (cz in -chunkRange..chunkRange) {
                val chunkPos = ChunkPos(centerChunk.x + cx, centerChunk.z + cz)
                val jobIds = chunkMap[chunkPos] ?: continue
                
                for (jobId in jobIds) {
                    val job = jobs[jobId] ?: continue
                    
                    // Check actual distance
                    val dx = job.centerPos.x - pos.x
                    val dy = job.centerPos.y - pos.y
                    val dz = job.centerPos.z - pos.z
                    val distSq = dx * dx + dy * dy + dz * dz
                    
                    if (distSq <= range * range) {
                        result.add(job)
                    }
                }
            }
        }
        
        return result
    }
    
    /**
     * Finds jobs that a BeeSource can contribute to.
     * 
     * @param source The bee source looking for jobs.
     * @return List of jobs within the source's work range.
     */
    fun findJobsForSource(source: BeeHive): List<BeeJob> {
        return findJobsInRange(source.sourceWorld, source.sourcePosition, source.getWorkRange())
    }
    
    /**
     * Contributes bees from a source to a job.
     * 
     * @param jobId The job to contribute to.
     * @param source The source contributing bees.
     * @param beeCount The number of bees to contribute.
     * @return true if the contribution was successful.
     */
    fun contributeToJob(jobId: UUID, source: BeeHive, beeCount: Int): Boolean {
        val job = jobs[jobId] ?: return false
        
        // Check if source is in range
        if (!source.isInRange(job.centerPos)) return false
        
        // Check if source can contribute more bees
        val currentContribution = job.getContribution(source.sourceId)
        val maxContribution = source.getMaxContributedBees()
        val canContribute = minOf(beeCount, maxContribution - currentContribution)
        
        if (canContribute <= 0) return false
        
        job.addContribution(source.sourceId, canContribute)
        return true
    }
    
    /**
     * Gets a task for a bee from a specific source.
     * 
     * @param source The source the bee belongs to.
     * @param robotId The ID of the robot requesting the task.
     * @return A task to work on, or null if no tasks available.
     */
    fun getTaskForBee(source: BeeHive, robotId: Int): BeeTask? {
        val jobsInRange = findJobsForSource(source)
        
        // Prioritize jobs that are in progress and have tasks
        for (job in jobsInRange.sortedByDescending { it.getContribution(source.sourceId) }) {
            if (job.status == BeeJob.JobStatus.IN_PROGRESS || job.canStart()) {
                val task = job.claimNextTask(robotId)
                if (task != null) return task
            }
        }
        
        return null
    }
    
    /**
     * Aggregates the total bee count from all sources that can reach a position.
     * 
     * @param level The level to search in.
     * @param jobPos The position of the job.
     * @param sources List of available bee sources.
     * @return Total number of bees that can be contributed.
     */
    fun aggregateFromSources(level: Level, jobPos: BlockPos, sources: List<BeeHive>): Int {
        return sources
            .filter { it.sourceWorld == level && it.isInRange(jobPos) }
            .sumOf { minOf(it.getAvailableBeeCount(), it.getMaxContributedBees()) }
    }
    
    /**
     * Cleans up completed or cancelled jobs.
     */
    fun cleanup() {
        val toRemove = jobs.values
            .filter { it.status == BeeJob.JobStatus.COMPLETED || it.status == BeeJob.JobStatus.CANCELLED }
            .map { it.jobId }
        
        toRemove.forEach { unregisterJob(it) }
    }
    
    /**
     * Gets all active jobs.
     */
    fun getAllJobs(): Collection<BeeJob> = jobs.values
    
    /**
     * Gets the count of active jobs.
     */
    fun getJobCount(): Int = jobs.size
    
    /**
     * Clears all jobs (used for server shutdown/restart).
     */
    fun clear() {
        jobs.clear()
        jobsByChunk.clear()
    }
}
