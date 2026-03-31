package de.devin.cbbees.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

class RequestPlayerJobsPacket {
    companion object {
        fun encode(pkt: RequestPlayerJobsPacket, buf: FriendlyByteBuf) { }
        fun decode(buf: FriendlyByteBuf) = RequestPlayerJobsPacket()

        fun handleServer(pkt: RequestPlayerJobsPacket, player: ServerPlayer) {
            HiveJobsSyncPacket.sendPlayerSnapshotTo(player)
        }
    }
}
