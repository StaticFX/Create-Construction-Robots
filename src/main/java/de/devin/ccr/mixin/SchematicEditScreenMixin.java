package de.devin.ccr.mixin;

import com.simibubi.create.content.schematics.client.SchematicEditScreen;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import de.devin.ccr.network.StartConstructionPacket;
import de.devin.ccr.network.StartDeconstructionPacket;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for {@link SchematicEditScreen} to add "Start Construction" and "Start Deconstruction" buttons
 * that trigger the robot construction/deconstruction system.
 * 
 * <p>The construction button will build the schematic using robots.</p>
 * <p>The deconstruction button will remove all blocks within the schematic bounds using robots.</p>
 */
@Mixin(value = SchematicEditScreen.class, remap = false)
public abstract class SchematicEditScreenMixin extends AbstractSimiScreen {

    @Unique
    private IconButton ccr$constructButton;

    /**
     * Injects at the end of the init method to add our custom construction buttons.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void ccr$addConstructButtons(CallbackInfo ci) {

        System.out.println("Tailing init method of SchematicEditScreenMixin");

        SchematicHandler handler = CreateClient.SCHEMATIC_HANDLER;
        
        // Only show the buttons if the schematic is deployed
        if (handler.isDeployed()) {
            // Position buttons at the bottom left of the screen
            int buttonX = guiLeft + 10;
            int buttonY = guiTop + 90;
            
            // Construction button (green/play icon) - builds the schematic
            ccr$constructButton = new IconButton(buttonX, buttonY, AllIcons.I_PLAY);
            ccr$constructButton.setToolTip(Component.translatable("gui.ccr.schematic.start_construction"));
            ccr$constructButton.withCallback(() -> {
                // Send packet to server to start construction (use singleton INSTANCE)
                PacketDistributor.sendToServer(StartConstructionPacket.Companion.getINSTANCE());
                
                // Close the screen
                if (Minecraft.getInstance().screen != null) {
                    Minecraft.getInstance().screen.onClose();
                }
            });
            addRenderableWidget(ccr$constructButton);
        }
    }
}
