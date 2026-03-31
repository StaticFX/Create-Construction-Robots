package de.devin.cbbees.network

import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.PacketDistributor

/**
 * Forge 1.20.1 — sends packets via [AllPackets.CHANNEL].
 */
object NetworkHelper {

    @JvmStatic
    fun sendToServer(packet: Any) {
        AllPackets.CHANNEL.sendToServer(packet)
    }

    @JvmStatic
    fun sendToPlayer(player: ServerPlayer, packet: Any) {
        AllPackets.CHANNEL.send(PacketDistributor.PLAYER.with { player }, packet)
    }
}
