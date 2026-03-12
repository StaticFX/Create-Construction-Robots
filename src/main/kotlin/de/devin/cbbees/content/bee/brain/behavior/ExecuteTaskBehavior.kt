package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus

class ExecuteTaskBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        BeeMemoryModules.CURRENT_TASK.get() to MemoryStatus.VALUE_PRESENT,
        BeeMemoryModules.HIVE_INSTANCE.get() to MemoryStatus.VALUE_PRESENT
    ),
    1
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        val batch = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val task = batch.getCurrentTask()
        if (task == null) {
            BeeDebug.log(owner, "Execute: no current task in batch")
            return false
        }

        // Check if task is within the bee's current network range
        val network = owner.network()
        if (network != null && !network.isInRange(task.targetPos)) {
            BeeDebug.log(owner, "Execute: task at ${task.targetPos} out of network range")
            return false
        }

        val workRange = owner.tier.capabilities.workRange
        val inRange = owner.blockPosition().closerThan(task.targetPos, workRange)

        if (!inRange) {
            return false
        }

        // Check if the task needs items the bee doesn't have
        val action = task.action
        if (action is ItemConsumingAction && !action.hasItems(owner)) {
            BeeDebug.log(
                owner,
                "Execute: missing items for ${action.requiredItems.joinToString { "${it.count}x ${it.item}" }}"
            )
            return false
        }

        BeeDebug.log(owner, "Execute: ready to run ${task.action.getDescription()}")
        return true
    }

    override fun start(level: ServerLevel, owner: MechanicalBeeEntity, gameTime: Long) {
        val batch = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val task = batch.getCurrentTask() ?: return
        val hive = owner.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get()

        BeeDebug.log(owner, "Executing: ${task.action.getDescription()}")

        val done = task.action.execute(level, owner, owner.getBeeContext())

        if (done) {
            task.complete()
            if (!batch.advance()) {
                val nextBatch = hive.notifyTaskCompleted(task, owner)

                if (nextBatch != null) {
                    BeeDebug.log(owner, "Batch done — received next batch")
                    owner.brain.setMemory(BeeMemoryModules.CURRENT_TASK.get(), nextBatch)
                    owner.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
                } else {
                    BeeDebug.log(owner, "Batch done — no more work, returning to hive")
                    owner.brain.eraseMemory(BeeMemoryModules.CURRENT_TASK.get())
                }
            } else {
                BeeDebug.log(owner, "Advancing to next sub-task in batch")
                batch.getCurrentTask()?.action?.onActivate(owner)
                owner.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            }
        } else {
            BeeDebug.log(owner, "Task failed — releasing batch (retry ${batch.retryCount + 1}/${TaskBatch.MAX_RETRIES})")
            owner.network()?.releaseReservations(owner.uuid)
            batch.release(gameTick = gameTime)
            owner.brain.eraseMemory(BeeMemoryModules.CURRENT_TASK.get())
            owner.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        }
    }
}
