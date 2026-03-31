package de.devin.cbbees.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.item.ItemStack

/**
 * NeoForge 1.21.1 — ItemStack serialization requires RegistryFriendlyByteBuf
 * for component-based encoding. The buf is always a RegistryFriendlyByteBuf
 * at runtime in NeoForge's network pipeline.
 */
object ItemStackBufHelper {
    @JvmStatic
    fun write(buf: FriendlyByteBuf, stack: ItemStack) {
        ItemStack.STREAM_CODEC.encode(buf as RegistryFriendlyByteBuf, stack)
    }

    @JvmStatic
    fun read(buf: FriendlyByteBuf): ItemStack {
        return ItemStack.STREAM_CODEC.decode(buf as RegistryFriendlyByteBuf)
    }
}
