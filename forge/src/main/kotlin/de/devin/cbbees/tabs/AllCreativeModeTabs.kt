package de.devin.cbbees.tabs

import com.tterrag.registrate.util.entry.RegistryEntry
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.blocks.AllBlocks
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab

/**
 * Forge 1.20.1 — RegistryEntry has a single type parameter.
 */
object AllCreativeModeTabs {
    fun register() {}

    val BASE_MOD_TAB: RegistryEntry<CreativeModeTab> =
        CreateBuzzyBeez.REGISTRATE.defaultCreativeTab("base_creative_tab") {
            CreativeModeTab
                .builder()
                .icon { AllBlocks.MECHANICAL_BEEHIVE.asStack() }
                .title(Component.translatable("itemGroup.cbbees.base_creative_tab"))
                .build()
        }.register()
}
