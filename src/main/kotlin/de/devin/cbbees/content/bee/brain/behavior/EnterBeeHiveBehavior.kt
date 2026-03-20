package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.config.CBeesConfig
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryStatus

class EnterBeeHiveBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        BeeMemoryModules.HIVE_INSTANCE.get() to MemoryStatus.VALUE_PRESENT,
        BeeMemoryModules.CURRENT_TASK.get() to MemoryStatus.VALUE_ABSENT,
    )
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        val hivePos = owner.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get().pos
        return owner.blockPosition().closerThan(hivePos, 4.0)
    }

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val hive = entity.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get()

        BeeDebug.log(entity, "Entering hive")

        // Recharge spring on reentry — consume honey from portable beehive
        if (hive is PortableBeeHive) {
            val deficit = 1.0f - entity.springTension
            if (deficit > 0f) {
                val ctx = entity.getBeeContext()
                val honeyCost = (deficit * CBeesConfig.portableHoneyPerRewind.get() * ctx.fuelConsumptionMultiplier).toInt().coerceAtLeast(1)
                hive.consumeHoney(honeyCost)
            }
        }

        val success = hive.returnBee(ItemStack(de.devin.cbbees.items.AllItems.MECHANICAL_BEE.get()))

        if (success) {
            entity.discard()
        } else {
            entity.dropBeeItemAndDiscard()
        }
    }
}