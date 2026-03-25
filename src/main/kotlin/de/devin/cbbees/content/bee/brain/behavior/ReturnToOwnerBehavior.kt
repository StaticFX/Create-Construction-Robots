package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget

/**
 * Core behavior that flies the bee back to its owner player and drops it as an item.
 *
 * Activated when RETURNING_TO_OWNER memory is set (portable beehive was removed).
 * Takes priority over all work/rest behaviors since it runs in CORE activity.
 */
class ReturnToOwnerBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        BeeMemoryModules.RETURNING_TO_OWNER.get() to MemoryStatus.VALUE_PRESENT
    ),
    1 // re-evaluate every tick
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        val player = owner.brain.getMemory(BeeMemoryModules.RETURNING_TO_OWNER.get()).orElse(null)
        if (player == null || !player.isAlive) {
            // Owner gone — drop immediately
            owner.dropBeeItemAndDiscard()
            return false
        }
        return true
    }

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val player = entity.brain.getMemory(BeeMemoryModules.RETURNING_TO_OWNER.get()).orElse(null)
        if (player == null || !player.isAlive) {
            entity.dropBeeItemAndDiscard()
            return
        }

        if (entity.blockPosition().closerThan(player.blockPosition(), 3.0)) {
            BeeDebug.log(entity, "Reached owner — dropping as item")
            entity.dropBeeItemAndDiscard()
            return
        }

        // Keep flying toward the player (above their head to avoid blocking vision)
        entity.brain.setMemory(
            MemoryModuleType.WALK_TARGET,
            WalkTarget(player.blockPosition().above(2), 1.0f, 1)
        )
    }
}
