package de.devin.cbbees.content.domain.task

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.domain.job.BeeJob
import net.minecraft.core.BlockPos
import java.util.UUID

class TaskBatch(
    val tasks: List<BeeTask>,
    val job: BeeJob,
    val targetPosition: BlockPos
) {
    companion object {
        const val MAX_RETRIES = 5
        /** Minimum ticks before a released batch can be re-dispatched (3 seconds). */
        const val RETRY_COOLDOWN_TICKS = 60L
    }

    var status: TaskStatus = TaskStatus.PENDING
    var assignedNetworkId: UUID? = null
    /** UUID of the bee currently working on this batch. */
    var assignedBeeId: UUID? = null

    val priority: Int get() = tasks.maxOfOrNull { it.priority } ?: 0

    /** How many times this batch has been released after a failure. */
    var retryCount: Int = 0
        private set

    /** Game tick when this batch was last released. Used for cooldown. */
    var lastReleasedTick: Long = 0L
        private set

    /** Game tick when this batch was picked up or started. Used for stale detection. */
    var startedAtTick: Long = 0L
        private set

    private var currentIndex = 0

    val primaryTask: BeeTask? get() = tasks.firstOrNull()

    fun getCurrentTask(): BeeTask? = if (currentIndex < tasks.size) tasks[currentIndex] else null

    fun advance(): Boolean {
        currentIndex++
        if (currentIndex >= tasks.size) {
            status = TaskStatus.COMPLETED
            return false
        }
        return true
    }

    fun getRemainingTasks(): List<BeeTask> = tasks.subList(currentIndex, tasks.size)

    fun isComplete(): Boolean = currentIndex >= tasks.size

    /** Whether this batch can be retried (hasn't exceeded max retries). */
    fun canRetry(): Boolean = retryCount < MAX_RETRIES

    /** Whether the cooldown period has elapsed since last release. */
    fun isCooldownElapsed(currentTick: Long): Boolean =
        currentTick - lastReleasedTick >= RETRY_COOLDOWN_TICKS

    fun release(resetNetwork: Boolean = true, gameTick: Long = 0L) {
        currentIndex = 0
        assignedBeeId = null
        tasks.forEach { it.release() }
        retryCount++
        lastReleasedTick = gameTick
        if (retryCount >= MAX_RETRIES) {
            status = TaskStatus.FAILED
        } else {
            status = TaskStatus.PENDING
        }
        if (resetNetwork) {
            assignedNetworkId = null
        }
    }

    fun assignToRobot(bee: MechanicalBeeEntity) {
        status = TaskStatus.IN_PROGRESS
        assignedBeeId = bee.uuid
        startedAtTick = bee.level().gameTime
        tasks.forEach { it.assignToRobot(bee) }
    }
}
