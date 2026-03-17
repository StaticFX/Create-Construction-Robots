package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import com.simibubi.create.foundation.gui.AllGuiTextures
import de.devin.cbbees.network.InstantConstructionPacket
import de.devin.cbbees.network.SelectSchematicPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Full-screen schematic browser with group navigation, breadcrumbs, and search.
 *
 * Opens via keybind while holding the Construction Planner. Supports:
 * - Scrollable list with group folders and schematic entries
 * - Breadcrumb navigation (clickable segments)
 * - Search filtering across all groups
 * - Right-click schematic → select + enter Create overlay
 * - Shift+right-click schematic → instant construction
 */
@OnlyIn(Dist.CLIENT)
class ConstructionPlannerScreen : Screen(Component.translatable("gui.cbbees.construction_planner.title")) {

    private lateinit var entryList: BrowserList
    private lateinit var selectButton: Button
    private lateinit var searchBox: EditBox
    private var selectedEntry: BrowserEntry? = null
    private var currentGroupPath: String = ""
    private var allSchematics: List<String> = emptyList()
    private var searchActive = false

    override fun init() {
        super.init()

        // Load schematics
        CreateClient.SCHEMATIC_SENDER.refresh()
        allSchematics = CreateClient.SCHEMATIC_SENDER.availableSchematics.map { it.string }
        SchematicGroupManager.ensureLoaded()
        SchematicGroupManager.reconcile(allSchematics)

        // Sync with HUD navigation state
        currentGroupPath = ConstructionPlannerHandler.currentGroupPath

        val listWidth = 260
        val listLeft = (width - listWidth) / 2

        // Search box
        searchBox = EditBox(font, width / 2 - 100, 14, 200, 16, Component.translatable("gui.cbbees.construction_planner.search"))
        searchBox.setMaxLength(100)
        searchBox.setResponder { query ->
            searchActive = query.isNotBlank()
            rebuildList()
        }
        addRenderableWidget(searchBox)

        // Entry list
        entryList = BrowserList(minecraft!!, listWidth, height - 80, 36, 20, listLeft)
        addWidget(entryList)

        // Select button
        selectButton = Button.builder(Component.translatable("gui.cbbees.construction_planner.select")) {
            onSelectAction()
        }.bounds(width / 2 - 105, height - 38, 100, 20).build()
        selectButton.active = false
        addRenderableWidget(selectButton)

        // Cancel button
        val cancelButton = Button.builder(Component.translatable("gui.done")) {
            onClose()
        }.bounds(width / 2 + 5, height - 38, 100, 20).build()
        addRenderableWidget(cancelButton)

        rebuildList()
    }

    private fun rebuildList() {
        entryList.clearEntries()
        selectedEntry = null
        selectButton.active = false

        if (searchActive) {
            // Search mode: show all schematics matching the query, flattened
            val query = searchBox.value.lowercase()
            for (filename in allSchematics) {
                val group = SchematicGroupManager.getGroup(filename)
                val displayPath = if (group.isEmpty()) filename else "$group/$filename"
                if (displayPath.lowercase().contains(query)) {
                    entryList.addEntry(BrowserEntry(
                        BrowserEntryType.SCHEMATIC, filename, filename.removeSuffix(".nbt"),
                        if (group.isNotEmpty()) "($group)" else ""
                    ))
                }
            }
        } else {
            // Normal group navigation
            if (currentGroupPath.isNotEmpty()) {
                entryList.addEntry(BrowserEntry(BrowserEntryType.PARENT, "..", "[..]", ""))
            }

            val (subgroups, schematics) = SchematicGroupManager.getItemsAtLevel(currentGroupPath, allSchematics)

            for (group in subgroups) {
                val fullPath = if (currentGroupPath.isEmpty()) group else "$currentGroupPath/$group"
                entryList.addEntry(BrowserEntry(BrowserEntryType.GROUP, fullPath, group, ""))
            }

            for (filename in schematics) {
                entryList.addEntry(BrowserEntry(
                    BrowserEntryType.SCHEMATIC, filename, filename.removeSuffix(".nbt"), ""
                ))
            }
        }
    }

    private fun onSelectAction() {
        val entry = selectedEntry ?: return
        when (entry.type) {
            BrowserEntryType.GROUP -> navigateToGroup(entry.value)
            BrowserEntryType.PARENT -> navigateUp()
            BrowserEntryType.SCHEMATIC -> selectSchematic(entry.value)
        }
    }

    private fun navigateToGroup(groupPath: String) {
        currentGroupPath = groupPath
        searchBox.value = ""
        searchActive = false
        rebuildList()
    }

    private fun navigateUp() {
        val lastSlash = currentGroupPath.lastIndexOf('/')
        currentGroupPath = if (lastSlash >= 0) currentGroupPath.substring(0, lastSlash) else ""
        rebuildList()
    }

    private fun selectSchematic(filename: String) {
        PacketDistributor.sendToServer(SelectSchematicPacket(filename))
        onClose()
    }

