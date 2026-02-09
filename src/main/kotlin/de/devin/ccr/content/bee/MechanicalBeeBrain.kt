package de.devin.ccr.content.bee

import de.devin.ccr.content.domain.bee.InternalBeeState
import de.devin.ccr.content.domain.beehive.BeeHive
import de.devin.ccr.content.domain.task.BeeTask

/**
 * This serves as the core logic and mapping of a mechanical bee entity
 */
class MechanicalBeeBrain(
    val mechanicalBee: MechanicalBeeEntity
) {
    lateinit var beehive: BeeHive
    var currentState: InternalBeeState = InternalBeeState.IDLE
    var currentTask: BeeTask? = null
}