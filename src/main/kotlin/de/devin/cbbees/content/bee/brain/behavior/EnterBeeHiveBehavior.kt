package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.items.AllItems
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
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

        val deficit = 1.0f - entity.springTension
        val ctx = entity.getBeeContext()
        hive.chargeReturnFuel(deficit, ctx)

        val success = hive.returnBee(ItemStack(AllItems.MECHANICAL_BEE.get()))

        if (success) {
            entity.discard()
        } else {
            entity.hiveEntryRetries++
            BeeDebug.log(entity, "Hive full — retry ${entity.hiveEntryRetries}/${MechanicalBeeEntity.MAX_HIVE_ENTRY_RETRIES}")

            if (entity.hiveEntryRetries >= MechanicalBeeEntity.MAX_HIVE_ENTRY_RETRIES) {
                BeeDebug.log(entity, "Max retries reached — dropping as item")
                entity.dropBeeItemAndDiscard()
                return
            }

            // Try to find another hive in the network, excluding the one that just rejected us
            entity.brain.eraseMemory(BeeMemoryModules.HIVE_INSTANCE.get())
            entity.brain.eraseMemory(BeeMemoryModules.HIVE_POS.get())
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            val adopted = entity.tryAdoptHive(exclude = hive)
            if (adopted == null) {
                BeeDebug.log(entity, "No other hive in network — dropping as item")
                entity.dropBeeItemAndDiscard()
            } else {
                BeeDebug.log(entity, "Redirecting to hive at ${adopted.pos}")
            }
        }
    }
}