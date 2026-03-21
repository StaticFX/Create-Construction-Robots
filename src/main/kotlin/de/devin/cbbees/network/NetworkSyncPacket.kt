package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.network.ClientBeeNetworkManager
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Periodically sent from server to client to synchronize the authoritative
 * network membership. Each entry maps a network UUID to all component positions
 * in that network, allowing the client to rebuild its view from scratch.
 */
class NetworkSyncPacket(
    val networks: Map<UUID, List<BlockPos>>
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<NetworkSyncPacket>(CreateBuzzyBeez.asResource("network_sync"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NetworkSyncPacket> =
            object : StreamCodec<RegistryFriendlyByteBuf, NetworkSyncPacket> {
                override fun decode(buf: RegistryFriendlyByteBuf): NetworkSyncPacket {
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

                override fun encode(buf: RegistryFriendlyByteBuf, packet: NetworkSyncPacket) {
                    buf.writeVarInt(packet.networks.size)
                    for ((id, positions) in packet.networks) {
                        buf.writeUUID(id)
                        buf.writeVarInt(positions.size)
                        for (pos in positions) {
                            buf.writeBlockPos(pos)
                        }
                    }
                }
            }

        fun handle(packet: NetworkSyncPacket, context: IPayloadContext) {
            context.enqueueWork {
                ClientBeeNetworkManager.applyServerSnapshot(packet.networks)
            }
        }

        /**
         * Builds and sends a network snapshot to the given player.
         * Only includes networks in the player's current dimension.
         */
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

            PacketDistributor.sendToPlayer(player, NetworkSyncPacket(snapshot))
        }
    }

    override fun type(): CustomPacketPayload.Type<NetworkSyncPacket> = TYPE
}
