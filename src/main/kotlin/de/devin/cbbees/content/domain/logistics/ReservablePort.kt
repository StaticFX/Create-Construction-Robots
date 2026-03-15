package de.devin.cbbees.content.domain.logistics

import de.devin.cbbees.content.domain.network.INetworkComponent
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.items.IItemHandler
import java.util.UUID

/**
 * Common interface for network ports that hold item inventories and support reservations.
 *
 * Both [LogisticsPort] (construction bee ports) and [TransportPort] (bumble bee cargo ports)
 * share these operations. Extracting them allows [de.devin.cbbees.content.domain.network.BeeNetwork]
 * to manage reservations uniformly.
 */
interface ReservablePort : INetworkComponent {
    override val id: UUID
    override val world: Level
    override val pos: BlockPos

    fun getItemHandler(level: Level): IItemHandler?

    fun walkTarget(): WalkTarget

    fun priority(): Int

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

    override fun isInWorkRange(pos: BlockPos): Boolean = false
}
