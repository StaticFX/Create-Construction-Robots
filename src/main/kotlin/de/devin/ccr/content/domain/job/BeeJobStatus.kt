package de.devin.ccr.content.domain.job

/**
 * Status of this job.
 */
enum class JobStatus {
    /** Waiting for enough bees to start */
    WAITING_FOR_BEES,
    /** Job is actively being worked on */
    IN_PROGRESS,
    /** All tasks completed */
    COMPLETED,
    /** Job was cancelled */
    CANCELLED
}