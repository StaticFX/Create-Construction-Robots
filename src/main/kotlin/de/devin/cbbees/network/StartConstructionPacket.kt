package de.devin.cbbees.network

import de.devin.cbbees.compat.SchematicDataHelper
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.SchematicPlacement
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import java.util.*

class StartConstructionPacket(
    val anchor: BlockPos,
    val rotation: Rotation,
    val mirror: Mirror
) {
    companion object {
        fun encode(pkt: StartConstructionPacket, buf: FriendlyByteBuf) {
            buf.writeBlockPos(pkt.anchor)
            buf.writeEnum(pkt.rotation)
            buf.writeEnum(pkt.mirror)
        }

        fun decode(buf: FriendlyByteBuf) = StartConstructionPacket(
            buf.readBlockPos(),
            buf.readEnum(Rotation::class.java),
            buf.readEnum(Mirror::class.java)
        )

        fun handleServer(pkt: StartConstructionPacket, player: ServerPlayer) {
            val mainHand = player.mainHandItem
            if (!AllItems.CONSTRUCTION_PLANNER.isIn(mainHand)) {
                player.displayClientMessage(
                    Component.translatable("cbbees.construction.requires_planner"), true
                )
                return
            }

            if (!SchematicDataHelper.hasFile(mainHand)) {
                player.displayClientMessage(Component.translatable("cbbees.construction.no_schematic"), true)
                return
            }

            SchematicDataHelper.setAnchor(mainHand, pkt.anchor)
            SchematicDataHelper.setRotation(mainHand, pkt.rotation)
            SchematicDataHelper.setMirror(mainHand, pkt.mirror)
            SchematicDataHelper.setDeployed(mainHand, true)

            val schematicFile = SchematicDataHelper.getFile(mainHand)
            val schematicOwner = SchematicDataHelper.getOwner(mainHand)
            if (schematicFile != null && schematicOwner != null) {
                ensureSchematicUploaded(schematicOwner, schematicFile)
            }

            val schematicStack = mainHand
            val bridge = SchematicCreateBridge(player.level())
            if (!bridge.loadSchematic(schematicStack)) {
                player.displayClientMessage(Component.translatable("cbbees.construction.load_failed"), true)
                return
            }

            val jobId = UUID.randomUUID()
            val job = BeeJob(jobId, BlockPos.ZERO, player.level()).apply {
                ownerId = player.uuid

                val file = SchematicDataHelper.getFile(schematicStack)
                if (file != null) {
                    val anchor = SchematicDataHelper.getAnchor(schematicStack)
                    uniquenessKey = SchematicJobKey(player.uuid, file, anchor.x, anchor.y, anchor.z)
                    schematicPlacement = SchematicPlacement(
                        file = file,
                        anchor = anchor,
                        rotation = SchematicDataHelper.getRotation(schematicStack),
                        mirror = SchematicDataHelper.getMirror(schematicStack)
                    )
                }
            }

            val batches = bridge.generateBuildTasks(job)
            if (batches.isNotEmpty()) {
                job.centerPos = bridge.getAnchor() ?: batches[0].targetPosition
                job.batches.addAll(batches)

                ServerBeeNetworkManager.findPortableHive(player.uuid)?.let {
                    ServerBeeNetworkManager.reconnectPortableHive(it)
                }
                GlobalJobPool.dispatchNewJob(job)
                ConstructionPlannerItem.clearSchematic(schematicStack)
                HiveJobsSyncPacket.sendPlayerSnapshotTo(player)

                player.displayClientMessage(
                    Component.translatable("cbbees.construction.started", batches.size),
                    true
                )
            } else {
                player.displayClientMessage(Component.translatable("cbbees.construction.no_tasks"), true)
            }
        }
    }
}
