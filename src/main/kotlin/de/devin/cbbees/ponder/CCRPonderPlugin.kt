package de.devin.cbbees.ponder

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.blocks.AllBlocks
import net.createmod.ponder.api.registration.PonderPlugin
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.createmod.ponder.api.scene.PonderStoryBoard
import net.minecraft.resources.ResourceLocation

class CCRPonderPlugin : PonderPlugin {
    override fun getModId(): String = CreateBuzzyBeez.ID

    override fun registerScenes(helper: PonderSceneRegistrationHelper<ResourceLocation>) {
        helper.addStoryBoard(
            AllBlocks.MECHANICAL_BEEHIVE.id,
            "mechanical_beehive",
            PonderStoryBoard { scene, util -> BeeScenes.mechanicalBeehive(scene, util) }
        )

        helper.forComponents(AllBlocks.LOGISTICS_PORT.id)
            .addStoryBoard("logistics_port", { scene, util -> BeeScenes.introLogisticsPort(scene, util) })
            .addStoryBoard("logistics_port_1", { scene, util -> BeeScenes.logisticsPortWithBees(scene, util) })
    }

    override fun registerTags(helper: PonderTagRegistrationHelper<ResourceLocation>) {
        helper.registerTag("buzzy_bees")
            .title("Buzzy Bees")
            .description("Automated construction and deconstruction with mechanical bees")
            .item(AllBlocks.MECHANICAL_BEEHIVE.get().asItem())
            .addToIndex()
            .register()
    }
}
