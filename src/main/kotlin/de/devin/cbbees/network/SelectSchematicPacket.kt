package de.devin.cbbees.network

import com.simibubi.create.AllDataComponents
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.items.AllItems
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
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

                // Set schematic data components on the planner
                stack.set(AllDataComponents.SCHEMATIC_FILE, name)
                stack.set(AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
                stack.set(AllDataComponents.SCHEMATIC_DEPLOYED, false)
                stack.set(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO)
                stack.set(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE)
                stack.set(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE)

                // Try to write bounds if the file already exists on the server
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
