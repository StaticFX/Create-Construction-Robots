package de.devin.cbbees.content.domain.network

import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import java.util.*
import kotlin.math.abs

class BeeNetwork(val id: UUID = UUID.randomUUID()) {
    val name: String = id.toString().substring(0, 4).uppercase()
    val color: Int = (id.hashCode() and 0x7F7F7F) or 0x808080

    var level: net.minecraft.world.level.Level? = null

    val components = mutableSetOf<INetworkComponent>()

    val hives: List<BeeHive> get() = components.filterIsInstance<BeeHive>()
    val ports: List<LogisticsPort> get() = components.filterIsInstance<LogisticsPort>()

    /**
     * The aggregate range of all hives in this network.
     */
    fun isInRange(pos: BlockPos): Boolean = components.any { it.isAnchor() && it.isInWorkRange(pos) }

    /**
     * Checks if any hive in the network covers this position for logistics.
     */
    fun isInLogisticsRange(pos: BlockPos): Boolean = components.any { it.isAnchor() && it.isInWorkRange(pos) }

    fun findProvider(stack: ItemStack): LogisticsPort? {
        return ports.filter { it.isValidForPickup() && it.hasItemStack(stack) }
            .sortedByDescending { it.priority() }
            .firstOrNull()
    }

    fun findDropOff(stack: ItemStack): LogisticsPort? {
        return ports.filter { it.isValidForDropOff() }
            .sortedByDescending { it.priority() }
            .firstOrNull()
    }

    fun dispatchBatch(batch: TaskBatch) {
        val hive = hives.firstOrNull { it.isInWorkRange(batch.targetPosition) } ?: return
        hive.acceptBatch(batch)
    }

    fun canConnect(component: INetworkComponent): Boolean {
        if (components.isEmpty()) return true
        val firstComp = components.first()
        if (component.world != firstComp.world) return false

        // If component is an anchor, check if it connects to any existing anchor
        if (component.isAnchor()) {
            // Special case: if there are no anchors in the network yet, it can connect if it's the first anchor
            if (hives.isEmpty()) return true

            return components.any { other ->
                other.isAnchor() && areComponentsConnected(component, other)
            }
        }

        // If component is a port, check if it's within range of any anchor
        // If there are no anchors, ports can only connect if we explicitly allow it during load (handled in manager)
        return isInLogisticsRange(component.pos)
    }

    private fun areComponentsConnected(c1: INetworkComponent, c2: INetworkComponent): Boolean {
        val dx = abs(c1.pos.x - c2.pos.x)
        val dz = abs(c1.pos.z - c2.pos.z)
        val combinedRange = c1.getNetworkingRange() + c2.getNetworkingRange()
        return dx <= combinedRange && dz <= combinedRange
    }

    fun addComponent(component: INetworkComponent) {
        if (components.add(component)) {
            if (level == null) level = component.world
            component.networkId = id
            component.sync()
        }
    }

    fun removeComponent(component: INetworkComponent) {
        components.remove(component)
    }

    fun merge(other: BeeNetwork) {
        other.components.forEach { addComponent(it) }
        other.components.clear()
    }

    /**
     * Performs a graph traversal to detect if the network has split.
     */
    fun split(): List<BeeNetwork> {
        val anchors = components.filter { it.isAnchor() }.toMutableList()
        if (anchors.isEmpty()) return emptyList()

        val newNetworks = mutableListOf<BeeNetwork>()

        while (anchors.isNotEmpty()) {
            val start = anchors.removeAt(0)
            val group = mutableSetOf(start)
            val toProcess = mutableListOf(start)

            while (toProcess.isNotEmpty()) {
                val current = toProcess.removeAt(0)
                val neighbors = anchors.filter { areComponentsConnected(current, it) }
                for (neighbor in neighbors) {
                    group.add(neighbor)
                    anchors.remove(neighbor)
                    toProcess.add(neighbor)
                }
            }

            // If this group is the whole network (and it's the first group), no split happened
            if (newNetworks.isEmpty() && group.size == components.count { it.isAnchor() }) {
                // We still need to re-assign ports to this group
                val ports = components.filter { !it.isAnchor() }
                for (port in ports) {
                    if (!group.any { it.isInWorkRange(port.pos) }) {
                        components.remove(port)
                        port.networkId = UUID.randomUUID()
                        port.sync()
                    }
                }
                // For simplicity, we'll let the Manager handle full recalculation if any split is detected
                return listOf(this)
            }

            val newNetwork = if (newNetworks.isEmpty()) this else BeeNetwork()
            newNetwork.components.clear()
            group.forEach { newNetwork.addComponent(it) }

            // Re-assign ports that are in range of this new group
            val remainingPorts = components.filter { !it.isAnchor() && !newNetwork.components.contains(it) }
            for (port in remainingPorts) {
                if (group.any { it.isInWorkRange(port.pos) }) {
                    newNetwork.addComponent(port)
                }
            }

            newNetworks.add(newNetwork)
        }

        return newNetworks
    }
}
