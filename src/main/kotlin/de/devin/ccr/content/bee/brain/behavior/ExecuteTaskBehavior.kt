package de.devin.ccr.content.bee.brain.behavior

import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.bee.brain.BeeMemoryModules
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryStatus

class ExecuteTaskBehavior: Behavior<MechanicalBeeEntity>(mapOf(
    BeeMemoryModules.CURRENT_TASK.get() to MemoryStatus.VALUE_PRESENT
)) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        val task = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()

        val workRange = owner.tier.capabilities.workRange

        return owner.blockPosition().closerThan(task.targetPos, workRange)
    }

    override fun tick(level: ServerLevel, owner: MechanicalBeeEntity, gameTime: Long) {
        val task = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()

        val done = task.action.execute(level, task.targetPos, owner, owner.getBeeContext())

        if (done) {
            task.complete()
            owner.brain.eraseMemory(BeeMemoryModules.CURRENT_TASK.get())
        }
    }
}