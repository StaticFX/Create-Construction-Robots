package de.devin.ccr.content.bee.brain

import de.devin.ccr.content.bee.MechanicalBeeEntity
import net.minecraft.world.entity.ai.Brain

object BeeBrainProvider {

    fun brain(): Brain.Provider<MechanicalBeeEntity> {
        return Brain.provider(
            listOf(
                BeeMemoryModules.HIVE_POS.get(),
                BeeMemoryModules.HIVE_INSTANCE.get(),
                BeeMemoryModules.CURRENT_TASK.get()
            ),
            listOf(
                BeeSensors.HIVE_SENSOR.get()
            )
        )
    }

}