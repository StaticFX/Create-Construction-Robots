package de.devin.cbbees.content.domain.action

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

/**
 * Interface for actions bees perform on blocks.
 */
interface BeeAction {
    val pos: BlockPos
    fun getWorkTicks(context: BeeContext): Int = 0

    fun onStart(robot: MechanicalBeeEntity) {}
    fun onTick(robot: MechanicalBeeEntity, tick: Int) {}

    /**
     * Called when this action's task becomes the current task in its batch.
     * Use this to resolve dynamic targets (e.g. finding the nearest port).
     */
    fun onActivate(bee: MechanicalBeeEntity) {}

    fun execute(level: Level, bee: MechanicalBeeEntity, context: BeeContext): Boolean

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