package de.devin.cbbees.content.domain.action

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Interface for actions bees perform on blocks.
 */
interface BeeAction {
    val pos: BlockPos
    fun getWorkTicks(context: BeeContext): Int = 0
    val requiredItems: List<ItemStack> get() = emptyList()

    fun onStart(robot: MechanicalBeeEntity) {}
    fun onTick(robot: MechanicalBeeEntity, tick: Int) {}
    fun execute(level: Level, robot: MechanicalBeeEntity, context: BeeContext): Boolean

    fun hasItems(bee: MechanicalBeeEntity): Boolean =
        requiredItems.all { bee.carriedItems.any { ItemStack.isSameItemSameComponents(it, it) } }


    fun consumeItems(bee: MechanicalBeeEntity): Boolean {
        if (!hasItems(bee)) return false

        for (req in requiredItems) {
            var toRemove = req.count
            val iterator = bee.carriedItems.iterator()
            while (iterator.hasNext() && toRemove > 0) {
                val carried = iterator.next()
                if (ItemStack.isSameItemSameComponents(carried, req)) {
                    val removed = minOf(carried.count, toRemove)
                    carried.shrink(removed)
                    toRemove -= removed
                    if (carried.isEmpty) iterator.remove()
                }
            }
        }

        return true
    }

    /**
     * Whether the bee should return to home after performing this action.
     */
    fun shouldReturnAfter(context: BeeContext): Boolean = true

    /**
     * Priority offset for this action.
     */
    fun getPriorityOffset(): Int = 0

    /**
     * Gets a human-readable description of this action.
     */
    fun getDescription(): String
}