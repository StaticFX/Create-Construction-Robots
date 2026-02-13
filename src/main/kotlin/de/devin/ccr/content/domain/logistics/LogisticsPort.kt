package de.devin.ccr.content.domain.logistics

import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.logistics.ports.LogisticPortBlockEntity
import de.devin.ccr.content.logistics.ports.LogisticsPortMode
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.items.IItemHandler
import java.util.UUID

interface LogisticsPort {
    val sourceId: UUID

    /**
     * The world/level this source exists in.
     */
    val sourceWorld: Level

    /**
     * The position of this source in the world.
     */
    val sourcePosition: BlockPos

    /** * Is this port providing items to the network,
     * or looking to receive them?
     */
    fun getMode(): LogisticsPortMode

    /** * The specific item filter set in the ValueBox.
     * Return ItemStack.EMPTY if no filter is set.
     */
    fun getFilter(): ItemStack

    fun isValidForPickup(): Boolean

    fun isValidForDropOff(): Boolean

    /**
     * Access to the inventory attached to the port.
     */
    fun getItemHandler(level: Level): IItemHandler?

    fun walkTarget(): WalkTarget

    /**
     * Check if the bee can currently work with this port
     */
    fun canBeeDropOffItem(bee: MechanicalBeeEntity): Boolean

    fun hasItemStack(stack: ItemStack): Boolean

    fun removeItemStack(stack: ItemStack): Boolean

    fun addItemStack(stack: ItemStack): Boolean
}