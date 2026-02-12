package de.devin.ccr.network

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.backpack.client.TaskProgressTracker
import java.util.UUID
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Packet sent from server to client to sync task progress information.
 * Used to display task status toasts in the BeehiveScreen.
 *
 * @param globalTotal Global total number of tasks generated across all jobs
 * @param globalCompleted Global number of tasks completed across all jobs
 * @param jobProgress Map of jobId to (completed, total) tasks
 */
class TaskProgressSyncPacket(
    val globalTotal: Int,
    val globalCompleted: Int,
    val jobProgress: Map<UUID, Pair<Int, Int>>
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<TaskProgressSyncPacket>(CreateCCR.asResource("task_progress_sync"))

        private val JOB_DATA_CODEC: StreamCodec<RegistryFriendlyByteBuf, Pair<Int, Int>> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, { it.first },
            ByteBufCodecs.VAR_INT, { it.second },
            { first, second -> first to second }
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, TaskProgressSyncPacket> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            TaskProgressSyncPacket::globalTotal,
            ByteBufCodecs.VAR_INT,
            TaskProgressSyncPacket::globalCompleted,
            ByteBufCodecs.map({ mutableMapOf() }, net.minecraft.core.UUIDUtil.STREAM_CODEC, JOB_DATA_CODEC),
            TaskProgressSyncPacket::jobProgress,
            ::TaskProgressSyncPacket
        )

        fun build() {

        }

        fun handle(payload: TaskProgressSyncPacket, context: IPayloadContext) {
            context.enqueueWork {
                // Update client-side tracker
                TaskProgressTracker.update(
                    payload.globalTotal,
                    payload.globalCompleted,
                    payload.jobProgress
                )
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
