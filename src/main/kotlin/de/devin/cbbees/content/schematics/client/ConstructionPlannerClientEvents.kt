package de.devin.cbbees.content.schematics.client

import de.devin.cbbees.items.AllItems
import de.devin.cbbees.registry.AllKeys
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.lwjgl.glfw.GLFW

/**
 * Client-side event handler for the Construction Planner HUD.
 *
 * Registers event listeners for:
 * - Client tick: refreshes the schematic list, manages browsing preview
 * - Mouse scroll: Alt+Scroll cycles through schematics/groups
 * - Key input: Backspace for group navigation, keybind for full screen
 * - Render GUI: draws the schematic selector above the hotbar
 * - Render level: draws ghost blocks during browsing preview (state 2)
 */
object ConstructionPlannerClientEvents {

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        ConstructionPlannerHandler.tick()

        // Clear custom tool state if player is no longer holding the planner
        if (ConstructionToolState.activeTool != ConstructionToolState.CustomTool.NONE) {
            val player = Minecraft.getInstance().player
            if (player == null || !AllItems.CONSTRUCTION_PLANNER.isIn(player.mainHandItem)) {
                ConstructionToolState.activeTool = ConstructionToolState.CustomTool.NONE
            }
        }

        // Check keybind for opening full-screen browser
        if (AllKeys.OPEN_SCHEMATIC_BROWSER.consumeClick()) {
            val player = Minecraft.getInstance().player ?: return
            if (AllItems.CONSTRUCTION_PLANNER.isIn(player.mainHandItem)) {
                Minecraft.getInstance().setScreen(ConstructionPlannerScreen())
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onMouseScroll(event: InputEvent.MouseScrollingEvent) {
        if (ConstructionPlannerHandler.onScroll(event.scrollDeltaY)) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onKeyInput(event: InputEvent.Key) {
        if (!ConstructionPlannerHandler.isActive()) return

        // Backspace navigates up in group hierarchy
        if (event.key == GLFW.GLFW_KEY_BACKSPACE && event.action == GLFW.GLFW_PRESS) {
            if (ConstructionPlannerHandler.onNavigateOut()) {
                // Consumed
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onRenderGui(event: RenderGuiEvent.Post) {
        ConstructionPlannerHandler.renderHUD(event.guiGraphics, event.partialTick)
    }

    /**
     * Renders ghost blocks during browsing preview (state 2).
     * Uses our own SchematicHoverPreview renderer positioned via Create's
     * transformation so ghost blocks follow the crosshair.
     */
    @SubscribeEvent
    @JvmStatic
    fun onRenderLevel(event: RenderLevelStageEvent) {
        SchematicHoverPreview.render(event)
    }

}
