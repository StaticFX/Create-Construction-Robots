package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.content.schematics.SchematicItem
import com.simibubi.create.content.schematics.client.SchematicRenderer
import net.minecraft.client.renderer.RenderType.chunkBufferLayers
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.beehive.client.ClientJobCache
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.util.ClientSide
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.impl.client.render.ColoringVertexConsumer
import net.createmod.catnip.levelWrappers.SchematicLevel
import net.createmod.catnip.outliner.AABBOutline
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer
import net.createmod.catnip.render.SuperRenderTypeBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import java.util.UUID

/**
 * Renders ghost blocks for incomplete construction tasks using Create's
 * [SchematicRenderer] pipeline. Loads the actual `.nbt` schematic file
 * on the client and uses [SchematicLevel] + [StructurePlaceSettings] to
 * populate a virtual world with full BlockEntity data — identical to
 * Create's own SchematicHandler.setupRenderer().
 *
 * Blocks that are already placed in the real world are removed from the
 * virtual level to prevent z-fighting. The entire preview is rendered
 * semi-transparently using [ColoringVertexConsumer].
 */
@ClientSide
object ConstructionRenderer {

    /** Alpha for ghost block rendering (0.0 = invisible, 1.0 = opaque). */
    private const val GHOST_ALPHA = 0.5f

    private val outlineCache = mutableMapOf<UUID, AABBOutline>()

    /** Cached max bounds per job — only grows, never shrinks. */
    private val outlineBoundsCache = mutableMapOf<UUID, AABB>()

    /** Per-job schematic renderer cache. Built once per job, updated when blocks change. */
    private val rendererCache = mutableMapOf<UUID, JobRenderer>()

    private var lastDataVersion = -1L

    /** Throttle removePlacedBlocks to avoid per-update world lookups. */
    private var lastBlockCheckTick = 0L
    private const val BLOCK_CHECK_INTERVAL = 20L // Check every second

    private class JobRenderer(
        val renderer: SchematicRenderer,
        val schematicLevel: SchematicLevel,
        val anchor: BlockPos
    )

    /**
     * Wraps a [SuperRenderTypeBuffer] to apply semi-transparency to all
     * vertex output via [ColoringVertexConsumer].
     */
    private class TransparentBuffer(
        private val delegate: SuperRenderTypeBuffer,
        private val alpha: Float
    ) : SuperRenderTypeBuffer {

        private val chunkLayers = chunkBufferLayers().toSet()

        private fun wrap(consumer: VertexConsumer): VertexConsumer =
            ColoringVertexConsumer(consumer, 1f, 1f, 1f, alpha)

        override fun getBuffer(type: RenderType): VertexConsumer {
            // Redirect chunk buffer layers (solid, cutout, etc.) to translucent
            // so GL blending is enabled and vertex alpha takes effect
            val redirected = if (type in chunkLayers) RenderType.translucent() else type
            return wrap(delegate.getBuffer(redirected))
        }

        override fun getEarlyBuffer(type: RenderType): VertexConsumer = wrap(delegate.getEarlyBuffer(type))
        override fun getLateBuffer(type: RenderType): VertexConsumer = wrap(delegate.getLateBuffer(type))
        override fun draw() = delegate.draw()
        override fun draw(type: RenderType) = delegate.draw(type)
    }

    @SubscribeEvent
    @JvmStatic
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return

        val jobs = ClientJobCache.getAllJobs()
        if (jobs.isEmpty()) {
            outlineCache.clear()
            outlineBoundsCache.clear()
            rendererCache.clear()
            lastDataVersion = -1L
            return
        }

        val dataVersion = ClientJobCache.version
        val gameTick = level.gameTime
        val shouldCheckBlocks = gameTick - lastBlockCheckTick >= BLOCK_CHECK_INTERVAL
        if (dataVersion != lastDataVersion || shouldCheckBlocks) {
            rebuildCache(jobs, level, shouldCheckBlocks)
            lastDataVersion = dataVersion
            if (shouldCheckBlocks) lastBlockCheckTick = gameTick
        }

        val poseStack = event.poseStack
        val camera = mc.gameRenderer.mainCamera.position
        val superBuffer = DefaultSuperRenderTypeBuffer.getInstance()
        val transparentBuffer = TransparentBuffer(superBuffer, GHOST_ALPHA)

        // Render each job's schematic via Create's SchematicRenderer with transparency
        for ((_, jobRenderer) in rendererCache) {
            poseStack.pushPose()
            poseStack.translate(-camera.x, -camera.y, -camera.z)
            jobRenderer.renderer.render(poseStack, transparentBuffer)
            poseStack.popPose()
        }

        // Render blue outlines (at full opacity, through the real buffer)
        val pt = AnimationTickHolder.getPartialTicks()
        for ((_, outline) in outlineCache) {
            outline.render(poseStack, superBuffer, camera, pt)
        }

