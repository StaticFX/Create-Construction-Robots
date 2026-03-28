package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBeelike
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.Behavior

/**
 * CORE behavior that detects when a bee has lost its hive (destroyed, unloaded, etc.)
 * and attempts to adopt into another hive in the network. Drops the bee as an item
 * after [ORPHANED_DROP_THRESHOLD] ticks with no hive.
 *
 * Runs continuously via tick() so it can track orphaned time accurately.
 */
class OrphanedSafetyBehavior : Behavior<PathfinderMob>(mapOf(), Int.MAX_VALUE) {

    companion object {
        /** Ticks without a hive before the bee drops as an item (10 seconds) */
        private const val ORPHANED_DROP_THRESHOLD = 200

        /** Ticks between hive adoption attempts */
        private const val ADOPT_INTERVAL = 40
    }

    private var orphanedTicks = 0

    override fun tick(level: ServerLevel, entity: PathfinderMob, gameTime: Long) {
        val bee = entity as MechanicalBeelike

        if (bee.beehive() != null || entity.brain.hasMemoryValue(BeeMemoryModules.HIVE_INSTANCE.get())) {
            orphanedTicks = 0
            return
        }

        orphanedTicks++

        if (orphanedTicks % ADOPT_INTERVAL == 1) {
            val adopted = bee.tryAdoptHive()
            if (adopted != null) {
                BeeDebug.log(bee, "Adopted into hive at ${adopted.pos}")
                orphanedTicks = 0
                return
            }
        }

        if (orphanedTicks >= ORPHANED_DROP_THRESHOLD) {
            bee.dropBeeItemAndDiscard("orphaned for ${ORPHANED_DROP_THRESHOLD} ticks — no hive found")
        }
    }
}
