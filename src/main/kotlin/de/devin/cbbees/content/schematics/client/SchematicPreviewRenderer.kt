package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Axis
import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.schematics.SchematicItem
import com.simibubi.create.content.schematics.requirement.ItemRequirement
import net.createmod.catnip.levelWrappers.SchematicLevel
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.util.RandomSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.client.model.data.ModelData
import kotlin.math.max
import kotlin.math.min

/**
 * Renders an isometric 3D preview of a schematic in GUI space.
 *
 * Loads the schematic into a [SchematicLevel] and renders each block
 * using [net.minecraft.client.renderer.block.BlockRenderDispatcher.renderSingleBlock].
 * Caches the loaded level per filename.
 */
@OnlyIn(Dist.CLIENT)
object SchematicPreviewRenderer {

    /** Material entry: an item stack (with count=1 for display) and total count. */
    data class MaterialEntry(val stack: ItemStack, val count: Int)

    private var cachedFilename: String? = null
    private var cachedLevel: SchematicLevel? = null
    private var cachedSize: Vec3i = Vec3i.ZERO
    private var cachedMaterials: List<MaterialEntry> = emptyList()

    /**
     * Renders an isometric 3D preview of the given schematic in the specified GUI rectangle.
     * Does nothing if the schematic cannot be loaded.
     */
    fun renderPreview(filename: String, guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        val level = getOrLoadLevel(filename) ?: return
        val size = cachedSize
        if (size == Vec3i.ZERO) return

        guiGraphics.enableScissor(x, y, x + w, y + h)

        val pose = guiGraphics.pose()
        pose.pushPose()

        // Position at center of preview area, with Z depth for proper ordering
        pose.translate((x + w / 2.0), (y + h / 2.0), 150.0)

        // Scale to fit the preview rectangle
        val maxDim = max(size.x, max(size.y, size.z)).toFloat()
        val scale = min(w, h) / (maxDim * 1.6f)
        pose.scale(scale, -scale, scale) // Negative Y because GUI Y is downward

        // Isometric rotation
        pose.mulPose(Axis.XP.rotationDegrees(30f))
        pose.mulPose(Axis.YP.rotationDegrees(-45f))

        // Center the schematic at origin
        pose.translate(-size.x / 2.0, -size.y / 2.0, -size.z / 2.0)

        // Render each block
        val dispatcher = Minecraft.getInstance().blockRenderer
        val bufferSource = guiGraphics.bufferSource()
        val bounds = level.bounds

        RenderSystem.enableDepthTest()

        for (blockPos in BlockPos.betweenClosed(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()
        )) {
            val state = level.getBlockState(blockPos)
            if (state.isAir) continue
            if (state.renderShape == RenderShape.INVISIBLE) continue

            pose.pushPose()
            pose.translate(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())

            try {
                // Query block entity for model data (Create blocks need this for correct rendering)
                val blockEntity = level.getBlockEntity(blockPos)
                val bakedModel = dispatcher.getBlockModel(state)
                val modelData = if (blockEntity != null) {
                    val beData = blockEntity.modelData
                    bakedModel.getModelData(level, blockPos, state, beData)
                } else {
                    ModelData.EMPTY
                }

                // Render the baked block model directly instead of using renderSingleBlock,
                // which would render ENTITYBLOCK_ANIMATED blocks (shafts, belts, crushing wheels,
                // gearboxes) as their item form rather than their actual block model.
                val color = Minecraft.getInstance().blockColors.getColor(state, null, null, 0)
                val r = (color shr 16 and 0xFF) / 255f
                val g = (color shr 8 and 0xFF) / 255f
                val b = (color and 0xFF) / 255f
                val random = RandomSource.create(state.getSeed(blockPos))

                for (renderType in bakedModel.getRenderTypes(state, random, modelData)) {
                    dispatcher.modelRenderer.renderModel(
                        pose.last(), bufferSource.getBuffer(renderType),
                        state, bakedModel, r, g, b,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        modelData, renderType
                    )
                }
            } catch (_: Exception) {
                // Skip blocks that fail to render
            }

            pose.popPose()
        }

        // Render block entities (belts, kinetic blocks, etc.) via their BlockEntityRenderers.
        // The baked model pass above handles static geometry; this pass adds the dynamic parts
        // that only exist in BlockEntityRenderer (e.g. the belt strip, rotating shafts).
        val beDispatcher = Minecraft.getInstance().blockEntityRenderDispatcher
        for (blockEntity in level.renderedBlockEntities) {
            @Suppress("UNCHECKED_CAST")
            val beRenderer = beDispatcher.getRenderer(blockEntity) as? BlockEntityRenderer<Any> ?: continue
            val pos = blockEntity.blockPos

            pose.pushPose()
            pose.translate(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

            try {
                beRenderer.render(
                    blockEntity, 0f, pose, bufferSource,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
                )
            } catch (_: Exception) {
                // Skip block entities that fail to render
            }

            pose.popPose()
        }

        bufferSource.endBatch()
        pose.popPose()
        guiGraphics.disableScissor()
    }

    private fun getOrLoadLevel(filename: String): SchematicLevel? {
        if (filename == cachedFilename) return cachedLevel

        cachedFilename = filename
        cachedLevel = null
        cachedSize = Vec3i.ZERO

        try {
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return null
            val player = mc.player ?: return null

            val fakeStack = com.simibubi.create.AllItems.SCHEMATIC.asStack()
            fakeStack.set(AllDataComponents.SCHEMATIC_FILE, filename)
            fakeStack.set(AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
            fakeStack.set(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO)
            fakeStack.set(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE)
            fakeStack.set(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE)
            fakeStack.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)

            val template = SchematicItem.loadSchematic(level, fakeStack)
            if (template.size == Vec3i.ZERO) return null

            cachedSize = template.size

            val schematicLevel = SchematicLevel(level)
            val settings = StructurePlaceSettings()
            settings.rotation = Rotation.NONE
            settings.mirror = Mirror.NONE

            template.placeInWorld(
                schematicLevel, BlockPos.ZERO, BlockPos.ZERO,
                settings, schematicLevel.random, Block.UPDATE_CLIENTS
            )

            for (blockEntity in schematicLevel.blockEntities) {
                blockEntity.setLevel(schematicLevel)
            }

            cachedLevel = schematicLevel
            buildMaterialList(schematicLevel)
            return schematicLevel
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Returns the material list for the given schematic.
     * Sorted by count descending. Loads the schematic if not cached.
     */
    fun getMaterials(filename: String): List<MaterialEntry> {
        getOrLoadLevel(filename)
        return cachedMaterials
    }

    /** Returns the schematic size, or [Vec3i.ZERO] if not loaded. */
    fun getSize(filename: String): Vec3i {
        getOrLoadLevel(filename)
        return cachedSize
    }

    /**
     * Builds the material list using Create's [ItemRequirement] system.
     * This correctly handles multi-block structures (belts), encased blocks,
     * double slabs, and other special cases.
     */
    private fun buildMaterialList(level: SchematicLevel) {
        val counts = mutableMapOf<Item, Int>()
        val bounds = level.bounds

        for (blockPos in BlockPos.betweenClosed(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()
        )) {
            val state = level.getBlockState(blockPos)
            if (state.isAir) continue

            val blockEntity = level.getBlockEntity(blockPos)
            val requirement = ItemRequirement.of(state, blockEntity)

            if (requirement.isEmpty || requirement.isInvalid) continue

            for (stackReq in requirement.requiredItems) {
                if (stackReq.usage != ItemRequirement.ItemUseType.CONSUME) continue
                val item = stackReq.stack.item
                val amount = stackReq.stack.count
                counts[item] = (counts[item] ?: 0) + amount
            }
        }

        cachedMaterials = counts.entries
            .sortedByDescending { it.value }
            .map { MaterialEntry(ItemStack(it.key), it.value) }
    }

    /** Clears the cache. Call when the screen closes. */
    fun clear() {
        cachedFilename = null
        cachedLevel = null
        cachedSize = Vec3i.ZERO
        cachedMaterials = emptyList()
    }
}
