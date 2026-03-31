package de.devin.cbbees.network

import de.devin.cbbees.content.bee.client.BeeTargetLineHandler
import net.minecraft.network.FriendlyByteBuf

class BeeDebugSyncPacket(val enabled: Boolean) {
    companion object {
        fun encode(pkt: BeeDebugSyncPacket, buf: FriendlyByteBuf) {
            buf.writeBoolean(pkt.enabled)
        }

        fun decode(buf: FriendlyByteBuf) = BeeDebugSyncPacket(buf.readBoolean())

        fun handleClient(pkt: BeeDebugSyncPacket) {
            BeeTargetLineHandler.debugEnabled = pkt.enabled
        }
    }
}
