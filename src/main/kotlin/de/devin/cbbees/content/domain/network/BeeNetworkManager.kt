package de.devin.cbbees.content.domain.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.network.topology.DefaultAnchorTopology
import de.devin.cbbees.content.domain.network.topology.NetworkTopology
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.*
import de.devin.cbbees.util.ServerSide
import de.devin.cbbees.util.ClientSide

@ServerSide
object ServerBeeNetworkManager {
    private val networks = mutableListOf<BeeNetwork>()
    private var isScanning = false

    /** Topology used for creating new networks on the server. */
    private var topology: NetworkTopology = DefaultAnchorTopology

    fun setTopology(topology: NetworkTopology) {
        this.topology = topology
    }

    fun getNetworks(): List<BeeNetwork> = networks

    fun clear() {
        val size = networks.size
        networks.clear()
        CreateBuzzyBeez.LOGGER.info("Cleared $size networks")
    }

    fun registerComponent(component: INetworkComponent) {
        if (component.world.isClientSide) return

        // Prevent recursive registration if we're already in a network
        if (getNetworkFor(component) != null) return

        val nearbyNetworks = networks.filter { it.canConnect(component) }.toMutableList()

        // Also merge with any network that shares the same networkId, even if it has no anchors yet.
        // This is crucial for correctly reconstructing networks during world load from NBT.
        val idNetwork = networks.find { it.id == component.networkId }
        if (idNetwork != null && !nearbyNetworks.contains(idNetwork)) {
            // Only merge by ID if it's the same level
            if (idNetwork.level == null || idNetwork.level == component.world) {
                nearbyNetworks.add(idNetwork)
            }
        }

        val targetNetwork: BeeNetwork

        if (nearbyNetworks.isEmpty()) {
            targetNetwork = BeeNetwork(component.networkId, topology)
            networks.add(targetNetwork)
            CreateBuzzyBeez.LOGGER.info("Created new network with ${component.javaClass.simpleName} id: ${component.networkId}")
        } else {
            targetNetwork = nearbyNetworks.first()
            if (nearbyNetworks.size > 1) {
                nearbyNetworks.drop(1).forEach { other ->
                    targetNetwork.merge(other)
                    networks.remove(other)
                }
                CreateBuzzyBeez.LOGGER.info("Merged ${nearbyNetworks.size} networks into main network id: ${targetNetwork.id}")
            } else {
                CreateBuzzyBeez.LOGGER.info("Added ${component.javaClass.simpleName} to existing network id: ${targetNetwork.id}")
            }
        }

        targetNetwork.addComponent(component)

        // If the registered component is an anchor, it might pick up nearby orphaned components
        if (component.isAnchor() && !isScanning) {
            isScanning = true
            try {
                scanAndJoinNearbyComponents(
                    targetNetwork,
                    component.world,
                    component.pos,
                    component.getNetworkingRange()
                )
            } finally {
                isScanning = false
            }
        }
    }

    private fun scanAndJoinNearbyComponents(network: BeeNetwork, level: Level, pos: BlockPos, range: Double) {
        val r = range.toInt()
        val minX = (pos.x - r) shr 4
        val maxX = (pos.x + r) shr 4
        val minZ = (pos.z - r) shr 4
        val maxZ = (pos.z + r) shr 4

        for (cx in minX..maxX) {
            for (cz in minZ..maxZ) {
                val chunk = level.getChunkSource().getChunk(cx, cz, false) ?: continue
                for (be in chunk.blockEntities.values) {
                    if (be is INetworkComponent && be !in network.components) {
                        if (network.canConnect(be)) {
                            val other = getNetworkFor(be)
                            if (other != null && other != network) {
                                network.merge(other)
                                networks.remove(other)
                            } else {
                                network.addComponent(be)
                            }
                        }
                    }
                }
            }
        }
    }

    fun unregisterComponent(component: INetworkComponent) {
        if (component.world.isClientSide) return

        val network = getNetworkFor(component) ?: return
        network.removeComponent(component)

        // If network is now empty, remove it
        if (network.components.isEmpty()) {
            networks.remove(network)
            return
        }

        // Handle potential split
        val splitResults = network.split()
        if (splitResults.isEmpty()) {
            // No anchors left, disband the network
            val remaining = network.components.toList()
            remaining.forEach {
                it.networkId = UUID.randomUUID()
                it.sync()
            }
            networks.remove(network)
        } else if (splitResults.size > 1) {
            // Original network might have been modified or replaced by new ones
            // For now, let's just make sure all splitResults are in the list
            splitResults.forEach { result ->
                if (!networks.contains(result)) {
                    networks.add(result)
                }
            }
        }
    }

    // Simplified delegating methods
    fun registerWorker(worker: BeeHive) = registerComponent(worker)

    fun unregisterWorker(worker: BeeHive) = unregisterComponent(worker)

    fun unregisterWorker(id: UUID) {
        // Create a copy to avoid ConcurrentModificationException if networks are removed
        networks.toList().forEach { net ->
            net.components.find { it.id == id }?.let { unregisterComponent(it) }
        }
    }

    fun registerPort(port: LogisticsPort) = registerComponent(port)

    fun unregisterPort(port: LogisticsPort) = unregisterComponent(port)

    fun getNetworkAt(level: Level, pos: BlockPos): BeeNetwork? {
        return networks.find { it.level == level && it.isInRange(pos) }
    }

    fun getNetworkFor(component: INetworkComponent): BeeNetwork? {
        return networks.find { it.components.contains(component) }
    }

    fun getNetwork(id: UUID): BeeNetwork? {
        return networks.find { it.id == id }
    }

    fun findHive(id: UUID): BeeHive? {
        return networks.flatMap { it.hives }.find { it.id == id }
    }

    fun getNetwork(id: UUID, level: Level): BeeNetwork? {
        return networks.find { it.id == id && (it.level == null || it.level == level) }
    }

    fun findProviderFor(level: Level, stack: ItemStack, startPos: BlockPos): LogisticsPort? {
        val network = getNetworkAt(level, startPos)
        return network?.findProvider(stack)
    }
}

@ClientSide
object ClientBeeNetworkManager {
    private val networks = mutableListOf<BeeNetwork>()

    fun getNetworks(): List<BeeNetwork> = networks

    fun clear() {
        val size = networks.size
        networks.clear()
        CreateBuzzyBeez.LOGGER.info("Cleared $size client networks")
    }

    fun getNetwork(id: UUID): BeeNetwork {
        return networks.find { it.id == id } ?: run {
            // Lazy-create proxy network on client if it's missing
            // This allows the client to group hives/ports by ID even without full topology
            val net = BeeNetwork(id)
            networks.add(net)
            net
        }
    }

    fun removeComponent(component: INetworkComponent) {
        component.network().removeComponent(component)
        if (component.network().components.isEmpty()) {
            networks.remove(component.network())
        }
    }
}
