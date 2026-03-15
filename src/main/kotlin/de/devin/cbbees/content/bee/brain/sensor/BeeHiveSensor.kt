package de.devin.cbbees.content.bee.brain.sensor

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.domain.beehive.BeeHive
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor

class BeeHiveSensor : Sensor<LivingEntity>() {
    override fun requires(): Set<MemoryModuleType<*>?> {
        return setOf(BeeMemoryModules.HIVE_POS.get())
    }

    override fun doTick(level: ServerLevel, entity: LivingEntity) {
        val brain = entity.brain
        val pos = brain.getMemory(BeeMemoryModules.HIVE_POS.get()).orElse(null) ?: return
        if (brain.hasMemoryValue(BeeMemoryModules.HIVE_INSTANCE.get())) return

        if (level.isLoaded(pos)) {
            val blockEntity = level.getBlockEntity(pos)
            if (blockEntity is BeeHive) {
                brain.setMemory(BeeMemoryModules.HIVE_INSTANCE.get(), blockEntity)
            } else {
                brain.eraseMemory(BeeMemoryModules.HIVE_POS.get())
                CreateBuzzyBeez.LOGGER.debug("Here the bee would die :(")
            }
        }
    }
}
