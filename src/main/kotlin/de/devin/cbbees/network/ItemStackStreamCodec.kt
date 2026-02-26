package de.devin.cbbees.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack

object ItemStackStreamCodec {
    val CODEC: StreamCodec<RegistryFriendlyByteBuf, ItemStack> = ItemStack.STREAM_CODEC
}
