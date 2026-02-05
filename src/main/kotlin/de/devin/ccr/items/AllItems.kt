package de.devin.ccr.items

import com.tterrag.registrate.util.entry.ItemEntry
import de.devin.ccr.CreateCCR
import de.devin.ccr.content.backpack.ConstructorBackpackItem
import de.devin.ccr.content.robots.ConstructorRobotItem
import de.devin.ccr.content.schematics.DeconstructionPlannerItem
import de.devin.ccr.content.upgrades.*
import de.devin.ccr.tabs.AllCreativeModeTabs
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity


object AllItems {

    init {
        CreateCCR.REGISTRATE.setCreativeTab(AllCreativeModeTabs.BASE_MOD_TAB)
    }

    // Constructor Backpack - wearable in Curios "back" slot
    val CONSTRUCTOR_BACKPACK: ItemEntry<ConstructorBackpackItem> = CreateCCR.REGISTRATE
        .item("constructor_backpack") { props ->
            ConstructorBackpackItem(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
    
    // Constructor Robot - goes in backpack robot slots (stackable, consumed on deploy)
    val CONSTRUCTOR_ROBOT: ItemEntry<ConstructorRobotItem> = CreateCCR.REGISTRATE
        .item("constructor_robot") { props ->
            ConstructorRobotItem(props)
        }
        .properties {
            it.stacksTo(ConstructorRobotItem.MAX_STACK_SIZE)
              .rarity(Rarity.UNCOMMON)
        }
        .register()

    // Deconstruction Planner - select areas for removal (alternative to schematic-based deconstruction)
    val DECONSTRUCTION_PLANNER: ItemEntry<DeconstructionPlannerItem> = CreateCCR.REGISTRATE
        .item("deconstruction_planner") { props ->
            DeconstructionPlannerItem(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
    
    // ===== Backpack Upgrades =====
    
    // Speed Coil - +25% robot speed (max 4)
    val SPEED_COIL: ItemEntry<SpeedCoilUpgrade> = CreateCCR.REGISTRATE
        .item("speed_coil") { props ->
            SpeedCoilUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
    
    // Parallel Processor - +2 concurrent robots (max 3)
    val PARALLEL_PROCESSOR: ItemEntry<ParallelProcessorUpgrade> = CreateCCR.REGISTRATE
        .item("parallel_processor") { props ->
            ParallelProcessorUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
    
    // Wireless Link - connect to nearby storage
    val WIRELESS_LINK: ItemEntry<WirelessLinkUpgrade> = CreateCCR.REGISTRATE
        .item("wireless_link") { props ->
            WirelessLinkUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

    // Extended Range - +16 blocks work radius
    val EXTENDED_RANGE: ItemEntry<ExtendedRangeUpgrade> = CreateCCR.REGISTRATE
        .item("extended_range") { props ->
            ExtendedRangeUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Efficiency Module - Higher capacity and break speed
    val EFFICIENCY_MODULE: ItemEntry<EfficiencyModuleUpgrade> = CreateCCR.REGISTRATE
        .item("efficiency_module") { props ->
            EfficiencyModuleUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Precision Core - Robots can place redstone/rails correctly
    val PRECISION_CORE: ItemEntry<PrecisionCoreUpgrade> = CreateCCR.REGISTRATE
        .item("precision_core") { props ->
            PrecisionCoreUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

    // Silk Touch Module - Deconstruction preserves blocks
    val SILK_TOUCH_MODULE: ItemEntry<SilkTouchModuleUpgrade> = CreateCCR.REGISTRATE
        .item("silk_touch_module") { props ->
            SilkTouchModuleUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

    // Pickup Module - Robots pick up broken blocks
    val PICKUP_MODULE: ItemEntry<PickupModuleUpgrade> = CreateCCR.REGISTRATE
        .item("pickup_module") { props ->
            PickupModuleUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
}