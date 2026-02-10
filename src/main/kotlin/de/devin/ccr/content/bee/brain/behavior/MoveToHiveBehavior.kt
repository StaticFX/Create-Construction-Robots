package de.devin.ccr.content.bee.brain.behavior

import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.bee.brain.BeeMemoryModules
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget

class MoveToHiveBehavior: Behavior<MechanicalBeeEntity>(mapOf(
    BeeMemoryModules.HIVE_POS.get() to MemoryStatus.VALUE_PRESENT,
    MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
)) {

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val hivePose = entity.brain.getMemory(BeeMemoryModules.HIVE_POS.get()).get()

        val flySpeed = entity.tier.capabilities.flySpeedModifier

        entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(hivePose, flySpeed, 1))
    }
}