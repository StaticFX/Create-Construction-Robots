package de.devin.cbbees.network

import de.devin.cbbees.content.domain.GlobalJobPool
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

class StopTasksPacket {
    companion object {
        @JvmStatic
        val INSTANCE = StopTasksPacket()

        fun encode(pkt: StopTasksPacket, buf: FriendlyByteBuf) { }
        fun decode(buf: FriendlyByteBuf) = StopTasksPacket()

        fun handleServer(pkt: StopTasksPacket, player: ServerPlayer) {
            GlobalJobPool.getAllJobs().filter { it.ownerId == player.uuid }.forEach { it.cancel() }
        }
    }
}
