package de.devin.ccr.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.foundation.gui.AllGuiTextures
import de.devin.ccr.registry.AllKeys
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component

/**
 * Handles HUD and world rendering for the deconstruction planner.
 */
object DeconstructionRenderer {
    private val outlineSlot = Any()
    private const val SELECTION_COLOR = 0xc56868

    fun renderWorldOutline(selectedFace: Direction?) {
        val box = DeconstructionSelection.getSelectionBox() ?: return
        
        Outliner.getInstance()
            .chaseAABB(outlineSlot, box)
            .colored(SELECTION_COLOR)
            .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
            .lineWidth(1 / 16f)
            .highlightFace(selectedFace)
    }

    fun renderHUD(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        if (!DeconstructionHandler.isActive()) return
        
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui) return
        
        val first = DeconstructionSelection.firstPos ?: return

        val screenWidth = guiGraphics.guiWidth()
        val screenHeight = guiGraphics.guiHeight()

        // Selection Info
        val infoWidth = 140
        val infoHeight = 40
        val infoX = screenWidth / 2 - infoWidth / 2
        val infoY = 20

        val gray = AllGuiTextures.HUD_BACKGROUND
        
        RenderSystem.enableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.75f)
        guiGraphics.blit(gray.location, infoX, infoY, gray.startX.toFloat(), gray.startY.toFloat(), 
            infoWidth, infoHeight, gray.width, gray.height)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        val titleText = Component.translatable("ccr.deconstruction.title")
        guiGraphics.drawString(mc.font, titleText, infoX + (infoWidth - mc.font.width(titleText)) / 2, infoY + 5, 0xFFCCCC, false)
        
        val second = DeconstructionSelection.secondPos
        if (second != null) {
            val sizeX = Math.abs(first.x - second.x) + 1
            val sizeY = Math.abs(first.y - second.y) + 1
            val sizeZ = Math.abs(first.z - second.z) + 1
            val dimText = Component.translatable("ccr.deconstruction.dimensions", sizeX, sizeY, sizeZ)
            guiGraphics.drawString(mc.font, dimText, infoX + (infoWidth - mc.font.width(dimText)) / 2, infoY + 20, 0xCCDDFF, false)
            
            val promptText = Component.translatable("ccr.deconstruction.second_pos", AllKeys.START_ACTION.translatedKeyMessage)
            guiGraphics.drawString(mc.font, promptText, infoX + (infoWidth - mc.font.width(promptText)) / 2, infoY + 30, 0xAAAAAA, false)
            
            // Start Button
            renderStartButton(guiGraphics, mc, screenWidth, screenHeight)
        } else {
            val waitingText = Component.translatable("ccr.deconstruction.first_pos")
            guiGraphics.drawString(mc.font, waitingText, infoX + (infoWidth - mc.font.width(waitingText)) / 2, infoY + 20, 0xAAAAAA, false)
        }
        
        RenderSystem.disableBlend()
    }

    private fun renderStartButton(guiGraphics: GuiGraphics, mc: Minecraft, screenWidth: Int, screenHeight: Int) {
        val buttonWidth = 150
        val buttonHeight = 20
        val buttonX = (screenWidth - buttonWidth) / 2
        val buttonY = screenHeight - 50
        val gray = AllGuiTextures.HUD_BACKGROUND

        RenderSystem.setShaderColor(1f, 1f, 1f, 0.75f)
        guiGraphics.blit(gray.location, buttonX, buttonY, gray.startX.toFloat(), gray.startY.toFloat(), 
            buttonWidth, buttonHeight, gray.width, gray.height)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        val buttonText = Component.translatable("gui.ccr.schematic.start_deconstruction", AllKeys.START_ACTION.translatedKeyMessage)
        guiGraphics.drawString(mc.font, buttonText, buttonX + (buttonWidth - mc.font.width(buttonText)) / 2, buttonY + (buttonHeight - 8) / 2, 0xFFCCCC, false)
    }
}
