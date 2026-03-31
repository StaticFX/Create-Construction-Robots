package de.devin.cbbees.network

import de.devin.cbbees.compat.SchematicDataHelper
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.SchematicPlacement
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import de.devin.cbbees.items.AllItems
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import java.util.UUID

class InstantConstructionPacket(
    val schematicName: String,
    val anchor: BlockPos,
    val rotation: Rotation,
    val mirror: Mirror
) {
    companion object {
        fun encode(pkt: InstantConstructionPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(pkt.schematicName)
            buf.writeBlockPos(pkt.anchor)
            buf.writeEnum(pkt.rotation)
            buf.writeEnum(pkt.mirror)
        }

        fun decode(buf: FriendlyByteBuf) = InstantConstructionPacket(
            buf.readUtf(),
            buf.readBlockPos(),
            buf.readEnum(Rotation::class.java),
            buf.readEnum(Mirror::class.java)
        )

        fun handleServer(pkt: InstantConstructionPacket, player: ServerPlayer) {
            val mainHand = player.mainHandItem

            if (!AllItems.CONSTRUCTION_PLANNER.isIn(mainHand)) {
                player.displayClientMessage(
                    Component.translatable("cbbees.construction.requires_planner"), true
                )
                return
            }

            val name = pkt.schematicName
            if (name.contains("..") || name.contains("/") || name.contains("\\")) return

            val owner = player.gameProfile.name
            ensureSchematicUploaded(owner, name)

            SchematicDataHelper.setPlacement(mainHand, name, owner, pkt.anchor, pkt.rotation, pkt.mirror)

            try {
                com.simibubi.create.content.schematics.SchematicItem.writeSize(player.level(), mainHand)
            } catch (_: Exception) { }

            val bridge = SchematicCreateBridge(player.level())
            if (!bridge.loadSchematic(mainHand)) {
                player.displayClientMessage(
                    Component.translatable("cbbees.construction.load_failed"), true
                )
                ConstructionPlannerItem.clearSchematic(mainHand)
                return
            }

            val jobId = UUID.randomUUID()
            val job = BeeJob(jobId, BlockPos.ZERO, player.level()).apply {
                ownerId = player.uuid
                uniquenessKey = SchematicJobKey(
                    player.uuid, name,
                    pkt.anchor.x, pkt.anchor.y, pkt.anchor.z
                )
                schematicPlacement = SchematicPlacement(
                    file = name,
                    anchor = pkt.anchor,
                    rotation = pkt.rotation,
                    mirror = pkt.mirror
                )
            }

            val batches = bridge.generateBuildTasks(job)
            if (batches.isNotEmpty()) {
                job.centerPos = bridge.getAnchor() ?: batches[0].targetPosition
                job.batches.addAll(batches)

                ServerBeeNetworkManager.findPortableHive(player.uuid)?.let {
                    ServerBeeNetworkManager.reconnectPortableHive(it)
                }
                GlobalJobPool.dispatchNewJob(job)
                ConstructionPlannerItem.clearSchematic(mainHand)
                HiveJobsSyncPacket.sendPlayerSnapshotTo(player)

                player.displayClientMessage(
                    Component.translatable("cbbees.construction.started", batches.size), true
                )
            } else {
                ConstructionPlannerItem.clearSchematic(mainHand)
                player.displayClientMessage(
                    Component.translatable("cbbees.construction.no_tasks"), true
                )
            }
        }
    }
}
