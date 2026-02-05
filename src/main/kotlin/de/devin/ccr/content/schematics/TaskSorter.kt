package de.devin.ccr.content.schematics

import net.minecraft.core.BlockPos

/**
 * Interface for task sorting strategies.
 */
interface ITaskSorter {
    fun sort(tasks: List<RobotTask>): List<RobotTask>
}

/**
 * Standard bottom-up sorter for construction.
 */
class BottomUpSorter : ITaskSorter {
    override fun sort(tasks: List<RobotTask>): List<RobotTask> {
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
    override fun sort(tasks: List<RobotTask>): List<RobotTask> {
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
    override fun sort(tasks: List<RobotTask>): List<RobotTask> {
        return tasks.sortedBy { it.targetPos.distSqr(center) }
    }
}
