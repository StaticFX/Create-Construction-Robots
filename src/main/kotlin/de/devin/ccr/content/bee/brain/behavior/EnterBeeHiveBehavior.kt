package de.devin.ccr.content.bee.brain.behavior

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.bee.brain.BeeMemoryModules
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryStatus

class EnterBeeHiveBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        BeeMemoryModules.HIVE_INSTANCE.get() to MemoryStatus.VALUE_PRESENT,
        BeeMemoryModules.HIVE_POS.get() to MemoryStatus.VALUE_PRESENT,
        BeeMemoryModules.CURRENT_TASK.get() to MemoryStatus.VALUE_ABSENT,
    )
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        val hivePos = owner.brain.getMemory(BeeMemoryModules.HIVE_POS.get()).get()
        return owner.blockPosition().closerThan(hivePos, 1.5)
    }

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val hive = entity.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get()

        CreateCCR.LOGGER.info("Returning bee to hive")

        val success = hive.returnBee(entity.tier)


        if (success) {
            entity.discard()
        } else {
            entity.dropBeeItemAndDiscard()
        }
    }
}