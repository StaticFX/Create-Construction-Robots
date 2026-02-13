package de.devin.ccr.content.domain.task

import de.devin.ccr.content.domain.job.BeeJob

class TaskBatch(
    val tasks: List<BeeTask>,
    val job: BeeJob
) {
    private var currentIndex = 0

    val primaryTask: BeeTask? get() = tasks.firstOrNull()

    fun getCurrentTask(): BeeTask? = if (currentIndex < tasks.size) tasks[currentIndex] else null

    fun advance(): Boolean {
        currentIndex++
        return currentIndex < tasks.size
    }

    fun isComplete(): Boolean = currentIndex >= tasks.size
}
