package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus

class ExecuteTaskBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        BeeMemoryModules.CURRENT_TASK.get() to MemoryStatus.VALUE_PRESENT,
        BeeMemoryModules.HIVE_INSTANCE.get() to MemoryStatus.VALUE_PRESENT
    )
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        val batch = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val task = batch.getCurrentTask() ?: return false

        val workRange = owner.tier.capabilities.workRange

        return owner.blockPosition().closerThan(task.targetPos, workRange)
    }

    override fun start(level: ServerLevel, owner: MechanicalBeeEntity, gameTime: Long) {
        CreateBuzzyBeez.LOGGER.info("Bee now executing task")
        val batch = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val task = batch.getCurrentTask() ?: return
        val hive = owner.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get()

        val done = task.action.execute(level, owner, owner.getBeeContext())

        if (done) {
            if (!batch.advance()) {
                val nextBatch = hive.notifyTaskCompleted(task, owner)

                CreateBuzzyBeez.LOGGER.info("Finished")


                if (nextBatch != null) {
                    CreateBuzzyBeez.LOGGER.info("Found next task")

                    owner.brain.setMemory(BeeMemoryModules.CURRENT_TASK.get(), nextBatch)
                    owner.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
                } else {
                    CreateBuzzyBeez.LOGGER.info("No other task found")
                    owner.brain.eraseMemory(BeeMemoryModules.CURRENT_TASK.get())
                }
            } else {
                CreateBuzzyBeez.LOGGER.info("Advancing to next sub-task in batch")
                owner.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            }
        }
    }
}