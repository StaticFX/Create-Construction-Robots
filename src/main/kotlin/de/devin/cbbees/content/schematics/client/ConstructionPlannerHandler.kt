package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.simibubi.create.AllDataComponents
import com.simibubi.create.CreateClient
import com.simibubi.create.foundation.gui.AllGuiTextures
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.network.InstantConstructionPacket
import de.devin.cbbees.network.SelectSchematicPacket
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * Client-side handler for the Construction Planner's inline schematic selector HUD.
 *
 * When the player holds a Construction Planner without a schematic loaded,
 * this renders a selector above the hotbar with group navigation support.
 * Alt+Scroll cycles through schematics/groups.
 *
 * When a schematic is hovered, its data is set on the client-side ItemStack
 * so Create's [com.simibubi.create.content.schematics.client.SchematicHandler]
 * activates with the full deployed experience — blue bounding box, ghost blocks,
 * and positioning/rotation tools. Scrolling to a different schematic swaps it live.
 *
 * Construction via R key or CONSTRUCT tool syncs the filename to the server
 * before dispatching the build job.
 */
@OnlyIn(Dist.CLIENT)
object ConstructionPlannerHandler {

    /** Represents an item in the HUD list — either a group folder or a schematic file. */
    sealed class HudEntry {
        data class GroupEntry(val name: String, val fullPath: String) : HudEntry()
        data class SchematicEntry(val filename: String) : HudEntry()
    }

    private var allSchematics: List<String> = emptyList()
    private var currentItems: List<HudEntry> = emptyList()
    var selectedIndex = 0
        private set

    /** Current group navigation path. Empty string = root. */
    var currentGroupPath: String = ""
        private set

    /**
     * True while browsing schematics with a live preview set on the client item.
     * Accessible from Java mixins via `ConstructionPlannerHandler.INSTANCE.isBrowsingPreview()`.
     */
    var isBrowsingPreview = false
        private set

    /** Filename currently set on the client item for preview. */
    private var browsingFilename: String? = null

    private var lastRefreshTick = 0L
    private const val REFRESH_INTERVAL = 40L // 2 seconds

