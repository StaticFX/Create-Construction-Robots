package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import de.devin.cbbees.network.SelectSchematicPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Screen for selecting a schematic file to load into the Construction Planner.
 * Lists available `.nbt` schematics from the local schematics folder.
 */
@OnlyIn(Dist.CLIENT)
class ConstructionPlannerScreen : Screen(Component.translatable("gui.cbbees.construction_planner.title")) {

    private lateinit var schematicList: SchematicSelectionList
    private lateinit var selectButton: Button
    private var selectedSchematic: String? = null

    override fun init() {
        super.init()

        val listWidth = 220
        val listLeft = (width - listWidth) / 2

        // Schematic list
        schematicList = SchematicSelectionList(
            minecraft!!, listWidth, height - 80, 30, 18, listLeft
        )
        addWidget(schematicList)

        // Load available schematics
        CreateClient.SCHEMATIC_SENDER.refresh()
        val available = CreateClient.SCHEMATIC_SENDER.availableSchematics
        for (entry in available) {
            schematicList.addEntry(SchematicEntry(entry.string))
        }

        // Select button
        selectButton = Button.builder(Component.translatable("gui.cbbees.construction_planner.select")) {
            onSelect()
        }.bounds(width / 2 - 105, height - 40, 100, 20).build()
        selectButton.active = false
        addRenderableWidget(selectButton)

        // Cancel button
        val cancelButton = Button.builder(Component.translatable("gui.done")) {
            onClose()
        }.bounds(width / 2 + 5, height - 40, 100, 20).build()
        addRenderableWidget(cancelButton)
    }

    private fun onSelect() {
        val name = selectedSchematic ?: return

        // Start upload (Create handles deduplication if already uploaded)
        CreateClient.SCHEMATIC_SENDER.startNewUpload(name)

        // Tell server to set schematic data on the planner item
        PacketDistributor.sendToServer(SelectSchematicPacket(name))

        onClose()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        schematicList.render(guiGraphics, mouseX, mouseY, partialTick)

        // Title
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF)

        // Empty state
        if (schematicList.children().isEmpty()) {
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

    /**
     * Scrollable list of schematic file names.
     */
    inner class SchematicSelectionList(
        mc: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        itemHeight: Int,
        private val listLeft: Int
    ) : ObjectSelectionList<SchematicEntry>(mc, width, height, top, itemHeight) {

        override fun getRowLeft(): Int = listLeft
        override fun getRowWidth(): Int = this.width - 12

        public override fun addEntry(entry: SchematicEntry): Int {
            return super.addEntry(entry)
        }

        override fun setSelected(entry: SchematicEntry?) {
            super.setSelected(entry)
            selectedSchematic = entry?.name
            selectButton.active = entry != null
        }
    }

    /**
     * Single entry in the schematic list.
     */
    inner class SchematicEntry(val name: String) :
        ObjectSelectionList.Entry<SchematicEntry>() {

        override fun getNarration(): Component = Component.literal(name)

        override fun render(
            guiGraphics: GuiGraphics, index: Int,
            top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float
        ) {
            val displayName = name.removeSuffix(".nbt")
            val selected = schematicList.selected === this
            val color = if (selected) 0xFFFF00 else if (hovered) 0xFFFFFF else 0xCCCCCC
            guiGraphics.drawString(font, displayName, left + 2, top + 2, color, false)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            schematicList.setSelected(this)
            return true
        }
    }
}
