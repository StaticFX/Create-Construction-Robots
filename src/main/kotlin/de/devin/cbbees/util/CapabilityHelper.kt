package de.devin.cbbees.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.IItemHandler

/**
 * Abstracts NeoForge/Forge capability queries.
 * NeoForge 1.21.1 uses Level.getCapability(); Forge 1.20.1 uses BlockEntity.getCapability().
 */
object CapabilityHelper {
    @JvmStatic
    fun getItemHandler(level: Level, pos: BlockPos, direction: Direction? = null): IItemHandler? {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction)
    }
}
