package de.devin.cbbees.content.bee.brain.behavior

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.schedule.Activity

/**
 * Switches the bee between WORK and REST activities based on whether
 * the bee has an active task.
 *
 * @param taskMemory the memory module for this bee's task type
 */
class UpdateStatusBehavior(
    private val taskMemory: MemoryModuleType<*>
) : Behavior<PathfinderMob>(mapOf()) {

    override fun start(level: ServerLevel, entity: PathfinderMob, gameTime: Long) {
        val brain = entity.brain
        val hasTask = brain.hasMemoryValue(taskMemory)

        when {
            hasTask -> brain.setActiveActivityIfPossible(Activity.WORK)
            else -> brain.setActiveActivityIfPossible(Activity.REST)
        }
    }
}
