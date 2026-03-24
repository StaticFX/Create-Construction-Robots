package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import de.devin.cbbees.content.schematics.external.ExternalSchematicSource
import de.devin.cbbees.content.schematics.external.RemoteSchematic
import de.devin.cbbees.content.schematics.external.SchematicListResult
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

/**
 * Full-screen card grid for browsing remote schematics.
 *
 * Shows a searchable, paginated grid of schematic cards with thumbnails.
 * Clicking a card opens [SchematicDetailScreen] with a full-screen 3D preview.
 */
@OnlyIn(Dist.CLIENT)
class BrowseOnlineScreen(
    private val source: ExternalSchematicSource,
    private val parentScreen: Screen?
) : Screen(Component.translatable("gui.cbbees.browse_online.title")) {

    private lateinit var searchBox: EditBox
    private lateinit var prevPageButton: Button
    private lateinit var nextPageButton: Button

    private var currentPage = 0
    private var currentQuery = ""
    private var lastResult: SchematicListResult? = null
    private var schematics: List<RemoteSchematic> = emptyList()
    private var loading = false
    private var statusMessage: Component? = null

    // Shared byte cache so detail screen doesn't re-download
    internal val bytesCache = mutableMapOf<String, ByteArray>()
    internal val savedFiles = mutableMapOf<String, String>()

    // Debounce
    private var searchDebounceTimer = 0
    private var pendingSearchQuery: String? = null
    private var fetchCooldownTimer = 0

    // Grid layout
    private var scrollOffset = 0f
    private var maxScroll = 0f
    private var gridLeft = 0
    private var gridTop = 0
    private var gridWidth = 0
    private var gridHeight = 0
    private var cols = 1
    private var gridOffsetX = 0
    private var hoveredIndex = -1

    companion object {
        private const val MARGIN = 10
        private const val CARD_W = 130
        private const val CARD_H = 100
        private const val CARD_GAP = 8
        private const val THUMB_H = 62
    }

    override fun init() {
        super.init()

        gridLeft = MARGIN
        gridTop = 50
        gridWidth = width - MARGIN * 2
        gridHeight = height - gridTop - 40

        cols = ((gridWidth + CARD_GAP) / (CARD_W + CARD_GAP)).coerceAtLeast(1)
        val usedWidth = cols * CARD_W + (cols - 1) * CARD_GAP
        gridOffsetX = (gridWidth - usedWidth) / 2

        // Search box — centered at top
        val searchW = (width * 0.5).toInt().coerceAtMost(250)
        val searchX = (width - searchW) / 2
        searchBox = EditBox(font, searchX, 28, searchW, 16, Component.translatable("gui.cbbees.construction_planner.search"))
        searchBox.setMaxLength(100)
        searchBox.value = currentQuery
        searchBox.setResponder { query ->
            if (query != currentQuery) {
                pendingSearchQuery = query
                searchDebounceTimer = 15
            }
        }
        addRenderableWidget(searchBox)

        // Bottom buttons
        val buttonY = height - 30
        prevPageButton = Button.builder(Component.literal("<")) {
            if (currentPage > 0 && fetchCooldownTimer <= 0) {
                currentPage--
                fetchCooldownTimer = 10
                fetchSchematics()
            }
        }.bounds(MARGIN, buttonY, 20, 20).build()
        prevPageButton.active = false
        addRenderableWidget(prevPageButton)

        nextPageButton = Button.builder(Component.literal(">")) {
            val result = lastResult
            if (result != null && result.hasNextPage && fetchCooldownTimer <= 0) {
                currentPage++
                fetchCooldownTimer = 10
                fetchSchematics()
            }
        }.bounds(MARGIN + 26, buttonY, 20, 20).build()
        nextPageButton.active = false
        addRenderableWidget(nextPageButton)

        val backButton = Button.builder(Component.translatable("gui.back")) {
            minecraft?.setScreen(parentScreen)
        }.bounds(width - MARGIN - 60, buttonY, 60, 20).build()
        addRenderableWidget(backButton)

        fetchSchematics()
    }

    override fun tick() {
        super.tick()
        if (searchDebounceTimer > 0) {
            searchDebounceTimer--
            if (searchDebounceTimer == 0 && pendingSearchQuery != null) {
                currentQuery = pendingSearchQuery!!
                pendingSearchQuery = null
                currentPage = 0
                fetchSchematics()
            }
        }
        if (fetchCooldownTimer > 0) fetchCooldownTimer--
    }

    private fun fetchSchematics() {
        loading = true
        statusMessage = null
        scrollOffset = 0f
        hoveredIndex = -1

        source.listSchematics(currentQuery, currentPage).thenAccept { result ->
            Minecraft.getInstance().execute {
                lastResult = result
                schematics = result.schematics
                loading = false
                prevPageButton.active = result.hasPreviousPage
                nextPageButton.active = result.hasNextPage
                recalcScroll()

                // Start loading thumbnails
                for (s in schematics) {
                    s.thumbnailUrl?.let { ThumbnailCache.load(it) }
                }
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

    private fun recalcScroll() {
        val rows = (schematics.size + cols - 1) / cols
        val totalH = rows * (CARD_H + CARD_GAP) - CARD_GAP
        maxScroll = (totalH - gridHeight).coerceAtLeast(0).toFloat()
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
        val usedWidth = cols * CARD_W + (cols - 1) * CARD_GAP
        gridOffsetX = (gridWidth - usedWidth) / 2
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // Title + attribution
        guiGraphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF)
        val attribution = Component.translatable("gui.cbbees.browse_online.attribution", source.name)
            .withStyle(ChatFormatting.GREEN)
        guiGraphics.drawCenteredString(font, attribution, width / 2, 17, 0x666666)

        if (loading) {
            guiGraphics.drawCenteredString(
                font, Component.translatable("gui.cbbees.browse_online.loading"),
                width / 2, gridTop + gridHeight / 2, 0xAAAAAA
            )
            return
        }

        if (schematics.isEmpty() && lastResult != null) {
            guiGraphics.drawCenteredString(
                font, Component.translatable("gui.cbbees.browse_online.no_results"),
                width / 2, gridTop + gridHeight / 2, 0x888888
            )
        }

        // Determine hovered card
        hoveredIndex = -1
        if (mouseX >= gridLeft && mouseX < gridLeft + gridWidth
            && mouseY >= gridTop && mouseY < gridTop + gridHeight
        ) {
            val relX = mouseX - gridLeft - gridOffsetX
            val relY = mouseY - gridTop + scrollOffset
            if (relX >= 0) {
                val col = relX / (CARD_W + CARD_GAP)
                val row = (relY / (CARD_H + CARD_GAP)).toInt()
                if (col < cols && relX % (CARD_W + CARD_GAP) < CARD_W
                    && relY % (CARD_H + CARD_GAP) < CARD_H
                ) {
                    val idx = row * cols + col
                    if (idx in schematics.indices) hoveredIndex = idx
                }
            }
        }

        // Render card grid
        guiGraphics.enableScissor(gridLeft, gridTop, gridLeft + gridWidth, gridTop + gridHeight)

        for (i in schematics.indices) {
            val col = i % cols
            val row = i / cols
            val cardX = gridLeft + gridOffsetX + col * (CARD_W + CARD_GAP)
            val cardY = gridTop + row * (CARD_H + CARD_GAP) - scrollOffset.toInt()

            if (cardY + CARD_H < gridTop || cardY > gridTop + gridHeight) continue

            renderCard(guiGraphics, schematics[i], cardX, cardY, i == hoveredIndex)
        }

        guiGraphics.disableScissor()

        // Scrollbar
        if (maxScroll > 0) {
            val trackX = gridLeft + gridWidth - 3
            val thumbFraction = gridHeight.toFloat() / (gridHeight + maxScroll)
            val thumbH = (gridHeight * thumbFraction).toInt().coerceAtLeast(10)
            val thumbY = gridTop + ((scrollOffset / maxScroll) * (gridHeight - thumbH)).toInt()
            guiGraphics.fill(trackX, gridTop, trackX + 2, gridTop + gridHeight, 0x30FFFFFF)
            guiGraphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x80FFFFFF.toInt())
        }

        // Page indicator
        val result = lastResult
        if (result != null && result.totalPages > 0) {
            val pageText = Component.translatable("gui.cbbees.browse_online.page", currentPage + 1, result.totalPages)
            guiGraphics.drawString(font, pageText, MARGIN + 54, height - 24, 0x888888, false)
        }

        // Status
        statusMessage?.let {
            guiGraphics.drawCenteredString(font, it, width / 2, height - 10, 0xFFFFFF)
        }
    }

    private fun renderCard(guiGraphics: GuiGraphics, schematic: RemoteSchematic, x: Int, y: Int, hovered: Boolean) {
        // Card background
        val bgColor = if (hovered) 0x50FFFFFF else 0x30FFFFFF
        guiGraphics.fill(x, y, x + CARD_W, y + CARD_H, bgColor)

        // Thumbnail area
        val thumbUrl = schematic.thumbnailUrl
        val thumb = thumbUrl?.let { ThumbnailCache.get(it) }
        if (thumb != null) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            // Set textureWidth/Height = render width/height so UVs span 0..1, rendering the full image scaled to fit
            guiGraphics.blit(thumb.location, x, y, 0f, 0f, CARD_W, THUMB_H, CARD_W, THUMB_H)
        } else {
            guiGraphics.fill(x + 1, y + 1, x + CARD_W - 1, y + THUMB_H - 1, 0x40000000)
            if (thumbUrl != null && ThumbnailCache.isLoading(thumbUrl)) {
                guiGraphics.drawCenteredString(font, "...", x + CARD_W / 2, y + THUMB_H / 2 - 4, 0x555555)
            }
        }

        // Divider line
        guiGraphics.fill(x, y + THUMB_H, x + CARD_W, y + THUMB_H + 1, 0x40FFFFFF)

        // Name (truncated)
        val textX = x + 4
        val textY = y + THUMB_H + 3
        val maxTextW = CARD_W - 8
        val name = truncate(schematic.name, maxTextW)
        val nameColor = if (hovered) 0xFFFF55 else 0xFFFFFF
        guiGraphics.drawString(font, name, textX, textY, nameColor, false)

        // Author + size
        val subtitle = truncate("${schematic.author} · ${schematic.sizeX}x${schematic.sizeY}x${schematic.sizeZ}", maxTextW)
        guiGraphics.drawString(font, subtitle, textX, textY + 11, 0x888888, false)

        // Border on hover
        if (hovered) {
            guiGraphics.renderOutline(x, y, CARD_W, CARD_H, 0xFFFFFF55.toInt())
        }
    }

    private fun truncate(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        var s = text
        while (s.isNotEmpty() && font.width("$s..") > maxWidth) s = s.dropLast(1)
        return "$s.."
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(guiGraphics)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchBox.isFocused) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers)
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseY >= gridTop && mouseY < gridTop + gridHeight) {
            scrollOffset = (scrollOffset - scrollY.toFloat() * 20f).coerceIn(0f, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && hoveredIndex in schematics.indices) {
            minecraft?.setScreen(SchematicDetailScreen(source, this, schematics[hoveredIndex], bytesCache, savedFiles))
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun removed() {
        super.removed()
        ThumbnailCache.clear()
    }
}
