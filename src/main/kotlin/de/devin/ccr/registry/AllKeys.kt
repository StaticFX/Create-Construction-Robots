package de.devin.ccr.registry

import com.mojang.blaze3d.platform.InputConstants
import de.devin.ccr.CreateCCR
import net.minecraft.client.KeyMapping
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import org.lwjgl.glfw.GLFW

/**
 * Registry for all keybindings in the mod.
 */
object AllKeys {
    
    /**
     * Keybinding to toggle the task progress HUD visibility.
     * Default key: P
     */
    val TOGGLE_TASK_HUD: KeyMapping = KeyMapping(
        "key.${CreateCCR.ID}.toggle_task_hud",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_P,
        "key.categories.${CreateCCR.ID}"
    )

    /**
     * Keybinding to start construction or deconstruction.
     * Default key: R
     */
    val START_ACTION: KeyMapping = KeyMapping(
        "key.${CreateCCR.ID}.start_action",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_R,
        "key.categories.${CreateCCR.ID}"
    )

    /**
     * Keybinding to stop all robot tasks.
     * Default key: BACKSPACE
     */
    val STOP_ACTION: KeyMapping = KeyMapping(
        "key.${CreateCCR.ID}.stop_action",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_BACKSPACE,
        "key.categories.${CreateCCR.ID}"
    )
    
    /**
     * Registers all keybindings with the game.
     * Called from RegisterKeyMappingsEvent.
     */
    fun register(event: RegisterKeyMappingsEvent) {
        event.register(TOGGLE_TASK_HUD)
        event.register(START_ACTION)
        event.register(STOP_ACTION)
    }
    
    /**
     * Checks if the toggle HUD key was just pressed.
     */
    fun isToggleTaskHudPressed(): Boolean {
        return TOGGLE_TASK_HUD.consumeClick()
    }
}
