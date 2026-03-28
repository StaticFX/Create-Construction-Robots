package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBeelike
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus

/**
 * WORK-activity behavior that handles spring recharge for all mechanical bee types.
 *
 * When spring tension is depleted, this behavior takes priority over
 * task-specific behaviors, navigates the bee back to its hive,
 * and recharges the spring over a duration that scales with RPM.
 *
 * @param taskMemory the memory module for this bee's task type
 */
class RechargeSpringBehavior(
    taskMemory: MemoryModuleType<*>
) : Behavior<PathfinderMob>(
    mapOf(
        taskMemory to MemoryStatus.VALUE_PRESENT,
        BeeMemoryModules.HIVE_INSTANCE.get() to MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
    ),
    1
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: PathfinderMob): Boolean {
        val bee = owner as MechanicalBeelike
        return bee.springTension <= 0f || bee.rechargeFinishTick >= 0
    }

    override fun start(level: ServerLevel, entity: PathfinderMob, gameTime: Long) {
        val bee = entity as MechanicalBeelike
        val hive = entity.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get()

        // Phase 2: Currently recharging — check if done
        if (bee.rechargeFinishTick >= 0) {
            if (gameTime >= bee.rechargeFinishTick) {
                bee.springTension = 1.0f
                bee.rechargeFinishTick = -1
                BeeDebug.log(bee, "Spring recharged — resuming work")
            }
            return
        }

        // Phase 1: Need to reach hive first
        if (entity.blockPosition().closerThan(hive.pos, 4.0)) {
            val ctx = bee.getBeeContextForRecharge()
            val rechargeTicks = hive.rechargeSpring(ctx)

            bee.rechargeFinishTick = gameTime + rechargeTicks
            BeeDebug.log(bee, "Recharging spring at hive ($rechargeTicks ticks)")
        } else {
            BeeDebug.log(bee, "Flying to hive for spring recharge")
            entity.brain.setMemory(MemoryModuleType.WALK_TARGET, hive.walkTarget())
        }
    }
}
