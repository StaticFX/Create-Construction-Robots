package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import java.nio.file.Files

/**
 * Forge 1.20.1 override for SchematicDeleteScreen.
 *
 * Differences from NeoForge:
 * - renderBackground(GuiGraphics) instead of renderBackground(GuiGraphics, Int, Int, Float)
 * - renderTransparentBackground doesn't exist; replaced with renderBackground
 */
@OnlyIn(Dist.CLIENT)
class SchematicDeleteScreen(
    private val filename: String,
    private val parentScreen: Screen? = null
) : Screen(Component.translatable("gui.cbbees.schematic_delete.title")) {

    companion object {
        private const val PANEL_WIDTH = 240
        private const val PANEL_HEIGHT = 100
    }

    private var panelLeft = 0
    private var panelTop = 0

    override fun init() {
        super.init()

        panelLeft = (width - PANEL_WIDTH) / 2
        panelTop = (height - PANEL_HEIGHT) / 2

        val innerLeft = panelLeft + 10
        val innerWidth = PANEL_WIDTH - 20

        // Buttons
        val buttonY = panelTop + PANEL_HEIGHT - 28
        val buttonWidth = (innerWidth - 6) / 2

        addRenderableWidget(Button.builder(Component.translatable("gui.cbbees.schematic_delete.confirm")) {
            doDelete()
        }.bounds(innerLeft, buttonY, buttonWidth, 20).build())

        addRenderableWidget(Button.builder(Component.translatable("gui.cbbees.group_picker.cancel")) {
            onClose()
        }.bounds(innerLeft + buttonWidth + 6, buttonY, buttonWidth, 20).build())
    }

    private fun doDelete() {
        val path = CreatePaths.SCHEMATICS_DIR.resolve(filename)
        try {
            Files.deleteIfExists(path)
            SchematicGroupManager.removeGroup(filename)
            CreateClient.SCHEMATIC_SENDER.refresh()
        } catch (e: Exception) {
            CreateBuzzyBeez.LOGGER.error("Failed to delete schematic $filename", e)
        }

        minecraft?.setScreen(ConstructionPlannerScreen())
    }

    override fun onClose() {
        minecraft?.setScreen(parentScreen)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // Panel background
        guiGraphics.fill(
            panelLeft - 1, panelTop - 1,
            panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT + 1, 0xFF333333.toInt()
        )
        guiGraphics.fill(
            panelLeft, panelTop,
            panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF1a1a2e.toInt()
        )

        // Title
        guiGraphics.drawCenteredString(
            font, title,
            panelLeft + PANEL_WIDTH / 2, panelTop + 8, 0xFFFFFF
        )

        // Warning message
        val message = Component.translatable("gui.cbbees.schematic_delete.message", filename.removeSuffix(".nbt"))
        guiGraphics.drawCenteredString(
            font, message,
            panelLeft + PANEL_WIDTH / 2, panelTop + 36, 0xFF5555
        )

        val hint = Component.translatable("gui.cbbees.schematic_delete.hint")
        guiGraphics.drawCenteredString(
            font, hint,
            panelLeft + PANEL_WIDTH / 2, panelTop + 50, 0xAAAAAA
        )
    }

    // Forge 1.20.1: renderBackground(GuiGraphics) instead of 4-param version
    override fun renderBackground(guiGraphics: GuiGraphics) {
        // renderTransparentBackground doesn't exist in 1.20.1
        super.renderBackground(guiGraphics)
    }
}
