package de.devin.ccr.content.robots

import net.minecraft.client.renderer.entity.EntityRendererProvider
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Renderer for the Constructor Robot entity using GeckoLib.
 * 
 * Uses the custom model and flying animation.
 */
class ConstructorRobotRenderer(context: EntityRendererProvider.Context) : 
    GeoEntityRenderer<ConstructorRobotEntity>(context, ConstructorRobotModel()) {
    
    init {
        // Shadow radius for the robot
        this.shadowRadius = 0.3f
    }
}
