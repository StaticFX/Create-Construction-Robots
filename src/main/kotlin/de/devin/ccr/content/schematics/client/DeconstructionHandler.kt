package de.devin.ccr.content.schematics.client

import de.devin.ccr.items.AllItems
import com.mojang.blaze3d.systems.RenderSystem
import com.simibubi.create.foundation.gui.AllGuiTextures
import de.devin.ccr.network.StartDeconstructionPacket
import net.neoforged.neoforge.network.PacketDistributor
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.DeltaTracker
import org.lwjgl.glfw.GLFW
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.gui.ScreenOpener
import net.createmod.catnip.math.VecHelper
import net.createmod.catnip.outliner.Outliner
import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.foundation.utility.RaycastHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.AxisDirection
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Client-side handler for the Deconstruction Planner tool.
 * 
 * This mirrors Create's SchematicAndQuillHandler but renders in red
 * and opens a deconstruction prompt instead of saving a schematic.
 * 
 * Selection flow:
 * 1. Player holds Deconstruction Planner
 * 2. Right-click to set first corner
 * 3. Right-click again to set second corner
 * 4. Scroll wheel to resize selection
 * 5. Right-click to open deconstruction prompt
 * 6. Shift+Right-click to cancel selection
 */
object DeconstructionHandler {
    
    private val outlineSlot = Any()
    
    /** First corner of the selection */
    var firstPos: BlockPos? = null
        private set
    
    /** Second corner of the selection */
    var secondPos: BlockPos? = null
        private set
    
    /** Currently targeted block position */
    private var selectedPos: BlockPos? = null
    
    /** Currently selected face (for resizing) */
    private var selectedFace: Direction? = null
    
    /** Selection range when using CTRL */
    private var range = 10
    
    /** Red color for deconstruction selection (contrast to blue construction) */
    private const val SELECTION_COLOR = 0xc56868
    
    /**
     * Handles mouse scroll input for resizing the selection.
     * @return true if the scroll was handled
     */
    fun mouseScrolled(delta: Double): Boolean {
        if (!isActive()) return false
        
        // Only handle scroll when CTRL is held
        if (!isCtrlDown()) return false
        
        if (secondPos == null) {
            range = Mth.clamp(range + delta.toInt(), 1, 100)
        }
        
        val face = selectedFace ?: return true
        val first = firstPos ?: return true
        val second = secondPos ?: return true
        
        var bb = AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(second))
        val vec = face.normal
        val projectedView = Minecraft.getInstance().gameRenderer.mainCamera.position
        
        var adjustedDelta = delta
        if (bb.contains(projectedView)) {
            adjustedDelta *= -1
        }
        
        // Round away from zero
        val intDelta = if (adjustedDelta > 0) Math.ceil(adjustedDelta).toInt() else Math.floor(adjustedDelta).toInt()
        val x = vec.x * intDelta
        val y = vec.y * intDelta
        val z = vec.z * intDelta
        
        val axisDirection = face.axisDirection
        if (axisDirection == AxisDirection.NEGATIVE) {
            bb = bb.move(-x.toDouble(), -y.toDouble(), -z.toDouble())
        }
        
        val maxX = maxOf(bb.maxX - x * axisDirection.step, bb.minX)
        val maxY = maxOf(bb.maxY - y * axisDirection.step, bb.minY)
        val maxZ = maxOf(bb.maxZ - z * axisDirection.step, bb.minZ)
        
        bb = AABB(bb.minX, bb.minY, bb.minZ, maxX, maxY, maxZ)
        
        firstPos = BlockPos.containing(bb.minX, bb.minY, bb.minZ)
        secondPos = BlockPos.containing(bb.maxX, bb.maxY, bb.maxZ)
        
        val player = Minecraft.getInstance().player ?: return true
        val sizeX = (bb.xsize + 1).toInt()
        val sizeY = (bb.ysize + 1).toInt()
        val sizeZ = (bb.zsize + 1).toInt()
        player.displayClientMessage(
            Component.translatable("ccr.deconstruction.dimensions", sizeX, sizeY, sizeZ),
            true
        )
        
