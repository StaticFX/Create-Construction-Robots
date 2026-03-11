package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.content.schematics.client.SchematicRenderer
import net.createmod.catnip.levelWrappers.SchematicLevel
import net.createmod.catnip.render.ShadedBlockSbbBuilder
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.ModelBlockRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.RenderShape
import net.neoforged.neoforge.client.model.data.ModelData

/**
 * Extends Create's [SchematicRenderer] to render ALL blocks regardless of
 * [RenderShape]. The parent only renders [RenderShape.MODEL]; this also
 * renders [RenderShape.ENTITYBLOCK_ANIMATED] blocks (crushing wheels, etc.)
 * via their baked block model.
 */
class GhostSchematicRenderer(world: SchematicLevel) : SchematicRenderer(world) {

    private val anchor: BlockPos = world.anchor

    companion object {
        private val OBJECTS = ThreadLocal.withInitial { Objects() }
    }

    private class Objects {
        val poseStack = PoseStack()
        val random: RandomSource = RandomSource.createNewThreadLocalInstance()
        val mutableBlockPos = BlockPos.MutableBlockPos()
        val sbbBuilder: ShadedBlockSbbBuilder = ShadedBlockSbbBuilder.create()
    }

    override fun drawLayer(layer: RenderType): SuperByteBuffer {
        val dispatcher = Minecraft.getInstance().blockRenderer
        val renderer = dispatcher.modelRenderer
        val objects = OBJECTS.get()

        val poseStack = objects.poseStack
        val random = objects.random
        val mutableBlockPos = objects.mutableBlockPos
        val renderWorld = schematic
        val bounds = renderWorld.bounds

        val sbbBuilder = objects.sbbBuilder
        sbbBuilder.begin()

        renderWorld.renderMode = true
        ModelBlockRenderer.enableCaching()

        for (localPos in BlockPos.betweenClosed(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()
        )) {
            val pos = mutableBlockPos.setWithOffset(localPos, anchor)
            val state = renderWorld.getBlockState(pos)

            // Render ALL blocks except INVISIBLE (unlike parent which only renders MODEL)
            if (state.renderShape != RenderShape.INVISIBLE) {
                val model = dispatcher.getBlockModel(state)
                val blockEntity = renderWorld.getBlockEntity(localPos)
                val modelData = if (blockEntity != null) blockEntity.modelData else ModelData.EMPTY
                val finalModelData = model.getModelData(renderWorld, pos, state, modelData)
                val seed = state.getSeed(pos)
                random.setSeed(seed)
                if (model.getRenderTypes(state, random, finalModelData).contains(layer)) {
                    poseStack.pushPose()
                    poseStack.translate(localPos.x.toDouble(), localPos.y.toDouble(), localPos.z.toDouble())

                    renderer.tesselateBlock(
                        renderWorld, model, state, pos, poseStack, sbbBuilder, true,
                        random, seed, OverlayTexture.NO_OVERLAY, finalModelData, layer
                    )

                    poseStack.popPose()
                }
            }
        }

        ModelBlockRenderer.clearCache()
        renderWorld.renderMode = false

        return sbbBuilder.end()
    }
}
