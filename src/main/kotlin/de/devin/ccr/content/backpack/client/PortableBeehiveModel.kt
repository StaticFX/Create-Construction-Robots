package de.devin.ccr.content.backpack.client

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.backpack.PortableBeehiveItem
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class PortableBeehiveModel : GeoModel<PortableBeehiveItem>() {
    override fun getModelResource(animatable: PortableBeehiveItem): ResourceLocation {
        return CreateCCR.asResource("geo/portable_beehive.geo.json")
    }

    override fun getTextureResource(animatable: PortableBeehiveItem): ResourceLocation {
        return CreateCCR.asResource("textures/armor/portabel_beehive_texture.png")
    }

    override fun getAnimationResource(animatable: PortableBeehiveItem): ResourceLocation {
        return CreateCCR.asResource("animations/portable_beehive.animation.json")
    }
}