        return true
    }
    
    /**
     * Handles mouse button input.
     * @param button The mouse button (0=left, 1=right)
     * @param pressed Whether the button was pressed (true) or released (false)
     * @return true if the input was handled
     */
    fun onMouseInput(button: Int, pressed: Boolean): Boolean {
        if (!pressed || button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false
        if (!isActive()) return false
        
        val player = Minecraft.getInstance().player ?: return false
        
        // Shift + right-click to discard selection
        if (player.isShiftKeyDown) {
            discard()
            return true
        }
        
        // If both corners are set, we don't open the prompt screen anymore.
        // The deconstruction is now started via the HUD button or R key.
        if (secondPos != null) {
            return true
        }
        
        // No target selected
        if (selectedPos == null) {
            player.displayClientMessage(
                Component.translatable("ccr.deconstruction.no_target"),
                true
            )
            return true
        }
        
        // Set second corner if first is already set
        if (firstPos != null) {
            secondPos = selectedPos
            player.displayClientMessage(
                Component.translatable("ccr.deconstruction.second_pos"),
                true
            )
            return true
        }
        
        // Set first corner
        firstPos = selectedPos
        player.displayClientMessage(
            Component.translatable("ccr.deconstruction.first_pos"),
            true
        )
        return true
    }
    
    /**
     * Discards the current selection.
     */
    fun discard() {
        firstPos = null
        secondPos = null
        Minecraft.getInstance().player?.displayClientMessage(
            Component.translatable("ccr.deconstruction.abort"),
            true
        )
    }
    
    /**
     * Called every client tick to update selection state and rendering.
     */
    fun tick() {
        if (!isActive()) return
        
        val player = Minecraft.getInstance().player ?: return
        
        // Update selected position based on where player is looking
        if (isCtrlDown()) {
            // Free-aim mode when CTRL is held
            val pt = AnimationTickHolder.getPartialTicks()
            val targetVec = player.getEyePosition(pt).add(player.lookAngle.scale(range.toDouble()))
            selectedPos = BlockPos.containing(targetVec)
        } else {
            // Normal raycast mode
            val trace = RaycastHelper.rayTraceRange(player.level(), player, 75.0)
            if (trace != null && trace.type == HitResult.Type.BLOCK) {
                var hit = trace.blockPos
                val replaceable = player.level().getBlockState(hit)
                    .canBeReplaced(BlockPlaceContext(UseOnContext(player, InteractionHand.MAIN_HAND, trace)))
                if (trace.direction.axis.isVertical && !replaceable) {
                    hit = hit.relative(trace.direction)
                }
                selectedPos = hit
            } else {
                selectedPos = null
            }
        }
        
        // Update selected face for resizing
        selectedFace = null
        val first = firstPos
        val second = secondPos
        if (first != null && second != null) {
            var bb = AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(second))
                .expandTowards(1.0, 1.0, 1.0)
                .inflate(0.45)
            val projectedView = Minecraft.getInstance().gameRenderer.mainCamera.position
            val inside = bb.contains(projectedView)
            val result = RaycastHelper.rayTraceUntil(player, 70.0) { pos ->
                inside xor bb.contains(VecHelper.getCenterOf(pos))
            }
            selectedFace = when {
                result.missed() -> null
                inside -> result.facing.opposite
                else -> result.facing
            }
        }
        
        // Render the selection box
        val currentBox = getCurrentSelectionBox()
        if (currentBox != null) {
            Outliner.getInstance()
                .chaseAABB(outlineSlot, currentBox)
                .colored(SELECTION_COLOR)
                .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
                .lineWidth(1 / 16f)
                .highlightFace(selectedFace)
        }
    }
    
    /**
     * Gets the current selection bounding box based on selection state.
     */
    private fun getCurrentSelectionBox(): AABB? {
        val first = firstPos
        val second = secondPos
        val selected = selectedPos
        
        return when {
            second != null && first != null -> {
                AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(second)).expandTowards(1.0, 1.0, 1.0)
            }
            first != null -> {
                if (selected != null) {
                    AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(selected)).expandTowards(1.0, 1.0, 1.0)
                } else {
                    AABB(first)
                }
            }
            selected != null -> AABB(selected)
            else -> null
        }
    }
    
    /**
     * Handles key input for deconstruction.
     */
    fun onKeyInput(key: Int, pressed: Boolean): Boolean {
        if (!pressed || !isActive()) return false
        
        if (key == GLFW.GLFW_KEY_R && firstPos != null && secondPos != null) {
            val first = firstPos!!
            val second = secondPos!!
            
            // Send packet to server to start deconstruction
            PacketDistributor.sendToServer(StartDeconstructionPacket(first, second))
            
            // Show feedback
            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable("message.ccr.planner.started")
                    .withStyle { it.withColor(0x00FF00) },
                true
            )
            
            discard()
            return true
        }
        
        return false
    }

    /**
     * Renders the deconstruction HUD.
     */
    fun renderHUD(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        if (!isActive()) return
        
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui) return
        
        val first = firstPos
        val second = secondPos
        
        // Only show HUD if at least one corner is set
        if (first == null) return

        val screenWidth = guiGraphics.guiWidth()
        val screenHeight = guiGraphics.guiHeight()

        // 1. Draw the "Selection Info" part (similar to Create's schematic info)
        val infoWidth = 140
        val infoHeight = 40
        val infoX = screenWidth / 2 - infoWidth / 2
        val infoY = 20

        val gray = AllGuiTextures.HUD_BACKGROUND
        
        RenderSystem.enableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.75f)
        
        // Draw info background
        guiGraphics.blit(gray.location, infoX, infoY, gray.startX.toFloat(), gray.startY.toFloat(), 
            infoWidth, infoHeight, gray.width, gray.height)
        
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        // Draw info text
        val titleText = Component.translatable("ccr.deconstruction.title")
        guiGraphics.drawString(mc.font, titleText, infoX + (infoWidth - mc.font.width(titleText)) / 2, infoY + 5, 0xFFCCCC, false)
        
        if (second != null) {
            val sizeX = Math.abs(first.x - second.x) + 1
            val sizeY = Math.abs(first.y - second.y) + 1
            val sizeZ = Math.abs(first.z - second.z) + 1
            val dimText = Component.translatable("ccr.deconstruction.dimensions", sizeX, sizeY, sizeZ)
            guiGraphics.drawString(mc.font, dimText, infoX + (infoWidth - mc.font.width(dimText)) / 2, infoY + 20, 0xCCDDFF, false)
        } else {
            val waitingText = Component.translatable("ccr.deconstruction.first_pos")
            guiGraphics.drawString(mc.font, waitingText, infoX + (infoWidth - mc.font.width(waitingText)) / 2, infoY + 20, 0xAAAAAA, false)
        }

        // 2. Draw the "Start Button" part (only if both corners are set)
        if (second != null) {
            val buttonWidth = 150
            val buttonHeight = 20
            val buttonX = (screenWidth - buttonWidth) / 2
            val buttonY = screenHeight - 50

            RenderSystem.setShaderColor(1f, 1f, 1f, 0.75f)
            guiGraphics.blit(gray.location, buttonX, buttonY, gray.startX.toFloat(), gray.startY.toFloat(), 
                buttonWidth, buttonHeight, gray.width, gray.height)
            
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            val buttonText = Component.translatable("gui.ccr.schematic.start_deconstruction")
            guiGraphics.drawString(mc.font, buttonText, buttonX + (buttonWidth - mc.font.width(buttonText)) / 2, buttonY + (buttonHeight - 8) / 2, 0xFFCCCC, false)
        }
        
        RenderSystem.disableBlend()
    }

    /**
     * Checks if the deconstruction handler should be active.
     * Active when player is holding the Deconstruction Planner item.
     */
    fun isActive(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val mainHandItem = player.mainHandItem
        return AllItems.DECONSTRUCTION_PLANNER.isIn(mainHandItem)
    }
    
    
    /**
     * Checks if CTRL key is held.
     */
    private fun isCtrlDown(): Boolean {
        return Minecraft.getInstance().window?.let { window ->
            GLFW.glfwGetKey(window.window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(window.window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        } ?: false
    }
}
