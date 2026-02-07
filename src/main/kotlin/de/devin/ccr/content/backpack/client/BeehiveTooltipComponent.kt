package de.devin.ccr.content.backpack.client

import com.simibubi.create.foundation.gui.AllGuiTextures
import de.devin.ccr.content.backpack.BeehiveTooltipData
import de.devin.ccr.content.backpack.PortableBeehiveItem
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

/**
 * Client-side renderer for the backpack tooltip preview.
 * 
 * Renders a small grid showing robots and upgrades currently in the backpack.
 * Layout: 1 row of 4 robot slots, 1 row of 6 upgrade slots.
 */
class BeehiveTooltipComponent(val data: BeehiveTooltipData) : ClientTooltipComponent {

    private val items: NonNullList<ItemStack> = NonNullList.withSize(PortableBeehiveItem.TOTAL_SLOTS, ItemStack.EMPTY)

    init {
        val contents = data.stack.get(DataComponents.CONTAINER)
        contents?.copyInto(items)
    }

    override fun getHeight(): Int {
        // 1 row of robots (18px) + 1 row of upgrades (18px) + padding
        return 2 * 18 + 4
    }

    override fun getWidth(font: Font): Int {
        // 6 columns for upgrades (18px each) - widest row
        return 6 * 18 + 2
    }

    override fun renderImage(font: Font, x: Int, y: Int, guiGraphics: GuiGraphics) {
        var currentY = y

        // Render robots (1x4 grid, centered)
        val robotOffset = (6 - PortableBeehiveItem.ROBOT_SLOTS) * 18 / 2  // Center the 4 slots in 6-wide space
        for (i in 0 until PortableBeehiveItem.ROBOT_SLOTS) {
            val stack = items[i]
            renderSlot(guiGraphics, x + robotOffset + i * 18, currentY, stack, font)
        }

        // Render upgrades (1x6 grid)
        currentY += 18 + 2
        for (i in 0 until PortableBeehiveItem.UPGRADE_SLOTS) {
            val index = PortableBeehiveItem.ROBOT_SLOTS + i
            val stack = items[index]
            renderSlot(guiGraphics, x + i * 18, currentY, stack, font)
        }
    }

    private fun renderSlot(guiGraphics: GuiGraphics, x: Int, y: Int, stack: ItemStack, font: Font) {
        // Draw slot background
        AllGuiTextures.JEI_SLOT.render(guiGraphics, x, y)
        
        if (!stack.isEmpty) {
            guiGraphics.renderItem(stack, x + 1, y + 1)
            guiGraphics.renderItemDecorations(font, stack, x + 1, y + 1)
        }
    }
}
