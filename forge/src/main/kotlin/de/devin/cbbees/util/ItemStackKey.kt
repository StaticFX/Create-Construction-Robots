package de.devin.cbbees.util

import net.minecraft.world.item.ItemStack

/**
 * Forge 1.20.1 — uses isSameItemSameTags (NBT-based) instead of isSameItemSameComponents.
 */
data class ItemStackKey(val stack: ItemStack) {
    override fun equals(other: Any?): Boolean {
        if (other !is ItemStackKey) return false
        return ItemStack.isSameItemSameTags(stack, other.stack)
    }

    override fun hashCode(): Int {
        var result = stack.item.hashCode()
        result = 31 * result + (stack.tag?.hashCode() ?: 0)
        return result
    }
}
