package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus

/**
 * CORE behavior that keeps bees from portable beehives (backpacks) in sync
 * with the player's position. When the backpack is removed, releases current
 * work and sends the bee back to its owner.
 *
 * Only relevant for [MechanicalBeeEntity] since bumble bees don't spawn
 * from portable beehives.
 */
class PortableBeehiveTrackingBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(BeeMemoryModules.HIVE_INSTANCE.get() to MemoryStatus.VALUE_PRESENT),
    Int.MAX_VALUE
) {

    private companion object {
        /** Ticks between tracking updates */
        const val CHECK_INTERVAL = 20
    }

    override fun tick(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        if (entity.tickCount % CHECK_INTERVAL != 0) return

        val hive = entity.beehive() as? PortableBeeHive ?: return
        entity.brain.setMemory(BeeMemoryModules.HIVE_POS.get(), hive.player.blockPosition().above(2))

        // Backpack removed — release current work and set brain to return to owner
        if (!hive.isValid() && !entity.brain.hasMemoryValue(BeeMemoryModules.RETURNING_TO_OWNER.get())) {
            val batch = entity.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).orElse(null)
            if (batch != null) {
                val tick = (level as? ServerLevel)?.gameTime ?: 0L
                batch.release(gameTick = tick)
                entity.brain.eraseMemory(BeeMemoryModules.CURRENT_TASK.get())
            }
            entity.brain.eraseMemory(BeeMemoryModules.HIVE_INSTANCE.get())
            entity.brain.eraseMemory(BeeMemoryModules.HIVE_POS.get())
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            entity.brain.setMemory(BeeMemoryModules.RETURNING_TO_OWNER.get(), hive.player)
            entity.springTension = 1.0f // enough fuel to get back

            BeeDebug.log(entity, "Backpack removed — returning to owner")
        }
    }
}
