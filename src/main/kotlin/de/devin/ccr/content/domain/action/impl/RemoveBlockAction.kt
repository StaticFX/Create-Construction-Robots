package de.devin.ccr.content.domain.action.impl

import de.devin.ccr.content.domain.action.BeeAction
import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block

/**
 * Models an action to break a block at a given position
 */
class RemoveBlockAction : BeeAction {
    //TODO calculate how long it takes to break block
    override fun getWorkTicks(context: BeeContext): Int = 5 // BASE_BREAK_TICKS

    override fun onTick(robot: MechanicalBeeEntity, tick: Int) {
        if (robot.level() is ServerLevel) {
            (robot.level() as ServerLevel).sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                robot.x, robot.y, robot.z,
                2, 0.2, 0.2, 0.2, 0.05
            )
        }
    }

    override fun execute(level: Level, pos: BlockPos, robot: MechanicalBeeEntity, context: BeeContext): Boolean {
        if (context.pickupEnabled) {
            val state = level.getBlockState(pos)
            if (level is ServerLevel) {
                // Determine drops
                val drops = if (context.silkTouchEnabled) {
                    listOf(ItemStack(state.block.asItem()))
                } else {
                    Block.getDrops(state, level, pos, level.getBlockEntity(pos))
                }

                level.destroyBlock(pos, false)
                robot.carriedItems.addAll(drops)
                return true
            }
        } else {
            // No pickup upgrade - just destroy the block (void it)
            level.destroyBlock(pos, true)
            return true
        }
        return false
    }

    override fun shouldReturnAfter(context: BeeContext): Boolean = context.pickupEnabled

    override fun getDescription(pos: BlockPos): String {
        return "Removing block at (${pos.x}, ${pos.y}, ${pos.z})"
    }
}