package de.devin.cbbees.content.bee

import de.devin.cbbees.content.domain.network.BeeNetwork
import net.minecraft.core.BlockPos

/**
 * Common interface for bee entities that belong to a network and have a walk target.
 *
 * Implemented by both [MechanicalBeeEntity] and [MechanicalBumbleBeeEntity] so that
 * client-side rendering (target lines, debug overlays) can handle them uniformly.
 */
interface NetworkedBee {
    fun getTargetPos(): BlockPos?
    fun network(): BeeNetwork?
}
