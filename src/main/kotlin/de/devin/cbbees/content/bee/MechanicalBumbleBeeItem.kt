package de.devin.cbbees.content.bee

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

/**
 * Mechanical Bumble Bee Item - A logistics transport bee that shuttles items between ports.
 *
 * Features:
 * - Stackable up to 64
 * - Consumed from beehive when deployed for transport
 * - Returned to beehive when no transport tasks remain
 * - Slower but carries more items per trip (9 slots)
 */
class MechanicalBumbleBeeItem(properties: Properties) : Item(properties) {

    companion object {
        const val MAX_STACK_SIZE = 64
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)

        tooltipComponents.add(
            Component.translatable("tooltip.cbbees.mechanical_bumble_bee.description")
                .withStyle(ChatFormatting.GRAY)
        )

        tooltipComponents.add(
            Component.translatable("tooltip.cbbees.mechanical_bumble_bee.consumed_on_deploy")
                .withStyle(ChatFormatting.DARK_GRAY)
        )
    }
}
