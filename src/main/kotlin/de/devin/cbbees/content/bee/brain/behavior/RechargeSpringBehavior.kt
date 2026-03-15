package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.config.CBeesConfig
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus

/**
 * WORK-activity behavior that handles spring recharge for Mechanical Bees.
 *
 * When spring tension is depleted, this behavior takes priority over
 * gather/move/execute behaviors, navigates the bee back to its hive,
 * and recharges the spring over a duration that scales with RPM.
 */
class RechargeSpringBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        BeeMemoryModules.CURRENT_TASK.get() to MemoryStatus.VALUE_PRESENT,
        BeeMemoryModules.HIVE_INSTANCE.get() to MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
    ),
    1
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        return owner.springTension <= 0f || owner.rechargeFinishTick >= 0
    }

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val hive = entity.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get()

        // Phase 2: Currently recharging — check if done
        if (entity.rechargeFinishTick >= 0) {
            if (gameTime >= entity.rechargeFinishTick) {
                entity.springTension = 1.0f
                entity.rechargeFinishTick = -1
                BeeDebug.log(entity, "Spring recharged — resuming work")
            }
            return
        }

        // Phase 1: Need to reach hive first
        if (entity.blockPosition().closerThan(hive.pos, 4.0)) {
            // At hive — initiate recharge
            val ctx = entity.getBeeContext()
            val baseTicks = CBeesConfig.springRechargeTicks.get()
            val rechargeTicks = (baseTicks / ctx.springEfficiency).toInt().coerceAtLeast(20)

            // Portable beehive: consume air for rewind
            if (hive is PortableBeeHive) {
                val airCost = CBeesConfig.portableAirPerRewind.get()
                if (!hive.hasAir(airCost)) {
                    BeeDebug.log(entity, "Not enough air for spring recharge")
                    return
                }
                hive.consumeAir(airCost)
            }

            entity.rechargeFinishTick = gameTime + rechargeTicks
            BeeDebug.log(entity, "Recharging spring at hive ($rechargeTicks ticks)")
        } else {
            // Navigate to hive
            BeeDebug.log(entity, "Flying to hive for spring recharge")
            entity.brain.setMemory(MemoryModuleType.WALK_TARGET, hive.walkTarget())
        }
    }
}
