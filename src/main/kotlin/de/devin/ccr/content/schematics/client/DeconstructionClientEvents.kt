package de.devin.ccr.content.schematics.client

import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent

/**
 * Client-side event handler for the Deconstruction Planner system.
 * 
 * Registers event listeners for:
 * - Client tick: Updates the DeconstructionHandler each tick for selection rendering
 * - Mouse input: Handles right-click for setting selection corners
 * - Mouse scroll: Handles scroll wheel for resizing selection
 * - Key input: Handles R key for starting deconstruction
 * - Render GUI: Renders the deconstruction HUD
 * 
 * Note: Events are manually registered in CreateCCR to avoid compatibility issues
 * with Kotlin For Forge's auto-registration system.
 */
object DeconstructionClientEvents {
    
    /**
     * Called every client tick.
     * Updates the DeconstructionHandler to handle selection state and rendering.
     */
    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        DeconstructionHandler.tick()
    }
    
    /**
     * Called when a mouse button is clicked.
     * Handles right-click for setting selection corners and opening the prompt.
     */
    @SubscribeEvent
    @JvmStatic
    fun onMouseInput(event: InputEvent.MouseButton.Pre) {
        if (DeconstructionHandler.onMouseInput(event.button, event.action == org.lwjgl.glfw.GLFW.GLFW_PRESS)) {
            event.isCanceled = true
        }
    }
    
    /**
     * Called when the mouse scroll wheel is used.
     * Handles scroll for resizing the selection area.
     */
    @SubscribeEvent
    @JvmStatic
    fun onMouseScroll(event: InputEvent.MouseScrollingEvent) {
        if (DeconstructionHandler.mouseScrolled(event.scrollDeltaY)) {
            event.isCanceled = true
        }
    }

    /**
     * Called when a key is pressed.
     * Handles R key for starting deconstruction.
     */
    @SubscribeEvent
    @JvmStatic
    fun onKeyInput(event: InputEvent.Key) {
        if (DeconstructionHandler.onKeyInput(event.key, event.action == org.lwjgl.glfw.GLFW.GLFW_PRESS)) {
            // No cancel needed for key input usually, or it might interfere with other things
        }
    }

    /**
     * Called when the GUI is rendered.
     * Renders the deconstruction HUD.
     */
    @SubscribeEvent
    @JvmStatic
    fun onRenderGui(event: RenderGuiEvent.Post) {
        DeconstructionHandler.renderHUD(event.guiGraphics, event.partialTick)
    }
}
