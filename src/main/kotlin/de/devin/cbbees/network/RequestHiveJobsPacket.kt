package de.devin.cbbees.network

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

class RequestHiveJobsPacket(val pos: BlockPos) {
    companion object {
        fun encode(pkt: RequestHiveJobsPacket, buf: FriendlyByteBuf) {
            buf.writeBlockPos(pkt.pos)
        }

        fun decode(buf: FriendlyByteBuf) = RequestHiveJobsPacket(buf.readBlockPos())

        fun handleServer(pkt: RequestHiveJobsPacket, player: ServerPlayer) {
            HiveJobsSyncPacket.sendSnapshotTo(player, pkt.pos)
        }
    }
}
