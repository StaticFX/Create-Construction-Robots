package de.devin.ccr.content.domain.network

import de.devin.ccr.content.domain.beehive.BeeHive
import de.devin.ccr.content.domain.logistics.LogisticsPort
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import java.util.*

class BeeNetwork(val id: UUID = UUID.randomUUID()) {
    val hives = mutableSetOf<BeeHive>()
    val ports = mutableSetOf<LogisticsPort>()

    /**
     * The aggregate range of all hives in this network.
     */
    fun isInRange(pos: BlockPos): Boolean = hives.any { it.isInRange(pos) }

    /**
     * Checks if any hive in the network covers this position for logistics.
     * In Factorio, the logistics range is often smaller than the construction range.
     * For now, we use the same range, but we could differentiate.
     */
    fun isInLogisticsRange(pos: BlockPos): Boolean = hives.any { it.isInRange(pos) }

    fun findProvider(stack: ItemStack): LogisticsPort? {
        return ports.filter { it.isValidForPickup() && it.hasItemStack(stack) }
            .minByOrNull { port ->
                // We could use distance to some reference point, 
                // but usually we want to find the best port in the whole network.
                // For now, just return any valid port.
                0
            }
    }

    fun findDropOff(stack: ItemStack): LogisticsPort? {
        return ports.filter { it.isValidForDropOff() }
            .firstOrNull()
    }

    fun addHive(hive: BeeHive) {
        hives.add(hive)
    }

    fun removeHive(hive: BeeHive) {
        hives.remove(hive)
    }

    fun addPort(port: LogisticsPort) {
        ports.add(port)
    }

    fun removePort(port: LogisticsPort) {
        ports.remove(port)
    }

    fun merge(other: BeeNetwork): BeeNetwork {
        val newNetwork = BeeNetwork()
        newNetwork.hives.addAll(this.hives)
        newNetwork.hives.addAll(other.hives)
        newNetwork.ports.addAll(this.ports)
        newNetwork.ports.addAll(other.ports)
        return newNetwork
    }
}
