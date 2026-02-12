package de.devin.ccr.network

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.domain.GlobalJobPool
import de.devin.ccr.content.domain.job.BeeJob
import de.devin.ccr.content.schematics.goals.ConstructionGoal
import de.devin.ccr.content.schematics.SchematicCreateBridge
import java.util.*
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
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
                    val goal = ConstructionGoal(schematicStack)
                    val jobId = UUID.randomUUID()

                    // First create the job object with dummy center (we'll update it later or goal can use it)
                    val job = BeeJob(jobId, BlockPos.ZERO, player.level()).apply {
                        ownerId = player.uuid
                        uniquenessKey = goal.createJobKey(player.uuid)
                    }

                    val tasks = goal.generateTasks(job)
                    if (tasks.isNotEmpty()) {
                        val center = goal.getCenterPos(player.level(), tasks)
                        // Update job with actual center and tasks
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
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
