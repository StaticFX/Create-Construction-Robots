package de.devin.cbbees.util

import net.minecraft.world.item.ItemStack

/**
 * A wrapper around [ItemStack] that compares by item type and components only (ignoring count).
 * Suitable for use as a map key when aggregating item quantities.
 */
data class ItemStackKey(val stack: ItemStack) {
    override fun equals(other: Any?): Boolean {
        if (other !is ItemStackKey) return false
        return ItemStack.isSameItemSameComponents(stack, other.stack)
    }

    override fun hashCode(): Int = ItemStack.hashItemAndComponents(stack)
}
