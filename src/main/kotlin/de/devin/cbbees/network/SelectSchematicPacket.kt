package de.devin.cbbees.network

import com.simibubi.create.content.schematics.SchematicItem
import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.compat.SchematicDataHelper
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.items.AllItems
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SelectSchematicPacket(
    val schematicName: String,
    val anchor: BlockPos,
    val rotation: Rotation,
    val mirror: Mirror
) {
    companion object {
        fun encode(pkt: SelectSchematicPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(pkt.schematicName)
            buf.writeBlockPos(pkt.anchor)
            buf.writeEnum(pkt.rotation)
            buf.writeEnum(pkt.mirror)
        }

        fun decode(buf: FriendlyByteBuf) = SelectSchematicPacket(
            buf.readUtf(),
            buf.readBlockPos(),
            buf.readEnum(Rotation::class.java),
            buf.readEnum(Mirror::class.java)
        )

        fun handleServer(pkt: SelectSchematicPacket, player: ServerPlayer) {
            val stack = player.mainHandItem
            if (!AllItems.CONSTRUCTION_PLANNER.isIn(stack)) return

            val name = pkt.schematicName
            if (name.contains("..") || name.contains("/") || name.contains("\\")) return

            val owner = player.gameProfile.name
            ensureSchematicUploaded(owner, name)

            SchematicDataHelper.setPlacement(stack, name, owner, pkt.anchor, pkt.rotation, pkt.mirror)

            try {
                SchematicItem.writeSize(player.level(), stack)
            } catch (_: Exception) {
                CreateBuzzyBeez.LOGGER.warn("Failed to write schematic bounds for $name")
            }
        }
    }
}

/**
 * Ensures a schematic file exists in the server's uploaded directory.
 */
fun ensureSchematicUploaded(owner: String, schematicName: String) {
    val uploadedDir = CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(owner)
    val uploadedFile = uploadedDir.resolve(schematicName).normalize()

    if (!uploadedFile.startsWith(CreatePaths.UPLOADED_SCHEMATICS_DIR)) return
    if (Files.exists(uploadedFile)) return

    val sourceFile = CreatePaths.SCHEMATICS_DIR.resolve(schematicName).normalize()
    if (!sourceFile.startsWith(CreatePaths.SCHEMATICS_DIR)) return

    if (!Files.exists(sourceFile)) {
        CreateBuzzyBeez.LOGGER.warn("Schematic file not found: $sourceFile")
        return
    }

    try {
        Files.createDirectories(uploadedDir)
        Files.copy(sourceFile, uploadedFile, StandardCopyOption.REPLACE_EXISTING)
        CreateBuzzyBeez.LOGGER.info("Copied schematic $schematicName to uploaded directory for $owner")
    } catch (e: Exception) {
        CreateBuzzyBeez.LOGGER.error("Failed to copy schematic $schematicName: ${e.message}")
    }
}
