package de.devin.cbbees.content.domain.logistics

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.domain.network.INetworkComponent
import de.devin.cbbees.content.logistics.ports.LogisticPortBlockEntity
import de.devin.cbbees.content.logistics.ports.LogisticsPortMode
import de.devin.cbbees.content.logistics.ports.PortType
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.items.IItemHandler
import java.util.UUID

interface LogisticsPort : INetworkComponent {
    override val id: UUID

    /**
     * The world/level this source exists in.
     */
    override val world: Level

    /**
     * The position of this source in the world.
     */
    override val pos: BlockPos

    /** * Is this port providing items to the network,
     * or looking to receive them?
     */
    fun getPortType(): PortType

    /** * The specific item filterStack set in the ValueBox.
     * Return ItemStack.EMPTY if no filterStack is set.
     */
    fun getFilter(): ItemStack

    fun isValidForPickup(): Boolean

    fun isValidForDropOff(): Boolean

    /**
     * Priority of this port.
     * Higher is more important.
     */
    fun priority(): Int

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

    /**
     * Checks whether the port has enough unreserved stock of [stack],
     * optionally excluding a specific bee's reservation.
     */
    fun hasAvailableItemStack(stack: ItemStack, excludeBeeId: UUID? = null): Boolean = hasItemStack(stack)

    fun removeItemStack(stack: ItemStack): Boolean

    fun addItemStack(stack: ItemStack): ItemStack

    /**
     * Reserves [items] at this port for the given bee. One reservation per bee;
     * calling again replaces the previous reservation.
     */
    fun reserve(beeId: UUID, items: List<ItemStack>, tick: Long) {}

    /**
     * Releases any reservation held by [beeId].
     */
    fun releaseReservation(beeId: UUID) {}

    /**
     * Removes reservations older than [maxAge] ticks.
     */
    fun cleanupReservations(currentTick: Long, maxAge: Long = 600) {}

    /**
     * Removes all reservations.
     */
    fun clearReservations() {}

    override fun isAnchor(): Boolean = false

    override fun getNetworkingRange(): Double = 0.0

    override fun isInWorkRange(pos: BlockPos): Boolean = false // Ports don't provide range
}