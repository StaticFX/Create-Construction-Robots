package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.config.CBeesConfig
import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget

/**
 * BumbleBee behavior: fly to the source (EXTRACT) port and pick up items.
 *
 * Only runs when the bee has a transport task and hasn't picked up items yet
 * (inventory is empty).
 */
class PickUpFromSourceBehavior : Behavior<MechanicalBumbleBeeEntity>(
    mapOf(
        BeeMemoryModules.TRANSPORT_TASK.get() to MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
    ),
    1
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBumbleBeeEntity): Boolean {
        if (owner.springTension <= 0f) return false
        // Only pick up if inventory is empty (haven't collected yet)
        return owner.isInventoryEmpty()
    }

    override fun start(level: ServerLevel, entity: MechanicalBumbleBeeEntity, gameTime: Long) {
        val task = entity.brain.getMemory(BeeMemoryModules.TRANSPORT_TASK.get()).get()
        val sourcePos = task.sourcePos

        if (entity.blockPosition().closerThan(sourcePos, entity.workRange)) {
            // At the source port — extract items
            val network = entity.network() ?: run {
                BeeDebug.logForEntity(entity, "Bumble", "No network — clearing task")
                entity.brain.eraseMemory(BeeMemoryModules.TRANSPORT_TASK.get())
                return
            }

            val port = network.transportPorts.find { it.pos == sourcePos && it.isValidProvider() }
            if (port == null) {
                BeeDebug.logForEntity(entity, "Bumble", "Source port gone at $sourcePos — clearing task")
                entity.brain.eraseMemory(BeeMemoryModules.TRANSPORT_TASK.get())
                return
            }

            // Release reservation now that we're at the port
            port.releaseReservation(entity.uuid)

            var pickedUp = false
            for (item in task.items) {
                if (entity.isInventoryFull()) break
                if (port.hasItemStack(item) && port.removeItemStack(item)) {
                    val remainder = entity.addToInventory(item.copy())
                    if (!remainder.isEmpty) {
                        port.addItemStack(remainder)
                    }
                    pickedUp = true
                    entity.consumeSpring(CBeesConfig.springDrainPickup.get())
                    BeeDebug.logForEntity(entity, "Bumble", "Picked up ${item.count}x ${item.item} from source at $sourcePos")
                }
            }

            if (!pickedUp) {
                BeeDebug.logForEntity(entity, "Bumble", "No items available at source — clearing task")
                entity.brain.eraseMemory(BeeMemoryModules.TRANSPORT_TASK.get())
            }
        } else {
            BeeDebug.logForEntity(entity, "Bumble", "Flying to source port at $sourcePos")
            entity.brain.setMemory(
                MemoryModuleType.WALK_TARGET,
                WalkTarget(sourcePos, 1.0f, 1)
            )
        }
    }
}
