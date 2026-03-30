package de.devin.cbbees.content.schematics

import de.devin.cbbees.compat.SchematicDataHelper
import de.devin.cbbees.content.schematics.client.ConstructionPlannerHandler
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

/**
 * Construction Planner - a reusable tool for selecting and deploying schematics.
 *
 * When right-clicked with no schematic loaded, enters groups or selects schematics
 * from the inline HUD. Shift+right-click on a schematic instantly starts construction
 * at the crosshair position without the Create overlay.
 *
 * After construction starts, the schematic data is cleared and the planner
 * is ready for reuse.
 */
class ConstructionPlannerItem(properties: Properties) : Item(properties) {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)

        if (level.isClientSide) {
            if (!hasSchematic(stack)) {
                handleClientUse(player)
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    @OnlyIn(Dist.CLIENT)
    private fun handleClientUse(player: Player) {
        if (player.isShiftKeyDown) {
            // Shift+RMB: instant construction at crosshair
            ConstructionPlannerHandler.instantConstruct()
        } else {
            // Normal RMB: enter group or select schematic
            ConstructionPlannerHandler.confirmSelection()
        }
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltip: MutableList<Component>,
        flag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltip, flag)
        val file = SchematicDataHelper.getFile(stack)
        if (file != null) {
            tooltip.add(Component.literal(file).withStyle(ChatFormatting.GOLD))
        } else {
            tooltip.add(
                Component.translatable("tooltip.cbbees.construction_planner.empty")
                    .withStyle(ChatFormatting.GRAY)
            )
        }
    }

    override fun isFoil(stack: ItemStack): Boolean {
        return hasSchematic(stack)
    }

    companion object {
        fun hasSchematic(stack: ItemStack): Boolean {
            return SchematicDataHelper.hasFile(stack)
        }

        fun clearSchematic(stack: ItemStack) {
            SchematicDataHelper.clearAll(stack)
        }
    }
}
