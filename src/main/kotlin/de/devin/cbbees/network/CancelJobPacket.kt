package de.devin.cbbees.network

import de.devin.cbbees.content.domain.GlobalJobPool
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

class CancelJobPacket(val jobId: UUID) {
    companion object {
        fun encode(pkt: CancelJobPacket, buf: FriendlyByteBuf) {
            buf.writeUUID(pkt.jobId)
        }

        fun decode(buf: FriendlyByteBuf) = CancelJobPacket(buf.readUUID())

        fun handleServer(pkt: CancelJobPacket, player: ServerPlayer) {
            GlobalJobPool.getAllJobs().firstOrNull { it.jobId == pkt.jobId }?.cancel()
            HiveJobsSyncPacket.sendPlayerSnapshotTo(player)
        }
    }
}
