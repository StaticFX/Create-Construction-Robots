package de.devin.ccr.content.robots

import de.devin.ccr.content.schematics.RobotTask
import de.devin.ccr.content.upgrades.RobotContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block

/**
 * Interface for actions robots perform on blocks.
 */
interface IWorkAction {
    fun execute(level: Level, pos: BlockPos, task: RobotTask, context: RobotContext)
}

/**
 * Action for placing blocks, with optional precision support.
 */
class PlaceAction : IWorkAction {
    override fun execute(level: Level, pos: BlockPos, task: RobotTask, context: RobotContext) {
        val state = task.blockState ?: return
        level.setBlock(pos, state, 3)
        
        if (context.precisionEnabled && task.blockEntityTag != null) {
            level.getBlockEntity(pos)?.let { be ->
                be.loadWithComponents(task.blockEntityTag, level.registryAccess())
                be.setChanged()
            }
        }
        
        if (level is ServerLevel) {
            level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
                5, 0.3, 0.3, 0.3, 0.0
            )
        }
    }
}

/**
 * Action for breaking blocks, with optional silk touch support.
 */
class RemoveAction(private val robot: ConstructorRobotEntity) : IWorkAction {
    override fun execute(level: Level, pos: BlockPos, task: RobotTask, context: RobotContext) {
        if (context.pickupEnabled) {
            val state = level.getBlockState(pos)
            if (level is ServerLevel) {
                // Determine drops
                // Note: This doesn't perfectly simulate all tool effects (like silk touch)
                // but it's a good approximation for a robot.
                val drops = if (context.silkTouchEnabled) {
                    listOf(ItemStack(state.block.asItem()))
                } else {
                    Block.getDrops(state, level, pos, level.getBlockEntity(pos))
                }
                
                level.destroyBlock(pos, false)
                robot.carriedItems.addAll(drops)
            }
        } else {
            // No pickup upgrade - just destroy the block (void it)
            level.destroyBlock(pos, true)
        }
    }
}
