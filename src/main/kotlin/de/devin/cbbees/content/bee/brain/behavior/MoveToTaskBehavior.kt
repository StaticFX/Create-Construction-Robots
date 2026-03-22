package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.action.impl.DropOffItemsAction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget

class MoveToTaskBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        BeeMemoryModules.CURRENT_TASK.get() to MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
    ),
    1
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        if (owner.springTension <= 0f) return false

        val batch = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val task = batch.getCurrentTask() ?: return false

        // Don't navigate to task if we still need to gather items
        val action = task.action
        if (action is ItemConsumingAction && !action.hasItems(owner)) {
            return false
        }

        val workRange = owner.workRange
        val close = owner.blockPosition().closerThan(task.targetPos, workRange)
        if (action is DropOffItemsAction) {
            BeeDebug.log(owner, "MoveTo(DropOff): beePos=${owner.blockPosition()}, target=${task.targetPos}, close=$close, workRange=$workRange")
        }
        return !close
    }

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val batch = entity.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val task = batch.getCurrentTask() ?: return
        val moveTo = task.targetPos

        if (task.action is DropOffItemsAction) {
            BeeDebug.log(entity, "MoveTo(DropOff): flying to $moveTo")
        } else {
            BeeDebug.log(entity, "Flying to task at $moveTo")
        }

        entity.brain.setMemory(
            MemoryModuleType.WALK_TARGET,
            WalkTarget(moveTo, 1.0f, 1)
        )
    }
}