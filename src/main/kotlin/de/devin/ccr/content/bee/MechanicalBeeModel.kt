package de.devin.ccr.content.bee

import de.devin.ccr.CreateCCR
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

/**
 * GeckoLib model for the Mechanical Bee.
 */
class MechanicalBeeModel : GeoModel<MechanicalBeeEntity>() {
    
    override fun getModelResource(animatable: MechanicalBeeEntity): ResourceLocation {
        return CreateCCR.asResource("geo/mechanical_bee.geo.json")
    }

    override fun getTextureResource(animatable: MechanicalBeeEntity): ResourceLocation {
        return CreateCCR.asResource("textures/entity/mechanical_bee_${animatable.tier.id}.png")
    }

    override fun getAnimationResource(animatable: MechanicalBeeEntity): ResourceLocation {
        return CreateCCR.asResource("animations/mechanical_bee_animation.json")
    }
}
