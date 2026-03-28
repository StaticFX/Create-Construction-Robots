package de.devin.cbbees.content.bee.brain

import com.google.common.collect.ImmutableList
import com.mojang.datafixers.util.Pair
import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.brain.behavior.*
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.schedule.Activity

object MechanicalBumbleBrainProvider {

    fun brain(): Brain.Provider<MechanicalBumbleBeeEntity> {
        return Brain.provider(
            listOf(
                BeeMemoryModules.HIVE_POS.get(),
                BeeMemoryModules.HIVE_INSTANCE.get(),
                BeeMemoryModules.TRANSPORT_TASK.get(),
                MemoryModuleType.WALK_TARGET,
                MemoryModuleType.LOOK_TARGET,
                MemoryModuleType.PATH,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
            ),
            listOf(
                BeeSensors.HIVE_SENSOR.get()
            )
        )
    }

    fun makeBrain(brain: Brain<MechanicalBumbleBeeEntity>): Brain<MechanicalBumbleBeeEntity> {
        val taskMemory = BeeMemoryModules.TRANSPORT_TASK.get()

        brain.addActivity(
            Activity.CORE, ImmutableList.of(
                Pair.of(0, LookAtTargetSink(45, 90)),
                Pair.of(1, MoveToTargetSink()),
                Pair.of(2, UpdateStatusBehavior(taskMemory)),
                Pair.of(3, StuckSafetyBehavior()),
                Pair.of(4, OrphanedSafetyBehavior()),
                Pair.of(5, FlightDrainBehavior())
            )
        )

        brain.addActivityWithConditions(
            Activity.WORK,
            ImmutableList.of(
                Pair.of(0, RechargeSpringBehavior(taskMemory)),
                Pair.of(1, PickUpFromSourceBehavior()),
                Pair.of(2, FlyToTargetPortBehavior()),
                Pair.of(3, DepositAtTargetBehavior())
            ),
            setOf(Pair.of(taskMemory, MemoryStatus.VALUE_PRESENT))
        )

        brain.addActivityWithConditions(
            Activity.REST,
            ImmutableList.of(
                Pair.of(1, EnterHiveBehavior(taskMemory)),
                Pair.of(2, SetHiveTargetBehavior())
            ),
            setOf(Pair.of(taskMemory, MemoryStatus.VALUE_ABSENT))
        )

        brain.setCoreActivities(setOf(Activity.CORE))
        brain.setDefaultActivity(Activity.CORE)
        brain.useDefaultActivity()

        return brain
    }
}
