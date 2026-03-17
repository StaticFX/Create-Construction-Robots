package de.devin.cbbees.content.schematics.client

import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import net.createmod.catnip.gui.AbstractSimiScreen
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

/**
 * Popup screen for selecting or creating a group path for a schematic.
 * Used from the Schematic & Quill save dialog (via mixin) and from the main browser.
 *
 * @param callback Called with the selected group path when confirmed (empty string = root)
 * @param currentGroup The currently assigned group path (pre-selected in the list)
 */
@OnlyIn(Dist.CLIENT)
class GroupPickerScreen(
    private val callback: (String) -> Unit,
    private val currentGroup: String = ""
) : AbstractSimiScreen(Component.translatable("gui.cbbees.group_picker.title")) {

    private val background = AllGuiTextures.SCHEMATIC_PROMPT
    private var groupList: GroupSelectionList? = null
    private var newGroupField: EditBox? = null
    private var selectedPath: String = currentGroup

    override fun init() {
        setWindowSize(background.width, background.height + 80)
        super.init()

        val x = guiLeft
        val y = guiTop

        // Scrollable list of existing groups
        val listWidth = background.width - 20
        groupList = GroupSelectionList(
            minecraft!!, listWidth, 80, y + 20, 14, x + 10
        )
        addWidget(groupList!!)

        // Add root entry
        groupList!!.addEntry(GroupEntry("", Component.translatable("gui.cbbees.group_picker.root")))

        // Add all existing group paths
        for (path in SchematicGroupManager.getAllGroupPaths().sorted()) {
            val indent = "  ".repeat(path.count { it == '/' })
            val displayName = path.substringAfterLast("/")
            groupList!!.addEntry(GroupEntry(path, Component.literal("$indent$displayName")))
        }

        // Pre-select current group
        groupList!!.children().find { it.path == currentGroup }?.let {
            groupList!!.setSelected(it)
        }

        // New group text field
        newGroupField = EditBox(
            font, x + 10, y + 108, background.width - 20, 16,
            Component.translatable("gui.cbbees.group_picker.new_group")
        )
        newGroupField!!.setMaxLength(200)
        newGroupField!!.value = ""
        newGroupField!!.setHint(Component.translatable("gui.cbbees.group_picker.new_group_hint"))
        addRenderableWidget(newGroupField!!)

        // Confirm button
        val confirmBtn = IconButton(x + background.width - 33, y + background.height + 80 - 33, AllIcons.I_CONFIRM)
        confirmBtn.setToolTip(Component.translatable("gui.cbbees.group_picker.confirm"))
        confirmBtn.withCallback<IconButton>(Runnable { confirm() })
        addRenderableWidget(confirmBtn)

        // Cancel button
        val cancelBtn = IconButton(x + 7, y + background.height + 80 - 33, AllIcons.I_TRASH)
        cancelBtn.setToolTip(Component.translatable("gui.cbbees.group_picker.cancel"))
        cancelBtn.withCallback<IconButton>(Runnable { onClose() })
        addRenderableWidget(cancelBtn)
    }

    private fun confirm() {
        val newGroup = newGroupField?.value?.trim() ?: ""
        val result = if (newGroup.isNotEmpty()) {
            // User typed a new group path — use it
            newGroup.trim('/')
        } else {
            // Use the selected path from the list
            selectedPath
        }
        callback(result)
        onClose()
    }

    override fun renderWindow(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val x = guiLeft
        val y = guiTop

        // Draw extended background
        background.render(graphics, x, y)

        // Title
        graphics.drawString(
            font, title,
            x + (background.width - font.width(title)) / 2,
            y + 5,
            0x505050, false
        )

        // "Existing groups:" label
        graphics.drawString(font,
            Component.translatable("gui.cbbees.group_picker.existing"),
            x + 10, y + 20 - 10, 0x606060, false
        )

        // Render the group list
        groupList?.render(graphics, mouseX, mouseY, partialTicks)

        // "Or create new:" label
        graphics.drawString(font,
            Component.translatable("gui.cbbees.group_picker.or_create"),
            x + 10, y + 96, 0x606060, false
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        groupList?.let {
            if (it.mouseClicked(mouseX, mouseY, button)) return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
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
            val color = when {
                selected -> 0xFFFF00
                path.isEmpty() -> 0xAAAAAA
                hovered -> 0xFFFFFF
                else -> 0xCCCCCC
            }
            guiGraphics.drawString(font, display, left + 2, top + 2, color, false)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            groupList?.setSelected(this)
            selectedPath = path
            return true
        }
    }

    inner class GroupSelectionList(
        mc: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        itemHeight: Int,
        private val listLeft: Int
    ) : ObjectSelectionList<GroupEntry>(mc, width, height, top, itemHeight) {

        override fun getRowLeft(): Int = listLeft
        override fun getRowWidth(): Int = this.width - 12

        public override fun addEntry(entry: GroupEntry): Int = super.addEntry(entry)
    }
}
