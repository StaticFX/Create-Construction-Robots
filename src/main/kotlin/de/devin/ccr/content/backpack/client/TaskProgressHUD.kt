package de.devin.ccr.content.backpack.client

import com.mojang.blaze3d.systems.RenderSystem
import com.simibubi.create.foundation.gui.AllGuiTextures
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/**
 * HUD overlay that displays task progress in the bottom left corner of the screen.
 * 
 * Shows a simple list of active tasks with their progress percentages,
 * plus an overall progress bar at the bottom.
 * 
 * Can be toggled on/off with a hotkey.
 */
object TaskProgressHUD {
    
    /** Whether the HUD is currently visible */
    var isVisible: Boolean = true
        private set
    
    /**
     * Toggles the HUD visibility.
     */
    fun toggle() {
        isVisible = !isVisible
    }
    
    /**
     * Renders the task progress HUD.
     * Called from the RenderGuiEvent.Post event.
     */
    fun renderHUD(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        // Don't render if hidden or no active tasks
        if (!isVisible) return
        if (!TaskProgressTracker.hasActiveTasks()) return
        if (!TaskProgressTracker.isDataRecent()) return
        
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui) return
        
        // Don't show when a screen is open (except for chat)
        if (mc.screen != null) return
        
        val screenWidth = guiGraphics.guiWidth()
        val screenHeight = guiGraphics.guiHeight()
        
        val padding = 6
        val lineHeight = 11
        val progressBarHeight = 4
        
        // Calculate content
        val taskDescriptions = TaskProgressTracker.taskDescriptions
        val totalTasks = TaskProgressTracker.totalTasks
        val completedTasks = TaskProgressTracker.completedTasks
        val progress = TaskProgressTracker.getProgress()
        val progressPercent = (progress * 100).toInt()
        
        
        // Calculate toast dimensions
        val headerText = "Tasks: $completedTasks/$totalTasks ($progressPercent%)"
        val headerWidth = mc.font.width(headerText)
        val toastWidth = headerWidth + (padding * 2)
        
        // Height: header + task lines + progress bar + padding
        val toastHeight = progressBarHeight + (padding * 2) + 4

        val toastX = 4
        val toastY = screenHeight - toastHeight - 4
        
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
        
        // Draw header
        guiGraphics.drawString(
            mc.font,
            headerText,
            toastX + padding,
            toastY + padding,
            0xAAFFAA, // Light green
            false
        )
        
        val barY = toastY + toastHeight - padding - progressBarHeight
        val barWidth = toastWidth - (padding * 2)
        val filledWidth = (barWidth * progress).toInt()
        
        // Background of progress bar (dark gray)
        guiGraphics.fill(
            toastX + padding,
            barY,
            toastX + padding + barWidth,
            barY + progressBarHeight,
            0xFF333333.toInt()
        )
        
        // Filled portion of progress bar (green gradient)
        if (filledWidth > 0) {
            guiGraphics.fill(
                toastX + padding,
                barY,
                toastX + padding + filledWidth,
                barY + progressBarHeight,
                0xFF55FF55.toInt()
            )
        }
    }
    
    /**
     * Simplifies a task description for display.
     * Converts "Placing cobblestone at (10, 64, 20)" to "Placing cobblestone (40%)"
     */
    private fun simplifyTaskDescription(description: String): String {
        // Extract the action and block name, calculate a pseudo-progress
        return when {
            description.startsWith("Placing ") -> {
                val blockName = description.substringAfter("Placing ").substringBefore(" at")
                "Placing $blockName"
            }
            description.startsWith("Removing ") -> {
                "Removing block"
            }
            else -> description.take(30)
        }
    }
}
