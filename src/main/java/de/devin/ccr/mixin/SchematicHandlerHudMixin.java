package de.devin.ccr.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import de.devin.ccr.registry.AllKeys;
import de.devin.ccr.network.StartConstructionPacket;
import de.devin.ccr.network.StopTasksPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for {@link SchematicHandler} to add a "Start Construction" button to the schematic HUD
 * that appears when holding a schematic item.
 * 
 * <p>This adds a visual button and keybind (R key) to trigger robot construction while
 * the schematic is deployed and the player is holding it.</p>
 */
@Mixin(value = SchematicHandler.class, remap = false)
public abstract class SchematicHandlerHudMixin {

    @Shadow
    private boolean active;

    @Shadow
    public abstract boolean isDeployed();

    @Unique
    private static final int BUTTON_WIDTH = 120;
    @Unique
    private static final int BUTTON_HEIGHT = 20;

    /**
     * Injects at the end of the render method to draw a "Start Construction" button
     * on the schematic HUD when the schematic is deployed.
     * 
     * <p>The button is positioned centered above the hotbar for easy access.</p>
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void ccr$renderConstructionButton(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!active || !isDeployed()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        // Position the button centered above the hotbar
        int buttonX = (screenWidth - BUTTON_WIDTH) / 2;
        int buttonY = screenHeight - 50;   // Above the hotbar

        // Use Create's HUD background texture for consistent styling
        AllGuiTextures gray = AllGuiTextures.HUD_BACKGROUND;
        
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 0.75f);
        
        // Draw the background using Create's texture
        guiGraphics.blit(gray.location, buttonX, buttonY, gray.getStartX(), gray.getStartY(), 
                BUTTON_WIDTH, BUTTON_HEIGHT, gray.getWidth(), gray.getHeight());
        
        RenderSystem.setShaderColor(1, 1, 1, 1);

        // Draw button text with Create-style coloring
        KeyMapping startKey = AllKeys.INSTANCE.getSTART_ACTION();
        Component buttonText = Component.translatable("gui.ccr.schematic.start_construction_key", startKey.getTranslatedKeyMessage());
        int textWidth = mc.font.width(buttonText);
        int textX = buttonX + (BUTTON_WIDTH - textWidth) / 2;
        int textY = buttonY + (BUTTON_HEIGHT - 8) / 2;
        
        // Use Create's typical text color (light cyan/blue)
        guiGraphics.drawString(mc.font, buttonText, textX, textY, 0xCCDDFF, false);
        
        RenderSystem.disableBlend();
    }

    /**
     * Injects at the head of onKeyInput to handle the configured keys for starting construction
     * and stopping tasks.
     */
    @Inject(method = "onKeyInput", at = @At("HEAD"))
    private void ccr$handleKeys(int key, boolean pressed, CallbackInfo ci) {
        if (!active || !isDeployed() || !pressed) {
            return;
        }

        // Check if start action key is pressed
        if (AllKeys.INSTANCE.getSTART_ACTION().matches(key, 0)) {
            // Send packet to server to start construction (use singleton INSTANCE)
            PacketDistributor.sendToServer(StartConstructionPacket.Companion.getINSTANCE());
            
            // Show feedback to player
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.translatable("gui.ccr.schematic.construction_started").withStyle(style -> style.withColor(0x00FF00)),
                    true
                );
            }
        }

        // Check if stop action key is pressed
        if (AllKeys.INSTANCE.getSTOP_ACTION().matches(key, 0)) {
            PacketDistributor.sendToServer(StopTasksPacket.getINSTANCE());
        }
    }
}
