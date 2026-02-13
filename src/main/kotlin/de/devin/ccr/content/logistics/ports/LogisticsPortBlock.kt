package de.devin.ccr.content.logistics.ports

import com.mojang.serialization.MapCodec
import com.simibubi.create.AllShapes
import com.simibubi.create.content.equipment.wrench.IWrenchable
import com.simibubi.create.foundation.block.IBE
import com.simibubi.create.foundation.block.ProperWaterloggedBlock
import com.simibubi.create.foundation.block.ProperWaterloggedBlock.WATERLOGGED
import de.devin.ccr.registry.AllBlockEntityTypes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape


class LogisticPortBlock(properties: Properties) :
    FaceAttachedHorizontalDirectionalBlock(properties),
    ProperWaterloggedBlock,
    IBE<LogisticPortBlockEntity>,
    IWrenchable {

    companion object {
        /**
         * Determines which direction the Port is "pointing" into the attached inventory.
         * If placed on a wall, it's the opposite of its horizontal facing.
         * If placed on a floor, it's DOWN.
         * If placed on a ceiling, it's UP.
         */
        fun getConnectedDirection(state: BlockState): Direction {
            val face = state.getValue(FACE)
            return when (face) {
                AttachFace.FLOOR -> Direction.UP    // The port sits ON the floor, so it connects DOWN
                AttachFace.CEILING -> Direction.DOWN // The port sits ON the ceiling, so it connects UP
                else -> state.getValue(FACING)      // On a wall, the 'facing' is the direction it LOOKS,
            }
        }
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(WATERLOGGED, false)
        )
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult {
        if (player.isShiftKeyDown) {
            withBlockEntityDo(level, pos) { it.toggleMode(player) }
            return InteractionResult.SUCCESS
        }
        return onBlockEntityUse(level, pos) { be ->
            InteractionResult.SUCCESS
        }
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, FACE, WATERLOGGED)
    }

    override fun codec(): MapCodec<out FaceAttachedHorizontalDirectionalBlock?> {
        TODO("Not yet implemented")
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        // We only want to survive if attached to a block with an inventory (optional, but realistic)
        val connectedPos = pos.relative(getConnectedDirection(state).opposite)
        return level.getBlockState(connectedPos).block !is AirBlock
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        super.onPlace(state, level, pos, oldState, isMoving)
        if (!level.isClientSide) {
            // Register this port to the GlobalJobPool
            updateNetwork(level, pos, true)
        }
    }

    public override fun getFluidState(pState: BlockState): FluidState {
        return fluidState(pState)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        var placed = super.getStateForPlacement(context) ?: return null
        if (placed.getValue(FACE) == AttachFace.CEILING) placed = placed.setValue(
            FACING, placed.getValue(FACING).opposite
        )
        return withWater(placed, context)
    }

    public override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape {
        return AllShapes.STOCK_LINK.get(getConnectedDirection(pState))
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block) {
            updateNetwork(level, pos, false)
            super.onRemove(state, level, pos, newState, isMoving)
        }
    }

    override fun getBlockEntityClass(): Class<LogisticPortBlockEntity> {
        return LogisticPortBlockEntity::class.java
    }

    override fun getBlockEntityType(): BlockEntityType<out LogisticPortBlockEntity> {
        return AllBlockEntityTypes.LOGISTICS_PORT.get()
    }

    private fun updateNetwork(level: Level, pos: BlockPos, isAdding: Boolean) {
        if (level is ServerLevel) {
            //TODO Add later
            //val pool = GlobalJobPool.get(level)
            //if (isAdding) pool.logisticNetwork.addNode(pos)
            //else pool.logisticNetwork.removeNode(pos)
        }
    }
}