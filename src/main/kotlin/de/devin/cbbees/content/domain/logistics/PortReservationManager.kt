package de.devin.cbbees.content.domain.logistics

import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Manages item reservations for a port.
 *
 * Both [de.devin.cbbees.content.logistics.ports.LogisticPortBlockEntity] and
 * [de.devin.cbbees.content.logistics.transport.TransportPortBlockEntity] use identical
 * reservation tracking. This class extracts that shared logic.
 */
class PortReservationManager {

    private data class Reservation(val items: List<ItemStack>, val tick: Long)

    private val reservations = mutableMapOf<UUID, Reservation>()

    val hasReservations: Boolean get() = reservations.isNotEmpty()

    /**
     * Returns the total reserved count of [stack], optionally excluding one bee's reservation.
     */
    fun getReservedCount(stack: ItemStack, excludeBeeId: UUID? = null): Int {
        return reservations
            .filter { excludeBeeId == null || it.key != excludeBeeId }
            .values.flatMap { it.items }
            .filter { ItemStack.isSameItemSameComponents(it, stack) }
            .sumOf { it.count }
    }

    fun reserve(beeId: UUID, items: List<ItemStack>, tick: Long) {
        reservations[beeId] = Reservation(items, tick)
    }

    fun release(beeId: UUID): Boolean {
        return reservations.remove(beeId) != null
    }

    fun cleanup(currentTick: Long, maxAge: Long = 600): Boolean {
        val sizeBefore = reservations.size
        reservations.entries.removeAll { currentTick - it.value.tick > maxAge }
        return reservations.size != sizeBefore
    }

    fun clear(): Boolean {
        val had = reservations.isNotEmpty()
        reservations.clear()
        return had
    }
}
