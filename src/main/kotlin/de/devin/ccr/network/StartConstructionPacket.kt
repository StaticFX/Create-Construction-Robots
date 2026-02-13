package de.devin.ccr.network

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.domain.GlobalJobPool
import de.devin.ccr.content.domain.job.BeeJob
import de.devin.ccr.content.schematics.SchematicCreateBridge
import de.devin.ccr.content.schematics.SchematicJobKey
import java.util.*
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

class StartConstructionPacket private constructor() : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<StartConstructionPacket>(CreateCCR.asResource("start_construction"))

        /** Singleton instance - MUST be used when sending this packet */
        val INSTANCE = StartConstructionPacket()

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StartConstructionPacket> = StreamCodec.unit(INSTANCE)

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
                        player.displayClientMessage(Component.translatable("ccr.construction.load_failed"), true)
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

                    val tasks = bridge.generateBuildTasks(job).flatMap { it.tasks }
                    if (tasks.isNotEmpty()) {
                        val center = bridge.getAnchor() ?: tasks[0].targetPos

                        val finalJob = job.copy(centerPos = center).apply {
                            ownerId = job.ownerId
                            uniquenessKey = job.uniquenessKey
                            addTasks(tasks)
                        }

                        GlobalJobPool.dispatchNewJob(finalJob)

                        if (!player.isCreative) {
                            schematicStack.shrink(1)
                        }

                        player.displayClientMessage(
                            Component.translatable("ccr.construction.started", tasks.size),
                            true
                        )
                    } else {
                        player.displayClientMessage(Component.translatable("ccr.construction.no_tasks"), true)
                    }
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
