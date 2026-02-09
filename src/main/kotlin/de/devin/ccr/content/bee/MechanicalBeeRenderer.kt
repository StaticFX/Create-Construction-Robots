package de.devin.ccr.content.bee

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
}
