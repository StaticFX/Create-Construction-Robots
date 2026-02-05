package de.devin.ccr.network

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.backpack.client.TaskProgressTracker
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Packet sent from server to client to sync task progress information.
 * Used to display task status toasts in the BackpackScreen.
 * 
 * @param totalTasks Total number of tasks generated
 * @param completedTasks Number of tasks completed
 * @param activeTasks Number of currently active tasks (robots working)
 * @param pendingTasks Number of tasks waiting to be assigned
 * @param taskDescriptions List of descriptions for active tasks (max 3)
 */
class TaskProgressSyncPacket(
    val totalTasks: Int,
    val completedTasks: Int,
    val activeTasks: Int,
    val pendingTasks: Int,
    val taskDescriptions: List<String>
) : CustomPacketPayload {
    
    companion object {
        val TYPE = CustomPacketPayload.Type<TaskProgressSyncPacket>(CreateCCR.asResource("task_progress_sync"))
        
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, TaskProgressSyncPacket> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TaskProgressSyncPacket::totalTasks,
            ByteBufCodecs.VAR_INT, TaskProgressSyncPacket::completedTasks,
            ByteBufCodecs.VAR_INT, TaskProgressSyncPacket::activeTasks,
            ByteBufCodecs.VAR_INT, TaskProgressSyncPacket::pendingTasks,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TaskProgressSyncPacket::taskDescriptions,
            ::TaskProgressSyncPacket
        )
        
        fun handle(payload: TaskProgressSyncPacket, context: IPayloadContext) {
            context.enqueueWork {
                // Update client-side tracker
                TaskProgressTracker.update(
                    totalTasks = payload.totalTasks,
                    completedTasks = payload.completedTasks,
                    activeTasks = payload.activeTasks,
                    pendingTasks = payload.pendingTasks,
                    taskDescriptions = payload.taskDescriptions
                )
            }
        }
    }
    
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
