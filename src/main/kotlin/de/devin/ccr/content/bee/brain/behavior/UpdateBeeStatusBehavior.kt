package de.devin.ccr.content.bee.brain.behavior

import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.bee.brain.BeeMemoryModules
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.schedule.Activity

class UpdateBeeStatusBehavior: Behavior<MechanicalBeeEntity>(mapOf()) {

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val brain = entity.brain

        val hasTask = brain.hasMemoryValue(BeeMemoryModules.CURRENT_TASK.get())

        when {
            hasTask -> brain.setActiveActivityIfPossible(Activity.WORK)
            else -> brain.setActiveActivityIfPossible(Activity.REST)
        }
    }
}