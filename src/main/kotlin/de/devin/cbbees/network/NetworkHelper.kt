package de.devin.cbbees.network

import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor

/**
 * NeoForge 1.21.1 — wraps packets via [AllPackets.wrapPayload] and sends
 * through NeoForge's [PacketDistributor].
 */
object NetworkHelper {

    @JvmStatic
    fun sendToServer(packet: Any) {
        PacketDistributor.sendToServer(AllPackets.wrapPayload(packet))
    }

    @JvmStatic
    fun sendToPlayer(player: ServerPlayer, packet: Any) {
        PacketDistributor.sendToPlayer(player, AllPackets.wrapPayload(packet))
    }
}
