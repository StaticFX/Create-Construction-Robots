package de.devin.cbbees.tabs


import com.tterrag.registrate.util.entry.RegistryEntry
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.blocks.AllBlocks
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab

/**
 * Object representing all creative mode tabs for the application.
 * This includes a registry of custom creative mode tabs used within the application.
 *
 * The `AllCreativeModeTabs` object provides a centralized place for defining and initializing
 * creative mode tabs, ensuring consistency and ease of management.
 */
object AllCreativeModeTabs {
    fun register() {}

    val BASE_MOD_TAB: RegistryEntry<CreativeModeTab, CreativeModeTab> =
        CreateBuzzyBeez.REGISTRATE.defaultCreativeTab("base_creative_tab") {
            CreativeModeTab
                .builder()
                .icon { AllBlocks.MECHANICAL_BEEHIVE.asStack() }
                .title(Component.translatable("itemGroup.cbbees.base_creative_tab"))
                .build()
        }.register()
}