package de.devin.ccr.network

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.domain.GlobalJobPool
import de.devin.ccr.content.domain.job.BeeJob
import de.devin.ccr.content.schematics.goals.DeconstructionGoal
import java.util.*
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Packet sent from client to server to start deconstruction of blocks within a selected area.
 *
 * When received, the server will:
 * 1. Validate the selection positions
 * 2. Generate removal tasks for all blocks within the selected area
 * 3. Spawn robots to perform the deconstruction
 *
 * @param pos1 First corner of the selection area
 * @param pos2 Second corner of the selection area
 */
class StartDeconstructionPacket(
    val pos1: BlockPos,
    val pos2: BlockPos
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<StartDeconstructionPacket>(CreateCCR.asResource("start_deconstruction"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StartDeconstructionPacket> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StartDeconstructionPacket::pos1,
            BlockPos.STREAM_CODEC, StartDeconstructionPacket::pos2,
            ::StartDeconstructionPacket
        )

        fun handle(payload: StartDeconstructionPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork

                CreateCCR.LOGGER.info("Received deconstruction request from ${player.name.string} for area ${payload.pos1} to ${payload.pos2}")

                // Start deconstruction with the provided positions
                val goal = DeconstructionGoal(payload.pos1, payload.pos2)
                val jobId = UUID.randomUUID()

                val job = BeeJob(jobId, BlockPos.ZERO, player.level()).apply {
                    ownerId = player.uuid
                    uniquenessKey = goal.createJobKey(player.uuid)
                }

                val tasks = goal.generateTasks(job)
                if (tasks.isNotEmpty()) {
                    val center = goal.getCenterPos(player.level(), tasks)
                    val finalJob = job.copy(centerPos = center).apply {
                        ownerId = job.ownerId
                        uniquenessKey = job.uniquenessKey
                        addTasks(tasks)
                    }

                    GlobalJobPool.dispatchNewJob(finalJob)
                    goal.onJobStarted(player)
                    player.displayClientMessage(goal.getStartMessage(tasks.size), true)
                } else {
                    player.displayClientMessage(goal.getNoTasksMessage(), true)
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
