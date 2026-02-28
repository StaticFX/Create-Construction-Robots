package de.devin.cbbees.content.domain.action.impl

import com.simibubi.create.foundation.utility.BlockHelper
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * Places a block at a given position using Create's [BlockHelper.placeSchematicBlock].
 *
 * This delegates to the same placement method Create's SchematiCannon uses, which handles:
 * - State filtering via [SchematicStateFilterRegistry]
 * - Piston state reset and waterlogging
 * - Special placement for rails (update suppression) and belts
 * - Block entity data loading with coordinate fixup
 * - [Block.setPlacedBy] for multi-block structures (doors, beds, tall flowers)
 * - [IMergeableBE] support for existing block entities
 *
 * @see com.simibubi.create.foundation.utility.BlockHelper.placeSchematicBlock
 */
class PlaceBlockAction(
    override val pos: BlockPos,
    val blockState: BlockState,
    val blockEntityTag: CompoundTag? = null,
    override val requiredItems: List<ItemStack> = emptyList()
) : BeeAction {

    override fun execute(level: Level, robot: MechanicalBeeEntity, context: BeeContext): Boolean {
        // Consume required items from robot's inventory
        for (req in requiredItems) {
            var toRemove = req.count
            val iterator = robot.carriedItems.iterator()
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

        // Use Create's BlockHelper for proper schematic block placement.
        // This handles all edge cases: rails, state filtering, block entity data,
        // setPlacedBy for double blocks (doors/beds), and proper update flags.
        val representativeItem = requiredItems.firstOrNull() ?: ItemStack.EMPTY
        BlockHelper.placeSchematicBlock(level, blockState, pos, representativeItem, blockEntityTag)

        if (level is ServerLevel) {
            level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
                5, 0.3, 0.3, 0.3, 0.0
            )
        }

        return true
    }

    override fun getDescription(): String {
        val blockName = blockState.block.name.string
        return "Placing $blockName at (${pos.x}, ${pos.y}, ${pos.z})"
    }
}