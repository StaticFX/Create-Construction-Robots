package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Sent by the client when opening the portable beehive job screen.
 * Server responds with a [HiveJobsSyncPacket] at [net.minecraft.core.BlockPos.ZERO]
 * containing all jobs owned by the requesting player.
 */
class RequestPlayerJobsPacket : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<RequestPlayerJobsPacket>(CreateBuzzyBeez.asResource("player_jobs_req"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestPlayerJobsPacket> =
            StreamCodec.of(
                { _, _ -> },
                { _ -> RequestPlayerJobsPacket() }
            )

        fun handle(payload: RequestPlayerJobsPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                HiveJobsSyncPacket.sendPlayerSnapshotTo(ctx.player() as ServerPlayer)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
