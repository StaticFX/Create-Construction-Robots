package de.devin.cbbees.network

import com.simibubi.create.AllDataComponents
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.items.AllItems
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client → Server packet sent when the player selects a schematic in the
 * Construction Planner screen. The server sets the schematic data components
 * on the planner item so Create's SchematicHandler activates.
 */
class SelectSchematicPacket(val schematicName: String) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<SelectSchematicPacket>(
            CreateBuzzyBeez.asResource("select_schematic")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SelectSchematicPacket> = StreamCodec.of(
            { buf, p -> buf.writeUtf(p.schematicName) },
            { buf -> SelectSchematicPacket(buf.readUtf()) }
        )

        fun handle(payload: SelectSchematicPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                val stack = player.mainHandItem

                if (!AllItems.CONSTRUCTION_PLANNER.isIn(stack)) return@enqueueWork

                // Sanitize filename
                val name = payload.schematicName
                if (name.contains("..") || name.contains("/") || name.contains("\\")) return@enqueueWork

                // Set filename and owner on the server-side item.
                // Create's SchematicSyncPacket will handle deployed/anchor/rotation/mirror
                // when its own sync fires after the client-side deploy.
                stack.set(AllDataComponents.SCHEMATIC_FILE, name)
                stack.set(AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)

                // Write bounds if the file already exists on the server
                try {
                    com.simibubi.create.content.schematics.SchematicItem.writeSize(player.level(), stack)
                } catch (_: Exception) {
                    // File may not be uploaded yet — bounds will be set by client
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
