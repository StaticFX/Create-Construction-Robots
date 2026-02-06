package de.devin.ccr.content.schematics

import de.devin.ccr.content.robots.MechanicalBeeEntity
import de.devin.ccr.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

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

/**
 * Action for placing blocks.
 */
class PlaceAction(
    val blockState: BlockState,
    val blockEntityTag: CompoundTag? = null,
    override val requiredItems: List<ItemStack> = emptyList()
) : BeeAction {

    override fun execute(level: Level, pos: BlockPos, robot: MechanicalBeeEntity, context: BeeContext) {
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
    }

    override fun getDescription(pos: BlockPos): String {
        val blockName = blockState.block.name.string
        return "Placing $blockName at (${pos.x}, ${pos.y}, ${pos.z})"
    }
}

/**
 * Action for breaking blocks.
 */
class RemoveAction : BeeAction {
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

    override fun execute(level: Level, pos: BlockPos, robot: MechanicalBeeEntity, context: BeeContext) {
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
            }
        } else {
            // No pickup upgrade - just destroy the block (void it)
            level.destroyBlock(pos, true)
        }
    }
    
    override fun shouldReturnAfter(context: BeeContext): Boolean = context.pickupEnabled

    override fun getDescription(pos: BlockPos): String {
        return "Removing block at (${pos.x}, ${pos.y}, ${pos.z})"
    }
}

/**
 * Action for fertilizing crops.
 */
class FertilizeAction : BeeAction {
    override fun getWorkTicks(context: BeeContext): Int = 20
    
    override val requiredItems: List<ItemStack> = listOf(ItemStack(net.minecraft.world.item.Items.BONE_MEAL))

    override fun onTick(robot: MechanicalBeeEntity, tick: Int) {
        if (robot.level() is ServerLevel && tick % 5 == 0) {
            (robot.level() as ServerLevel).sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                robot.x, robot.y, robot.z,
                1, 0.1, 0.1, 0.1, 0.0
            )
        }
    }

    override fun execute(level: Level, pos: BlockPos, robot: MechanicalBeeEntity, context: BeeContext) {
        val state = level.getBlockState(pos)
        if (state.block is net.minecraft.world.level.block.BonemealableBlock) {
            val bonemealable = state.block as net.minecraft.world.level.block.BonemealableBlock
            if (bonemealable.isValidBonemealTarget(level, pos, state)) {
                if (level is ServerLevel) {
                    if (bonemealable.isBonemealSuccess(level, level.random, pos, state)) {
                        bonemealable.performBonemeal(level, level.random, pos, state)
                        level.levelEvent(2005, pos, 0)
                    }
                }
            }
        }
    }

    override fun getDescription(pos: BlockPos): String {
        return "Fertilizing crop at (${pos.x}, ${pos.y}, ${pos.z})"
    }
}
