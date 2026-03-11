package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.phys.Vec3

/**
 * Detects when a bee is stuck by checking whether it makes progress toward
 * its walk target. Wiggling around without getting closer counts as stuck.
 *
 * Every [CHECK_INTERVAL] ticks, we snapshot the distance to the target.
 * If the bee hasn't closed at least [MIN_PROGRESS] blocks in that window,
 * we increment a fail counter. [MAX_FAILS] consecutive failures triggers
 * a teleport to the target.
 */
class StuckSafetyBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_PRESENT,
    )
) {
    companion object {
        /** Ticks between progress checks */
        private const val CHECK_INTERVAL = 20

        /** Minimum distance (blocks) the bee must close per check interval */
        private const val MIN_PROGRESS = 1.5

        /** Consecutive failed checks before teleporting */
        private const val MAX_FAILS = 3
    }

    private var lastDistanceToTarget = Double.MAX_VALUE
    private var ticksSinceCheck = 0
    private var failedChecks = 0
    private var lastTargetPos: Vec3 = Vec3.ZERO

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        val walkTarget = owner.brain.getMemory(MemoryModuleType.WALK_TARGET).get()
        val targetPos = Vec3.atCenterOf(walkTarget.target.currentBlockPosition())

        // If the target changed, reset tracking
        if (targetPos.distanceToSqr(lastTargetPos) > 1.0) {
            lastTargetPos = targetPos
            lastDistanceToTarget = owner.position().distanceTo(targetPos)
            ticksSinceCheck = 0
            failedChecks = 0
            return false
        }

        ticksSinceCheck++
        if (ticksSinceCheck < CHECK_INTERVAL) return false

        // Check progress
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

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val walkTarget = entity.brain.getMemory(MemoryModuleType.WALK_TARGET).get()
        val targetPos = walkTarget.target.currentBlockPosition()

        BeeDebug.log(entity, "Stuck! Teleporting to target at $targetPos")

        entity.teleportTo(targetPos.x + 0.5, targetPos.y + 1.0, targetPos.z + 0.5)

        failedChecks = 0
        ticksSinceCheck = 0
        lastDistanceToTarget = Double.MAX_VALUE
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        entity.brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
    }
}