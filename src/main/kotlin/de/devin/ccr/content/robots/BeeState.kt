package de.devin.ccr.content.robots

/**
 * Possible states for a constructor robot's state machine.
 */
enum class BeeState {
    /** Waiting for a new task or returning to player */
    IDLE,
    /** Moving to player or storage to collect required items */
    FETCHING_ITEMS,
    /** Moving to the target work position */
    TRAVELING_TO_WORK,
    /** Performing the actual placement or removal */
    WORKING,
    /** Returning to the player's backpack after work is done */
    RETURNING_TO_PLAYER
}
