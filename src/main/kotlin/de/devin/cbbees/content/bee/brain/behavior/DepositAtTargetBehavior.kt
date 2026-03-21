package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.item.ItemEntity

/**
 * BumbleBee behavior: deposit carried items at the target (INSERT) port.
 *
 * Runs when the bee has items and is at the target port.
 * After depositing, clears the transport task so the bee returns to the hive.
 */
class DepositAtTargetBehavior : Behavior<MechanicalBumbleBeeEntity>(
    mapOf(
        BeeMemoryModules.TRANSPORT_TASK.get() to MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
    ),
    1
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBumbleBeeEntity): Boolean {
        if (owner.springTension <= 0f) return false
        if (owner.isInventoryEmpty()) return false

        val task = owner.brain.getMemory(BeeMemoryModules.TRANSPORT_TASK.get()).get()
        return owner.blockPosition().closerThan(task.targetPos, owner.workRange)
    }

    override fun start(level: ServerLevel, entity: MechanicalBumbleBeeEntity, gameTime: Long) {
        val task = entity.brain.getMemory(BeeMemoryModules.TRANSPORT_TASK.get()).get()
        val targetPos = task.targetPos

        val network = entity.network()
        val port = network?.transportPorts?.find { it.pos == targetPos && it.isValidRequester() }

        val items = entity.getInventoryContents().map { it.copy() }

        if (port != null) {
            for (item in items) {
                val remainder = port.addItemStack(item)
                if (!remainder.isEmpty) {
                    // Drop overflow on the ground near the port
                    val itemEntity = ItemEntity(
                        level,
                        targetPos.x + 0.5,
                        targetPos.y + 0.5,
                        targetPos.z + 0.5,
                        remainder
                    )
                    level.addFreshEntity(itemEntity)
                }
                entity.removeFromInventory(item, item.count)
                entity.consumeSpring(CBBeesConfig.springDrainDeposit.get())
                BeeDebug.logForEntity(entity, "Bumble", "Deposited ${item.count}x ${item.item} at target $targetPos")
            }
        } else {
            // No port available, drop on the ground
            BeeDebug.logForEntity(entity, "Bumble", "Target port gone at $targetPos — dropping items")
            for (item in items) {
                entity.removeFromInventory(item, item.count)
                val itemEntity = ItemEntity(level, entity.x, entity.y, entity.z, item)
                level.addFreshEntity(itemEntity)
            }
        }

        // Task complete — clear it so bee returns to hive
        entity.brain.eraseMemory(BeeMemoryModules.TRANSPORT_TASK.get())
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
    }
}
