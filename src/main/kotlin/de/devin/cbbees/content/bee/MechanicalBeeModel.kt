package de.devin.cbbees.content.bee

import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

/**
 * GeckoLib model for the Mechanical Bee.
 */
class MechanicalBeeModel : GeoModel<MechanicalBeeEntity>() {

    override fun getModelResource(animatable: MechanicalBeeEntity): ResourceLocation {
        return CreateBuzzyBeez.asResource("geo/mechanical_bee.geo.json")
    }

    override fun getTextureResource(animatable: MechanicalBeeEntity): ResourceLocation {
        return CreateBuzzyBeez.asResource("textures/entity/mechanical_bee/${animatable.tier.id}.png")
    }

    override fun getAnimationResource(animatable: MechanicalBeeEntity): ResourceLocation {
        return CreateBuzzyBeez.asResource("animations/mechanical_bee.animation.json")
    }
}
