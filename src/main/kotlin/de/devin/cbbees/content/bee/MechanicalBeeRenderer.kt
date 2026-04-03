package de.devin.cbbees.content.bee

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRendererProvider
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Renderer for the Mechanical Bee entity using GeckoLib.
 *
 * Uses the custom model and flying animation.
 */
class MechanicalBeeRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<MechanicalBeeEntity>(context, MechanicalBeeModel()) {

    init {
        // Shadow radius for the robot
        this.shadowRadius = 0.3f
    }

    override fun render(
        entity: MechanicalBeeEntity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        // Drones are invisible to all players — they exist only as a camera anchor
        if (entity.isDrone) return
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)
    }
}
