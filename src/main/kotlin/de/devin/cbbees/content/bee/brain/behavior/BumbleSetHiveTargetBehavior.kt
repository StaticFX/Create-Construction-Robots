package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget

/**
 * BumbleBee behavior: set walk target to hive when resting.
 */
class BumbleSetHiveTargetBehavior : Behavior<MechanicalBumbleBeeEntity>(
    mapOf(
        BeeMemoryModules.HIVE_INSTANCE.get() to MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
    )
) {
    override fun start(level: ServerLevel, entity: MechanicalBumbleBeeEntity, gameTime: Long) {
        val hive = entity.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get()
        val hiveTarget = hive.walkTarget()

        BeeDebug.logForEntity(entity, "Bumble", "Returning to hive")

        entity.brain.setMemory(
            MemoryModuleType.WALK_TARGET,
            WalkTarget(hiveTarget.target, 1.0f, hiveTarget.closeEnoughDist)
        )
    }
}
