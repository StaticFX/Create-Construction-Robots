package de.devin.ccr.content.bee.brain.behavior

import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.bee.brain.BeeMemoryModules
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget

class MoveToTaskBehavior: Behavior<MechanicalBeeEntity>(mapOf(
    BeeMemoryModules.CURRENT_TASK.get() to MemoryStatus.VALUE_PRESENT,
    MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
)) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        val task = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val workRange = owner.tier.capabilities.workRange
        return !owner.blockPosition().closerThan(task.targetPos, workRange)
    }

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val task = entity.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val moveTo = task.targetPos

        entity.brain.setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(moveTo, entity.tier.capabilities.flySpeedModifier, 1))
    }

}