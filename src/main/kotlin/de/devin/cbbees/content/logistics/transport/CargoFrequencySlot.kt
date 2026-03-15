package de.devin.cbbees.content.logistics.transport

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform
import dev.engine_room.flywheel.lib.transform.TransformStack
import net.createmod.catnip.math.AngleHelper
import net.createmod.catnip.math.VecHelper
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.phys.Vec3

class CargoFrequencySlot(first: Boolean) : ValueBoxTransform.Dual(first) {

    override fun getLocalOffset(level: LevelAccessor, pos: BlockPos, state: BlockState): Vec3 {
        val face = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)
        val facing = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING)
        val horizontalAngle = AngleHelper.horizontalAngle(facing)

        // Offset between the two slots
        val slotOffset = if (isFirst) 3.0 / 16.0 else -3.0 / 16.0

        if (face != AttachFace.WALL) {
            val isFloor = face == AttachFace.FLOOR
            val location = VecHelper.voxelSpace(8.0, if (isFloor) 5.5 else 10.5, 5.0)
                .add(slotOffset, 0.0, 0.0)
            return VecHelper.rotateCentered(
                location,
                (horizontalAngle + (if (isFloor) 0f else 180f)).toDouble(),
                Direction.Axis.Y
            )
        }

        // Wall placement
        val location = VecHelper.voxelSpace(8.0, 11.0, 5.5)
            .add(slotOffset, 0.0, 0.0)
        return VecHelper.rotateCentered(location, horizontalAngle.toDouble(), Direction.Axis.Y)
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

    override fun getScale(): Float = 0.4975f

    override fun shouldRender(level: LevelAccessor, pos: BlockPos, state: BlockState): Boolean = true
}
