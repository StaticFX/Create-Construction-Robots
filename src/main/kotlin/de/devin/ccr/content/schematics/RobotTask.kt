package de.devin.ccr.content.schematics

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/**
 * Represents a single task for a constructor robot.
 *
 * Tasks can be either:
 * - [TaskType.PLACE]: Place a block at a position
 * - [TaskType.REMOVE]: Remove a block from a position
 *
 * @property type The type of task to perform.
 * @property targetPos The world position where the task should be performed.
 * @property blockState The block state to place (only for [TaskType.PLACE]).
 * @property requiredItems The items required to perform the placement.
 * @property priority The priority of this task (higher values are processed first).
 */
data class RobotTask(
    val type: TaskType,
    val targetPos: BlockPos,
    val blockState: BlockState?,
    val blockEntityTag: net.minecraft.nbt.CompoundTag? = null,
    val requiredItems: List<ItemStack> = emptyList(),
    val priority: Int = 0
) {
    /**
     * Enum defining the possible types of robot tasks.
     */
    enum class TaskType {
        PLACE,
        REMOVE
    }
    
    /**
     * Enum defining the possible states of a robot task.
     */
    enum class TaskStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    var status: TaskStatus = TaskStatus.PENDING
    var assignedRobotId: Int? = null
    var jobId: UUID? = null
    
    /**
     * Checks if this task can be started based on available materials and player mode.
     *
     * @param availableItems A list of ItemStacks currently available to the robot.
     * @param isCreative Whether the player/owner is in creative mode (bypasses material check).
     * @return true if the task can be started, false otherwise.
     */
    fun canStart(availableItems: List<ItemStack>, isCreative: Boolean = false): Boolean {
        // Creative mode players can always start any task
        if (isCreative) return true
        
        // REMOVE tasks never require materials
        if (type == TaskType.REMOVE) return true
        
        // For PLACE tasks, check if we have the required items
        for (required in requiredItems) {
            val available = availableItems.sumOf { stack ->
                if (ItemStack.isSameItem(stack, required)) stack.count else 0
            }
            if (available < required.count) return false
        }
        return true
    }
    
    /**
     * Mark this task as in progress by a specific robot
     */
    fun assignToRobot(robotId: Int) {
        status = TaskStatus.IN_PROGRESS
        assignedRobotId = robotId
    }
    
    /**
     * Mark this task as completed
     */
    fun complete() {
        status = TaskStatus.COMPLETED
    }
    
    /**
     * Mark this task as failed
     */
    fun fail() {
        status = TaskStatus.FAILED
        assignedRobotId = null
    }
    
    /**
     * Cancel this task and make it available again
     */
    fun cancel() {
        status = TaskStatus.CANCELLED
        assignedRobotId = null
    }
    
    companion object {
        /**
         * Create a placement task
         */
        fun place(pos: BlockPos, state: BlockState, items: List<ItemStack>, priority: Int = 0, tag: net.minecraft.nbt.CompoundTag? = null, jobId: UUID? = null): RobotTask {
            return RobotTask(TaskType.PLACE, pos, state, tag, items, priority).apply { this.jobId = jobId }
        }
        
        /**
         * Create a removal task
         */
        fun remove(pos: BlockPos, priority: Int = 0, jobId: UUID? = null): RobotTask {
            return RobotTask(TaskType.REMOVE, pos, null, null, emptyList(), priority).apply { this.jobId = jobId }
        }
    }
}
