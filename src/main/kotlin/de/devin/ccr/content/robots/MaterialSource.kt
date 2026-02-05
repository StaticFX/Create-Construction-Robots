package de.devin.ccr.content.robots

import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.player.Player
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.neoforged.neoforge.capabilities.Capabilities

/**
 * Abstraction for finding and extracting materials from various sources.
 */
interface MaterialSource {
    /**
     * Tries to find and extract the required item from this source.
     * @param required The item stack representing the type of item needed.
     * @param amount The maximum amount to extract.
     * @return The extracted item stack.
     */
    fun extractItems(required: ItemStack, amount: Int): ItemStack
}

/**
 * Sources materials from a player's inventory.
 */
class PlayerMaterialSource(private val player: Player) : MaterialSource {
    override fun extractItems(required: ItemStack, amount: Int): ItemStack {
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (ItemStack.isSameItem(stack, required)) {
                return player.inventory.removeItem(i, amount)
            }
        }
        return ItemStack.EMPTY
    }
}

/**
 * Sources materials from external inventories via Wireless Link.
 */
class WirelessMaterialSource(private val level: Level, private val positions: List<BlockPos>) : MaterialSource {
    override fun extractItems(required: ItemStack, amount: Int): ItemStack {
        for (pos in positions) {
            val handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null)
            if (handler != null) {
                for (slot in 0 until handler.slots) {
                    val stack = handler.getStackInSlot(slot)
                    if (ItemStack.isSameItem(stack, required)) {
                        return handler.extractItem(slot, amount, false)
                    }
                }
            }
        }
        return ItemStack.EMPTY
    }
}

/**
 * Combines multiple material sources, checking them in order.
 */
class CompositeMaterialSource(private val sources: List<MaterialSource>) : MaterialSource {
    override fun extractItems(required: ItemStack, amount: Int): ItemStack {
        for (source in sources) {
            val items = source.extractItems(required, amount)
            if (!items.isEmpty) return items
        }
        return ItemStack.EMPTY
    }
}
