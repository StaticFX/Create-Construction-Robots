package de.devin.cbbees.content.domain.action.impl

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block

/**
 * Models an action to break a block at a given position
 */
class RemoveBlockAction(override val pos: BlockPos) : BeeAction {
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

    override fun execute(level: Level, bee: MechanicalBeeEntity, context: BeeContext): Boolean {
        if (level !is ServerLevel) return false

        // Never destroy a Mechanical Beehive — skip silently
        if (level.getBlockEntity(pos) is BeeHive) return true

        // Drop Items upgrade: break block and let items drop naturally, skip pickup entirely
        if (context.dropItemsEnabled) {
            level.destroyBlock(pos, true)
            return true
        }

        val shouldPickUp = CBBeesConfig.beePickupItems.get()

        if (shouldPickUp) {
            val state = level.getBlockState(pos)
            val drops = if (context.silkTouchEnabled) {
                listOf(ItemStack(state.block.asItem()))
            } else {
                Block.getDrops(state, level, pos, level.getBlockEntity(pos))
            }

            level.destroyBlock(pos, false)
            for (drop in drops) {
                val remainder = bee.addToInventory(drop)
                if (!remainder.isEmpty) {
                    val itemEntity = ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, remainder)
                    level.addFreshEntity(itemEntity)
                }
            }
        } else {
            level.destroyBlock(pos, true)
        }
        return true
    }

    override fun shouldReturnAfter(context: BeeContext): Boolean =
        !context.dropItemsEnabled && CBBeesConfig.beePickupItems.get()

    override fun getDescription(): String {
        return "Removing block at (${pos.x}, ${pos.y}, ${pos.z})"
    }
}