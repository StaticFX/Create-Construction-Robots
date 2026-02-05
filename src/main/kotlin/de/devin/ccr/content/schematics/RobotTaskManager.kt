package de.devin.ccr.content.schematics

import net.minecraft.world.item.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages the task queue for constructor robots.
 *
 * This manager is responsible for:
 * - Maintaining a thread-safe queue of pending construction and deconstruction tasks.
 * - Assigning appropriate tasks to robots based on material availability.
 * - Tracking task completion and failure statistics.
 * - Sorting tasks to ensure stable building (bottom-up) and dismantling (top-down).
 */
class RobotTaskManager {
    
    private val pendingTasks: Queue<RobotTask> = ConcurrentLinkedQueue()
    private val activeTasks: MutableMap<Int, RobotTask> = mutableMapOf()
    
    // Track stats per jobId
    private val jobTotalTasks = mutableMapOf<UUID, Int>()
    private val jobCompletedTasks = mutableMapOf<UUID, Int>()
    private val jobWorkingTaskCount = mutableMapOf<UUID, Int>()
    
    // Statistics
    var totalTasksGenerated: Int = 0
        private set
    var tasksCompleted: Int = 0
        private set
    var tasksFailed: Int = 0
        private set
    
    /**
     * Add a single task to the queue
     */
    fun addTask(task: RobotTask) {
        pendingTasks.add(task)
        totalTasksGenerated++
        task.jobId?.let { id ->
            jobTotalTasks[id] = jobTotalTasks.getOrDefault(id, 0) + 1
            jobWorkingTaskCount[id] = jobWorkingTaskCount.getOrDefault(id, 0) + 1
        }
    }
    
    /**
     * Add multiple tasks to the queue
     */
    fun addTasks(tasks: List<RobotTask>) {
        tasks.forEach { addTask(it) }
    }
    
    /**
     * Get the next available task for a robot.
     * 
     * @param robotId The ID of the robot requesting a task.
     * @param availableItems Items available for the robot to use.
     * @param isCreative Whether the player/owner is in creative mode.
     * @return The next task, or null if no tasks are available.
     */
    fun getNextTask(robotId: Int, availableItems: List<ItemStack>, isCreative: Boolean = false): RobotTask? {
        val task = pendingTasks.find { it.canStart(availableItems, isCreative) } ?: return null
        
        pendingTasks.remove(task)
        task.assignToRobot(robotId)
        activeTasks[robotId] = task
        return task
    }
    
    /**
     * Mark a task as completed
     */
    fun completeTask(robotId: Int) {
        val task = activeTasks.remove(robotId)
        if (task != null) {
            task.complete()
            tasksCompleted++
            task.jobId?.let { id ->
                jobCompletedTasks[id] = jobCompletedTasks.getOrDefault(id, 0) + 1
                
                // Update working count
                val remaining = jobWorkingTaskCount.getOrDefault(id, 0) - 1
                if (remaining <= 0) {
                    jobWorkingTaskCount.remove(id)
                } else {
                    jobWorkingTaskCount[id] = remaining
                }
            }
        }
    }
    
    /**
     * Mark a task as failed and optionally re-queue it
     */
    fun failTask(robotId: Int, requeue: Boolean = true) {
        val task = activeTasks.remove(robotId)
        if (task != null) {
            task.fail()
            if (requeue) {
                task.status = RobotTask.TaskStatus.PENDING
                pendingTasks.add(task)
            } else {
                tasksFailed++
                task.jobId?.let { id ->
                    // Update working count for non-requeued failures
                    val remaining = jobWorkingTaskCount.getOrDefault(id, 0) - 1
                    if (remaining <= 0) {
                        jobWorkingTaskCount.remove(id)
                    } else {
                        jobWorkingTaskCount[id] = remaining
                    }
                }
            }
        }
    }
    
    /**
     * Cancel all tasks and clear the queue
     */
    fun cancelAll() {
        pendingTasks.forEach { it.cancel() }
        pendingTasks.clear()
        
        activeTasks.values.forEach { it.cancel() }
        activeTasks.clear()

        jobTotalTasks.clear()
        jobCompletedTasks.clear()
        jobWorkingTaskCount.clear()
    }
    
    /**
     * Get the current task for a robot
     */
    fun getCurrentTask(robotId: Int): RobotTask? = activeTasks[robotId]
    
    /**
     * Check if there are any pending tasks
     */
    fun hasPendingTasks(): Boolean = pendingTasks.isNotEmpty()
    
    /**
     * Check if there are any active tasks
     */
    fun hasActiveTasks(): Boolean = activeTasks.isNotEmpty()
    
    /**
     * Check if all tasks are complete
     */
    fun isComplete(): Boolean = pendingTasks.isEmpty() && activeTasks.isEmpty()
    
    /**
     * Get the number of pending tasks
     */
    fun getPendingCount(): Int = pendingTasks.size
    
    /**
     * Get the number of active tasks
     */
    fun getActiveCount(): Int = activeTasks.size
    
    /**
     * Get progress as a percentage (0.0 to 1.0) for active jobs.
     */
    fun getProgress(): Float {
        val jobProgress = getActiveJobProgress()
        val total = jobProgress.values.sumOf { it.second }
        if (total == 0) return 0f
        return jobProgress.values.sumOf { it.first }.toFloat() / total.toFloat()
    }
    
    /**
     * Get descriptions of active tasks (max 3) for display in UI.
     */
    fun getActiveTaskDescriptions(maxCount: Int = 3): List<String> {
        return activeTasks.values.take(maxCount).map { task ->
            val pos = task.targetPos
            val posStr = "(${pos.x}, ${pos.y}, ${pos.z})"
            when (task.type) {
                RobotTask.TaskType.PLACE -> {
                    val blockName = task.blockState?.block?.name?.string ?: "block"
                    "Placing $blockName at $posStr"
                }
                RobotTask.TaskType.REMOVE -> "Removing block at $posStr"
            }
        }
    }
    
    /**
     * Get data for all active jobs (jobs that have pending or active tasks)
     */
    fun getActiveJobProgress(): Map<UUID, Pair<Int, Int>> {
        return jobWorkingTaskCount.keys.associateWith { id ->
            (jobCompletedTasks[id] ?: 0) to (jobTotalTasks[id] ?: 0)
        }
    }

    /**
     * Get total tasks for currently active jobs
     */
    fun getActiveTotalTasks(): Int {
        return getActiveJobProgress().values.sumOf { it.second }
    }

    /**
     * Get completed tasks for currently active jobs
     */
    fun getActiveCompletedTasks(): Int {
        return getActiveJobProgress().values.sumOf { it.first }
    }
    
    /**
     * Sort pending tasks using a specific strategy.
     */
    fun sortTasks(sorter: ITaskSorter) {
        val sorted = sorter.sort(pendingTasks.toList())
        pendingTasks.clear()
        pendingTasks.addAll(sorted)
    }

    /**
     * Sort pending tasks by priority and Y-level
     * For building: lower Y first (bottom-up)
     * For removal: higher Y first (top-down)
     */
    fun sortTasks() {
        // Fallback for when we don't know the intent, though usually we should use specific sorters
        val sorted = pendingTasks.sortedWith(compareBy(
            { -it.priority }, // Higher priority first
            { task -> 
                when (task.type) {
                    RobotTask.TaskType.PLACE -> task.targetPos.y  // Bottom-up for placing
                    RobotTask.TaskType.REMOVE -> -task.targetPos.y // Top-down for removing
                }
            }
        ))
        pendingTasks.clear()
        pendingTasks.addAll(sorted)
    }
    
    companion object {
    }
}
