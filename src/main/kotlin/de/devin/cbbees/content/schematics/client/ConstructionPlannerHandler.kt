package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.simibubi.create.CreateClient
import com.simibubi.create.foundation.gui.AllGuiTextures
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.network.SelectSchematicPacket
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * Client-side handler for the Construction Planner's inline schematic selector HUD.
 *
 * When the player holds a Construction Planner without a schematic loaded,
 * this renders a selector above the hotbar. Alt+Scroll cycles through
 * available schematics; right-click confirms the selection.
 */
@OnlyIn(Dist.CLIENT)
object ConstructionPlannerHandler {

    private var schematics: List<String> = emptyList()
    var selectedIndex = 0
        private set
    private var lastRefreshTick = 0L
    private const val REFRESH_INTERVAL = 40L // 2 seconds

    /**
     * Active when the player holds a Construction Planner with no schematic loaded.
     */
    fun isActive(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val stack = player.mainHandItem
        return AllItems.CONSTRUCTION_PLANNER.isIn(stack) && !ConstructionPlannerItem.hasSchematic(stack)
    }

    fun tick() {
        if (!isActive()) {
            if (schematics.isNotEmpty()) {
                schematics = emptyList()
                selectedIndex = 0
            }
            return
        }

        val tick = Minecraft.getInstance().level?.gameTime ?: 0L
        if (schematics.isEmpty() || tick - lastRefreshTick >= REFRESH_INTERVAL) {
            refreshSchematics()
            lastRefreshTick = tick
        }
    }

    private fun refreshSchematics() {
        CreateClient.SCHEMATIC_SENDER.refresh()
        schematics = CreateClient.SCHEMATIC_SENDER.availableSchematics.map { it.string }
        if (selectedIndex >= schematics.size) {
            selectedIndex = maxOf(0, schematics.size - 1)
        }
    }

    /**
     * Called from scroll events. Cycles the selected schematic when Alt is held.
     * @return true if the scroll was consumed
     */
    fun onScroll(delta: Double): Boolean {
        if (!isActive()) return false
        if (!isAltDown()) return false
        if (schematics.isEmpty()) return false

        selectedIndex = if (delta > 0) {
            (selectedIndex - 1 + schematics.size) % schematics.size
        } else {
            (selectedIndex + 1) % schematics.size
        }
        return true
    }

    /**
     * Confirms the current selection and sends the packet to the server.
     * Called from [ConstructionPlannerItem.use].
     *
     * The schematic file must already exist on the server (uploaded previously
     * via Create's Schematic Table). We do NOT call
     * `CreateClient.SCHEMATIC_SENDER.startNewUpload()` because that requires
     * a SchematicTableMenu to be open.
     *
     * @return true if a schematic was selected
     */
    fun confirmSelection(): Boolean {
        if (schematics.isEmpty() || selectedIndex >= schematics.size) return false

        val name = schematics[selectedIndex]

        // Tell server to set schematic data on the planner item
        PacketDistributor.sendToServer(SelectSchematicPacket(name))

        Minecraft.getInstance().player?.displayClientMessage(
            Component.translatable("gui.cbbees.construction_planner.selected", name.removeSuffix(".nbt"))
                .withStyle { it.withColor(0xFFFF00) },
            true
        )

        return true
    }

    fun renderHUD(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        if (!isActive()) return

        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.screen != null) return

        val screenWidth = guiGraphics.guiWidth()
        val screenHeight = guiGraphics.guiHeight()
        val gray = AllGuiTextures.HUD_BACKGROUND
        val centerX = screenWidth / 2

        // Push forward in Z to render on top of chat
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0f, 0f, 200f)

        if (schematics.isEmpty()) {
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

        // Schematic name (without .nbt extension)
        val name = schematics[selectedIndex].removeSuffix(".nbt")
        val nameComp = Component.literal(name)
        val leftArrow = "< "
        val rightArrow = " >"
        val hintText = Component.translatable("gui.cbbees.construction_planner.hint")
        val counterText = Component.literal("${selectedIndex + 1}/${schematics.size}")

        val nameWidth = mc.font.width(nameComp)
        val leftW = mc.font.width(leftArrow)
        val rightW = mc.font.width(rightArrow)
        val nameLineW = leftW + nameWidth + rightW
        val hintWidth = mc.font.width(hintText)

        val bgWidth = maxOf(nameLineW, hintWidth) + 30
        val bgHeight = 30
        val bgX = centerX - bgWidth / 2
        val bgY = screenHeight - 80

        RenderSystem.enableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.75f)
        guiGraphics.blit(
            gray.location, bgX, bgY,
            gray.startX.toFloat(), gray.startY.toFloat(),
            bgWidth, bgHeight, gray.width, gray.height
        )
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        // Name row: < SchematicName >
        val nameY = bgY + 5
        var drawX = centerX - nameLineW / 2
        guiGraphics.drawString(mc.font, leftArrow, drawX, nameY, 0x999999, false)
        drawX += leftW
        guiGraphics.drawString(mc.font, nameComp, drawX, nameY, 0xFFFF00, false)
        drawX += nameWidth
        guiGraphics.drawString(mc.font, rightArrow, drawX, nameY, 0x999999, false)

        // Counter top-right
        val counterWidth = mc.font.width(counterText)
        guiGraphics.drawString(mc.font, counterText, bgX + bgWidth - counterWidth - 6, nameY, 0x666666, false)

        // Hint row
        val hintY = bgY + 18
        guiGraphics.drawString(mc.font, hintText, centerX - hintWidth / 2, hintY, 0xAAAAAA, false)

        RenderSystem.disableBlend()
        guiGraphics.pose().popPose()
    }

    private fun isAltDown(): Boolean {
        return Minecraft.getInstance().window?.let { window ->
            GLFW.glfwGetKey(window.window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window.window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS
        } ?: false
    }
}
