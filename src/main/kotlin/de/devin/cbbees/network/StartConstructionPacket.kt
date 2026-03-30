package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.compat.SchematicDataHelper
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.SchematicPlacement
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.util.ServerSide
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.*

/**
 * Client -> Server packet that carries the client-side schematic placement data
 * (anchor, rotation, mirror) since Create's SchematicHandler only updates the
 * client ItemStack when the player deploys/moves/rotates the schematic.
 */
class StartConstructionPacket(
    val anchor: BlockPos,
    val rotation: Rotation,
    val mirror: Mirror
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<StartConstructionPacket>(CreateBuzzyBeez.asResource("start_construction"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StartConstructionPacket> = StreamCodec.of(
            { buf, pkt ->
                buf.writeBlockPos(pkt.anchor)
                buf.writeEnum(pkt.rotation)
                buf.writeEnum(pkt.mirror)
            },
            { buf ->
                StartConstructionPacket(
                    buf.readBlockPos(),
                    buf.readEnum(Rotation::class.java),
                    buf.readEnum(Mirror::class.java)
                )
            }
        )

        @ServerSide
        fun handle(payload: StartConstructionPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork

                // Construction can only be started from the Construction Planner
                val mainHand = player.mainHandItem
                if (!AllItems.CONSTRUCTION_PLANNER.isIn(mainHand)) {
                    player.displayClientMessage(
                        Component.translatable("cbbees.construction.requires_planner"), true
                    )
                    return@enqueueWork
                }

                if (!SchematicDataHelper.hasFile(mainHand)) {
                    player.displayClientMessage(Component.translatable("cbbees.construction.no_schematic"), true)
                    return@enqueueWork
                }

                // Sync placement data from client — Create's SchematicHandler only
                // updates these on the client-side ItemStack
                SchematicDataHelper.setAnchor(mainHand, payload.anchor)
                SchematicDataHelper.setRotation(mainHand, payload.rotation)
                SchematicDataHelper.setMirror(mainHand, payload.mirror)
                SchematicDataHelper.setDeployed(mainHand, true)

                // Ensure schematic file exists in uploaded directory
                val schematicFile = SchematicDataHelper.getFile(mainHand)
                val schematicOwner = SchematicDataHelper.getOwner(mainHand)
                if (schematicFile != null && schematicOwner != null) {
                    ensureSchematicUploaded(schematicOwner, schematicFile)
                }

                val schematicStack = mainHand
                val bridge = SchematicCreateBridge(player.level())
                if (!bridge.loadSchematic(schematicStack)) {
                    player.displayClientMessage(Component.translatable("cbbees.construction.load_failed"), true)
                    return@enqueueWork
                }

                val jobId = UUID.randomUUID()
                val job = BeeJob(jobId, BlockPos.ZERO, player.level()).apply {
                    ownerId = player.uuid

                    val schematicFile = SchematicDataHelper.getFile(schematicStack)
                    if (schematicFile != null) {
                        val anchor = SchematicDataHelper.getAnchor(schematicStack)
                        uniquenessKey = SchematicJobKey(player.uuid, schematicFile, anchor.x, anchor.y, anchor.z)
                        schematicPlacement = SchematicPlacement(
                            file = schematicFile,
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

                    // Construction Planner is reusable — clear all schematic data
                    ConstructionPlannerItem.clearSchematic(schematicStack)

                    // Requirement 2: Immediate sync for ghosts
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

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
