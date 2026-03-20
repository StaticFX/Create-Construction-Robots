package de.devin.cbbees.network

import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.schematics.SchematicItem
import de.devin.cbbees.CreateBuzzyBeez
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
 * Client → Server packet sent when the player confirms a schematic selection
 * in the Construction Planner. Sets ALL data components on the server item
 * so the server-side state matches the client and inventory syncs don't
 * overwrite the client-side deployed state.
 */
class SelectSchematicPacket(
    val schematicName: String,
    val anchor: BlockPos,
    val rotation: Rotation,
    val mirror: Mirror
) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<SelectSchematicPacket>(
            CreateBuzzyBeez.asResource("select_schematic")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SelectSchematicPacket> = StreamCodec.of(
            { buf, p ->
                buf.writeUtf(p.schematicName)
                buf.writeBlockPos(p.anchor)
                buf.writeEnum(p.rotation)
                buf.writeEnum(p.mirror)
            },
            { buf ->
                SelectSchematicPacket(
                    buf.readUtf(),
                    buf.readBlockPos(),
                    buf.readEnum(Rotation::class.java),
                    buf.readEnum(Mirror::class.java)
                )
            }
        )

        fun handle(payload: SelectSchematicPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                val stack = player.mainHandItem

                if (!AllItems.CONSTRUCTION_PLANNER.isIn(stack)) return@enqueueWork

                // Sanitize filename
                val name = payload.schematicName
                if (name.contains("..") || name.contains("/") || name.contains("\\")) return@enqueueWork

                // Set ALL data components so inventory sync doesn't clobber client state
                stack.set(AllDataComponents.SCHEMATIC_FILE, name)
                stack.set(AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
                stack.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)
                stack.set(AllDataComponents.SCHEMATIC_ANCHOR, payload.anchor)
                stack.set(AllDataComponents.SCHEMATIC_ROTATION, payload.rotation)
                stack.set(AllDataComponents.SCHEMATIC_MIRROR, payload.mirror)

                // Write bounds if the file already exists on the server
                try {
                    SchematicItem.writeSize(player.level(), stack)
                } catch (_: Exception) {
                    // File may not be uploaded yet
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
