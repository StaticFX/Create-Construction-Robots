package de.devin.cbbees.network

import com.simibubi.create.foundation.utility.CreatePaths
import com.simibubi.create.foundation.utility.FilesHelper
import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import java.io.OutputStream
import java.nio.file.Files

class PlannerUploadPacket(
    val action: Int,
    val schematicName: String,
    val totalSize: Long,
    val data: ByteArray
) {
    companion object {
        const val BEGIN = 0
        const val WRITE = 1
        const val FINISH = 2
        const val CHUNK_SIZE = 32_768

        fun encode(pkt: PlannerUploadPacket, buf: FriendlyByteBuf) {
            buf.writeVarInt(pkt.action)
            buf.writeUtf(pkt.schematicName)
            buf.writeVarLong(pkt.totalSize)
            buf.writeByteArray(pkt.data)
        }

        fun decode(buf: FriendlyByteBuf) = PlannerUploadPacket(
            buf.readVarInt(),
            buf.readUtf(),
            buf.readVarLong(),
            buf.readByteArray()
        )

        private val activeStreams = mutableMapOf<String, UploadEntry>()

        private class UploadEntry(
            val stream: OutputStream,
            val totalBytes: Long,
            var bytesWritten: Long = 0
        )

        fun handleServer(pkt: PlannerUploadPacket, player: ServerPlayer) {
            val owner = player.gameProfile.name
            val name = pkt.schematicName

            if (name.contains("..") || name.contains("/") || name.contains("\\")) return
            if (!name.endsWith(".nbt")) return

            val key = "$owner/$name"

            when (pkt.action) {
                BEGIN -> handleBegin(key, owner, name, pkt.totalSize)
                WRITE -> handleWrite(key, pkt.data)
                FINISH -> handleFinish(key)
            }
        }

        private fun handleBegin(key: String, owner: String, name: String, totalSize: Long) {
            activeStreams.remove(key)?.stream?.close()

            val baseDir = CreatePaths.UPLOADED_SCHEMATICS_DIR
            val playerDir = baseDir.resolve(owner).normalize()
            val uploadPath = playerDir.resolve(name).normalize()

            if (!playerDir.startsWith(baseDir) || !uploadPath.startsWith(playerDir)) {
                CreateBuzzyBeez.LOGGER.warn("Path traversal in planner upload: $key")
                return
            }

            try {
                FilesHelper.createFolderIfMissing(playerDir)
                Files.deleteIfExists(uploadPath)
                val stream = Files.newOutputStream(uploadPath)
                activeStreams[key] = UploadEntry(stream, totalSize)
                CreateBuzzyBeez.LOGGER.info("Started planner upload: $key ($totalSize bytes)")
            } catch (e: Exception) {
                CreateBuzzyBeez.LOGGER.error("Failed to start planner upload: $key", e)
            }
        }

        private fun handleWrite(key: String, data: ByteArray) {
            val entry = activeStreams[key] ?: return
            try {
                entry.stream.write(data)
                entry.bytesWritten += data.size

                if (entry.bytesWritten > entry.totalBytes) {
                    CreateBuzzyBeez.LOGGER.warn("Received more data than expected for $key, cancelling")
                    entry.stream.close()
                    activeStreams.remove(key)
                }
            } catch (e: Exception) {
                CreateBuzzyBeez.LOGGER.error("Error writing planner upload chunk: $key", e)
                try { entry.stream.close() } catch (_: Exception) {}
                activeStreams.remove(key)
            }
        }

        private fun handleFinish(key: String) {
            val entry = activeStreams.remove(key) ?: return
            try {
                entry.stream.close()
                CreateBuzzyBeez.LOGGER.info("Completed planner upload: $key (${entry.bytesWritten} bytes)")
            } catch (e: Exception) {
                CreateBuzzyBeez.LOGGER.error("Error finishing planner upload: $key", e)
            }
        }

        fun shutdown() {
            activeStreams.values.forEach { runCatching { it.stream.close() } }
            activeStreams.clear()
        }
    }
}
