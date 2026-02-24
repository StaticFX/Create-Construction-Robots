package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus

class SetHiveWalkTargetBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        BeeMemoryModules.HIVE_INSTANCE.get() to MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT // Only reset if lost
    )
) {
    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val hive = entity.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get()
        val walkTarget = hive.walkTarget()
        
        CreateBuzzyBeez.LOGGER.info("Setting hive target")

        entity.brain.setMemory(MemoryModuleType.WALK_TARGET, walkTarget)
    }
}