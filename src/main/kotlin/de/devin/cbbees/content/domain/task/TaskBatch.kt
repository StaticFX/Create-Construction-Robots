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
    var status: TaskStatus = TaskStatus.PENDING
    var assignedNetworkId: UUID? = null

    val priority: Int get() = tasks.maxOfOrNull { it.priority } ?: 0

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

    fun release(resetNetwork: Boolean = true) {
        currentIndex = 0
        status = TaskStatus.PENDING
        tasks.forEach { it.release() }
        if (resetNetwork) {
            assignedNetworkId = null
        }
    }

    fun assignToRobot(bee: MechanicalBeeEntity) {
        status = TaskStatus.IN_PROGRESS
        tasks.forEach { it.assignToRobot(bee) }
    }
}
