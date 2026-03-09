package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus

class StuckSafetyBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_PRESENT,
    )
) {
    private var lastPos = BlockPos.ZERO
    private var stuckTicks = 0

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        // If the bee has moved less than 0.5 blocks since the last check
        if (owner.blockPosition().distSqr(lastPos) < 0.25) {
            stuckTicks++
        } else {
            lastPos = owner.blockPosition()
            stuckTicks = 0
        }

        // Trigger if stuck for more than 5 seconds (100 ticks)
        return stuckTicks >= MechanicalBeeEntity.MAX_STUCK_TICKS
    }

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val walkTarget = entity.brain.getMemory(MemoryModuleType.WALK_TARGET).get()
        val targetPos = walkTarget.target.currentBlockPosition()

        BeeDebug.log(entity, "Stuck! Teleporting to target at $targetPos")

        // Teleport near the target, but not inside it
        entity.teleportTo(targetPos.x + 0.5, targetPos.y + 1.0, targetPos.z + 0.5)

        // Reset the safety counters
        stuckTicks = 0
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        entity.brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
    }
}