package de.devin.ccr.content.domain.task

import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.domain.action.BeeAction
import de.devin.ccr.content.domain.action.impl.PlaceBlockAction
import de.devin.ccr.content.domain.action.impl.RemoveBlockAction
import de.devin.ccr.content.domain.job.BeeJob
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/**
 * Represents a single task for a single bee.
 *
 * @property action The action to perform.
 * @property targetPos The world position where the task should be performed.
 * @property priority The priority of this task (higher values are processed first).
 */
data class BeeTask(
    val action: BeeAction,
    val targetPos: BlockPos,
    val job: BeeJob,
    val priority: Int = 0,
) {
    var status: TaskStatus = TaskStatus.PENDING
    var mechanicalBee: MechanicalBeeEntity? = null

    /**
     * The unique identifier for the job this task belongs to.
     */
    val jobId: UUID get() = job.jobId

    /**
     * Mark this task as in progress by a specific robot
     */
    fun assignToRobot(mechanicalBeeEntity: MechanicalBeeEntity) {
        status = TaskStatus.IN_PROGRESS
        mechanicalBee = mechanicalBeeEntity
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
        mechanicalBee = null
    }

    /**
     * Release this task back to the pending pool
     */
    fun release() {
        status = TaskStatus.PENDING
        mechanicalBee = null
    }

    /**
     * Cancel this task permanently
     */
    fun cancel() {
        status = TaskStatus.CANCELLED
        mechanicalBee = null
    }

    companion object {
        /**
         * Create a placement task
         */
        fun place(
            pos: BlockPos,
            state: BlockState,
            items: List<ItemStack>,
            priority: Int = 0,
            tag: CompoundTag? = null,
            job: BeeJob
        ): BeeTask {
            val action = PlaceBlockAction(state, tag, items)
            return BeeTask(action, pos, job, priority)
        }

        /**
         * Create a removal task
         */
        fun remove(pos: BlockPos, priority: Int = 0, job: BeeJob): BeeTask {
            return BeeTask(RemoveBlockAction(), pos, job, priority)
        }
    }
}