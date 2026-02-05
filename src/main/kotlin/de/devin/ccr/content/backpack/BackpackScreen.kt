package de.devin.ccr.content.backpack

import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * Screen/GUI for the Constructor Backpack.
 * 
 * Uses Create's Filter-style background for a clean look without pre-drawn slots.
 * Composes:
 * - FILTER background for the top section (backpack slots)
 * - PLAYER_INVENTORY background for the bottom section
 * 
 * Displays:
 * - 4 robot slots (1x4 row)
 * - 6 upgrade slots (1x6 row)
 * - Player inventory
 */
class BackpackScreen(
    menu: BackpackContainer,
    playerInventory: Inventory,
    title: Component
) : AbstractSimiContainerScreen<BackpackContainer>(menu, playerInventory, title) {
    
    companion object {
        val BG = AllGuiTextures.FILTER
        val PLAYER_INV = AllGuiTextures.PLAYER_INVENTORY
    }
    
    init {
        // Set window size to accommodate both backgrounds
        // FILTER is 214x99, PLAYER_INVENTORY is 176x108
        // Add 4px gap between them (like AbstractFilterScreen does)
        imageWidth = maxOf(BG.width, PLAYER_INV.width)
        imageHeight = BG.height + 4 + PLAYER_INV.height
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos
        val y = topPos
        
        // Draw the top background (FILTER)
        BG.render(guiGraphics, x, y)
        
        // Draw the player inventory background below
        val invX = leftPos + (imageWidth - PLAYER_INV.width) / 2
        val invY = topPos + BG.height + 4
        renderPlayerInventory(guiGraphics, invX, invY)
        
        // Draw slot backgrounds for the backpack slots using TOOLBELT_SLOT
        // TOOLBELT_SLOT is 22x22, with the 16x16 slot area centered (3px border)
        
        // Robots (1x4 row)
        for (i in 0 until 4) {
            val slotX = x + 60 + (i * 22)
            val slotY = y + 27
            AllGuiTextures.TOOLBELT_SLOT.render(guiGraphics, slotX, slotY)
        }
        
        // Upgrades (1x6 row) - below robots
        for (i in 0 until 6) {
            val slotX = x + 38 + (i * 22)
            val slotY = y + 57
            AllGuiTextures.TOOLBELT_SLOT.render(guiGraphics, slotX, slotY)
        }
        
        // Title - centered at top of FILTER background
        val titleX = x + (BG.width - font.width(title)) / 2
        guiGraphics.drawString(font, title, titleX, y + 5, 0x592424, false)
    }

    override fun renderForeground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Labels are handled in renderBg to match Create's style
        // Task progress is now shown in the HUD overlay (TaskProgressHUD)
    }
}
