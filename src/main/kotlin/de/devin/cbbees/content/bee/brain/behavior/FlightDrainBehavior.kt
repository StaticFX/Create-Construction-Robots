package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBeelike
import de.devin.cbbees.config.CBBeesConfig
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.Behavior

/**
 * CORE behavior that drains spring tension while the bee is in flight.
 * Uses each bee type's own [MechanicalBeelike.consumeSpring] implementation
 * (construction bees apply BeeContext modifiers, bumble bees use flat rates).
 */
class FlightDrainBehavior : Behavior<PathfinderMob>(mapOf(), Int.MAX_VALUE) {

    override fun tick(level: ServerLevel, entity: PathfinderMob, gameTime: Long) {
        if (entity.deltaMovement.lengthSqr() > 0.001) {
            (entity as MechanicalBeelike).consumeSpring(CBBeesConfig.springDrainFlight.get())
        }
    }
}
