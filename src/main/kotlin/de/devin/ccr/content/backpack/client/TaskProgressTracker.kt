package de.devin.ccr.content.backpack.client
import java.util.UUID

/**
 * Client-side tracker for robot task progress.
 * 
 * This object holds the latest task progress data received from the server
 * and is used by BackpackScreen to render status toasts.
 */
object TaskProgressTracker {
    
    /** Total number of tasks across all jobs */
    var globalTotal: Int = 0
        private set
    
    /** Number of tasks completed across all jobs */
    var globalCompleted: Int = 0
        private set
    
    /** Progress per job (jobId -> (completed, total)) */
    var jobProgress: Map<UUID, Pair<Int, Int>> = emptyMap()
        private set
    
    /** Timestamp of last update (client tick) */
    var lastUpdateTick: Long = 0
        private set
    
    /**
     * Updates the tracker with new data from the server.
     */
    fun update(
        globalTotal: Int,
        globalCompleted: Int,
        jobProgress: Map<UUID, Pair<Int, Int>>
    ) {
        this.globalTotal = globalTotal
        this.globalCompleted = globalCompleted
        this.jobProgress = jobProgress
        this.lastUpdateTick = System.currentTimeMillis()
    }
    
    /**
     * Clears all tracked data.
     */
    fun clear() {
        globalTotal = 0
        globalCompleted = 0
        jobProgress = emptyMap()
        lastUpdateTick = 0
    }
    
    /**
     * Returns true if there are any tasks being tracked.
     */
    fun hasActiveTasks(): Boolean = globalTotal > 0 && globalCompleted < globalTotal
    
    /**
     * Returns the global progress as a value between 0.0 and 1.0.
     */
    fun getGlobalProgress(): Float {
        if (globalTotal == 0) return 0f
        return globalCompleted.toFloat() / globalTotal.toFloat()
    }
    
    /**
     * Returns true if data is recent (within last 2 seconds).
     * Used to determine if the toast should be shown.
     */
    fun isDataRecent(): Boolean {
        return System.currentTimeMillis() - lastUpdateTick < 2000
    }
}
