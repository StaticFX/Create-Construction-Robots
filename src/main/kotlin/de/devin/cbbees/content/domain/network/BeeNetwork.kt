package de.devin.cbbees.content.domain.network

import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.logistics.ReservablePort
import de.devin.cbbees.content.domain.logistics.TransportPort
import de.devin.cbbees.content.domain.network.topology.DefaultAnchorTopology
import de.devin.cbbees.content.domain.network.topology.NetworkTopology
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import java.util.*

class BeeNetwork(
    val id: UUID = UUID.randomUUID(),
    private val topology: NetworkTopology = DefaultAnchorTopology
) {
    val name: String = id.toString().substring(0, 4).uppercase()
    val color: Int = (id.hashCode() and 0x7F7F7F) or 0x808080

    var level: net.minecraft.world.level.Level? = null

    private val _components = mutableSetOf<INetworkComponent>()
    val components: MutableSet<INetworkComponent> get() = _components

    private var _hives: List<BeeHive>? = null
    private var _ports: List<LogisticsPort>? = null
    private var _transportPorts: List<TransportPort>? = null
    private var _reservablePorts: List<ReservablePort>? = null

    private fun invalidateComponentCaches() {
        _hives = null
        _ports = null
        _transportPorts = null
        _reservablePorts = null
    }

    val hives: List<BeeHive> get() = _hives ?: components.filterIsInstance<BeeHive>().also { _hives = it }
    val ports: List<LogisticsPort> get() = _ports ?: components.filterIsInstance<LogisticsPort>().also { _ports = it }
    val transportPorts: List<TransportPort> get() = _transportPorts ?: components.filterIsInstance<TransportPort>().also { _transportPorts = it }
    val reservablePorts: List<ReservablePort> get() = _reservablePorts ?: components.filterIsInstance<ReservablePort>().also { _reservablePorts = it }

    /**
     * The aggregate operational range of all anchors in this network.
     */
    fun isInRange(pos: BlockPos): Boolean =
        components.any { topology.isAnchor(it) && topology.isOperationalRange(it, pos) }

    /**
     * Checks if any anchor is within networking range for logistics attachment.
     * Uses topology to allow custom reconnection semantics (e.g., min radius when unpowered).
     */
    fun isInLogisticsRange(pos: BlockPos): Boolean =
        components.any { c -> topology.isAnchor(c) && topology.isLogisticsRange(c, pos) }

    fun findProvider(stack: ItemStack): LogisticsPort? {
        return ports.filter { it.isValidForPickup() && it.testFilter(stack) && it.hasItemStack(stack) }
            .sortedByDescending { it.priority() }
            .firstOrNull()
    }

    fun findDropOff(stack: ItemStack): LogisticsPort? {
        return ports.filter { it.isValidForDropOff() && (stack.isEmpty || it.testFilter(stack)) }
            .sortedByDescending { it.priority() }
            .firstOrNull()
    }

    fun findAvailableProvider(stack: ItemStack, excludeBeeId: UUID? = null): LogisticsPort? {
        return ports.filter { it.isValidForPickup() && it.testFilter(stack) && it.hasAvailableItemStack(stack, excludeBeeId) }
            .sortedByDescending { it.priority() }
            .firstOrNull()
    }

    fun releaseReservations(beeId: UUID) {
        reservablePorts.forEach { it.releaseReservation(beeId) }
    }

    fun cleanupReservations(currentTick: Long) {
        reservablePorts.forEach { it.cleanupReservations(currentTick) }
    }

    fun clearReservations() {
        reservablePorts.forEach { it.clearReservations() }
    }

    fun dispatchBatch(batch: TaskBatch) {
        val candidates = hives.filter {
            topology.isOperationalRange(it, batch.targetPosition) &&
                    it.getAvailableBeeCount() > 0
        }.sortedBy { it.pos.distSqr(batch.targetPosition) }

        for (hive in candidates) {
            if (hive.acceptBatch(batch)) return
        }
    }

    fun canConnect(component: INetworkComponent): Boolean {
        if (components.isEmpty()) return true
        val firstComp = components.first()
        if (component.world != firstComp.world) return false

        val isAnchor = topology.isAnchor(component)
        if (isAnchor) {
            // No anchors yet? Accept the first one regardless
            if (components.none { topology.isAnchor(it) }) return true
            // Connect to any existing anchor via topology rule
            return components.any { other -> topology.isAnchor(other) && topology.canConnectAnchors(component, other) }
        }

        // Non-anchors (e.g. logistics ports) must be within logistics range of any anchor
        return isInLogisticsRange(component.pos)
    }

    private fun anchorsConnected(c1: INetworkComponent, c2: INetworkComponent): Boolean =
        topology.canConnectAnchors(c1, c2)

    fun addComponent(component: INetworkComponent) {
        if (components.add(component)) {
            invalidateComponentCaches()
            if (level == null) level = component.world
            component.networkId = id
            component.sync()
        }
    }

    fun removeComponent(component: INetworkComponent) {
        if (components.remove(component)) {
            invalidateComponentCaches()
        }
    }

    fun merge(other: BeeNetwork) {
        other.components.forEach { addComponent(it) }
        other.components.clear()
        other.invalidateComponentCaches()
    }

    /**
     * Performs a graph traversal to detect if the network has split.
     */
    fun split(): List<BeeNetwork> {
        val anchors = components.filter { topology.isAnchor(it) }.toMutableList()
        if (anchors.isEmpty()) return emptyList()

        val newNetworks = mutableListOf<BeeNetwork>()

        while (anchors.isNotEmpty()) {
            val start = anchors.removeAt(0)
            val group = mutableSetOf(start)
            val toProcess = mutableListOf(start)

            while (toProcess.isNotEmpty()) {
                val current = toProcess.removeAt(0)
                val neighbors = anchors.filter { anchorsConnected(current, it) }
                for (neighbor in neighbors) {
                    group.add(neighbor)
                    anchors.remove(neighbor)
                    toProcess.add(neighbor)
                }
            }

            // If this group is the whole network (and it's the first group), no split happened
            if (newNetworks.isEmpty() && group.size == components.count { topology.isAnchor(it) }) {
                // We still need to re-assign non-anchors to this group
                val nonAnchors = components.filter { !topology.isAnchor(it) }
                for (c in nonAnchors) {
                    if (!group.any { topology.isOperationalRange(it, c.pos) }) {
                        components.remove(c)
                        c.networkId = UUID.randomUUID()
                        c.sync()
                    }
                }
                // For simplicity, we'll let the Manager handle full recalculation if any split is detected
                return listOf(this)
            }

            val newNetwork = if (newNetworks.isEmpty()) this else BeeNetwork()
            newNetwork.components.clear()
            group.forEach { newNetwork.addComponent(it) }

            // Re-assign non-anchors that are in range of this new group
            val remaining = components.filter { !topology.isAnchor(it) && !newNetwork.components.contains(it) }
            for (c in remaining) {
                if (group.any { topology.isOperationalRange(it, c.pos) }) {
                    newNetwork.addComponent(c)
                }
            }

            newNetworks.add(newNetwork)
        }

        return newNetworks
    }
}
