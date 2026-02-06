package de.devin.ccr.content.robots

import de.devin.ccr.content.schematics.BeeTaskManager
import de.devin.ccr.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.*

/**
 * Interface for anything that can act as a home for mechanical bees.
 * A bee home manages tasks, provides upgrades, and acts as a refill station.
 */
interface IBeeHome {
    val world: Level
    val position: BlockPos
    val taskManager: BeeTaskManager
    
    /**
     * Gets the calculated context for bees based on installed upgrades.
     */
    fun getBeeContext(): BeeContext
    
    /**
     * Tries to consume pressurized air from the home's storage.
     * @param amount The amount of air to consume.
     * @return The amount of air actually consumed.
     */
    fun consumeAir(amount: Int): Int
    
    /**
     * Tries to add a bee back to the home's inventory.
     * @return true if successful, false if full.
     */
    fun addBee(): Boolean
    
    /**
     * Tries to consume a bee from the home's inventory to spawn it.
     * @return true if successful.
     */
    fun consumeBee(): Boolean
    
    /**
     * Gets a material source for this home.
     */
    fun getMaterialSource(): MaterialSource
    
    /**
     * Gets the unique ID of this home.
     */
    fun getHomeId(): UUID
    
    /**
     * If this home is owned by a player, returns that player.
     */
    fun getOwner(): Player? = null

    /**
     * Called when a bee is spawned from this home.
     */
    fun onBeeSpawned(bee: MechanicalBeeEntity) {}

    /**
     * Called when a bee is removed from the world.
     */
    fun onBeeRemoved(bee: MechanicalBeeEntity) {}
}
