package de.devin.ccr.content.beehive

import de.devin.ccr.blocks.AllBlocks
import de.devin.ccr.content.robots.MechanicalBeeItem
import de.devin.ccr.content.upgrades.BeeUpgradeItem
import de.devin.ccr.registry.AllMenuTypes
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.ItemStackHandler
import net.neoforged.neoforge.items.SlotItemHandler

class MechanicalBeehiveMenu(
    containerId: Int,
    val playerInventory: Inventory,
    val content: MechanicalBeehiveBlockEntity
) : AbstractContainerMenu(AllMenuTypes.MECHANICAL_BEEHIVE.get(), containerId) {

    init {
        // Bee Slots (3x3)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                addSlot(BeeSlot(content.beeInventory, col + row * 3, 15 + col * 18, 15 + row * 18))
            }
        }

        // Upgrade Slots (3x3)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                addSlot(UpgradeSlot(content.upgradeInventory, col + row * 3, 15 + col * 18, 75 + row * 18))
            }
        }

        // Player Inventory
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 145 + row * 18))
            }
        }

        for (col in 0 until 9) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, 203))
        }
    }

    override fun stillValid(player: Player): Boolean {
        return AbstractContainerMenu.stillValid(ContainerLevelAccess.create(content.world, content.position), player, AllBlocks.MECHANICAL_BEEHIVE.get())
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots[index]
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY

        val stack = slot.item
        val copy = stack.copy()

        if (index < 18) { // From Hive to Player
            if (!moveItemStackTo(stack, 18, 54, true)) {
                return ItemStack.EMPTY
            }
        } else { // From Player to Hive
            if (stack.item is MechanicalBeeItem) {
                if (!moveItemStackTo(stack, 0, 9, false)) {
                    return ItemStack.EMPTY
                }
            } else if (stack.item is BeeUpgradeItem) {
                if (!moveItemStackTo(stack, 9, 18, false)) {
                    return ItemStack.EMPTY
                }
            }
        }

        if (stack.isEmpty) {
            slot.setByPlayer(ItemStack.EMPTY)
        } else {
            slot.setChanged()
        }

        return copy
    }

    inner class BeeSlot(handler: ItemStackHandler, index: Int, x: Int, y: Int) : SlotItemHandler(handler, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = stack.item is MechanicalBeeItem
    }

    inner class UpgradeSlot(handler: ItemStackHandler, index: Int, x: Int, y: Int) : SlotItemHandler(handler, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = stack.item is BeeUpgradeItem
    }
}