        superBuffer.draw()
        RenderSystem.enableCull()
    }

    private fun rebuildCache(jobs: List<ClientJobInfo>, clientLevel: Level, checkBlocks: Boolean) {
        val activeJobIds = jobs.map { it.jobId }.toSet()
        outlineCache.keys.removeAll { it !in activeJobIds }
        outlineBoundsCache.keys.removeAll { it !in activeJobIds }
        rendererCache.keys.removeAll { it !in activeJobIds }

        for (job in jobs) {
            // Build schematic renderer (once per job), then update when blocks change
            val existing = rendererCache[job.jobId]
            if (existing != null) {
                // Only check placed blocks periodically (not on every data version bump)
                if (checkBlocks && removePlacedBlocks(existing.schematicLevel, clientLevel)) {
                    existing.renderer.update()
                }
            } else {
                val renderer = buildSchematicRenderer(job, clientLevel)
                if (renderer != null) {
                    rendererCache[job.jobId] = renderer

                    // Build outline from actual block positions (not schematicLevel.bounds
                    // which always includes origin due to BoundingBox(BlockPos.ZERO) init)
                    val positions = renderer.schematicLevel.blockMap.keys
                    if (positions.isEmpty()) continue
                    val anchor = renderer.anchor
                    val bounds = AABB(
                        (positions.minOf { it.x } + anchor.x).toDouble(),
                        (positions.minOf { it.y } + anchor.y).toDouble(),
                        (positions.minOf { it.z } + anchor.z).toDouble(),
                        (positions.maxOf { it.x } + anchor.x + 1).toDouble(),
                        (positions.maxOf { it.y } + anchor.y + 1).toDouble(),
                        (positions.maxOf { it.z } + anchor.z + 1).toDouble()
                    )
                    outlineBoundsCache[job.jobId] = bounds
                    val outline = AABBOutline(bounds)
                    outline.params
                        .colored(0x6886c5)
                        .withFaceTexture(AllSpecialTextures.CHECKERED)
                        .lineWidth(1 / 16f)
                    outlineCache[job.jobId] = outline
                }
            }
        }
    }

    /**
     * Removes blocks from the [SchematicLevel] that already exist in the real world.
     * Returns true if any blocks were removed (renderer needs update).
     */
    private fun removePlacedBlocks(schematicLevel: SchematicLevel, clientLevel: Level): Boolean {
        val blockMap = schematicLevel.blockMap
        val toRemove = mutableListOf<BlockPos>()

        for ((localPos, state) in blockMap) {
            val worldPos = localPos.offset(schematicLevel.anchor)
            if (clientLevel.getBlockState(worldPos) == state) {
                toRemove.add(localPos)
            }
        }

        if (toRemove.isEmpty()) return false

        for (pos in toRemove) {
            blockMap.remove(pos)
        }
        return true
    }

    /**
     * Loads the schematic `.nbt` file from disk and builds a [SchematicRenderer]
     * using the same approach as Create's SchematicHandler.setupRenderer().
     */
    private fun buildSchematicRenderer(job: ClientJobInfo, clientLevel: Level): JobRenderer? {
        val placement = job.schematicPlacement ?: return buildFallbackRenderer(job, clientLevel)
        val schematicFile = placement.file
        val anchor = placement.anchor
        val rotation = placement.rotation
        val mirror = placement.mirror

        try {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return buildFallbackRenderer(job, clientLevel)

            val fakeStack = com.simibubi.create.AllItems.SCHEMATIC.asStack()
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_FILE, schematicFile)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ANCHOR, anchor)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ROTATION, rotation)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_MIRROR, mirror)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_DEPLOYED, true)

            val template = SchematicItem.loadSchematic(clientLevel, fakeStack)
            if (template.size == net.minecraft.core.Vec3i.ZERO) {
                CreateBuzzyBeez.LOGGER.warn("Schematic template is empty for file: $schematicFile")
                return buildFallbackRenderer(job, clientLevel)
            }

            // Create SchematicLevel and place blocks — same as SchematicHandler.setupRenderer()
            val schematicLevel = SchematicLevel(clientLevel)
            val settings = StructurePlaceSettings()
            settings.rotation = rotation
            settings.mirror = mirror

            template.placeInWorld(schematicLevel, anchor, anchor, settings, schematicLevel.random, Block.UPDATE_CLIENTS)

            for (blockEntity in schematicLevel.blockEntities) {
                blockEntity.setLevel(schematicLevel)
            }
            fixControllerBlockEntities(schematicLevel)

            // Remove blocks already placed in the real world before first render
            removePlacedBlocks(schematicLevel, clientLevel)

            return JobRenderer(GhostSchematicRenderer(schematicLevel), schematicLevel, anchor)
        } catch (e: Exception) {
            CreateBuzzyBeez.LOGGER.error("Failed to load schematic for rendering: $schematicFile", e)
            return buildFallbackRenderer(job, clientLevel)
        }
    }

    /**
     * Fallback: build renderer from ghost block data when schematic file
     * is unavailable (e.g., file deleted, non-schematic job).
     */
    private fun buildFallbackRenderer(job: ClientJobInfo, clientLevel: Level): JobRenderer? {
        val allGhosts = mutableMapOf<BlockPos, BlockState>()
        for (batch in job.batches) {
            if (batch.status == "COMPLETED") continue
            for ((pos, state) in batch.ghostBlocks) {
                if (clientLevel.getBlockState(pos) == state) continue
                allGhosts[pos] = state
            }
        }
        if (allGhosts.isEmpty()) return null

        val ghostLevel = GhostBlockLevel(clientLevel)
        ghostLevel.populate(allGhosts)
        return JobRenderer(GhostSchematicRenderer(ghostLevel), ghostLevel, BlockPos.ZERO)
    }

    /**
     * Mirrors Create's SchematicHandler.fixControllerBlockEntities() —
     * adjusts multi-block controller references that may have shifted
     * during template placement.
     */
    private fun fixControllerBlockEntities(level: SchematicLevel) {
        for (blockEntity in level.blockEntities) {
            if (blockEntity !is IMultiBlockEntityContainer) continue
            val lastKnown = blockEntity.lastKnownPos ?: continue
            val current = blockEntity.blockPos ?: continue
            if (blockEntity.isController) continue
            if (lastKnown != current) {
                val newControllerPos = blockEntity.controller.offset(current.subtract(lastKnown))
                if (blockEntity is SmartBlockEntity) {
                    blockEntity.markVirtual()
                }
                blockEntity.controller = newControllerPos
            }
        }
    }
}
