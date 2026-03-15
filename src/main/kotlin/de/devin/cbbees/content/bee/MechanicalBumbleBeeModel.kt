package de.devin.cbbees.content.bee

import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

/**
 * GeckoLib model for the Mechanical Bumble Bee.
 * Reuses the mechanical bee geometry with a distinct texture.
 */
class MechanicalBumbleBeeModel : GeoModel<MechanicalBumbleBeeEntity>() {

    override fun getModelResource(animatable: MechanicalBumbleBeeEntity): ResourceLocation {
        return CreateBuzzyBeez.asResource("geo/mechanical_bumble_bee.geo.json")
    }

    override fun getTextureResource(animatable: MechanicalBumbleBeeEntity): ResourceLocation {
        return CreateBuzzyBeez.asResource("textures/entity/mechanical_bumble_bee.png")
    }

    override fun getAnimationResource(animatable: MechanicalBumbleBeeEntity): ResourceLocation {
        return CreateBuzzyBeez.asResource("animations/mechanical_bee.animation.json")
    }
}
