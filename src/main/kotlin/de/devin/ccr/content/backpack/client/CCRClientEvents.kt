package de.devin.ccr.content.backpack.client

import de.devin.ccr.content.backpack.BeehiveTooltipData
import de.devin.ccr.registry.AllKeys
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.bus.api.SubscribeEvent

/**
 * Client-side event handler for general CCR features.
 * 
 * Handles:
 * - Tooltip component registration (on MOD_BUS)
 * - Task progress HUD rendering (on NeoForge EVENT_BUS)
 * - Keybinding handling (on NeoForge EVENT_BUS)
 */
object CCRClientEvents {

    @SubscribeEvent
    @JvmStatic
    fun registerTooltipComponents(event: RegisterClientTooltipComponentFactoriesEvent) {
        event.register(BeehiveTooltipData::class.java) { data: BeehiveTooltipData -> BeehiveTooltipComponent(data) }
    }
}

/**
 * Event handler for NeoForge EVENT_BUS events related to task progress HUD.
 * Registered separately from CCRClientEvents which is on MOD_BUS.
 */
object TaskProgressClientEvents {
    
    /**
     * Renders the task progress HUD overlay.
     */
    @SubscribeEvent
    @JvmStatic
    fun onRenderGui(event: RenderGuiEvent.Post) {
        TaskProgressHUD.renderHUD(event.guiGraphics, event.partialTick)
    }
    
    /**
     * Handles keybinding checks each tick.
     */
    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        // Check if toggle key was pressed
        while (AllKeys.TOGGLE_TASK_HUD.consumeClick()) {
            TaskProgressHUD.toggle()
        }
    }
}
