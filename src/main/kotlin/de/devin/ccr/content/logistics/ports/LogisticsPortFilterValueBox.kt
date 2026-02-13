package de.devin.ccr.content.logistics.ports

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform
import dev.engine_room.flywheel.lib.transform.TransformStack
import net.createmod.catnip.math.AngleHelper
import net.createmod.catnip.math.VecHelper
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.phys.Vec3

class LogisticsPortFilterValueBox : ValueBoxTransform() {

    override fun getLocalOffset(level: LevelAccessor, pos: BlockPos, state: BlockState): Vec3 {
        val face = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)
        val facing = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING)
        val horizontalAngle = AngleHelper.horizontalAngle(facing)

        val floorZHeight = 3.5
        val ceilingZHeight = 16.0 - floorZHeight

        // If placed on FLOOR or CEILING
        if (face != AttachFace.WALL) {
            val isFloor = face == AttachFace.FLOOR
            val verticalLocation = VecHelper.voxelSpace(8.0, if (isFloor) floorZHeight else ceilingZHeight, 5.0)
            return VecHelper.rotateCentered(
                verticalLocation,
                (horizontalAngle + (if (isFloor) 0f else 180f)).toDouble(),
                Direction.Axis.Y
            )
        }

        // If placed on a WALL
        val wallLocation = VecHelper.voxelSpace(8.0, 11.0, floorZHeight)
        return VecHelper.rotateCentered(wallLocation, horizontalAngle.toDouble(), Direction.Axis.Y)
    }

    override fun rotate(level: LevelAccessor, pos: BlockPos, state: BlockState, ms: PoseStack) {
        val face = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)
        val facing = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING)
        val horizontalAngle = AngleHelper.horizontalAngle(facing)

        if (face == AttachFace.WALL) {
            TransformStack.of(ms)
                .rotateYDegrees(horizontalAngle)
                .rotateXDegrees(180f)
                .rotateZDegrees(180f)
            return
        }

        if (face == AttachFace.FLOOR || face == AttachFace.CEILING) {
            val isFloor = face == AttachFace.FLOOR
            TransformStack.of(ms)
                .rotateYDegrees(horizontalAngle + (if (isFloor) 0f else 180f))
                .rotateXDegrees(if (isFloor) 90f else -90f)
        }
    }

    override fun shouldRender(level: LevelAccessor, pos: BlockPos, state: BlockState): Boolean {
        return true
    }
}