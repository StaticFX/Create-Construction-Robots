package de.devin.ccr.content.bee.brain.sensor

import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.bee.brain.BeeMemoryModules
import de.devin.ccr.content.domain.beehive.BeeHive
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor

class BeeHiveSensor : Sensor<MechanicalBeeEntity>() {
    override fun requires(): Set<MemoryModuleType<*>?> {
        return setOf(BeeMemoryModules.HIVE_POS.get())
    }

    override fun doTick(level: ServerLevel, entity: MechanicalBeeEntity) {
        val brain = entity.brain
        val pos = brain.getMemory(BeeMemoryModules.HIVE_POS.get()).orElse(null) ?: return

        // Check if the block at the saved position is still a valid hive
        if (level.isLoaded(pos)) {
            val blockEntity = level.getBlockEntity(pos)
            if (blockEntity is BeeHive) {
                brain.setMemory(BeeMemoryModules.HIVE_INSTANCE.get(), blockEntity)
            } else {
                brain.eraseMemory(BeeMemoryModules.HIVE_POS.get())
                // TODO kill the bee :(
            }
        }
    }
}