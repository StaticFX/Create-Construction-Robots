package de.devin.ccr.content.domain.task

/**
 * Enum defining the possible states of a be task.
 */
enum class TaskStatus {
    /**
     * Task is waiting to be picked up by a hive.
     */
    PENDING,

    /**
     * Task has been picked up distributed to a hive and it worker bee
     */
    PICKED,

    /**
     * Bee is working on the task.
     */
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}