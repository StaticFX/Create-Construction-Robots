package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import com.simibubi.create.AllDataComponents
import com.simibubi.create.CreateClient
import com.simibubi.create.content.schematics.SchematicItem
import com.simibubi.create.content.schematics.client.SchematicRenderer
import net.createmod.catnip.impl.client.render.ColoringVertexConsumer
import net.createmod.catnip.levelWrappers.SchematicLevel
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer
import net.createmod.catnip.render.SuperRenderTypeBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.RenderType.chunkBufferLayers
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

/**
 * Renders the currently hovered schematic as semi-transparent ghost blocks
 * while browsing the Construction Planner HUD (state 2).
 *
 * Uses Create's SchematicTransformation for positioning so the ghost blocks
 * follow the crosshair (matching DeployTool behavior).
 */
@OnlyIn(Dist.CLIENT)
object SchematicHoverPreview {

    private const val GHOST_ALPHA = 0.5f

    /** Currently previewed schematic filename. */
    private var currentSchematic: String? = null

    /** Cached ghost renderer. */
    private var previewRenderer: SchematicRenderer? = null

    private class TransparentBuffer(
        private val delegate: SuperRenderTypeBuffer,
        private val alpha: Float
    ) : SuperRenderTypeBuffer {

        private val chunkLayers = chunkBufferLayers().toSet()

        private fun wrap(consumer: VertexConsumer): VertexConsumer =
            ColoringVertexConsumer(consumer, 1f, 1f, 1f, alpha)

        override fun getBuffer(type: RenderType): VertexConsumer {
            val redirected = if (type in chunkLayers) RenderType.translucent() else type
            return wrap(delegate.getBuffer(redirected))
        }

        override fun getEarlyBuffer(type: RenderType): VertexConsumer = wrap(delegate.getEarlyBuffer(type))
        override fun getLateBuffer(type: RenderType): VertexConsumer = wrap(delegate.getLateBuffer(type))
        override fun draw() = delegate.draw()
        override fun draw(type: RenderType) = delegate.draw(type)
    }

    /**
     * Called when the HUD selection changes. Loads the schematic if the filename
     * changed, or clears the preview if null.
     */
    fun updatePreview(filename: String?) {
        if (filename == currentSchematic) return

        currentSchematic = filename
        previewRenderer = null

        if (filename == null) return

        try {
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return
            val player = mc.player ?: return

            val fakeStack = com.simibubi.create.AllItems.SCHEMATIC.asStack()
            fakeStack.set(AllDataComponents.SCHEMATIC_FILE, filename)
            fakeStack.set(AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
            fakeStack.set(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO)
            fakeStack.set(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE)
            fakeStack.set(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE)
            fakeStack.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)

            val template = SchematicItem.loadSchematic(level, fakeStack)
            if (template.size == net.minecraft.core.Vec3i.ZERO) return

            val schematicLevel = SchematicLevel(level)
            val settings = StructurePlaceSettings()
            settings.rotation = Rotation.NONE
            settings.mirror = Mirror.NONE

            template.placeInWorld(schematicLevel, BlockPos.ZERO, BlockPos.ZERO, settings, schematicLevel.random, Block.UPDATE_CLIENTS)

            for (blockEntity in schematicLevel.blockEntities) {
                blockEntity.setLevel(schematicLevel)
            }

            previewRenderer = GhostSchematicRenderer(schematicLevel)
        } catch (_: Exception) {
            previewRenderer = null
        }
    }

    /**
     * Renders ghost blocks using Create's transformation for positioning.
     * This makes the ghost preview follow the crosshair just like the blue
     * bounding box does during Create's DeployTool state.
     */
    fun render(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return
        if (!ConstructionPlannerHandler.isBrowsingPreview) return

        val renderer = previewRenderer ?: return

        val handler = CreateClient.SCHEMATIC_HANDLER
        if (!handler.isActive) return

        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera.position
        val superBuffer = DefaultSuperRenderTypeBuffer.getInstance()
        val transparentBuffer = TransparentBuffer(superBuffer, GHOST_ALPHA)

        val poseStack = event.poseStack
        poseStack.pushPose()
        handler.transformation.applyTransformations(poseStack, camera)
        renderer.render(poseStack, transparentBuffer)
        poseStack.popPose()

        superBuffer.draw()
        RenderSystem.enableCull()
    }

    /** Clears all preview state. */
    fun clear() {
        currentSchematic = null
        previewRenderer = null
    }
}
