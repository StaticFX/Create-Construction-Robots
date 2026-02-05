package de.devin.ccr.tabs


import com.tterrag.registrate.util.entry.RegistryEntry
import de.devin.ccr.CreateCCR
import de.devin.ccr.items.AllItems
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

    val BASE_MOD_TAB: RegistryEntry<CreativeModeTab, CreativeModeTab> = CreateCCR.REGISTRATE.defaultCreativeTab("base_creative_tab") {
        CreativeModeTab
            .builder()
            .icon { AllItems.TUNGSTEN_INGOT.asStack() }
            .title(Component.translatable("itemGroup.ccr.base_creative_tab"))
            .build()
    }.register()


}