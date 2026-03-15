package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.schedule.Activity

class UpdateBumbleStatusBehavior : Behavior<MechanicalBumbleBeeEntity>(mapOf()) {

    override fun start(level: ServerLevel, entity: MechanicalBumbleBeeEntity, gameTime: Long) {
        val brain = entity.brain

        val hasTask = brain.hasMemoryValue(BeeMemoryModules.TRANSPORT_TASK.get())

        when {
            hasTask -> brain.setActiveActivityIfPossible(Activity.WORK)
            else -> brain.setActiveActivityIfPossible(Activity.REST)
        }
    }
}
