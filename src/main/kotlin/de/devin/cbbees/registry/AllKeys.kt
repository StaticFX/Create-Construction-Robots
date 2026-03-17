package de.devin.cbbees.registry

import com.mojang.blaze3d.platform.InputConstants
import de.devin.cbbees.CreateBuzzyBeez
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
        "key.${CreateBuzzyBeez.ID}.toggle_task_hud",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_P,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Keybinding to start construction or deconstruction.
     * Default key: R
     */
    val START_ACTION: KeyMapping = KeyMapping(
        "key.${CreateBuzzyBeez.ID}.start_action",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_R,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Keybinding to stop all robot tasks.
     * Default key: BACKSPACE
     */
    val STOP_ACTION: KeyMapping = KeyMapping(
        "key.${CreateBuzzyBeez.ID}.stop_action",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_BACKSPACE,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Keybinding to open the full-screen schematic browser.
     * Default key: B
     */
    val OPEN_SCHEMATIC_BROWSER: KeyMapping = KeyMapping(
        "key.${CreateBuzzyBeez.ID}.open_schematic_browser",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Registers all keybindings with the game.
     * Called from RegisterKeyMappingsEvent.
     */
    fun register(event: RegisterKeyMappingsEvent) {
        event.register(TOGGLE_TASK_HUD)
        event.register(START_ACTION)
        event.register(STOP_ACTION)
        event.register(OPEN_SCHEMATIC_BROWSER)
    }

    /**
     * Checks if the toggle HUD key was just pressed.
     */
    fun isToggleTaskHudPressed(): Boolean {
        return TOGGLE_TASK_HUD.consumeClick()
    }
}
