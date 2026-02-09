package de.devin.ccr.content.schematics

import de.devin.ccr.content.domain.task.BeeTask
import net.minecraft.core.BlockPos

/**
 * Interface for task sorting strategies.
 */
interface ITaskSorter {
    fun sort(tasks: List<BeeTask>): List<BeeTask>
}

/**
 * Standard bottom-up sorter for construction.
 */
class BottomUpSorter : ITaskSorter {
    override fun sort(tasks: List<BeeTask>): List<BeeTask> {
        return tasks.sortedWith(compareBy(
            { -it.priority },
            { it.targetPos.y },
            { it.targetPos.x },
            { it.targetPos.z }
        ))
    }
}

/**
 * Top-down sorter for deconstruction.
 */
class TopDownSorter : ITaskSorter {
    override fun sort(tasks: List<BeeTask>): List<BeeTask> {
        return tasks.sortedWith(compareBy(
            { -it.priority },
            { -it.targetPos.y },
            { it.targetPos.x },
            { it.targetPos.z }
        ))
    }
}

/**
 * Sorter that prioritizes proximity to a specific position.
 */
class DistanceSorter(val center: BlockPos) : ITaskSorter {
    override fun sort(tasks: List<BeeTask>): List<BeeTask> {
        return tasks.sortedBy { it.targetPos.distSqr(center) }
    }
}
