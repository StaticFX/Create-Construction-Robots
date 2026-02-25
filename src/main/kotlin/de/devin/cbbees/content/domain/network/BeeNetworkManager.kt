package de.devin.cbbees.content.domain.network

import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.*

object BeeNetworkManager {
    private val networks = mutableListOf<BeeNetwork>()
    private val allPorts = mutableSetOf<LogisticsPort>()

    val workers: Set<BeeHive> get() = networks.flatMap { it.hives }.toSet()

    fun getNetworks(): List<BeeNetwork> = networks

    fun registerWorker(worker: BeeHive) {
        // If already registered in some network, remove it first
        networks.forEach { it.hives.remove(worker) }
        networks.removeIf { it.hives.isEmpty() }

        if (worker.sourceWorld.isClientSide) {
            val id = (worker as? MechanicalBeehiveBlockEntity)?.networkId ?: return
            val network = getNetwork(id) ?: BeeNetwork(id).also { networks.add(it) }
            network.addHive(worker)
            updatePortsForNetworks()
            return
        }

        // 1. Find all networks that overlap with this hive's range
        val overlappingNetworks = networks.filter { network ->
            network.hives.any { existingHive ->
                areHivesConnected(worker, existingHive)
            }
        }

        val network: BeeNetwork
        if (overlappingNetworks.isEmpty()) {
            val preferredId = (worker as? MechanicalBeehiveBlockEntity)?.networkId ?: UUID.randomUUID()
            network = BeeNetwork(preferredId)
            network.addHive(worker)
            networks.add(network)
        } else {
            network = overlappingNetworks.first()
            network.addHive(worker)

            // Merge other overlapping networks into the primary one
            if (overlappingNetworks.size > 1) {
                for (i in 1 until overlappingNetworks.size) {
                    val other = overlappingNetworks[i]
                    network.hives.addAll(other.hives)
                    network.ports.addAll(other.ports)
                    networks.remove(other)
                }
            }
        }

        // Update networkId in block entities
        network.hives.forEach { hive ->
            if (hive is MechanicalBeehiveBlockEntity) {
                if (hive.networkId != network.id) {
                    hive.networkId = network.id
                    hive.setChanged()
                    hive.sendData()
                }
            }
        }

        updatePortsForNetworks()
    }

    fun unregisterWorker(worker: BeeHive) {
        unregisterWorker(worker.sourceId)
    }

    fun unregisterWorker(sourceId: UUID) {
        for (network in networks.toList()) {
            network.hives.removeIf { it.sourceId == sourceId }
            if (network.hives.isEmpty()) {
                networks.remove(network)
            }
        }
        recalculateNetworks()
    }

    private fun recalculateNetworks() {
        val hives = networks.flatMap { it.hives }.toList()

        networks.clear()

        val unassignedHives = hives.toMutableList()

        while (unassignedHives.isNotEmpty()) {
            val startHive = unassignedHives.removeAt(0)

            // Collect all hives that belong to this new network group
            val groupedHives = mutableListOf(startHive)
            val toProcess = mutableListOf(startHive)
            while (toProcess.isNotEmpty()) {
                val current = toProcess.removeAt(0)
                val neighbors = unassignedHives.filter { areHivesConnected(current, it) }
                for (neighbor in neighbors) {
                    groupedHives.add(neighbor)
                    unassignedHives.remove(neighbor)
                    toProcess.add(neighbor)
                }
            }

            // Try to preserve an existing network ID if possible
            val preferredId = groupedHives.mapNotNull { (it as? MechanicalBeehiveBlockEntity)?.networkId }
                .firstOrNull() ?: UUID.randomUUID()

            val network = BeeNetwork(preferredId)
            groupedHives.forEach { network.addHive(it) }
            networks.add(network)

            // Update networkId in block entities for this new network
            network.hives.forEach { hive ->
                if (hive is MechanicalBeehiveBlockEntity) {
                    if (hive.networkId != network.id) {
                        hive.networkId = network.id
                        hive.setChanged()
                        hive.sendData()
                    }
                }
            }
        }

        updatePortsForNetworks()
    }

    fun registerPort(port: LogisticsPort) {
        allPorts.add(port)
        updatePortsForNetworks()
    }

    fun unregisterPort(port: LogisticsPort) {
        allPorts.remove(port)
        for (network in networks) {
            network.removePort(port)
        }
    }

    private fun updatePortsForNetworks() {
        for (network in networks) {
            network.ports.clear()
            for (port in allPorts) {
                if (port.sourceWorld == network.hives.firstOrNull()?.sourceWorld &&
                    network.isInLogisticsRange(port.sourcePosition)
                ) {
                    network.addPort(port)
                }
            }
        }
    }

    private fun areHivesConnected(h1: BeeHive, h2: BeeHive): Boolean {
        if (h1.sourceWorld != h2.sourceWorld) return false
        val dx = Math.abs(h1.sourcePosition.x - h2.sourcePosition.x)
        val dz = Math.abs(h1.sourcePosition.z - h2.sourcePosition.z)
        // Use a minimum range of 6.0 for networking, even if unpowered, to allow initial connection
        val range1 = Math.max(6.0, h1.getWorkRange()).toInt()
        val range2 = Math.max(6.0, h2.getWorkRange()).toInt()
        val combinedRange = range1 + range2
        return dx <= combinedRange && dz <= combinedRange
    }

    fun getNetworkAt(level: Level, pos: BlockPos): BeeNetwork? {
        return networks.find { it.hives.firstOrNull()?.sourceWorld == level && it.isInRange(pos) }
    }

    fun getNetworkFor(hive: BeeHive): BeeNetwork? {
        return networks.find { it.hives.contains(hive) }
    }

    fun getNetwork(id: UUID): BeeNetwork? {
        return networks.find { it.id == id }
    }

    fun findProviderFor(level: Level, stack: ItemStack, startPos: BlockPos): LogisticsPort? {
        val network = getNetworkAt(level, startPos)
        return network?.findProvider(stack)
    }
}
