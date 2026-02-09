package de.devin.ccr.content.domain.task

import de.devin.ccr.content.domain.action.BeeAction
import de.devin.ccr.content.domain.action.impl.PlaceBlockAction
import de.devin.ccr.content.domain.action.impl.RemoveBlockAction
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
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
    var status: TaskStatus = TaskStatus.PENDING
    var assignedRobotId: Int? = null
    var jobId: UUID? = null

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
     * Release this task back to the pending pool
     */
    fun release() {
        status = TaskStatus.PENDING
        assignedRobotId = null
    }

    /**
     * Cancel this task permanently
     */
    fun cancel() {
        status = TaskStatus.CANCELLED
        assignedRobotId = null
    }

    companion object {
        /**
         * Create a placement task
         */
        fun place(pos: BlockPos, state: BlockState, items: List<ItemStack>, priority: Int = 0, tag: CompoundTag? = null, jobId: UUID? = null): BeeTask {
            return BeeTask(PlaceBlockAction(state, tag, items), pos, priority).apply { this.jobId = jobId }
        }

        /**
         * Create a removal task
         */
        fun remove(pos: BlockPos, priority: Int = 0, jobId: UUID? = null): BeeTask {
            return BeeTask(RemoveBlockAction(), pos, priority).apply { this.jobId = jobId }
        }
    }
}