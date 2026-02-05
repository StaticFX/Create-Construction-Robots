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
    SPEED_COIL(4, "tooltip.ccr.upgrade.speed_coil", IUpgrade { ctx, count ->
        ctx.speedMultiplier += count * 0.25
    }),
    PARALLEL_PROCESSOR(3, "tooltip.ccr.upgrade.parallel_processor", IUpgrade { ctx, count ->
        ctx.maxActiveRobots += count * 2
    }),
    WIRELESS_LINK(1, "tooltip.ccr.upgrade.wireless_link", IUpgrade { ctx, count ->
        if (count > 0) ctx.wirelessLinkEnabled = true
    }),
    EXTENDED_RANGE(2, "tooltip.ccr.upgrade.extended_range", IUpgrade { ctx, count ->
        ctx.workRange += count * 16.0
    }),
    EFFICIENCY_MODULE(2, "tooltip.ccr.upgrade.efficiency_module", IUpgrade { ctx, count ->
        ctx.efficiencyMultiplier -= count * 0.25
        ctx.carryCapacity += count * 2
    }),
    PRECISION_CORE(1, "tooltip.ccr.upgrade.precision_core", IUpgrade { ctx, count ->
        if (count > 0) ctx.precisionEnabled = true
    }),
    SILK_TOUCH_MODULE(1, "tooltip.ccr.upgrade.silk_touch_module", IUpgrade { ctx, count ->
        if (count > 0) ctx.silkTouchEnabled = true
    });

    companion object {
        /**
         * Creates a [RobotContext] based on the upgrades found in the given backpack stack.
         */
        fun fromBackpack(stack: ItemStack): RobotContext {
            val context = RobotContext()
            val item = stack.item as? de.devin.ccr.content.backpack.ConstructorBackpackItem ?: return context
            
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
 * Upgrades modify the behavior of robots deployed from the backpack:
 * - Speed Coil: +25% robot speed per upgrade (max 4)
 * - Parallel Processor: +2 concurrent robots per upgrade (max 3)
 * - Wireless Link: Connect to nearby storage (1 only)
 * - Extended Range: +16 blocks work radius per upgrade (max 2)
 * - Efficiency Module: Robots use less durability (max 2)
 * - Precision Core: Robots can place redstone/rails correctly (1 only)
 * - Silk Touch Module: Deconstruction preserves blocks (1 only)
 */
open class BackpackUpgradeItem(
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

class SpeedCoilUpgrade(properties: Properties) : BackpackUpgradeItem(UpgradeType.SPEED_COIL, properties)

class ParallelProcessorUpgrade(properties: Properties) : BackpackUpgradeItem(UpgradeType.PARALLEL_PROCESSOR, properties)

class WirelessLinkUpgrade(properties: Properties) : BackpackUpgradeItem(UpgradeType.WIRELESS_LINK, properties)

class ExtendedRangeUpgrade(properties: Properties) : BackpackUpgradeItem(UpgradeType.EXTENDED_RANGE, properties)

class EfficiencyModuleUpgrade(properties: Properties) : BackpackUpgradeItem(UpgradeType.EFFICIENCY_MODULE, properties)

class PrecisionCoreUpgrade(properties: Properties) : BackpackUpgradeItem(UpgradeType.PRECISION_CORE, properties)

class SilkTouchModuleUpgrade(properties: Properties) : BackpackUpgradeItem(UpgradeType.SILK_TOUCH_MODULE, properties)
