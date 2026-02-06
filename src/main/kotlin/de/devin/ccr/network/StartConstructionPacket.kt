package de.devin.ccr.network

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.schematics.BeeWorkManager
import de.devin.ccr.content.schematics.SchematicRobotHandler
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
                if (SchematicRobotHandler.isValidSchematic(mainHand) && SchematicRobotHandler.isSchematicDeployed(mainHand)) {
                    schematicStack = mainHand
                } else {
                    // Check whole inventory
                    for (i in 0 until player.inventory.containerSize) {
                        val stack = player.inventory.getItem(i)
                        if (SchematicRobotHandler.isValidSchematic(stack) && SchematicRobotHandler.isSchematicDeployed(stack)) {
                            schematicStack = stack
                            break
                        }
                    }
                }

                if (!schematicStack.isEmpty) {
                    BeeWorkManager.startConstruction(player, schematicStack)
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
