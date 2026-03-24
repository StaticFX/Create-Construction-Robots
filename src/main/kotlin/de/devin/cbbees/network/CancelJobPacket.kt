package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.GlobalJobPool
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

class CancelJobPacket(val jobId: UUID) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<CancelJobPacket>(CreateBuzzyBeez.asResource("cancel_job"))
        private val UUID_CODEC = net.minecraft.core.UUIDUtil.STREAM_CODEC

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CancelJobPacket> = StreamCodec.of(
            { buf, p -> UUID_CODEC.encode(buf, p.jobId) },
            { buf -> CancelJobPacket(UUID_CODEC.decode(buf)) }
        )

        fun handle(payload: CancelJobPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val job = GlobalJobPool.getAllJobs().firstOrNull { it.jobId == payload.jobId }
                job?.cancel()
                // Immediately sync so the client cache drops the cancelled job
                val player = ctx.player() as? net.minecraft.server.level.ServerPlayer ?: return@enqueueWork
                HiveJobsSyncPacket.sendPlayerSnapshotTo(player)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
