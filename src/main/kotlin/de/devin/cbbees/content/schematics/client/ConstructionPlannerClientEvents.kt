package de.devin.cbbees.content.schematics.client

import de.devin.cbbees.items.AllItems
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent

/**
 * Client-side event handler for the Construction Planner HUD.
 *
 * Registers event listeners for:
 * - Client tick: refreshes the schematic list
 * - Mouse scroll: Alt+Scroll cycles through schematics
 * - Render GUI: draws the schematic selector above the hotbar
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
    fun onRenderGui(event: RenderGuiEvent.Post) {
        ConstructionPlannerHandler.renderHUD(event.guiGraphics, event.partialTick)
    }
}