    private fun instantConstruct(filename: String) {
        val mc = Minecraft.getInstance()
        val hitResult = mc.hitResult
        val anchor = if (hitResult is BlockHitResult) {
            hitResult.blockPos.relative(hitResult.direction)
        } else {
            mc.player?.blockPosition() ?: return
        }

        val player = mc.player ?: return
        val rotation = when ((player.yRot % 360 + 360) % 360) {
            in 45f..135f -> Rotation.CLOCKWISE_90
            in 135f..225f -> Rotation.CLOCKWISE_180
            in 225f..315f -> Rotation.COUNTERCLOCKWISE_90
            else -> Rotation.NONE
        }

        PacketDistributor.sendToServer(
            InstantConstructionPacket(filename, anchor, rotation, Mirror.NONE)
        )
        onClose()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        entryList.render(guiGraphics, mouseX, mouseY, partialTick)

        // Title
        guiGraphics.drawCenteredString(font, title, width / 2, 3, 0xFFFFFF)

        // Breadcrumb below search
        val breadcrumb = buildBreadcrumb()
        guiGraphics.drawString(font, breadcrumb, (width - 260) / 2, height - 54, 0x888888, false)

        // Empty state
        if (entryList.children().isEmpty()) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.cbbees.construction_planner.no_schematics"),
                width / 2, height / 2,
                0x888888
            )
        }
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(guiGraphics)
    }

    private fun buildBreadcrumb(): Component {
        if (searchActive) return Component.translatable("gui.cbbees.construction_planner.search_results")
            .withStyle { it.withColor(0xAAAAAA) }

        val parts = if (currentGroupPath.isEmpty()) {
            listOf("Root")
        } else {
            listOf("Root") + currentGroupPath.split("/")
        }
        return Component.literal(parts.joinToString(" > ")).withStyle { it.withColor(0x888888) }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Right-click on selected entry
        if (button == 1) {
            val entry = selectedEntry
            if (entry != null) {
                when (entry.type) {
                    BrowserEntryType.GROUP -> {
                        navigateToGroup(entry.value)
                        return true
                    }
                    BrowserEntryType.SCHEMATIC -> {
                        if (hasShiftDown()) {
                            instantConstruct(entry.value)
                        } else {
                            selectSchematic(entry.value)
                        }
                        return true
                    }
                    BrowserEntryType.PARENT -> {
                        navigateUp()
                        return true
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Allow search box to capture input
        if (searchBox.isFocused) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers)
        }
        // Backspace navigates up when not in search
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !searchActive) {
            navigateUp()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    // --- Entry types ---

    enum class BrowserEntryType { GROUP, SCHEMATIC, PARENT }

    inner class BrowserEntry(
        val type: BrowserEntryType,
        val value: String,
        val displayName: String,
        val extra: String
    ) : ObjectSelectionList.Entry<BrowserEntry>() {

        override fun getNarration(): Component = Component.literal(displayName)

        override fun render(
            guiGraphics: GuiGraphics, index: Int,
            top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float
        ) {
            val selected = entryList.selected === this
            val isFolder = type == BrowserEntryType.GROUP || type == BrowserEntryType.PARENT

            val prefix = when (type) {
                BrowserEntryType.GROUP -> "\u00A79\u25B6 "      // Blue arrow for groups
                BrowserEntryType.PARENT -> "\u00A77\u25C0 "     // Gray arrow for parent
                BrowserEntryType.SCHEMATIC -> "  "
            }

            val nameColor = when {
                selected -> 0xFFFF00
                isFolder -> 0x88CCFF
                hovered -> 0xFFFFFF
                else -> 0xCCCCCC
            }

            val text = "$prefix$displayName"
            guiGraphics.drawString(font, text, left + 2, top + 4, nameColor, false)

            // Extra info (group path in search mode)
            if (extra.isNotEmpty()) {
                val extraWidth = font.width(extra)
                guiGraphics.drawString(font, extra, left + width - extraWidth - 4, top + 4, 0x666666, false)
            }
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (button == 0) {
                entryList.setSelected(this)

                // Double-click enters groups
                if (type == BrowserEntryType.GROUP) {
                    navigateToGroup(value)
                    return true
                }
                if (type == BrowserEntryType.PARENT) {
                    navigateUp()
                    return true
                }
            }
            return true
        }
    }

    inner class BrowserList(
        mc: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        itemHeight: Int,
        private val listLeft: Int
    ) : ObjectSelectionList<BrowserEntry>(mc, width, height, top, itemHeight) {

        override fun getRowLeft(): Int = listLeft
        override fun getRowWidth(): Int = this.width - 12

        public override fun addEntry(entry: BrowserEntry): Int = super.addEntry(entry)

        public override fun clearEntries() {
            super.clearEntries()
        }

        override fun setSelected(entry: BrowserEntry?) {
            super.setSelected(entry)
            selectedEntry = entry
            selectButton.active = entry != null
        }
    }
}
