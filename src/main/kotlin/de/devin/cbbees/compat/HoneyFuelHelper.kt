package de.devin.cbbees.compat

import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.world.item.ItemStack

/**
 * Centralizes honey fuel data access on ItemStacks.
 * On 1.21.1+: uses custom DataComponent.
 * On 1.20.1:  uses NBT tag (via preprocessor).
 */
object HoneyFuelHelper {

    @JvmStatic
    fun get(stack: ItemStack): Int =
        stack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0)

    @JvmStatic
    fun set(stack: ItemStack, value: Int) {
        stack.set(AllDataComponents.HONEY_FUEL.get(), value)
    }
}
