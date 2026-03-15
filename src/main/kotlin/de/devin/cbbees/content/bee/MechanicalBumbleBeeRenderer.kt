package de.devin.cbbees.content.bee

import net.minecraft.client.renderer.entity.EntityRendererProvider
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Renderer for the Mechanical Bumble Bee entity using GeckoLib.
 */
class MechanicalBumbleBeeRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<MechanicalBumbleBeeEntity>(context, MechanicalBumbleBeeModel()) {

    init {
        this.shadowRadius = 0.3f
        addRenderLayer(BumbleBeeCarriedItemLayer(this))
    }
}
