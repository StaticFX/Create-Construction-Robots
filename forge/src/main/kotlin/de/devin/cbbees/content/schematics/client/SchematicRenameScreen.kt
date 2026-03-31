package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.lwjgl.glfw.GLFW
import java.nio.file.Files

/**
 * Forge 1.20.1 override for SchematicRenameScreen.
 *
 * Differences from NeoForge:
 * - renderBackground(GuiGraphics) instead of renderBackground(GuiGraphics, Int, Int, Float)
 * - renderTransparentBackground doesn't exist; replaced with renderBackground
 */
@OnlyIn(Dist.CLIENT)
class SchematicRenameScreen(
    private val filename: String,
    private val parentScreen: Screen? = null
) : Screen(Component.translatable("gui.cbbees.schematic_rename.title")) {

    companion object {
        private const val PANEL_WIDTH = 240
        private const val PANEL_HEIGHT = 100
    }

    private var panelLeft = 0
    private var panelTop = 0
    private lateinit var nameField: EditBox
    private lateinit var confirmButton: Button

    override fun init() {
        super.init()

        panelLeft = (width - PANEL_WIDTH) / 2
        panelTop = (height - PANEL_HEIGHT) / 2

        val innerLeft = panelLeft + 10
        val innerWidth = PANEL_WIDTH - 20

        // Name field pre-filled with current name (without .nbt)
        nameField = EditBox(
            font, innerLeft, panelTop + 30, innerWidth, 16,
            Component.translatable("gui.cbbees.schematic_rename.field")
        )
        nameField.setMaxLength(200)
        nameField.value = filename.removeSuffix(".nbt")
        nameField.setResponder { updateConfirmButton() }
        addRenderableWidget(nameField)

        // Buttons
        val buttonY = panelTop + PANEL_HEIGHT - 28
        val buttonWidth = (innerWidth - 6) / 2

        confirmButton = Button.builder(Component.translatable("gui.cbbees.schematic_rename.confirm")) {
            doRename()
        }.bounds(innerLeft, buttonY, buttonWidth, 20).build()
        addRenderableWidget(confirmButton)

        addRenderableWidget(Button.builder(Component.translatable("gui.cbbees.group_picker.cancel")) {
            onClose()
        }.bounds(innerLeft + buttonWidth + 6, buttonY, buttonWidth, 20).build())

        updateConfirmButton()
    }

    private fun updateConfirmButton() {
        val newName = nameField.value.trim()
        confirmButton.active = newName.isNotEmpty() && "$newName.nbt" != filename
    }

    private fun doRename() {
        val newName = nameField.value.trim()
        if (newName.isEmpty()) return

        val newFilename = "$newName.nbt"
        if (newFilename == filename) return

        val oldPath = CreatePaths.SCHEMATICS_DIR.resolve(filename)
        val newPath = CreatePaths.SCHEMATICS_DIR.resolve(newFilename)

        if (Files.exists(newPath)) return // don't overwrite existing

        try {
            Files.move(oldPath, newPath)
            SchematicGroupManager.renameEntry(filename, newFilename)
            CreateClient.SCHEMATIC_SENDER.refresh()
        } catch (e: Exception) {
            CreateBuzzyBeez.LOGGER.error("Failed to rename schematic $filename to $newFilename", e)
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
    }

    // Forge 1.20.1: renderBackground(GuiGraphics) instead of 4-param version
    override fun renderBackground(guiGraphics: GuiGraphics) {
        // renderTransparentBackground doesn't exist in 1.20.1
        super.renderBackground(guiGraphics)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (confirmButton.active) doRename()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }
}
