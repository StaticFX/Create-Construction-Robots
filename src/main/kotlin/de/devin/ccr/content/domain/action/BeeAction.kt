package de.devin.ccr.content.domain.action

import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Interface for actions bees perform on blocks.
 */
interface BeeAction {
    fun getWorkTicks(context: BeeContext): Int = 0
    val requiredItems: List<ItemStack> get() = emptyList()

    fun onStart(robot: MechanicalBeeEntity) {}
    fun onTick(robot: MechanicalBeeEntity, tick: Int) {}
    fun execute(level: Level, pos: BlockPos, robot: MechanicalBeeEntity, context: BeeContext)

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
    fun getDescription(pos: BlockPos): String
}