package de.devin.ccr.content.backpack

import com.mojang.blaze3d.systems.RenderSystem
import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen
import de.devin.ccr.content.backpack.client.TaskProgressTracker
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
 * - 8 robot slots (2x4 grid)
 * - 4 upgrade slots (1x4 row)
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
        
        // Robots (2x4 grid) - centered in the FILTER background
        // Slot positions in container: x = 19 + (col * 22), y = 24 + (row * 22)
        // Background offset: slotX - 3, slotY - 3
        for (row in 0 until 2) {
            for (col in 0 until 4) {
                val slotX = x + 16 + (col * 22)
                val slotY = y + 21 + (row * 22)
                AllGuiTextures.TOOLBELT_SLOT.render(guiGraphics, slotX, slotY)
            }
        }
        
        // Upgrades (1x4 row) - below robots
        // Slot positions in container: x = 19 + (i * 22), y = 74
        for (i in 0 until 4) {
            val slotX = x + 16 + (i * 22)
            val slotY = y + 71
            AllGuiTextures.TOOLBELT_SLOT.render(guiGraphics, slotX, slotY)
        }
        
        // Title - centered at top of FILTER background
        val titleX = x + (BG.width - font.width(title)) / 2
        guiGraphics.drawString(font, title, titleX, y + 5, 0x592424, false)
    }

    override fun renderForeground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Labels are handled in renderBg to match Create's style
        
        // Render task progress toast in bottom left corner
        renderTaskProgressToast(guiGraphics)
    }
    
    /**
     * Renders a status toast in the bottom left corner showing current task progress.
     * Uses the same style as the deconstructor menu HUD.
     * Shows max 3 active tasks at a time.
     */
    private fun renderTaskProgressToast(guiGraphics: GuiGraphics) {
        // Only show if there are active tasks and data is recent
        if (!TaskProgressTracker.hasActiveTasks() || !TaskProgressTracker.isDataRecent()) {
            return
        }
        
        val toastWidth = 160
        val lineHeight = 12
        val padding = 6
        val taskDescriptions = TaskProgressTracker.taskDescriptions
        
        // Calculate toast height based on content
        // Header (progress) + task descriptions (max 3)
        val contentLines = 1 + taskDescriptions.size.coerceAtMost(3)
        val toastHeight = (contentLines * lineHeight) + (padding * 2)
        
        // Position in bottom left corner of the GUI window
        val toastX = leftPos + 4
        val toastY = topPos + imageHeight - toastHeight - 4
        
        // Draw semi-transparent background using HUD_BACKGROUND style
        val gray = AllGuiTextures.HUD_BACKGROUND
        
        RenderSystem.enableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.85f)
        
        guiGraphics.blit(
            gray.location, 
            toastX, toastY, 
            gray.startX.toFloat(), gray.startY.toFloat(),
            toastWidth, toastHeight, 
            gray.width, gray.height
        )
        
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
        
        // Draw progress header
        val progress = TaskProgressTracker.getProgress()
        val progressPercent = (progress * 100).toInt()
        val progressText = "Progress: ${TaskProgressTracker.completedTasks}/${TaskProgressTracker.totalTasks} ($progressPercent%)"
        guiGraphics.drawString(
            font, 
            progressText, 
            toastX + padding, 
            toastY + padding, 
            0xAAFFAA,  // Light green for progress
            false
        )
        
        // Draw active task descriptions (max 3)
        taskDescriptions.take(3).forEachIndexed { index, description ->
            // Truncate long descriptions
            val displayText = if (font.width(description) > toastWidth - padding * 2) {
                var truncated = description
                while (font.width("$truncated...") > toastWidth - padding * 2 && truncated.isNotEmpty()) {
                    truncated = truncated.dropLast(1)
                }
                "$truncated..."
            } else {
                description
            }
            
            guiGraphics.drawString(
                font,
                displayText,
                toastX + padding,
                toastY + padding + ((index + 1) * lineHeight),
                0xCCCCCC,  // Light gray for task descriptions
                false
            )
        }
    }
}
