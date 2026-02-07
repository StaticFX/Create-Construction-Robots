package de.devin.ccr.content.schematics

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/**
 * Represents a single task for a constructor robot.
 *
 * @property action The action to perform.
 * @property targetPos The world position where the task should be performed.
 * @property priority The priority of this task (higher values are processed first).
 * @property requiredBees The number of bees required to complete this task (default 1).
 */
data class BeeTask(
    val action: BeeAction,
    val targetPos: BlockPos,
    val priority: Int = 0,
    val requiredBees: Int = 1
) {
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
        
        val reqItems = action.requiredItems
        if (reqItems.isEmpty()) return true
        
        // Check if we have the required items
        for (required in reqItems) {
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
        fun place(pos: BlockPos, state: BlockState, items: List<ItemStack>, priority: Int = 0, tag: net.minecraft.nbt.CompoundTag? = null, jobId: UUID? = null): BeeTask {
            return BeeTask(PlaceAction(state, tag, items), pos, priority).apply { this.jobId = jobId }
        }
        
        /**
         * Create a removal task
         */
        fun remove(pos: BlockPos, priority: Int = 0, jobId: UUID? = null): BeeTask {
            return BeeTask(RemoveAction(), pos, priority).apply { this.jobId = jobId }
        }
    }
}
