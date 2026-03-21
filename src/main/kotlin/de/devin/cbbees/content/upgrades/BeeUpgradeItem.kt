package de.devin.cbbees.content.upgrades

import de.devin.cbbees.config.CBBeesConfig
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * Enum defining all upgrade types for the Constructor Backpack.
 */
enum class UpgradeType(
    val maxStackInBackpack: Int,
    val descriptionKey: String,
    val logic: IUpgrade
) {
    RAPID_WINGS(4, "tooltip.cbbees.upgrade.rapid_wings", IUpgrade { ctx, count ->
        ctx.speedMultiplier += count * CBBeesConfig.rapidWingsSpeedBonus.get()
    }),
    SWARM_INTELLIGENCE(3, "tooltip.cbbees.upgrade.swarm_intelligence", IUpgrade { ctx, count ->
        ctx.maxActiveRobots += count * CBBeesConfig.swarmIntelligenceBeeBonus.get()
    }),
    HONEY_EFFICIENCY(2, "tooltip.cbbees.upgrade.honey_efficiency", IUpgrade { ctx, count ->
        ctx.breakSpeedMultiplier -= count * CBBeesConfig.honeyEfficiencyBreakSpeedReduction.get()
        ctx.carryCapacity += count * CBBeesConfig.honeyEfficiencyCarryBonus.get()
        ctx.fuelConsumptionMultiplier -= count * CBBeesConfig.honeyEfficiencyFuelReduction.get()
    }),
    SOFT_TOUCH(1, "tooltip.cbbees.upgrade.soft_touch", IUpgrade { ctx, count ->
        if (count > 0) ctx.silkTouchEnabled = true
    });

    companion object {
        /**
         * Creates a [BeeContext] based on the upgrades found in the given backpack stack.
         */
        fun fromBackpack(stack: ItemStack): BeeContext {
            val context = BeeContext()
            val item = stack.item as? de.devin.cbbees.content.backpack.PortableBeehiveItem ?: return context

            val upgradeCounts = item.getUpgrades(stack)
            for ((type, count) in upgradeCounts) {
                if (count > 0) {
                    type.logic.apply(context, count)
                }
            }
            return context
        }
    }
}

/**
 * Base class for all backpack upgrade items.
 *
 * Upgrades modify the behavior of bees deployed from the beehive:
 * - Rapid Wings: +25% bee speed per upgrade (max 4)
 * - Swarm Intelligence: +2 concurrent bees per upgrade (max 3)
 * - Honey Efficiency: Increased carry capacity and break speed (max 2)
 * - Soft Touch: Deconstruction preserves blocks (1 only)
 */
open class BeeUpgradeItem(
    val upgradeType: UpgradeType,
    properties: Properties
) : Item(properties)

// Specific upgrade implementations

class RapidWingsUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.RAPID_WINGS, properties)

class SwarmIntelligenceUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.SWARM_INTELLIGENCE, properties)

class HoneyEfficiencyUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.HONEY_EFFICIENCY, properties)

class SoftTouchUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.SOFT_TOUCH, properties)
