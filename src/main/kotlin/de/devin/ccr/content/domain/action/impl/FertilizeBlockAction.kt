package de.devin.ccr.content.domain.action.impl

import de.devin.ccr.content.domain.action.BeeAction
import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BonemealableBlock

class FertilizeAction : BeeAction {
    override fun getWorkTicks(context: BeeContext): Int = 20

    override val requiredItems: List<ItemStack> = listOf(ItemStack(Items.BONE_MEAL))

    override fun onTick(robot: MechanicalBeeEntity, tick: Int) {
        if (robot.level() is ServerLevel && tick % 5 == 0) {
            (robot.level() as ServerLevel).sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                robot.x, robot.y, robot.z,
                1, 0.1, 0.1, 0.1, 0.0
            )
        }
    }

    override fun execute(level: Level, pos: BlockPos, robot: MechanicalBeeEntity, context: BeeContext): Boolean {
        val state = level.getBlockState(pos)
        if (state.block is BonemealableBlock) {
            val bonemealable = state.block as BonemealableBlock
            if (bonemealable.isValidBonemealTarget(level, pos, state)) {
                if (level is ServerLevel) {
                    if (bonemealable.isBonemealSuccess(level, level.random, pos, state)) {
                        bonemealable.performBonemeal(level, level.random, pos, state)
                        level.levelEvent(2005, pos, 0)
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun getDescription(pos: BlockPos): String {
        return "Fertilizing crop at (${pos.x}, ${pos.y}, ${pos.z})"
    }
}