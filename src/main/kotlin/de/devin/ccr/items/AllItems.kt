package de.devin.ccr.items

import com.tterrag.registrate.util.entry.ItemEntry
import de.devin.ccr.CreateCCR
import de.devin.ccr.content.backpack.ConstructorBackpackItem
import de.devin.ccr.content.robots.ConstructorRobotItem
import de.devin.ccr.content.schematics.DeconstructionPlannerItem
import de.devin.ccr.content.upgrades.*
import de.devin.ccr.tabs.AllCreativeModeTabs
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity


object AllItems {

    init {
        CreateCCR.REGISTRATE.setCreativeTab(AllCreativeModeTabs.BASE_MOD_TAB)
    }

    val TUNGSTEN_INGOT: ItemEntry<Item> = CreateCCR.REGISTRATE.item<Item>(
        "tungsten_ingot", ::Item
    ).properties { it.rarity(Rarity.RARE) }
        .register()

    val REFINED_TUNGSTEN_STEEL_INGOT: ItemEntry<Item> = CreateCCR.REGISTRATE.item<Item>(
        "refined_tungsten_steel_ingot", ::Item
    )
        .properties { it.rarity(Rarity.EPIC) }
        .register()
    
    // ===== Constructor System Items =====
    
    // Constructor Backpack - wearable in Curios "back" slot
    val CONSTRUCTOR_BACKPACK: ItemEntry<ConstructorBackpackItem> = CreateCCR.REGISTRATE
        .item<ConstructorBackpackItem>("constructor_backpack") { props ->
            ConstructorBackpackItem(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
    
    // Constructor Robot - goes in backpack robot slots (stackable, consumed on deploy)
    val CONSTRUCTOR_ROBOT: ItemEntry<ConstructorRobotItem> = CreateCCR.REGISTRATE
        .item<ConstructorRobotItem>("constructor_robot") { props ->
            ConstructorRobotItem(props)
        }
        .properties { 
            it.stacksTo(ConstructorRobotItem.MAX_STACK_SIZE)
              .rarity(Rarity.UNCOMMON)
        }
        .register()

    // Deconstruction Planner - select areas for removal (alternative to schematic-based deconstruction)
    val DECONSTRUCTION_PLANNER: ItemEntry<DeconstructionPlannerItem> = CreateCCR.REGISTRATE
        .item<DeconstructionPlannerItem>("deconstruction_planner") { props ->
            DeconstructionPlannerItem(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
    
    // ===== Backpack Upgrades =====
    
    // Speed Coil - +25% robot speed (max 4)
    val SPEED_COIL: ItemEntry<SpeedCoilUpgrade> = CreateCCR.REGISTRATE
        .item<SpeedCoilUpgrade>("speed_coil") { props ->
            SpeedCoilUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
    
    // Parallel Processor - +2 concurrent robots (max 3)
    val PARALLEL_PROCESSOR: ItemEntry<ParallelProcessorUpgrade> = CreateCCR.REGISTRATE
        .item<ParallelProcessorUpgrade>("parallel_processor") { props ->
            ParallelProcessorUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
    
    // Wireless Link - connect to nearby storage
    val WIRELESS_LINK: ItemEntry<WirelessLinkUpgrade> = CreateCCR.REGISTRATE
        .item<WirelessLinkUpgrade>("wireless_link") { props ->
            WirelessLinkUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

}