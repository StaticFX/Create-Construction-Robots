package de.devin.ccr.items

import com.tterrag.registrate.util.entry.ItemEntry
import de.devin.ccr.CreateCCR
import de.devin.ccr.content.backpack.PortableBeehiveItem
import de.devin.ccr.content.robots.MechanicalBeeItem
import de.devin.ccr.content.robots.MechanicalBeeTier
import de.devin.ccr.content.schematics.StingerPlannerItem
import de.devin.ccr.content.upgrades.*
import de.devin.ccr.tabs.AllCreativeModeTabs
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity


object AllItems {
    fun register() {}

    // Portable Beehive - wearable in Curios "back" slot
    val PORTABLE_BEEHIVE: ItemEntry<PortableBeehiveItem> = CreateCCR.REGISTRATE
        .item("portable_beehive") { props ->
            PortableBeehiveItem(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
    
    // Mechanical Bees - goes in backpack robot slots (stackable, consumed on deploy)
    val ANDESITE_BEE: ItemEntry<MechanicalBeeItem> = CreateCCR.REGISTRATE
        .item("andesite_mechanical_bee") { props ->
            MechanicalBeeItem(MechanicalBeeTier.ANDESITE, props)
        }
        .properties {
            it.stacksTo(MechanicalBeeItem.MAX_STACK_SIZE)
              .rarity(Rarity.UNCOMMON)
        }
        .register()

    val BRASS_BEE: ItemEntry<MechanicalBeeItem> = CreateCCR.REGISTRATE
        .item("brass_mechanical_bee") { props ->
            MechanicalBeeItem(MechanicalBeeTier.BRASS, props)
        }
        .properties {
            it.stacksTo(MechanicalBeeItem.MAX_STACK_SIZE)
              .rarity(Rarity.UNCOMMON)
        }
        .register()

    val STURDY_BEE: ItemEntry<MechanicalBeeItem> = CreateCCR.REGISTRATE
        .item("sturdy_mechanical_bee") { props ->
            MechanicalBeeItem(MechanicalBeeTier.STURDY, props)
        }
        .properties {
            it.stacksTo(MechanicalBeeItem.MAX_STACK_SIZE)
              .rarity(Rarity.UNCOMMON)
        }
        .register()

    // Stinger Planner - select areas for removal (alternative to schematic-based deconstruction)
    val STINGER_PLANNER: ItemEntry<StingerPlannerItem> = CreateCCR.REGISTRATE
        .item("stinger_planner") { props ->
            StingerPlannerItem(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
    
    // ===== Backpack Upgrades =====
    
    // Rapid Wings - +25% bee speed (max 4)
    val RAPID_WINGS: ItemEntry<RapidWingsUpgrade> = CreateCCR.REGISTRATE
        .item("rapid_wings") { props ->
            RapidWingsUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
    
    // Swarm Intelligence - +2 concurrent bees (max 3)
    val SWARM_INTELLIGENCE: ItemEntry<SwarmIntelligenceUpgrade> = CreateCCR.REGISTRATE
        .item("swarm_intelligence") { props ->
            SwarmIntelligenceUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
    
    // Pollen Link - connect to nearby storage
    val POLLEN_LINK: ItemEntry<PollenLinkUpgrade> = CreateCCR.REGISTRATE
        .item("pollen_link") { props ->
            PollenLinkUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

    // Long-Range Scout - +16 blocks work radius
    val LONG_RANGE_SCOUT: ItemEntry<LongRangeScoutUpgrade> = CreateCCR.REGISTRATE
        .item("long_range_scout") { props ->
            LongRangeScoutUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Honey Efficiency - Higher capacity and break speed
    val HONEY_EFFICIENCY: ItemEntry<HoneyEfficiencyUpgrade> = CreateCCR.REGISTRATE
        .item("honey_efficiency") { props ->
            HoneyEfficiencyUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Stinger Precision - Bees can place redstone/rails correctly
    val STINGER_PRECISION: ItemEntry<StingerPrecisionUpgrade> = CreateCCR.REGISTRATE
        .item("stinger_precision") { props ->
            StingerPrecisionUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

    // Soft Touch - Deconstruction preserves blocks
    val SOFT_TOUCH: ItemEntry<SoftTouchUpgrade> = CreateCCR.REGISTRATE
        .item("soft_touch") { props ->
            SoftTouchUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

    // Gathering Wings - Bees pick up broken blocks
    val GATHERING_WINGS: ItemEntry<GatheringWingsUpgrade> = CreateCCR.REGISTRATE
        .item("gathering_wings") { props ->
            GatheringWingsUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()
}