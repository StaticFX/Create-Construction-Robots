package de.devin.ccr.content.bee.brain

import com.google.common.collect.ImmutableList
import com.mojang.datafixers.util.Pair
import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.bee.brain.behavior.EnterBeeHiveBehavior
import de.devin.ccr.content.bee.brain.behavior.ExecuteTaskBehavior
import de.devin.ccr.content.bee.brain.behavior.MoveToHiveBehavior
import de.devin.ccr.content.bee.brain.behavior.MoveToTaskBehavior
import de.devin.ccr.content.bee.brain.behavior.StuckSafetyBehavior
import de.devin.ccr.content.bee.brain.behavior.UpdateBeeStatusBehavior
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
        ))

        brain.addActivityWithConditions(Activity.WORK,
            ImmutableList.of(
                Pair.of(0, MoveToTaskBehavior()),
                Pair.of(1, ExecuteTaskBehavior())
            ),
            setOf(Pair.of(BeeMemoryModules.CURRENT_TASK.get(), MemoryStatus.VALUE_PRESENT))
        )

        brain.addActivityWithConditions(Activity.REST,
            ImmutableList.of(
                Pair.of(0, EnterBeeHiveBehavior()),
                Pair.of(1, MoveToHiveBehavior())
            ),
            setOf(Pair.of(BeeMemoryModules.CURRENT_TASK.get(), MemoryStatus.VALUE_ABSENT))
        )

        //Fallback
        brain.addActivity(Activity.IDLE, ImmutableList.of(
            Pair.of(0, EnterBeeHiveBehavior())
        ))

        brain.setCoreActivities(setOf(Activity.CORE, Activity.WORK))
        brain.setDefaultActivity(Activity.IDLE)
        brain.useDefaultActivity()

        return brain
    }

}