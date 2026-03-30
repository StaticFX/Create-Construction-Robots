package de.devin.cbbees.compat

import net.minecraft.world.item.ItemStack

/**
 * Forge 1.20.1 version — uses NBT tag on ItemStack.
 * Stores honey fuel as an integer tag "HoneyFuel".
 */
object HoneyFuelHelper {

    private const val TAG_KEY = "HoneyFuel"

    @JvmStatic
    fun get(stack: ItemStack): Int =
        stack.tag?.getInt(TAG_KEY) ?: 0

    @JvmStatic
    fun set(stack: ItemStack, value: Int) {
        stack.orCreateTag.putInt(TAG_KEY, value)
    }
}
