package de.devin.ccr.content.domain.action.impl

import de.devin.ccr.content.domain.action.BeeAction
import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * Models an action to place a block at a given position
 */
class PlaceBlockAction(
    val blockState: BlockState,
    val blockEntityTag: CompoundTag? = null,
    override val requiredItems: List<ItemStack> = emptyList()
) : BeeAction {

    override fun execute(level: Level, pos: BlockPos, robot: MechanicalBeeEntity, context: BeeContext): Boolean {
        level.setBlock(pos, blockState, 3)

        if (context.precisionEnabled && blockEntityTag != null) {
            level.getBlockEntity(pos)?.let { be ->
                be.loadWithComponents(blockEntityTag, level.registryAccess())
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

        return true
    }

    override fun getDescription(pos: BlockPos): String {
        val blockName = blockState.block.name.string
        return "Placing $blockName at (${pos.x}, ${pos.y}, ${pos.z})"
    }
}
