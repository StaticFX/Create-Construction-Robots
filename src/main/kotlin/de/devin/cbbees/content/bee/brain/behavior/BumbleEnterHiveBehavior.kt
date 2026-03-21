package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.items.AllItems
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.item.ItemStack

/**
 * BumbleBee behavior: enter the hive when no transport tasks remain.
 */
class BumbleEnterHiveBehavior : Behavior<MechanicalBumbleBeeEntity>(
    mapOf(
        BeeMemoryModules.HIVE_INSTANCE.get() to MemoryStatus.VALUE_PRESENT,
        BeeMemoryModules.TRANSPORT_TASK.get() to MemoryStatus.VALUE_ABSENT,
    )
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBumbleBeeEntity): Boolean {
        val hivePos = owner.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get().pos
        return owner.blockPosition().closerThan(hivePos, 4.0)
    }

    override fun start(level: ServerLevel, entity: MechanicalBumbleBeeEntity, gameTime: Long) {
        val hive = entity.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get()

        BeeDebug.logForEntity(entity, "Bumble", "Entering hive")

        val deficit = 1.0f - entity.springTension
        val ctx = hive.getBeeContext()
        hive.chargeReturnFuel(deficit, ctx)

        val success = hive.returnBee(ItemStack(AllItems.MECHANICAL_BUMBLE_BEE.get()))

        if (success) {
            entity.discard()
        } else {
            entity.dropBeeItemAndDiscard()
        }
    }
}
