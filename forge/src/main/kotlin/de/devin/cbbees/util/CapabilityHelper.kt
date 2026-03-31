package de.devin.cbbees.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.items.IItemHandler

/**
 * Forge 1.20.1 — queries item handler capability via BlockEntity.getCapability().
 */
object CapabilityHelper {
    @JvmStatic
    fun getItemHandler(level: Level, pos: BlockPos, direction: Direction? = null): IItemHandler? {
        val be = level.getBlockEntity(pos) ?: return null
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, direction).orElse(null)
    }
}
