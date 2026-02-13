package de.devin.ccr.content.domain

import de.devin.ccr.content.domain.logistics.LogisticsPort
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

object LogisticsManager {

    private val logisticPorts = mutableSetOf<LogisticsPort>()

    fun registerPort(port: LogisticsPort) {
        logisticPorts.add(port)
    }

    fun unregisterPort(port: LogisticsPort) {
        logisticPorts.remove(port)
    }

    fun findProviderFor(level: Level, stack: ItemStack, startPos: BlockPos): LogisticsPort? {
        val ports = logisticPorts.filter { it.sourceWorld == level }

        if (ports.isEmpty()) {
            return null
        }

        return ports
            .filter { it.isValidForPickup() && it.hasItemStack(stack) }
            .minByOrNull { it.sourcePosition.distSqr(startPos) }
    }

}