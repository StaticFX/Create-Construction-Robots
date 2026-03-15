package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget

/**
 * BumbleBee behavior: fly to the target (INSERT) port after picking up items.
 *
 * Only runs when the bee has items in inventory and isn't at the target yet.
 */
class FlyToTargetPortBehavior : Behavior<MechanicalBumbleBeeEntity>(
    mapOf(
        BeeMemoryModules.TRANSPORT_TASK.get() to MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
    ),
    1
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBumbleBeeEntity): Boolean {
        if (owner.springTension <= 0f) return false
        // Only fly to target if we have items (picked up already)
        if (owner.isInventoryEmpty()) return false

        val task = owner.brain.getMemory(BeeMemoryModules.TRANSPORT_TASK.get()).get()
        return !owner.blockPosition().closerThan(task.targetPos, owner.workRange)
    }

    override fun start(level: ServerLevel, entity: MechanicalBumbleBeeEntity, gameTime: Long) {
        val task = entity.brain.getMemory(BeeMemoryModules.TRANSPORT_TASK.get()).get()
        val targetPos = task.targetPos

        BeeDebug.logForEntity(entity, "Bumble", "Flying to target port at $targetPos")
        entity.brain.setMemory(
            MemoryModuleType.WALK_TARGET,
            WalkTarget(targetPos, 1.0f, 1)
        )
    }
}
