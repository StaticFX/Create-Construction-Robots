package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import java.util.*
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext
import de.devin.cbbees.util.ServerSide

class StartConstructionPacket private constructor() : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<StartConstructionPacket>(CreateBuzzyBeez.asResource("start_construction"))

        /** Singleton instance - MUST be used when sending this packet */
        val INSTANCE = StartConstructionPacket()

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StartConstructionPacket> = StreamCodec.unit(INSTANCE)

        @ServerSide
        fun handle(payload: StartConstructionPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork

                // Find a schematic in inventory
                var schematicStack = net.minecraft.world.item.ItemStack.EMPTY

                // First check item in hand
                val mainHand = player.mainHandItem
                if (SchematicCreateBridge.isValidSchematic(mainHand) && SchematicCreateBridge.isSchematicDeployed(
                        mainHand
                    )
                ) {
                    schematicStack = mainHand
                } else {
                    // Check whole inventory
                    for (i in 0 until player.inventory.containerSize) {
                        val stack = player.inventory.getItem(i)
                        if (SchematicCreateBridge.isValidSchematic(stack) && SchematicCreateBridge.isSchematicDeployed(
                                stack
                            )
                        ) {
                            schematicStack = stack
                            break
                        }
                    }
                }

                if (!schematicStack.isEmpty) {
                    val bridge = SchematicCreateBridge(player.level())
                    if (!bridge.loadSchematic(schematicStack)) {
                        player.displayClientMessage(Component.translatable("cbbees.construction.load_failed"), true)
                        return@enqueueWork
                    }

                    val jobId = UUID.randomUUID()
                    val job = BeeJob(jobId, BlockPos.ZERO, player.level()).apply {
                        ownerId = player.uuid

                        val schematicFile = schematicStack.get(com.simibubi.create.AllDataComponents.SCHEMATIC_FILE)
                        val anchor = schematicStack.get(com.simibubi.create.AllDataComponents.SCHEMATIC_ANCHOR)
                        if (schematicFile != null && anchor != null) {
                            uniquenessKey = SchematicJobKey(player.uuid, schematicFile, anchor.x, anchor.y, anchor.z)
                        }
                    }

                    val batches = bridge.generateBuildTasks(job)
                    if (batches.isNotEmpty()) {
                        job.centerPos = bridge.getAnchor() ?: batches[0].targetPosition
                        job.batches.addAll(batches)

                        GlobalJobPool.dispatchNewJob(job)

                        // Requirement 1: Unanchor schematic instead of shrinking
                        schematicStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_DEPLOYED, false)
                        schematicStack.remove(com.simibubi.create.AllDataComponents.SCHEMATIC_ANCHOR)

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
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
