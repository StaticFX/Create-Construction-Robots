package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.phys.Vec3

/**
 * Stuck detection for BumbleBees — mirrors StuckSafetyBehavior logic.
 */
class BumbleStuckSafetyBehavior : Behavior<MechanicalBumbleBeeEntity>(
    mapOf(
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_PRESENT,
    )
) {
    companion object {
        private const val CHECK_INTERVAL = 20
        private const val MIN_PROGRESS = 1.5
        private const val MAX_FAILS = 3
    }

    private var lastDistanceToTarget = Double.MAX_VALUE
    private var ticksSinceCheck = 0
    private var failedChecks = 0
    private var lastTargetPos: Vec3 = Vec3.ZERO

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBumbleBeeEntity): Boolean {
        val walkTarget = owner.brain.getMemory(MemoryModuleType.WALK_TARGET).get()
        val targetPos = Vec3.atCenterOf(walkTarget.target.currentBlockPosition())

        if (targetPos.distanceToSqr(lastTargetPos) > 1.0) {
            lastTargetPos = targetPos
            lastDistanceToTarget = owner.position().distanceTo(targetPos)
            ticksSinceCheck = 0
            failedChecks = 0
            return false
        }

        ticksSinceCheck++
        if (ticksSinceCheck < CHECK_INTERVAL) return false

        val currentDist = owner.position().distanceTo(targetPos)
        val progress = lastDistanceToTarget - currentDist

        if (progress < MIN_PROGRESS) {
            failedChecks++
        } else {
            failedChecks = 0
        }

        lastDistanceToTarget = currentDist
        ticksSinceCheck = 0

        return failedChecks >= MAX_FAILS
    }

    override fun start(level: ServerLevel, entity: MechanicalBumbleBeeEntity, gameTime: Long) {
        val walkTarget = entity.brain.getMemory(MemoryModuleType.WALK_TARGET).get()
        val targetPos = walkTarget.target.currentBlockPosition()

        BeeDebug.logForEntity(entity, "Bumble", "Stuck! Teleporting to target at $targetPos")

        entity.teleportTo(targetPos.x + 0.5, targetPos.y + 1.0, targetPos.z + 0.5)

        failedChecks = 0
        ticksSinceCheck = 0
        lastDistanceToTarget = Double.MAX_VALUE
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        entity.brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
    }
}
