package de.devin.ccr.content.upgrades

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

/**
 * Enum defining all upgrade types for the Constructor Backpack.
 */
enum class UpgradeType(
    val maxStackInBackpack: Int,
    val descriptionKey: String,
    val logic: IUpgrade
) {
    RAPID_WINGS(4, "tooltip.ccr.upgrade.rapid_wings", IUpgrade { ctx, count ->
        ctx.speedMultiplier += count * 0.25
    }),
    SWARM_INTELLIGENCE(3, "tooltip.ccr.upgrade.swarm_intelligence", IUpgrade { ctx, count ->
        ctx.maxActiveRobots += count * 2
    }),
    POLLEN_LINK(1, "tooltip.ccr.upgrade.pollen_link", IUpgrade { ctx, count ->
        if (count > 0) ctx.wirelessLinkEnabled = true
    }),
    LONG_RANGE_SCOUT(2, "tooltip.ccr.upgrade.long_range_scout", IUpgrade { ctx, count ->
        ctx.workRange += count * 16.0
    }),
    HONEY_EFFICIENCY(2, "tooltip.ccr.upgrade.honey_efficiency", IUpgrade { ctx, count ->
        ctx.breakSpeedMultiplier -= count * 0.25
        ctx.carryCapacity += count * 2
        ctx.airConsumptionMultiplier -= count * 0.15
    }),
    STINGER_PRECISION(1, "tooltip.ccr.upgrade.stinger_precision", IUpgrade { ctx, count ->
        if (count > 0) ctx.precisionEnabled = true
    }),
    SOFT_TOUCH(1, "tooltip.ccr.upgrade.soft_touch", IUpgrade { ctx, count ->
        if (count > 0) ctx.silkTouchEnabled = true
    }),
    GATHERING_WINGS(1, "tooltip.ccr.upgrade.gathering_wings", IUpgrade { ctx, count ->
        if (count > 0) ctx.pickupEnabled = true
    });

    companion object {
        /**
         * Creates a [BeeContext] based on the upgrades found in the given backpack stack.
         */
        fun fromBackpack(stack: ItemStack): BeeContext {
            val context = BeeContext()
            val item = stack.item as? de.devin.ccr.content.backpack.PortableBeehiveItem ?: return context
            
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
 * - Pollen Link: Connect to nearby storage (1 only)
 * - Long-Range Scout: +16 blocks work radius per upgrade (max 2)
 * - Honey Efficiency: Increased carry capacity and break speed (max 2)
 * - Stinger Precision: Bees can place redstone/rails correctly (1 only)
 * - Soft Touch: Deconstruction preserves blocks (1 only)
 * - Gathering Wings: Bees collect and return broken blocks (1 only)
 */
open class BeeUpgradeItem(
    val upgradeType: UpgradeType,
    properties: Properties
) : Item(properties) {
    
    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)
        
        // Add upgrade description
        tooltipComponents.add(
            Component.translatable(upgradeType.descriptionKey)
                .withStyle(ChatFormatting.GRAY)
        )
        
        // Add max stack info
        if (upgradeType.maxStackInBackpack > 1) {
            tooltipComponents.add(
                Component.translatable("tooltip.ccr.upgrade.max_stack", upgradeType.maxStackInBackpack)
                    .withStyle(ChatFormatting.DARK_GRAY)
            )
        } else {
            tooltipComponents.add(
                Component.translatable("tooltip.ccr.upgrade.unique")
                    .withStyle(ChatFormatting.DARK_GRAY)
            )
        }
    }
}

// Specific upgrade implementations

class RapidWingsUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.RAPID_WINGS, properties)

class SwarmIntelligenceUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.SWARM_INTELLIGENCE, properties)

class PollenLinkUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.POLLEN_LINK, properties)

class LongRangeScoutUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.LONG_RANGE_SCOUT, properties)

class HoneyEfficiencyUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.HONEY_EFFICIENCY, properties)

class StingerPrecisionUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.STINGER_PRECISION, properties)

class SoftTouchUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.SOFT_TOUCH, properties)

class GatheringWingsUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.GATHERING_WINGS, properties)
