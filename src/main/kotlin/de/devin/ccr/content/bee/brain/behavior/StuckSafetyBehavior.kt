package de.devin.ccr.content.bee.brain.behavior

import de.devin.ccr.content.bee.MechanicalBeeEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus

class StuckSafetyBehavior: Behavior<MechanicalBeeEntity>(mapOf(
    MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE to MemoryStatus.VALUE_PRESENT,
    MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_PRESENT,
)) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        val stuckAtTime = owner.brain.getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).get()
        val timeSpentStuck = level.gameTime - stuckAtTime

        return timeSpentStuck >= MechanicalBeeEntity.MAX_STUCK_TICKS
    }

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val walkTarget = entity.brain.getMemory(MemoryModuleType.WALK_TARGET).get()
        val targetPos = walkTarget.target.currentBlockPosition()

        val teleportPos = targetPos.above()

        entity.teleportTo(teleportPos.x + 0.5, teleportPos.y.toDouble(), teleportPos.z + 0.5)

        // reset brain
        entity.brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
    }
}