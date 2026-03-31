package de.devin.cbbees.content.schematics.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.lwjgl.glfw.GLFW

/**
 * Forge 1.20.1 override for GroupPickerScreen.
 *
 * Differences from NeoForge:
 * - renderBackground(GuiGraphics) instead of renderBackground(GuiGraphics, Int, Int, Float)
 * - renderTransparentBackground doesn't exist; replaced with renderBackground
 * - mouseScrolled(Double, Double, Double) instead of mouseScrolled(Double, Double, Double, Double)
 * - ObjectSelectionList constructor takes 6 params (includes bottom) instead of 5
 * - renderWidget -> render override for custom list rendering
 */
@OnlyIn(Dist.CLIENT)
class GroupPickerScreen(
    private val callback: (String) -> Unit,
    private val currentGroup: String = "",
    private val parentScreen: Screen? = null
) : Screen(Component.translatable("gui.cbbees.group_picker.title")) {

    companion object {
        private const val PANEL_WIDTH = 220
        private const val PANEL_HEIGHT = 200
    }

    private var panelLeft = 0
    private var panelTop = 0

    private var groupList: GroupSelectionList? = null
    private var newGroupField: EditBox? = null
    private var selectedPath: String = currentGroup

    override fun init() {
        super.init()

        panelLeft = (width - PANEL_WIDTH) / 2
        panelTop = (height - PANEL_HEIGHT) / 2

        val innerLeft = panelLeft + 10
        val innerWidth = PANEL_WIDTH - 20

        // Scrollable list of existing groups
        val listTop = panelTop + 28
        val listHeight = 100
        // Forge 1.20.1: ObjectSelectionList(mc, width, height, top, bottom, itemHeight)
        groupList = GroupSelectionList(
            minecraft!!, innerWidth, listHeight, listTop, listTop + listHeight, 16, innerLeft
        )
        addWidget(groupList!!)

        // Populate list
        groupList!!.addGroupEntry(
            GroupEntry(
                "",
                Component.translatable("gui.cbbees.group_picker.root")
            )
        )

        for (path in SchematicGroupManager.getAllGroupPaths().sorted()) {
            val depth = path.count { it == '/' }
            val indent = "  ".repeat(depth)
            val displayName = path.substringAfterLast("/")
            groupList!!.addGroupEntry(GroupEntry(path, Component.literal("$indent$displayName")))
        }

        // Pre-select current group
        groupList!!.children().find { it.path == currentGroup }?.let {
            groupList!!.selected = it
        }

        // "Or create new" text field
        val fieldY = panelTop + 148
        newGroupField = EditBox(
            font, innerLeft, fieldY, innerWidth, 16,
            Component.translatable("gui.cbbees.group_picker.new_group")
        )
        newGroupField!!.setMaxLength(200)
        newGroupField!!.value = ""
        newGroupField!!.setHint(Component.translatable("gui.cbbees.group_picker.new_group_hint"))
        addRenderableWidget(newGroupField!!)

        // Buttons at bottom of panel
        val buttonY = panelTop + PANEL_HEIGHT - 24
        val buttonWidth = (innerWidth - 6) / 2

        addRenderableWidget(Button.builder(Component.translatable("gui.cbbees.group_picker.confirm")) {
            confirm()
        }.bounds(innerLeft, buttonY, buttonWidth, 20).build())

        addRenderableWidget(Button.builder(Component.translatable("gui.cbbees.group_picker.cancel")) {
            onClose()
        }.bounds(innerLeft + buttonWidth + 6, buttonY, buttonWidth, 20).build())
    }

    private fun confirm() {
        val newGroup = newGroupField?.value?.trim() ?: ""
        val result = if (newGroup.isNotEmpty()) {
            newGroup.trim('/')
        } else {
            selectedPath
        }
        callback(result)
        minecraft?.setScreen(parentScreen)
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
            panelLeft + PANEL_WIDTH / 2, panelTop + 6, 0xFFFFFF
        )

        // "Existing groups:" label
        guiGraphics.drawString(
            font,
            Component.translatable("gui.cbbees.group_picker.existing"),
            panelLeft + 10, panelTop + 18, 0xAAAAAA, false
        )

        // Render the group list
        groupList?.render(guiGraphics, mouseX, mouseY, partialTick)

        // "Or create new:" label
        guiGraphics.drawString(
            font,
            Component.translatable("gui.cbbees.group_picker.or_create"),
            panelLeft + 10, panelTop + 136, 0xAAAAAA, false
        )

        // Text input background highlight
        val fieldX = panelLeft + 9
        val fieldY = panelTop + 147
        val fieldW = PANEL_WIDTH - 18
        guiGraphics.fill(fieldX, fieldY, fieldX + fieldW, fieldY + 18, 0x60000000)
    }

    // Forge 1.20.1: renderBackground(GuiGraphics) instead of 4-param version
    override fun renderBackground(guiGraphics: GuiGraphics) {
        // Render a transparent background instead of the default
        // renderTransparentBackground doesn't exist in 1.20.1
        super.renderBackground(guiGraphics)
    }

    // Forward mouse events to the group list
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        groupList?.let {
            if (it.mouseClicked(mouseX, mouseY, button)) return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    // Forge 1.20.1: mouseScrolled has 3 params (no scrollX)
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        groupList?.let {
            if (it.mouseScrolled(mouseX, mouseY, scrollY)) return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        groupList?.let {
            if (it.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        groupList?.let {
            if (it.mouseReleased(mouseX, mouseY, button)) return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Enter confirms
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    // --- Inner classes ---

    inner class GroupEntry(val path: String, private val display: Component) :
        ObjectSelectionList.Entry<GroupEntry>() {

        override fun getNarration(): Component = display

        override fun render(
            guiGraphics: GuiGraphics, index: Int,
            top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float
        ) {
            val selected = groupList?.selected === this

            // Use the full list width for the highlight (not the narrower row width)
            val list = groupList ?: return
            val hlLeft = list.getRowLeft()
            val hlRight = hlLeft + list.getRowWidth() + 12

            if (selected) {
                guiGraphics.fill(hlLeft, top, hlRight, top + height, 0x50FFFFFF)
            } else if (hovered) {
                guiGraphics.fill(hlLeft, top, hlRight, top + height, 0x20FFFFFF)
            }

            val color = when {
                selected -> 0xFFFF00
                path.isEmpty() -> 0xAAAAAA
                hovered -> 0xFFFFFF
                else -> 0xCCCCCC
            }
            guiGraphics.drawString(font, display, left + 2, top + 3, color, false)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            groupList?.setSelected(this)
            selectedPath = path
            // Clear the new group field when selecting from the list
            newGroupField?.value = ""
            return true
        }
    }

    // Forge 1.20.1: ObjectSelectionList constructor takes 6 params (mc, width, height, top, bottom, itemHeight)
    inner class GroupSelectionList(
        mc: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        bottom: Int,
        itemHeight: Int,
        private val listLeft: Int
    ) : ObjectSelectionList<GroupEntry>(mc, width, height, top, bottom, itemHeight) {

        override fun getRowLeft(): Int = listLeft
        override fun getRowWidth(): Int = this.width - 12

        // Suppress the default white selection box -- we draw our own in the entry
        override fun isSelectedItem(index: Int): Boolean = false

        fun addGroupEntry(entry: GroupEntry): Int = super.addEntry(entry)

        // Forge 1.20.1: override render instead of renderWidget to suppress default background
        override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            guiGraphics.enableScissor(listLeft, this.y0, listLeft + this.width, this.y1)
            guiGraphics.fill(listLeft, this.y0, listLeft + this.width, this.y1, 0x40000000)
            renderList(guiGraphics, mouseX, mouseY, partialTick)
            guiGraphics.disableScissor()
        }
    }
}
