package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBeelike
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus

/**
 * Unified hive-entry behavior for all mechanical bee types.
 *
 * When the bee is close enough to its hive and has no active task,
 * charges return fuel, then attempts to enter. If the hive rejects
 * the bee (full), retries up to [MAX_HIVE_ENTRY_RETRIES] times,
 * trying to adopt alternate hives in the network. Drops as item
 * only after exhausting all options.
 *
 * @param taskMemory the memory module for this bee's task type
 *                   (CURRENT_TASK for construction bees, TRANSPORT_TASK for bumble bees)
 */
class EnterHiveBehavior(
    taskMemory: MemoryModuleType<*>
) : Behavior<PathfinderMob>(
    mapOf(
        BeeMemoryModules.HIVE_INSTANCE.get() to MemoryStatus.VALUE_PRESENT,
        taskMemory to MemoryStatus.VALUE_ABSENT,
    )
) {

    companion object {
        const val MAX_HIVE_ENTRY_RETRIES = 3
    }

    override fun checkExtraStartConditions(level: ServerLevel, owner: PathfinderMob): Boolean {
        val hivePos = owner.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get().pos
        return owner.blockPosition().closerThan(hivePos, 4.0)
    }

    override fun start(level: ServerLevel, entity: PathfinderMob, gameTime: Long) {
        val bee = entity as MechanicalBeelike
        val hive = entity.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get()

        BeeDebug.log(bee, "Entering hive")

        val deficit = 1.0f - bee.springTension
        val ctx = bee.getBeeContextForRecharge()
        hive.chargeReturnFuel(deficit, ctx)

        val success = hive.returnBee(bee.beeItemStack())

        if (success) {
            entity.discard()
        } else {
            bee.hiveEntryRetries++
            BeeDebug.log(bee, "Hive full — retry ${bee.hiveEntryRetries}/$MAX_HIVE_ENTRY_RETRIES")

            if (bee.hiveEntryRetries >= MAX_HIVE_ENTRY_RETRIES) {
                bee.dropBeeItemAndDiscard("hive full — max retries ($MAX_HIVE_ENTRY_RETRIES) reached")
                return
            }

            // Try to find another hive in the network
            entity.brain.eraseMemory(BeeMemoryModules.HIVE_INSTANCE.get())
            entity.brain.eraseMemory(BeeMemoryModules.HIVE_POS.get())
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            val adopted = bee.tryAdoptHive(exclude = hive)
            if (adopted == null) {
                bee.dropBeeItemAndDiscard("hive full — no other hive in network")
            } else {
                BeeDebug.log(bee, "Redirecting to hive at ${adopted.pos}")
            }
        }
    }
}
