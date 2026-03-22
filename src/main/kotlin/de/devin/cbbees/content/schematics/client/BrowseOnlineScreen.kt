package de.devin.cbbees.content.schematics.client

import de.devin.cbbees.content.schematics.external.ExternalSchematicSource
import de.devin.cbbees.content.schematics.external.RemoteSchematic
import de.devin.cbbees.content.schematics.external.SchematicListResult
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

/**
 * Full-screen browser for downloading schematics from an [ExternalSchematicSource].
 *
 * Displays a searchable, paginated list of remote schematics with details.
 * Users can download schematics directly into their local schematics folder.
 */
@OnlyIn(Dist.CLIENT)
class BrowseOnlineScreen(
    private val source: ExternalSchematicSource,
    private val parentScreen: Screen?
) : Screen(Component.translatable("gui.cbbees.browse_online.title")) {

    private lateinit var searchBox: EditBox
    private lateinit var entryList: SchematicList
    private lateinit var downloadButton: Button
    private lateinit var prevPageButton: Button
    private lateinit var nextPageButton: Button

    private var selectedEntry: SchematicEntry? = null
    private var currentPage = 0
    private var currentQuery = ""
    private var lastResult: SchematicListResult? = null
    private var loading = false
    private var downloadingId: String? = null
    private var statusMessage: Component? = null

    private val MARGIN = 8
    private val GAP = 6

    private var listLeft = 0
    private var listWidth = 0
    private var detailLeft = 0
    private var detailWidth = 0
    private var panelTop = 0
    private var panelHeight = 0

    override fun init() {
        super.init()

        // Two-panel layout: list (50%) | details (50%)
        val totalWidth = width - MARGIN * 2
        listWidth = (totalWidth * 0.5).toInt()
        detailWidth = totalWidth - listWidth - GAP

        listLeft = MARGIN
        detailLeft = listLeft + listWidth + GAP
        panelTop = 50
        panelHeight = height - panelTop - 50

        // Search box
        searchBox = EditBox(
            font, listLeft, 28, listWidth, 16,
            Component.translatable("gui.cbbees.construction_planner.search")
        )
        searchBox.setMaxLength(100)
        searchBox.setResponder { query ->
            if (query != currentQuery) {
                currentQuery = query
                currentPage = 0
                fetchSchematics()
            }
        }
        addRenderableWidget(searchBox)

        // Entry list
        entryList = SchematicList(minecraft!!, listWidth + MARGIN, panelHeight, panelTop, 28, listLeft)
        addWidget(entryList)

        // Bottom buttons
        val buttonY = height - 36
        val buttonWidth = 90

        val downloadButtonX = (totalWidth - buttonWidth - buttonWidth + GAP)

        downloadButton = Button.builder(Component.translatable("gui.cbbees.browse_online.download")) {
            onDownload()
        }.bounds(downloadButtonX, buttonY, buttonWidth, 20).build()
        downloadButton.active = false
        addRenderableWidget(downloadButton)

        prevPageButton = Button.builder(Component.literal("<")) {
            if (currentPage > 0) {
                currentPage--
                fetchSchematics()
            }
        }.bounds(listLeft, buttonY, 20, 20).build()
        prevPageButton.active = false
        addRenderableWidget(prevPageButton)

        nextPageButton = Button.builder(Component.literal(">")) {
            val result = lastResult
            if (result != null && result.hasNextPage) {
                currentPage++
                fetchSchematics()
            }
        }.bounds(listLeft + 28, buttonY, 20, 20).build()
        nextPageButton.active = false
        addRenderableWidget(nextPageButton)

        val backButtonX = (totalWidth - buttonWidth + MARGIN)


        val backButton = Button.builder(Component.translatable("gui.back")) {
            minecraft?.setScreen(parentScreen)
        }.bounds(backButtonX, buttonY, buttonWidth, 20).build()
        addRenderableWidget(backButton)

        fetchSchematics()
    }

    private fun fetchSchematics() {
        loading = true
        statusMessage = null
        selectedEntry = null
        downloadButton.active = false

        source.listSchematics(currentQuery, currentPage).thenAccept { result ->
            Minecraft.getInstance().execute {
                lastResult = result
                loading = false
                rebuildList(result)
            }
        }.exceptionally { ex ->
            Minecraft.getInstance().execute {
                loading = false
                statusMessage = Component.literal("Failed to load: ${ex.message}")
                    .withStyle(ChatFormatting.RED)
            }
            null
        }
    }

    private fun rebuildList(result: SchematicListResult) {
        entryList.clearEntries()
        for (schematic in result.schematics) {
            entryList.addEntry(SchematicEntry(schematic))
        }
        prevPageButton.active = result.hasPreviousPage
        nextPageButton.active = result.hasNextPage
    }

    private fun onDownload() {
        val entry = selectedEntry ?: return
        if (downloadingId != null) return

        downloadingId = entry.schematic.id
        downloadButton.active = false
        statusMessage = Component.translatable("gui.cbbees.browse_online.downloading")
            .withStyle(ChatFormatting.YELLOW)

        source.download(entry.schematic).thenAccept { localFilename ->
            Minecraft.getInstance().execute {
                downloadingId = null
                statusMessage = Component.translatable("gui.cbbees.browse_online.downloaded", localFilename)
                    .withStyle(ChatFormatting.GREEN)
                downloadButton.active = selectedEntry != null
            }
        }.exceptionally { ex ->
            Minecraft.getInstance().execute {
                downloadingId = null
                statusMessage = Component.literal("Download failed: ${ex.message}")
                    .withStyle(ChatFormatting.RED)
                downloadButton.active = selectedEntry != null
            }
            null
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        entryList.render(guiGraphics, mouseX, mouseY, partialTick)

        // Title
        guiGraphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF)

        // Attribution
        val attribution = Component.translatable("gui.cbbees.browse_online.attribution", source.name)
            .withStyle(ChatFormatting.GREEN)
        guiGraphics.drawCenteredString(font, attribution, width / 2, 17, 0x666666)

        // Loading indicator
        if (loading) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.cbbees.browse_online.loading"),
                listLeft + listWidth / 2, panelTop + 40, 0xAAAAAA
            )
        }

        // Empty results
        if (!loading && entryList.children().isEmpty() && lastResult != null) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.cbbees.browse_online.no_results"),
                listLeft + listWidth / 2, panelTop + 40, 0x888888
            )
        }

        // Detail panel
        renderDetailPanel(guiGraphics)

        // Page indicator
        val result = lastResult
        if (result != null && result.totalCount > 0) {
            val pageText = Component.translatable(
                "gui.cbbees.browse_online.page",
                currentPage + 1,
                (result.totalCount + result.pageSize - 1) / result.pageSize
            )
            guiGraphics.drawString(
                font,
                pageText,
                listLeft + 52,
                height - 30,
                0x888888,
                false
            )
        }

        // Status message
        statusMessage?.let {
            guiGraphics.drawCenteredString(font, it, width / 2, height - 14, 0xFFFFFF)
        }
    }

    private fun renderDetailPanel(guiGraphics: GuiGraphics) {
        // Panel background
        guiGraphics.fill(
            detailLeft - 1, panelTop - 1,
            detailLeft + detailWidth + 1, panelTop + panelHeight + 1, 0x40FFFFFF
        )
        guiGraphics.fill(
            detailLeft, panelTop,
            detailLeft + detailWidth, panelTop + panelHeight, 0xC0101010.toInt()
        )

        val entry = selectedEntry
        if (entry == null) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.cbbees.browse_online.select_hint"),
                detailLeft + detailWidth / 2,
                panelTop + panelHeight / 2 - 4,
                0x666666
            )
            return
        }

        val s = entry.schematic
        val x = detailLeft + 8
        var y = panelTop + 8

        // Name
        guiGraphics.drawString(font, s.name, x, y, 0xFFFF00, false)
        y += 14

        // Author
        val authorText = Component.translatable("gui.cbbees.browse_online.author", s.author)
        guiGraphics.drawString(font, authorText, x, y, 0xAAAAAA, false)
        y += 12

        // Size
        val sizeText = "${s.sizeX} x ${s.sizeY} x ${s.sizeZ}"
        guiGraphics.drawString(font, sizeText, x, y, 0x888888, false)
        y += 12

        // Downloads
        val downloadsText = Component.translatable("gui.cbbees.browse_online.downloads", s.downloads)
        guiGraphics.drawString(font, downloadsText, x, y, 0x888888, false)
        y += 16

        // Description
        if (s.description.isNotEmpty()) {
            val lines = font.split(Component.literal(s.description), detailWidth - 16)
            for (line in lines) {
                guiGraphics.drawString(font, line, x, y, 0xCCCCCC, false)
                y += 10
            }
        }
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(guiGraphics)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchBox.isFocused) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers)
                    || super.keyPressed(keyCode, scanCode, modifiers)
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    // --- Entry ---

    inner class SchematicEntry(
        val schematic: RemoteSchematic
    ) : ObjectSelectionList.Entry<SchematicEntry>() {

        override fun getNarration(): Component = Component.literal(schematic.name)

        override fun render(
            guiGraphics: GuiGraphics, index: Int,
            top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float
        ) {
            val selected = entryList.selected === this

            if (selected) {
                guiGraphics.fill(left, top, left + width, top + height, 0x30FFFFFF)
            } else if (hovered) {
                guiGraphics.fill(left, top, left + width, top + height, 0x18FFFFFF)
            }

            val nameColor = if (selected) 0xFFFF00 else if (hovered) 0xFFFFFF else 0xCCCCCC
            guiGraphics.drawString(font, schematic.name, left + 2, top + 2, nameColor, false)

            val subtitle = "${schematic.author} - ${schematic.sizeX}x${schematic.sizeY}x${schematic.sizeZ}"
            guiGraphics.drawString(font, subtitle, left + 2, top + 14, 0x777777, false)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (button == 0) {
                entryList.setSelected(this)
            }
            return true
        }
    }

    inner class SchematicList(
        mc: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        itemHeight: Int,
        private val listLeft: Int
    ) : ObjectSelectionList<SchematicEntry>(mc, width, height, top, itemHeight) {

        override fun getRowLeft(): Int = listLeft
        override fun getRowWidth(): Int = this.width - 12

        public override fun addEntry(entry: SchematicEntry): Int = super.addEntry(entry)

        public override fun clearEntries() {
            super.clearEntries()
        }

        override fun setSelected(entry: SchematicEntry?) {
            super.setSelected(entry)
            selectedEntry = entry
            downloadButton.active = entry != null && downloadingId == null
        }
    }
}
