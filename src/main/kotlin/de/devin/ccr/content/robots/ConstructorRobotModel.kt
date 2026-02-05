package de.devin.ccr.content.robots

import de.devin.ccr.CreateCCR
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

/**
 * GeckoLib model for the Constructor Robot.
 */
class ConstructorRobotModel : GeoModel<ConstructorRobotEntity>() {
    
    override fun getModelResource(animatable: ConstructorRobotEntity): ResourceLocation {
        return CreateCCR.asResource("geo/constructor_robot.geo.json")
    }

    override fun getTextureResource(animatable: ConstructorRobotEntity): ResourceLocation {
        return CreateCCR.asResource("textures/entity/constructor_robot.png")
    }

    override fun getAnimationResource(animatable: ConstructorRobotEntity): ResourceLocation {
        return CreateCCR.asResource("animations/constructor_robot.animation.json")
    }
}
