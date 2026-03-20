package de.devin.cbbees.content.schematics.client

import de.devin.cbbees.items.AllItems
import de.devin.cbbees.registry.AllKeys
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
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
        ConstructionPlannerHUD.update()

        // Clear custom tool state if player is no longer holding the planner
        if (ConstructionToolState.activeTool != ConstructionToolState.CustomTool.NONE) {
            val player = Minecraft.getInstance().player
            if (player == null || !AllItems.CONSTRUCTION_PLANNER.isIn(player.mainHandItem)) {
                ConstructionToolState.activeTool = ConstructionToolState.CustomTool.NONE
            }
        }

        // Check keybind for opening full-screen browser (only when holding planner)
        val player = Minecraft.getInstance().player
        if (player != null && AllItems.CONSTRUCTION_PLANNER.isIn(player.mainHandItem)) {
            if (AllKeys.OPEN_SCHEMATIC_BROWSER.consumeClick()) {
                Minecraft.getInstance().setScreen(ConstructionPlannerScreen())
            }
        }

        // Rotate/mirror ghost preview keybinds
        if (ConstructionPlannerHandler.isBrowsingPreview) {
            if (AllKeys.ROTATE_PREVIEW.consumeClick()) {
                SchematicHoverPreview.rotatePreview()
            }
            if (AllKeys.MIRROR_PREVIEW.consumeClick()) {
                SchematicHoverPreview.mirrorPreview()
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

    /**
     * Renders ghost blocks and AABB outline during browsing preview (state 2).
     * Positioned at the crosshair block position, independent of Create.
     */
    @SubscribeEvent
    @JvmStatic
    fun onRenderLevel(event: RenderLevelStageEvent) {
        SchematicHoverPreview.render(event)
    }

}
