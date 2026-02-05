package de.devin.ccr.content.backpack.client

/**
 * Client-side tracker for robot task progress.
 * 
 * This object holds the latest task progress data received from the server
 * and is used by BackpackScreen to render status toasts.
 */
object TaskProgressTracker {
    
    /** Total number of tasks generated for the current job */
    var totalTasks: Int = 0
        private set
    
    /** Number of tasks completed */
    var completedTasks: Int = 0
        private set
    
    /** Number of currently active tasks (robots working) */
    var activeTasks: Int = 0
        private set
    
    /** Number of tasks waiting to be assigned */
    var pendingTasks: Int = 0
        private set
    
    /** Descriptions of active tasks (max 3) */
    var taskDescriptions: List<String> = emptyList()
        private set
    
    /** Timestamp of last update (client tick) */
    var lastUpdateTick: Long = 0
        private set
    
    /**
     * Updates the tracker with new data from the server.
     */
    fun update(
        totalTasks: Int,
        completedTasks: Int,
        activeTasks: Int,
        pendingTasks: Int,
        taskDescriptions: List<String>
    ) {
        this.totalTasks = totalTasks
        this.completedTasks = completedTasks
        this.activeTasks = activeTasks
        this.pendingTasks = pendingTasks
        this.taskDescriptions = taskDescriptions.take(3) // Max 3 shown
        this.lastUpdateTick = System.currentTimeMillis()
    }
    
    /**
     * Clears all tracked data.
     */
    fun clear() {
        totalTasks = 0
        completedTasks = 0
        activeTasks = 0
        pendingTasks = 0
        taskDescriptions = emptyList()
        lastUpdateTick = 0
    }
    
    /**
     * Returns true if there are any tasks being tracked.
     */
    fun hasActiveTasks(): Boolean = totalTasks > 0 && completedTasks < totalTasks
    
    /**
     * Returns the progress as a value between 0.0 and 1.0.
     */
    fun getProgress(): Float {
        if (totalTasks == 0) return 0f
        return completedTasks.toFloat() / totalTasks.toFloat()
    }
    
    /**
     * Returns true if data is recent (within last 2 seconds).
     * Used to determine if the toast should be shown.
     */
    fun isDataRecent(): Boolean {
        return System.currentTimeMillis() - lastUpdateTick < 2000
    }
}
