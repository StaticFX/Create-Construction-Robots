package de.devin.cbbees.ponder

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.blocks.AllBlocks
import de.devin.cbbees.items.AllItems
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderPlugin
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.resources.ResourceLocation
import net.createmod.ponder.api.scene.PonderStoryBoard

class CCRPonderPlugin : PonderPlugin {
    override fun getModId(): String = CreateBuzzyBeez.ID

    override fun registerScenes(helper: PonderSceneRegistrationHelper<ResourceLocation>) {
        val TAG = CreateBuzzyBeez.asResource("buzzy_bees")
        
        helper.addStoryBoard(AllItems.MECHANICAL_BEE.id, "base", PonderStoryBoard { scene, util -> BeeScenes.intro(scene, util) }, TAG)
        helper.addStoryBoard(AllItems.PORTABLE_BEEHIVE.id, "base", PonderStoryBoard { scene, util -> BeeScenes.portableHive(scene, util) }, TAG)
        helper.addStoryBoard(AllBlocks.MECHANICAL_BEEHIVE.id, "base", PonderStoryBoard { scene, util -> BeeScenes.stationaryHive(scene, util) }, TAG)
    }

    override fun registerTags(helper: PonderTagRegistrationHelper<ResourceLocation>) {
        helper.registerTag("buzzy_bees")
            .title("Buzzy Bees")
            .description("Automated construction and deconstruction with mechanical bees")
            .item(AllItems.MECHANICAL_BEE.get())
            .addToIndex()
            .register()
    }
}
