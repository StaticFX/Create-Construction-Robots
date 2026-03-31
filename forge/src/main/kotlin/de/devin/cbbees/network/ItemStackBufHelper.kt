package de.devin.cbbees.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.item.ItemStack

/**
 * Forge 1.20.1 — ItemStack serialization uses NBT-based FriendlyByteBuf methods.
 */
object ItemStackBufHelper {
    @JvmStatic
    fun write(buf: FriendlyByteBuf, stack: ItemStack) {
        buf.writeItem(stack)
    }

    @JvmStatic
    fun read(buf: FriendlyByteBuf): ItemStack {
        return buf.readItem()
    }
}
