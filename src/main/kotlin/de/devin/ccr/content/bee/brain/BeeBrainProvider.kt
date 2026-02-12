package de.devin.ccr.content.bee.brain

import com.google.common.collect.ImmutableList
import com.mojang.datafixers.util.Pair
import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.bee.brain.behavior.*
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink
import net.minecraft.world.entity.ai.behavior.Swim
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.schedule.Activity

object BeeBrainProvider {

    fun brain(): Brain.Provider<MechanicalBeeEntity> {
        val brain = Brain.provider(
            listOf(
                BeeMemoryModules.HIVE_POS.get(),
                BeeMemoryModules.HIVE_INSTANCE.get(),
                BeeMemoryModules.CURRENT_TASK.get(),
                MemoryModuleType.WALK_TARGET,
                MemoryModuleType.LOOK_TARGET,
                MemoryModuleType.PATH,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
            ),
            listOf(
                BeeSensors.HIVE_SENSOR.get()
            )
        )

        return brain
    }

    fun makeBrain(brain: Brain<MechanicalBeeEntity>): Brain<MechanicalBeeEntity> {
        brain.addActivity(
            Activity.CORE, ImmutableList.of(
                Pair.of(0, LookAtTargetSink(45, 90)),
                Pair.of(1, MoveToTargetSink()),
                Pair.of(2, UpdateBeeStatusBehavior()),
                Pair.of(3, StuckSafetyBehavior()),
                Pair.of(4, Swim(0.8f))
            )
        )

        brain.addActivityWithConditions(
            Activity.WORK,
            ImmutableList.of(
                Pair.of(0, MoveToTaskBehavior()),
                Pair.of(1, ExecuteTaskBehavior())
            ),
            setOf(Pair.of(BeeMemoryModules.CURRENT_TASK.get(), MemoryStatus.VALUE_PRESENT))
        )

        brain.addActivityWithConditions(
            Activity.REST,
            ImmutableList.of(
                Pair.of(2, EnterBeeHiveBehavior()),
                Pair.of(3, SetHiveWalkTargetBehavior())
            ),
            setOf(Pair.of(BeeMemoryModules.CURRENT_TASK.get(), MemoryStatus.VALUE_ABSENT))
        )

        brain.setCoreActivities(setOf(Activity.CORE))
        brain.setDefaultActivity(Activity.CORE)
        brain.useDefaultActivity()

        return brain
    }

}