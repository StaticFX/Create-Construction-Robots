package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.client.BeeTargetLineHandler
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

class BeeDebugSyncPacket(val enabled: Boolean) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<BeeDebugSyncPacket>(CreateBuzzyBeez.asResource("bee_debug_sync"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BeeDebugSyncPacket> = StreamCodec.of(
            { buf, p -> buf.writeBoolean(p.enabled) },
            { buf -> BeeDebugSyncPacket(buf.readBoolean()) }
        )

        fun handle(payload: BeeDebugSyncPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                BeeTargetLineHandler.debugEnabled = payload.enabled
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
