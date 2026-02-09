package de.devin.ccr.content.domain.bee

/**
 * Internal state of a bee
 */
enum class InternalBeeState {
    /** Waiting for a new task or returning to player */
    IDLE,
    /** Moving to player or storage to collect required items */
    FETCHING_ITEMS,
    /** Moving to the target work position */
    TRAVELING_TO_WORK,
    /** Performing the actual placement or removal */
    WORKING,
    /** Returning to the player's backpack after work is done */
    RETURNING_TO_HOME
}