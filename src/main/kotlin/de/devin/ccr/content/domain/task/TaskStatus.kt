package de.devin.ccr.content.domain.task

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