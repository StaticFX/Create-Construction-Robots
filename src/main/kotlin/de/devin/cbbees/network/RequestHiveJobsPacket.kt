package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

class RequestHiveJobsPacket(val pos: BlockPos) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<RequestHiveJobsPacket>(CreateBuzzyBeez.asResource("hive_jobs_req"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestHiveJobsPacket> =
            StreamCodec.of(
                { buf, p -> buf.writeBlockPos(p.pos) },
                { buf -> RequestHiveJobsPacket(buf.readBlockPos()) }
            )

        fun handle(payload: RequestHiveJobsPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                HiveJobsSyncPacket.sendSnapshotTo(ctx.player() as net.minecraft.server.level.ServerPlayer, payload.pos)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
