package de.devin.cbbees.content.domain.bee

import de.devin.cbbees.content.bee.CompositeMaterialSource
import de.devin.cbbees.content.bee.MaterialSource
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.PlayerMaterialSource
import de.devin.cbbees.content.domain.network.NetworkMaterialSource
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Handles inventory operations for constructor robots, including item collection
 * from player inventory and wireless storages.
 */
class BeeInventoryManager(private val robot: MechanicalBeeEntity) {

    /**
     * Picks up items for the current task into the bee's inventory.
     */
    fun pickUpItems(required: List<ItemStack>, context: BeeContext): Boolean {
        val ownerPlayer = robot.getOwnerPlayer() ?: return false
        if (ownerPlayer.isCreative) return true

        val source = getMaterialSource(ownerPlayer, context)
        robot.inventory.clearContent()

        for (req in required) {
            if (req.isEmpty) continue
            if (robot.isInventoryFull()) break

            val extracted = source.extractItems(req, req.count)
            if (!extracted.isEmpty) {
                robot.addToInventory(extracted)
            }
        }

        val totalRequired = required.sumOf { it.count }
        var totalCarried = 0
        for (i in 0 until robot.inventory.containerSize) {
            totalCarried += robot.inventory.getItem(i).count
        }
        return totalCarried >= totalRequired
    }

    /**
     * Deposits carried items back into player or wireless inventory.
     */
    fun depositItems(context: BeeContext) {
        val ownerPlayer = robot.getOwnerPlayer() ?: return
        if (ownerPlayer.isCreative) {
            robot.inventory.clearContent()
            return
        }

        val source = getMaterialSource(ownerPlayer, context)

        for (i in 0 until robot.inventory.containerSize) {
            val stack = robot.inventory.getItem(i)
            if (stack.isEmpty) continue
            val remaining = source.insertItems(stack)
            robot.inventory.setItem(i, remaining)
        }
    }

    /**
     * Gets a material source that checks all available inventories.
     */
    private fun getMaterialSource(ownerPlayer: ServerPlayer, context: BeeContext): MaterialSource {
        val sources = mutableListOf<MaterialSource>()

        // 1. Player inventory
        sources.add(PlayerMaterialSource(ownerPlayer))

        // 2. Network inventory
        robot.network()?.let {
            sources.add(NetworkMaterialSource(it, robot.level()))
        }

        return CompositeMaterialSource(sources)
    }
}
