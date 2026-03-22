package de.devin.cbbees.content.domain.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
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

        // Non-anchor components (logistics ports) cannot create their own network.
        // They must join an existing network that has an anchor (mechanical beehive).
        if (nearbyNetworks.isEmpty() && !component.isAnchor()) {
            CreateBuzzyBeez.LOGGER.debug("No network with anchor available for ${component.javaClass.simpleName} at ${component.pos}")
            return
        }

        val targetNetwork: BeeNetwork

        if (nearbyNetworks.isEmpty()) {
            targetNetwork = BeeNetwork(component.networkId, topology)
            networks.add(targetNetwork)
            CreateBuzzyBeez.LOGGER.debug("Created new network with ${component.javaClass.simpleName} id: ${component.networkId}")
        } else {
            targetNetwork = nearbyNetworks.first()
            if (nearbyNetworks.size > 1) {
                nearbyNetworks.drop(1).forEach { other ->
                    targetNetwork.merge(other)
                    networks.remove(other)
                }
                CreateBuzzyBeez.LOGGER.debug("Merged ${nearbyNetworks.size} networks into main network id: ${targetNetwork.id}")
            } else {
                CreateBuzzyBeez.LOGGER.debug("Added ${component.javaClass.simpleName} to existing network id: ${targetNetwork.id}")
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

    fun findPortableHive(playerId: UUID): PortableBeeHive? {
        return networks.flatMap { it.hives }.filterIsInstance<PortableBeeHive>().find { it.player.uuid == playerId }
    }

    fun reconnectPortableHive(hive: PortableBeeHive) {
        val playerPos = hive.player.blockPosition()
        val playerLevel = hive.player.level()

        // Find a block-based network covering the player's position
        val blockNetwork = networks.find { net ->
            net.level == playerLevel &&
                net.isInRange(playerPos) &&
                net.components.any { it.isAnchor() && it !is PortableBeeHive }
        }

        val currentNetwork = getNetworkFor(hive)

        if (blockNetwork != null) {
            // Already in the target network — no-op
            if (currentNetwork == blockNetwork) return

            // Move from old network to the block network
            if (currentNetwork != null) {
                currentNetwork.removeComponent(hive)
                if (currentNetwork.components.isEmpty()) {
                    networks.remove(currentNetwork)
                }
            }
            blockNetwork.addComponent(hive)
            CreateBuzzyBeez.LOGGER.debug("Reconnected portable hive for ${hive.player.name.string} to block network ${blockNetwork.id}")
        } else {
            // No block network nearby
            if (currentNetwork != null && currentNetwork.components.any { it.isAnchor() && it !is PortableBeeHive }) {
                // Currently in a block network — detach into isolated network
                currentNetwork.removeComponent(hive)
                if (currentNetwork.components.isEmpty()) {
                    networks.remove(currentNetwork)
                }
                hive.networkId = UUID.randomUUID()
                registerComponent(hive)
                CreateBuzzyBeez.LOGGER.debug("Detached portable hive for ${hive.player.name.string} into isolated network")
            }
            // Otherwise already isolated — no-op
        }
    }
}

@ClientSide
object ClientBeeNetworkManager {
    private val networks = mutableListOf<BeeNetwork>()

    /**
     * Authoritative map of network UUID → component positions from the server.
     * Used to verify client-side grouping and detect desync.
     */
    private val serverSnapshot = mutableMapOf<UUID, List<BlockPos>>()

    fun getNetworks(): List<BeeNetwork> = networks

    fun clear() {
        val size = networks.size
        networks.clear()
        serverSnapshot.clear()
        CreateBuzzyBeez.LOGGER.info("Cleared $size client networks")
    }

    fun getNetwork(id: UUID): BeeNetwork {
        return networks.find { it.id == id } ?: run {
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

    /**
     * Applies an authoritative snapshot from the server. Reassigns any client-side
     * components that are in the wrong network, and removes stale networks.
     */
    fun applyServerSnapshot(snapshot: Map<UUID, List<BlockPos>>) {
        serverSnapshot.clear()
        serverSnapshot.putAll(snapshot)

        // Build a lookup: position → authoritative network UUID
        val posToNetwork = mutableMapOf<BlockPos, UUID>()
        for ((netId, positions) in snapshot) {
            for (pos in positions) {
                posToNetwork[pos] = netId
            }
        }

        // Collect all current client components
        val allComponents = networks.flatMap { it.components }.toList()

        for (component in allComponents) {
            val correctNetworkId = posToNetwork[component.pos]
            if (correctNetworkId == null) {
                // Component no longer exists on the server — remove it
                val net = networks.find { it.components.contains(component) }
                net?.removeComponent(component)
            } else if (correctNetworkId != component.networkId) {
                // Component is in the wrong network — move it
                val oldNet = networks.find { it.components.contains(component) }
                oldNet?.removeComponent(component)
                component.networkId = correctNetworkId
                getNetwork(correctNetworkId).components.add(component)
            }
        }

        // Remove empty networks
        networks.removeAll { it.components.isEmpty() }
    }

    /**
     * Returns the server-authoritative network ID for a position, if known.
     */
    fun getServerNetworkId(pos: BlockPos): UUID? {
        for ((netId, positions) in serverSnapshot) {
            if (pos in positions) return netId
        }
        return null
    }
}
