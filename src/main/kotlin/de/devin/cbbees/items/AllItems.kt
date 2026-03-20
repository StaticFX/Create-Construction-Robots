package de.devin.cbbees.items

import com.simibubi.create.AllBlocks
import com.simibubi.create.AllItems
import com.tterrag.registrate.providers.RegistrateRecipeProvider
import com.tterrag.registrate.util.entry.ItemEntry
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.bee.MechanicalBeeItem
import de.devin.cbbees.content.bee.MechanicalBumbleBeeItem
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.content.schematics.DeconstructionPlannerItem
import de.devin.cbbees.content.upgrades.*
import de.devin.cbbees.items.AllItems.UPGRADE_TEMPLATE
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.ShapedRecipeBuilder
import net.minecraft.world.item.Items
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

    val UPGRADE_TEMPLATE: ItemEntry<UpgradeTemplateItem> = CreateBuzzyBeez.REGISTRATE
        .item("upgrade_template") { props ->
            UpgradeTemplateItem(props)
        }
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', AllItems.ANDESITE_ALLOY.get())
                .define('V', Items.HONEYCOMB)
                .pattern(" W ")
                .pattern("WVW")
                .pattern(" V ")
                .unlockedBy("has_honey_comb", RegistrateRecipeProvider.has(Items.HONEYCOMB))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .register()

    // Rapid Wings - +25% bee speed (max 4)
    val RAPID_WINGS: ItemEntry<RapidWingsUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("rapid_wings") { props ->
            RapidWingsUpgrade(props)
        }
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', UPGRADE_TEMPLATE.get())
                .define('V', Items.SUGAR)
                .define('B', AllItems.PROPELLER.get())
                .pattern(" B ")
                .pattern("VWV")
                .pattern(" B ")
                .unlockedBy("has_upgrade_base", RegistrateRecipeProvider.has(UPGRADE_TEMPLATE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Swarm Intelligence - +2 concurrent bees (max 3)
    val SWARM_INTELLIGENCE: ItemEntry<SwarmIntelligenceUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("swarm_intelligence") { props ->
            SwarmIntelligenceUpgrade(props)
        }.recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', UPGRADE_TEMPLATE.get())
                .define('V', AllItems.PRECISION_MECHANISM)
                .define('B', AllItems.ELECTRON_TUBE.get())
                .pattern(" V ")
                .pattern("BWB")
                .unlockedBy("has_upgrade_base", RegistrateRecipeProvider.has(UPGRADE_TEMPLATE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Honey Efficiency - Higher capacity and break speed
    val HONEY_EFFICIENCY: ItemEntry<HoneyEfficiencyUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("honey_efficiency") { props ->
            HoneyEfficiencyUpgrade(props)
        }.recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', UPGRADE_TEMPLATE.get())
                .define('V', Items.HONEY_BOTTLE)
                .define('B', AllItems.PROPELLER.get())
                .pattern(" B ")
                .pattern("VWV")
                .pattern(" V ")
                .unlockedBy("has_upgrade_base", RegistrateRecipeProvider.has(UPGRADE_TEMPLATE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Soft Touch - Deconstruction preserves blocks
    val SOFT_TOUCH: ItemEntry<SoftTouchUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("soft_touch") { props ->
            SoftTouchUpgrade(props)
        }.recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', UPGRADE_TEMPLATE.get())
                .define('V', Items.FEATHER)
                .define('B', AllItems.STURDY_SHEET)
                .pattern(" V ")
                .pattern("VWV")
                .pattern("BBB")
                .unlockedBy("has_upgrade_base", RegistrateRecipeProvider.has(UPGRADE_TEMPLATE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

}
