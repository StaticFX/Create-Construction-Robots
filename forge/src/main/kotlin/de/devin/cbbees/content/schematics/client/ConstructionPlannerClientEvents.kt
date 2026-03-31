package de.devin.cbbees.content.schematics.client

import de.devin.cbbees.items.AllItems
import de.devin.cbbees.registry.AllKeys
import net.minecraft.client.Minecraft
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import org.lwjgl.glfw.GLFW

/**
 * Forge 1.20.1 override for ConstructionPlannerClientEvents.
 *
 * Differences from NeoForge:
 * - InputEvent.MouseButton (no .Pre inner class)
 * - event.scrollDelta instead of event.scrollDeltaY
 * - ClientTickEvent.Post doesn't fire via @SubscribeEvent (handled by manual tick in CreateBuzzyBeez)
 */
object ConstructionPlannerClientEvents {

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        tick()
    }

    @JvmStatic
    fun tick() {
        ConstructionPlannerHandler.tick()
        ConstructionPlannerHUD.update()

        // Clear custom tool state if player is no longer holding the planner
        if (ConstructionToolState.activeTool != ConstructionToolState.CustomTool.NONE) {
            val player = Minecraft.getInstance().player
            if (player == null || !AllItems.CONSTRUCTION_PLANNER.isIn(player.mainHandItem)) {
                ConstructionToolState.activeTool = ConstructionToolState.CustomTool.NONE
            }
        }

        // Always drain the browser keybind to prevent queued presses from firing
        // when the player switches to holding the planner
        val player = Minecraft.getInstance().player
        if (AllKeys.OPEN_SCHEMATIC_BROWSER.consumeClick()) {
            if (player != null && AllItems.CONSTRUCTION_PLANNER.isIn(player.mainHandItem)) {
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

    /**
     * Forge 1.20.1: scrollDelta instead of scrollDeltaY.
     */
    @SubscribeEvent
    @JvmStatic
    fun onMouseScroll(event: InputEvent.MouseScrollingEvent) {
        if (ConstructionPlannerHandler.onScroll(event.scrollDelta)) {
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
     * Handles right-click on construction job bounding boxes.
     * Opens a [JobDetailScreen] when the player right-clicks while looking at a job AABB
     * and not targeting a real block (i.e. the crosshair is in the air).
     *
     * Forge 1.20.1: InputEvent.MouseButton (no Pre/Post distinction).
     */
    @SubscribeEvent
    @JvmStatic
    fun onMouseInput(event: InputEvent.MouseButton.Pre) {
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return
        if (event.action != GLFW.GLFW_PRESS) return

        val mc = Minecraft.getInstance()
        if (mc.screen != null) return

        val player = mc.player ?: return

        // Only intercept when no real block is targeted — avoids stealing block interactions
        val hit = mc.hitResult
        if (hit != null && hit.type == net.minecraft.world.phys.HitResult.Type.BLOCK) return

        val eyePos = player.getEyePosition(1f)
        val lookDir = player.lookAngle
        val jobId = ConstructionRenderer.findJobAtRay(eyePos, lookDir, 5.0) ?: return

        ConstructionRenderer.getJobInfo(jobId) ?: return
        mc.setScreen(JobDetailScreen(jobId))
        event.isCanceled = true
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
