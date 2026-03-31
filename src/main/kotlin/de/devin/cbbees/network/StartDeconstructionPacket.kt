package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.*

class StartDeconstructionPacket(
    val pos1: BlockPos,
    val pos2: BlockPos
) {
    companion object {
        fun encode(pkt: StartDeconstructionPacket, buf: FriendlyByteBuf) {
            buf.writeBlockPos(pkt.pos1)
            buf.writeBlockPos(pkt.pos2)
        }

        fun decode(buf: FriendlyByteBuf) = StartDeconstructionPacket(buf.readBlockPos(), buf.readBlockPos())

        fun handleServer(pkt: StartDeconstructionPacket, player: ServerPlayer) {
            CreateBuzzyBeez.LOGGER.info("Received deconstruction request from ${player.name.string} for area ${pkt.pos1} to ${pkt.pos2}")

            val jobId = UUID.randomUUID()
            val job = BeeJob(jobId, BlockPos.ZERO, player.level()).apply {
                ownerId = player.uuid
                uniquenessKey =
                    SchematicJobKey(player.uuid, "deconstruct_area", pkt.pos1.x, pkt.pos1.y, pkt.pos1.z)
            }

            val tasks = SchematicCreateBridge(player.level()).generateRemovalTasks(pkt.pos1, pkt.pos2, job)
            if (tasks.isNotEmpty()) {
                job.centerPos = BlockPos(
                    (pkt.pos1.x + pkt.pos2.x) / 2,
                    (pkt.pos1.y + pkt.pos2.y) / 2,
                    (pkt.pos1.z + pkt.pos2.z) / 2
                )
                job.addBatches(tasks)

                ServerBeeNetworkManager.findPortableHive(player.uuid)?.let {
                    ServerBeeNetworkManager.reconnectPortableHive(it)
                }

                GlobalJobPool.dispatchNewJob(job)
                player.displayClientMessage(
                    Component.translatable("cbbees.deconstruction.started", tasks.size),
                    true
                )
            } else {
                player.displayClientMessage(Component.translatable("cbbees.deconstruction.no_blocks"), true)
            }
        }
    }
}