    /**
     * Our HUD is active when the player holds a Construction Planner that is
     * either empty or in browsing state (has file, not yet deployed).
     * State 3 (deployed) is fully owned by Create — our HUD hides.
     */
    fun isActive(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val stack = player.mainHandItem
        if (!AllItems.CONSTRUCTION_PLANNER.isIn(stack)) return false
        // State 3: deployed → Create owns it, our HUD is inactive
        if (stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false)) return false
        // State 1 (empty) or State 2 (has file, not deployed)
        return true
    }

    fun tick() {
        val player = Minecraft.getInstance().player
        val stack = player?.mainHandItem

        // Not holding a planner — just pause internal flags, preserve item data
        if (stack == null || !AllItems.CONSTRUCTION_PLANNER.isIn(stack)) {
            if (isBrowsingPreview) {
                isBrowsingPreview = false
                SchematicHoverPreview.clear()
            }
            return
        }

        // State 3: deployed → detect transition from browsing and sync to server
        if (stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false)) {
            if (isBrowsingPreview && browsingFilename != null) {
                // User deployed via Create's RMB → sync filename to server
                PacketDistributor.sendToServer(SelectSchematicPacket(browsingFilename!!))
                isBrowsingPreview = false
                browsingFilename = null
                SchematicHoverPreview.clear()
            }
            return
        }

        // State 2: has file but not deployed → restore browsing if needed
        val fileOnItem = stack.get(AllDataComponents.SCHEMATIC_FILE)
        if (fileOnItem != null && !isBrowsingPreview) {
            // Returning to planner that was in browsing state — restore
            browsingFilename = fileOnItem
            isBrowsingPreview = true
            SchematicHoverPreview.updatePreview(fileOnItem)
        }

        // State 2: keep preview data in sync
        if (isBrowsingPreview && browsingFilename != null) {
            ensurePreviewData()
        }

        // Refresh schematic list periodically
        val tick = Minecraft.getInstance().level?.gameTime ?: 0L
        if (allSchematics.isEmpty() || tick - lastRefreshTick >= REFRESH_INTERVAL) {
            refreshSchematics()
            lastRefreshTick = tick
        }
    }

    private fun reset() {
        currentItems = emptyList()
        allSchematics = emptyList()
        selectedIndex = 0
        currentGroupPath = ""
    }

    private fun refreshSchematics() {
        CreateClient.SCHEMATIC_SENDER.refresh()
        allSchematics = CreateClient.SCHEMATIC_SENDER.availableSchematics.map { it.string }

        SchematicGroupManager.ensureLoaded()
        SchematicGroupManager.reconcile(allSchematics)

        rebuildItems()
    }

    /** Rebuilds the HUD item list for the current group path. */
    private fun rebuildItems() {
        val (subgroups, schematics) = SchematicGroupManager.getItemsAtLevel(currentGroupPath, allSchematics)

        val items = mutableListOf<HudEntry>()
        for (group in subgroups) {
            val fullPath = if (currentGroupPath.isEmpty()) group else "$currentGroupPath/$group"
            items.add(HudEntry.GroupEntry(group, fullPath))
        }
        for (filename in schematics) {
            items.add(HudEntry.SchematicEntry(filename))
        }

        currentItems = items
        if (selectedIndex >= currentItems.size) {
            selectedIndex = maxOf(0, currentItems.size - 1)
        }

        updateBrowsingPreview()
    }

    /**
     * Sets or clears the browsing preview based on the current selection.
     * When a schematic is selected, sets its data on the client-side ItemStack
     * so Create's SchematicHandler activates with the full deployed experience.
     */
    private fun updateBrowsingPreview() {
        val entry = currentItems.getOrNull(selectedIndex)
        if (entry is HudEntry.SchematicEntry) {
            setBrowsingPreview(entry.filename)
            SchematicHoverPreview.updatePreview(entry.filename)
        } else {
            clearBrowsingPreview()
            SchematicHoverPreview.updatePreview(null)
        }
    }

    /**
     * Sets schematic data on the client-side ItemStack for hover preview.
     * DEPLOYED = false so Create's SchematicHandler enters the pre-deploy state:
     * blue bounding box follows the crosshair, only DEPLOY tool available.
     * The user right-clicks to deploy (Create handles the transition to full tools).
     */
    private fun setBrowsingPreview(filename: String) {
        val player = Minecraft.getInstance().player ?: return
        val stack = player.mainHandItem
        if (!AllItems.CONSTRUCTION_PLANNER.isIn(stack)) return

        val previousFile = browsingFilename
        browsingFilename = filename
        isBrowsingPreview = true

        // Only reset when switching to a different schematic
        if (previousFile != filename) {
            stack.set(AllDataComponents.SCHEMATIC_FILE, filename)
            stack.set(AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
            stack.set(AllDataComponents.SCHEMATIC_DEPLOYED, false)
            stack.set(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO)
            stack.set(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE)
            stack.set(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE)

            // Write bounds so Create can show the blue bounding box
            try {
                com.simibubi.create.content.schematics.SchematicItem.writeSize(player.level(), stack)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Re-applies SCHEMATIC_FILE if a server inventory sync cleared it.
     * Does NOT touch deployed/anchor/rotation/mirror since Create manages those.
     */
    private fun ensurePreviewData() {
        val player = Minecraft.getInstance().player ?: return
        val stack = player.mainHandItem
        if (!AllItems.CONSTRUCTION_PLANNER.isIn(stack)) return

        val filename = browsingFilename ?: return
        val currentFile = stack.get(AllDataComponents.SCHEMATIC_FILE)

        if (currentFile != filename) {
            stack.set(AllDataComponents.SCHEMATIC_FILE, filename)
            stack.set(AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
        }
    }

    /**
     * Clears browsing preview — removes schematic data from the client-side
     * ItemStack so Create's SchematicHandler deactivates.
     * Called from Java mixins via `ConstructionPlannerHandler.INSTANCE.clearBrowsingPreview()`.
     */
    fun clearBrowsingPreview() {
        if (!isBrowsingPreview) return
        isBrowsingPreview = false
        browsingFilename = null
        SchematicHoverPreview.clear()

        val player = Minecraft.getInstance().player ?: return

        // Clear browsing preview data from the planner wherever it is in the inventory.
        // Only clear planners that are NOT deployed (deployed = user committed to selection).
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (AllItems.CONSTRUCTION_PLANNER.isIn(stack)
                && ConstructionPlannerItem.hasSchematic(stack)
                && !stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false)
            ) {
                ConstructionPlannerItem.clearSchematic(stack)
            }
        }
    }

    /**
     * Called from scroll events. Cycles through items when Alt is held.
     * @return true if the scroll was consumed
     */
    fun onScroll(delta: Double): Boolean {
        if (!isActive()) return false
        if (!isAltDown()) return false
        if (currentItems.isEmpty()) return false

        selectedIndex = if (delta > 0) {
            (selectedIndex - 1 + currentItems.size) % currentItems.size
        } else {
            (selectedIndex + 1) % currentItems.size
        }

        updateBrowsingPreview()
        return true
    }

    /**
     * Navigates into the selected group.
     * @return true if navigation occurred
     */
    fun onNavigateIn(): Boolean {
        val entry = currentItems.getOrNull(selectedIndex) ?: return false
        if (entry !is HudEntry.GroupEntry) return false

        currentGroupPath = entry.fullPath
        selectedIndex = 0
        rebuildItems()
        return true
    }

    /**
     * Navigates up one level in the group hierarchy.
     * @return true if navigation occurred (false if already at root)
     */
    fun onNavigateOut(): Boolean {
        if (currentGroupPath.isEmpty()) return false

        val lastSlash = currentGroupPath.lastIndexOf('/')
        currentGroupPath = if (lastSlash >= 0) currentGroupPath.substring(0, lastSlash) else ""
        selectedIndex = 0
        rebuildItems()
        return true
    }

    /**
     * Confirms the current selection.
     * For groups: navigates into the group.
     * For schematics: sets up the hover preview (DEPLOYED=false) so Create shows
     * the blue bounding box following the crosshair. Also syncs filename to server.
     */
    fun confirmSelection(): Boolean {
        val entry = currentItems.getOrNull(selectedIndex) ?: return false

        return when (entry) {
            is HudEntry.GroupEntry -> onNavigateIn()
            is HudEntry.SchematicEntry -> {
                // Set up hover state on client item
                setBrowsingPreview(entry.filename)
                // Sync filename to server
                PacketDistributor.sendToServer(SelectSchematicPacket(entry.filename))

                Minecraft.getInstance().player?.displayClientMessage(
                    Component.translatable(
                        "gui.cbbees.construction_planner.selected",
                        entry.filename.removeSuffix(".nbt")
                    ).withStyle { it.withColor(0xFFFF00) },
                    true
                )
                true
            }
        }
    }

    /**
     * Shift+RMB: instantly places and starts construction at the crosshair position.
     * @return true if the action was dispatched
     */
    fun instantConstruct(): Boolean {
        val entry = currentItems.getOrNull(selectedIndex) ?: return false
        if (entry !is HudEntry.SchematicEntry) return false

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false

        val hitResult = mc.hitResult
        val anchor = if (hitResult is BlockHitResult) {
            hitResult.blockPos.relative(hitResult.direction)
        } else {
            return false
        }

        val rotation = when ((player.yRot % 360 + 360) % 360) {
            in 45f..135f -> Rotation.CLOCKWISE_90
            in 135f..225f -> Rotation.CLOCKWISE_180
            in 225f..315f -> Rotation.COUNTERCLOCKWISE_90
            else -> Rotation.NONE
        }

        PacketDistributor.sendToServer(
            InstantConstructionPacket(entry.filename, anchor, rotation, Mirror.NONE)
        )

        player.displayClientMessage(
            Component.translatable("cbbees.construction.started_instant", entry.filename.removeSuffix(".nbt"))
                .withStyle { it.withColor(0x00FF00) },
            true
        )

        clearBrowsingPreview()
        return true
    }

    /** Returns the currently selected entry, if any. */
    fun getSelectedEntry(): HudEntry? = currentItems.getOrNull(selectedIndex)

    fun renderHUD(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        if (!isActive()) return

        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.screen != null) return

        val screenWidth = guiGraphics.guiWidth()
        val screenHeight = guiGraphics.guiHeight()
        val gray = AllGuiTextures.HUD_BACKGROUND
        val centerX = screenWidth / 2

        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0f, 0f, 200f)

        if (currentItems.isEmpty()) {
            val text = Component.translatable("gui.cbbees.construction_planner.no_schematics")
            val textWidth = mc.font.width(text)
            val bgWidth = textWidth + 20
            val bgX = centerX - bgWidth / 2
            val bgY = screenHeight - 75

            RenderSystem.enableBlend()
            RenderSystem.setShaderColor(1f, 1f, 1f, 0.75f)
            guiGraphics.blit(
                gray.location, bgX, bgY,
                gray.startX.toFloat(), gray.startY.toFloat(),
                bgWidth, 18, gray.width, gray.height
            )
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            guiGraphics.drawString(mc.font, text, centerX - textWidth / 2, bgY + 5, 0x888888, false)
            RenderSystem.disableBlend()
            guiGraphics.pose().popPose()
            return
        }

        val entry = currentItems[selectedIndex]
        val isGroup = entry is HudEntry.GroupEntry
        val displayName = when (entry) {
            is HudEntry.GroupEntry -> entry.name
            is HudEntry.SchematicEntry -> entry.filename.removeSuffix(".nbt")
        }
        val icon = if (isGroup) "\uD83D\uDCC1 " else ""
        val nameComp = Component.literal("$icon$displayName")
        val nameColor = if (isGroup) 0x88CCFF else 0xFFFF00

        val breadcrumb = buildBreadcrumb()
        val breadcrumbComp = Component.literal(breadcrumb).withStyle { it.withColor(0x888888) }

        val leftArrow = "< "
        val rightArrow = " >"
        val hintText = Component.translatable("gui.cbbees.construction_planner.hint_groups")
        val counterText = Component.literal("${selectedIndex + 1}/${currentItems.size}")

        val nameWidth = mc.font.width(nameComp)
        val leftW = mc.font.width(leftArrow)
        val rightW = mc.font.width(rightArrow)
        val nameLineW = leftW + nameWidth + rightW
        val hintWidth = mc.font.width(hintText)
        val breadcrumbWidth = mc.font.width(breadcrumbComp)

        val bgWidth = maxOf(nameLineW, hintWidth, breadcrumbWidth) + 30
        val bgHeight = if (currentGroupPath.isEmpty()) 30 else 42
        val bgX = centerX - bgWidth / 2
        val bgY = screenHeight - if (currentGroupPath.isEmpty()) 80 else 92

        RenderSystem.enableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.75f)
        guiGraphics.blit(
            gray.location, bgX, bgY,
            gray.startX.toFloat(), gray.startY.toFloat(),
            bgWidth, bgHeight, gray.width, gray.height
        )
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        var yOffset = bgY + 5

        if (currentGroupPath.isNotEmpty()) {
            guiGraphics.drawString(mc.font, breadcrumbComp, centerX - breadcrumbWidth / 2, yOffset, 0x888888, false)
            yOffset += 12
        }

        var drawX = centerX - nameLineW / 2
        guiGraphics.drawString(mc.font, leftArrow, drawX, yOffset, 0x999999, false)
        drawX += leftW
        guiGraphics.drawString(mc.font, nameComp, drawX, yOffset, nameColor, false)
        drawX += nameWidth
        guiGraphics.drawString(mc.font, rightArrow, drawX, yOffset, 0x999999, false)

        val counterWidth = mc.font.width(counterText)
        guiGraphics.drawString(mc.font, counterText, bgX + bgWidth - counterWidth - 6, yOffset, 0x666666, false)

        yOffset += 13
        guiGraphics.drawString(mc.font, hintText, centerX - hintWidth / 2, yOffset, 0xAAAAAA, false)

        RenderSystem.disableBlend()
        guiGraphics.pose().popPose()
    }

    private fun buildBreadcrumb(): String {
        if (currentGroupPath.isEmpty()) return "Root"
        val parts = currentGroupPath.split("/")
        return "Root > " + parts.joinToString(" > ")
    }

    private fun isAltDown(): Boolean {
        return Minecraft.getInstance().window.let { window ->
            GLFW.glfwGetKey(window.window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
                    GLFW.glfwGetKey(window.window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS
        }
    }
}
