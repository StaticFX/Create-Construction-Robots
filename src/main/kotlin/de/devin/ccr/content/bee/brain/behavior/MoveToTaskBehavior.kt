package de.devin.ccr.content.bee.brain.behavior

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.bee.brain.BeeMemoryModules
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget

class MoveToTaskBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        BeeMemoryModules.CURRENT_TASK.get() to MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
    )
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        val batch = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val task = batch.getCurrentTask() ?: return false
        val workRange = owner.tier.capabilities.workRange
        return !owner.blockPosition().closerThan(task.targetPos, workRange)
    }

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val batch = entity.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val task = batch.getCurrentTask() ?: return
        val moveTo = task.targetPos

        CreateCCR.LOGGER.info("Moving to task target $moveTo")

        entity.brain.setMemory(
            MemoryModuleType.WALK_TARGET,
            WalkTarget(moveTo, entity.tier.capabilities.flySpeedModifier, 1)
        )
    }
}