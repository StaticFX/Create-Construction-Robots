package de.devin.cbbees.network

import de.devin.cbbees.content.domain.network.ClientBeeNetworkManager
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

class NetworkSyncPacket(
    val networks: Map<UUID, List<BlockPos>>
) {
    companion object {
        fun encode(pkt: NetworkSyncPacket, buf: FriendlyByteBuf) {
            buf.writeVarInt(pkt.networks.size)
            for ((id, positions) in pkt.networks) {
                buf.writeUUID(id)
                buf.writeVarInt(positions.size)
                for (pos in positions) {
                    buf.writeBlockPos(pos)
                }
            }
        }

        fun decode(buf: FriendlyByteBuf): NetworkSyncPacket {
            val networkCount = buf.readVarInt()
            val networks = mutableMapOf<UUID, List<BlockPos>>()
            repeat(networkCount) {
                val id = buf.readUUID()
                val componentCount = buf.readVarInt()
                val positions = mutableListOf<BlockPos>()
                repeat(componentCount) {
                    positions.add(buf.readBlockPos())
                }
                networks[id] = positions
            }
            return NetworkSyncPacket(networks)
        }

        fun handleClient(pkt: NetworkSyncPacket) {
            ClientBeeNetworkManager.applyServerSnapshot(pkt.networks)
        }

        fun sendTo(player: ServerPlayer) {
            val level = player.serverLevel()
            val snapshot = mutableMapOf<UUID, List<BlockPos>>()

            for (network in ServerBeeNetworkManager.getNetworks()) {
                if (network.level != null && network.level != level) continue
                val positions = network.components.map { it.pos }
                if (positions.isNotEmpty()) {
                    snapshot[network.id] = positions
                }
            }

            NetworkHelper.sendToPlayer(player, NetworkSyncPacket(snapshot))
        }
    }
}
