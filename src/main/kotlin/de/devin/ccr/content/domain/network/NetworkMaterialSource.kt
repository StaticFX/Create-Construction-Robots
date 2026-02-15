package de.devin.ccr.content.domain.network

import de.devin.ccr.content.bee.MaterialSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class NetworkMaterialSource(val network: BeeNetwork, val level: Level) : MaterialSource {
    override fun extractItems(stack: ItemStack, amount: Int): ItemStack {
        val port = network.findProvider(stack) ?: return ItemStack.EMPTY

        // We need to actually extract from the port.
        // LogisticsPort has removeItemStack(stack) which extracts stack.count.
        // But here we might want to extract a specific amount.

        // Let's check if LogisticsPort interface can be improved or if we can use it as is.
        // removeItemStack(stack) currently extracts stack.count.

        val toExtract = stack.copyWithCount(amount)
        if (port.removeItemStack(toExtract)) {
            return toExtract
        }

        return ItemStack.EMPTY
    }

    override fun insertItems(stack: ItemStack): ItemStack {
        val port = network.findDropOff(stack) ?: return stack
        return port.addItemStack(stack)
    }
}
