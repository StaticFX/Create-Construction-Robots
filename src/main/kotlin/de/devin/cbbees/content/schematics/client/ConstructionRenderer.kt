package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.vertex.PoseStack
import de.devin.cbbees.content.beehive.client.ClientJobCache
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

@ClientSide
object ConstructionRenderer {
    @SubscribeEvent
    @JvmStatic
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val poseStack = event.poseStack
        val bufferSource = mc.renderBuffers().bufferSource()
        val camera = mc.gameRenderer.mainCamera.position

        ClientJobCache.getAllJobs().forEach { job ->
            job.batches.forEach { batch ->
                val state = batch.blockState ?: return@forEach
                if (batch.status == "COMPLETED") return@forEach

                val pos = batch.target
                if (level.getBlockState(pos) == state) return@forEach

                renderGhostBlock(poseStack, bufferSource, pos, state, camera.x, camera.y, camera.z)
            }
        }
    }

    private fun renderGhostBlock(
        ms: PoseStack,
        buffer: MultiBufferSource,
        pos: BlockPos,
        state: BlockState,
        cx: Double,
        cy: Double,
        cz: Double
    ) {
        ms.pushPose()
        ms.translate(pos.x - cx, pos.y - cy, pos.z - cz)

        // Slight shrink to prevent z-fighting with existing world blocks
        ms.scale(0.99f, 0.99f, 0.99f)
        ms.translate(0.005, 0.005, 0.005)

        val dispatcher = Minecraft.getInstance().blockRenderer
        val model = dispatcher.getBlockModel(state)
        val vertexConsumer = buffer.getBuffer(RenderType.translucent())

        dispatcher.modelRenderer.renderModel(
            ms.last(), vertexConsumer, state, model,
            1f, 1f, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
        )

        ms.popPose()
    }
}
