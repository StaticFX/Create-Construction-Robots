package de.devin.cbbees.content.schematics.client

import net.minecraftforge.eventbus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.minecraftforge.client.event.InputEvent

/**
 * Forge 1.20.1 override for DeconstructionClientEvents.
 *
 * Differences from NeoForge:
 * - InputEvent.MouseButton.Pre (same as NeoForge)
 * - event.scrollDelta instead of event.scrollDeltaY
 * - ClientTickEvent.Post doesn't fire via @SubscribeEvent (handled by manual tick in CreateBuzzyBeez)
 */
object DeconstructionClientEvents {

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        tick()
    }

    @JvmStatic
    fun tick() {
        DeconstructionHandler.tick()
        DeconstructionRenderer.update()
    }

    @SubscribeEvent
    @JvmStatic
    fun onMouseInput(event: InputEvent.MouseButton.Pre) {
        if (DeconstructionHandler.onMouseInput(event.button, event.action == org.lwjgl.glfw.GLFW.GLFW_PRESS)) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onMouseScroll(event: InputEvent.MouseScrollingEvent) {
        if (DeconstructionHandler.mouseScrolled(event.scrollDelta)) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onKeyInput(event: InputEvent.Key) {
        if (DeconstructionHandler.onKeyInput(event.key, event.action == org.lwjgl.glfw.GLFW.GLFW_PRESS)) {
        }
    }
}
