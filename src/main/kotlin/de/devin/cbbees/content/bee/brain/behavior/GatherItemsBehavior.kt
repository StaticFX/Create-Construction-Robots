package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.util.ItemStackKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Brain behavior that gathers required items from logistics ports at runtime.
 *
 * Runs before MoveToTask/ExecuteTask. Scans all remaining tasks in the current batch,
 * aggregates missing items, and navigates to logistics ports to pick them up.
 *
 * Items may be spread across multiple ports. The bee builds a gather plan mapping
 * each item type to a port, picks the port that covers the most types, and visits
 * it first. The behavior re-fires each tick until all items are gathered.
 */
class GatherItemsBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        BeeMemoryModules.CURRENT_TASK.get() to MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
    ),
    1 // Re-evaluate every tick to prevent MoveToTaskBehavior from hijacking the walk target
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        if (owner.springTension <= 0f) return false

        if (owner.isInventoryFull()) {
            BeeDebug.log(owner, "Gather: inventory full, skipping")
            return false
        }

        val batch = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val currentTask = batch.getCurrentTask()
        if (currentTask != null) {
            val action = currentTask.action
            if (action is ItemConsumingAction) {
                if (action.hasItems(owner)) {
                    // Current task already has its items — let ExecuteTaskBehavior run
                    BeeDebug.log(owner, "Gather: current task items satisfied, skipping")
                    return false
                }
            } else {
                // Current task doesn't need items — let it execute
                BeeDebug.log(owner, "Gather: current task needs no items, skipping")
                return false
            }
        }

        val missing = computeMissingItems(owner, batch)
        if (missing.isNotEmpty()) {
            BeeDebug.log(owner, "Gather: need ${missing.size} item type(s)")
        }
        return missing.isNotEmpty()
    }

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val batch = entity.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val missing = computeMissingItems(entity, batch)
        if (missing.isEmpty()) return

        val network = entity.network()

        if (network == null) {
            BeeDebug.log(entity, "No network — releasing batch")
            batch.release(gameTick = gameTime)
            entity.brain.eraseMemory(BeeMemoryModules.CURRENT_TASK.get())
            return
        }

        val hive = entity.beehive()
        val isPortable = hive is PortableBeeHive

        // Portable beehive bees: always prefer the player's inventory
        if (isPortable) {
            val player = (hive as PortableBeeHive).player
            val playerItems = missing.filter { playerHasItem(player, it) }
            if (playerItems.isNotEmpty()) {
                if (entity.blockPosition().closerThan(player.blockPosition(), entity.workRange)) {
                    for (item in playerItems) {
                        if (entity.isInventoryFull()) break
                        val extracted = extractFromPlayer(player, item)
                        if (!extracted.isEmpty) {
                            val remainder = entity.addToInventory(extracted)
                            if (!remainder.isEmpty) {
                                player.inventory.add(remainder)
                            }
                            entity.consumeSpring(CBBeesConfig.springDrainPickup.get())
                            BeeDebug.log(entity, "Picked up ${extracted.count}x ${extracted.item} from player")
                        }
                    }
                    return
                } else {
                    BeeDebug.log(entity, "Flying to player for ${playerItems.size} item type(s)")
                    entity.brain.setMemory(
                        MemoryModuleType.WALK_TARGET,
                        WalkTarget(player, 1.0f, 1)
                    )
                    return
                }
            }
        }

        // Portable beehive bees can only use logistics ports if the network
        // has a mechanical (block-based) beehive and the task is in that network's range.
        // Otherwise they are limited to the player's inventory.
        val canUsePorts = if (isPortable) {
            val taskPos = batch.targetPosition
            network.hives.any { it is MechanicalBeehiveBlockEntity && it.isInRange(taskPos) }
        } else {
            true
        }

        if (!canUsePorts) {
            BeeDebug.log(entity, "No mechanical beehive covers task — player inventory only, releasing batch")
            network.releaseReservations(entity.uuid)
            batch.release(gameTick = gameTime)
            entity.brain.eraseMemory(BeeMemoryModules.CURRENT_TASK.get())
            return
        }

        // Logistics port gathering
        val gatherPlan = buildGatherPlan(network, missing, entity.uuid)
        if (gatherPlan.isEmpty()) {
            BeeDebug.log(entity, "No providers for ${missing.size} missing item type(s) — releasing batch")
            network.releaseReservations(entity.uuid)
            batch.release(gameTick = gameTime)
            entity.brain.eraseMemory(BeeMemoryModules.CURRENT_TASK.get())
            return
        }

        // Pick the port that covers the most item types to minimize trips
        val (targetPort, itemsAtPort) = gatherPlan.maxByOrNull { it.value.size }!!

        network.releaseReservations(entity.uuid)
        targetPort.reserve(entity.uuid, itemsAtPort, level.gameTime)

        val portPos = targetPort.pos
        val workRange = entity.workRange

        if (entity.blockPosition().closerThan(portPos, workRange)) {
            // At the port — extract everything we can fit
            for (item in itemsAtPort) {
                if (entity.isInventoryFull()) break
                if (targetPort.hasItemStack(item) && targetPort.removeItemStack(item)) {
                    val remainder = entity.addToInventory(item.copy())
                    if (!remainder.isEmpty) {
                        // Put back what didn't fit
                        targetPort.addItemStack(remainder)
                    }
                    entity.consumeSpring(CBBeesConfig.springDrainPickup.get())
                    BeeDebug.log(entity, "Picked up ${item.count}x ${item.item} from port at $portPos")
                }
            }
            targetPort.releaseReservation(entity.uuid)
        } else {
            BeeDebug.log(entity, "Flying to port at $portPos for ${itemsAtPort.size} item type(s)")
            entity.brain.setMemory(
                MemoryModuleType.WALK_TARGET,
                WalkTarget(portPos, 1.0f, 1)
            )
        }
    }

    /**
     * For each missing item, finds a port that has at least some available stock.
     * Returns a map of port → list of items to pick up there.
     */
    private fun buildGatherPlan(
        network: BeeNetwork,
        missing: List<ItemStack>,
        beeId: UUID
    ): Map<LogisticsPort, List<ItemStack>> {
        val plan = mutableMapOf<LogisticsPort, MutableList<ItemStack>>()

        for (item in missing) {
            // Search with count 1 so we find any port that has this item,
            // even if it doesn't have the full requested amount
            val searchStack = item.copyWithCount(1)
            val provider = network.findAvailableProvider(searchStack, beeId) ?: continue
            plan.getOrPut(provider) { mutableListOf() }.add(item)
        }

        return plan
    }

    private fun playerHasItem(player: Player, stack: ItemStack): Boolean {
        for (i in 0 until player.inventory.containerSize) {
            val slot = player.inventory.getItem(i)
            if (!slot.isEmpty && ItemStack.isSameItemSameComponents(slot, stack)) {
                return true
            }
        }
        return false
    }

    private fun extractFromPlayer(player: Player, needed: ItemStack): ItemStack {
        var remaining = needed.count
        val result = needed.copy()
        result.count = 0

        for (i in 0 until player.inventory.containerSize) {
            val slot = player.inventory.getItem(i)
            if (!slot.isEmpty && ItemStack.isSameItemSameComponents(slot, needed)) {
                val take = minOf(remaining, slot.count)
                slot.shrink(take)
                if (slot.isEmpty) player.inventory.setItem(i, ItemStack.EMPTY)
                result.grow(take)
                remaining -= take
                if (remaining <= 0) break
            }
        }

        return if (result.count > 0) result else ItemStack.EMPTY
    }

    private fun computeMissingItems(
        bee: MechanicalBeeEntity,
        batch: TaskBatch
    ): List<ItemStack> {
        // Aggregate all required items from remaining tasks
        val totalRequired = mutableMapOf<ItemStackKey, Int>()
        for (task in batch.getRemainingTasks()) {
            val action = task.action
            if (action is ItemConsumingAction) {
                for (req in action.requiredItems) {
                    val key = ItemStackKey(req)
                    totalRequired[key] = (totalRequired[key] ?: 0) + req.count
                }
            }
        }

        // Subtract what the bee already carries
        for (carried in bee.getInventoryContents()) {
            val key = ItemStackKey(carried)
            val needed = totalRequired[key] ?: continue
            val remaining = needed - carried.count
            if (remaining <= 0) {
                totalRequired.remove(key)
            } else {
                totalRequired[key] = remaining
            }
        }

        // Convert back to ItemStack list
        return totalRequired.map { (key, count) ->
            val stack = key.stack.copy()
            stack.count = count
            stack
        }
    }

}
