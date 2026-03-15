package de.devin.cbbees.items

import com.tterrag.registrate.util.entry.ItemEntry
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.bee.MechanicalBeeItem
import de.devin.cbbees.content.bee.MechanicalBumbleBeeItem
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.content.schematics.DeconstructionPlannerItem
import de.devin.cbbees.content.upgrades.*
import net.minecraft.world.item.Rarity


object AllItems {
    fun register() {}

    // Portable Beehive - wearable in Curios "back" slot
    val PORTABLE_BEEHIVE: ItemEntry<PortableBeehiveItem> = CreateBuzzyBeez.REGISTRATE
        .item("portable_beehive") { props ->
            PortableBeehiveItem(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Mechanical Bee - goes in beehive/backpack robot slots (stackable, consumed on deploy)
    val MECHANICAL_BEE: ItemEntry<MechanicalBeeItem> = CreateBuzzyBeez.REGISTRATE
        .item("mechanical_bee") { props ->
            MechanicalBeeItem(props)
        }
        .properties {
            it.stacksTo(MechanicalBeeItem.MAX_STACK_SIZE)
                .rarity(Rarity.UNCOMMON)
        }
        .register()

    // Mechanical Bumble Bee - logistics transport bee
    val MECHANICAL_BUMBLE_BEE: ItemEntry<MechanicalBumbleBeeItem> = CreateBuzzyBeez.REGISTRATE
        .item("mechanical_bumble_bee") { props ->
            MechanicalBumbleBeeItem(props)
        }
        .properties {
            it.stacksTo(MechanicalBumbleBeeItem.MAX_STACK_SIZE)
                .rarity(Rarity.UNCOMMON)
        }
        .register()

    // Construction Planner - select and deploy schematics for bee construction
    val CONSTRUCTION_PLANNER: ItemEntry<ConstructionPlannerItem> = CreateBuzzyBeez.REGISTRATE
        .item("construction_planner") { props ->
            ConstructionPlannerItem(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Stinger Planner - select areas for removal (alternative to schematic-based deconstruction)
    val DECONSTRUCTION_PLANNER: ItemEntry<DeconstructionPlannerItem> = CreateBuzzyBeez.REGISTRATE
        .item("deconstruction_planner") { props ->
            DeconstructionPlannerItem(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // ===== Backpack Upgrades =====

    // Rapid Wings - +25% bee speed (max 4)
    val RAPID_WINGS: ItemEntry<RapidWingsUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("rapid_wings") { props ->
            RapidWingsUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Swarm Intelligence - +2 concurrent bees (max 3)
    val SWARM_INTELLIGENCE: ItemEntry<SwarmIntelligenceUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("swarm_intelligence") { props ->
            SwarmIntelligenceUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Pollen Link - connect to nearby storage
    val POLLEN_LINK: ItemEntry<PollenLinkUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("pollen_link") { props ->
            PollenLinkUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

    // Long-Range Scout - +16 blocks work radius
    val LONG_RANGE_SCOUT: ItemEntry<LongRangeScoutUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("long_range_scout") { props ->
            LongRangeScoutUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Honey Efficiency - Higher capacity and break speed
    val HONEY_EFFICIENCY: ItemEntry<HoneyEfficiencyUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("honey_efficiency") { props ->
            HoneyEfficiencyUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Stinger Precision - Bees can place redstone/rails correctly
    val STINGER_PRECISION: ItemEntry<StingerPrecisionUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("stinger_precision") { props ->
            StingerPrecisionUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

    // Soft Touch - Deconstruction preserves blocks
    val SOFT_TOUCH: ItemEntry<SoftTouchUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("soft_touch") { props ->
            SoftTouchUpgrade(props)
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

}
