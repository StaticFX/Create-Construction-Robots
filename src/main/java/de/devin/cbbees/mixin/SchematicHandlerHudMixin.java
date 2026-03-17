package de.devin.cbbees.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import de.devin.cbbees.content.schematics.ConstructionPlannerItem;
import de.devin.cbbees.content.schematics.client.ConstructionPlannerHandler;
import de.devin.cbbees.content.schematics.client.ConstructionToolState;
import de.devin.cbbees.items.AllItems;
import de.devin.cbbees.network.SelectSchematicPacket;
import de.devin.cbbees.network.StartConstructionPacket;
import de.devin.cbbees.network.StopTasksPacket;
import de.devin.cbbees.network.UnselectSchematicPacket;
import de.devin.cbbees.registry.AllKeys;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for {@link SchematicHandler} providing:
 * <ul>
 *   <li>RMB interception for the custom Construct / Unselect tools</li>
 *   <li>R-key shortcut for construction (Construction Planner only)</li>
 *   <li>Backspace shortcut for stopping tasks</li>
 *   <li>Suppresses Create's GUI overlay during browsing preview (our HUD shown instead)</li>
 * </ul>
 */
@Mixin(value = SchematicHandler.class, remap = false)
public abstract class SchematicHandlerHudMixin {

    @Shadow private boolean active;
    @Shadow public abstract boolean isDeployed();

    /* ------------------------------------------------------------------ */
    /*  Suppress Create's GUI overlay during browsing preview               */
    /* ------------------------------------------------------------------ */

    /**
     * Cancels Create's schematic HUD (tool selection, hotbar overlay, tool overlay)
     * while the player is browsing schematics — our own HUD is shown instead.
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void ccr$suppressHudDuringBrowsing(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ConstructionPlannerHandler.INSTANCE.isBrowsingPreview()) {
            ci.cancel();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Construction packet helper                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Reads placement data from the client-side ItemStack and sends a
     * StartConstructionPacket. If the player is in browsing preview mode,
     * first syncs the schematic filename to the server via SelectSchematicPacket
     * so the server has the correct file before construction starts.
     */
    @Unique
    private void ccr$sendConstructionPacket(ItemStack stack) {
        // If browsing preview is active, sync filename to server first
        if (ConstructionPlannerHandler.INSTANCE.isBrowsingPreview()) {
            String filename = stack.get(AllDataComponents.SCHEMATIC_FILE);
            if (filename != null) {
                PacketDistributor.sendToServer(new SelectSchematicPacket(filename));
            }
            ConstructionPlannerHandler.INSTANCE.clearBrowsingPreview();
        }

        BlockPos anchor = stack.getOrDefault(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO);
        Rotation rotation = stack.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
        Mirror mirror = stack.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);
        PacketDistributor.sendToServer(new StartConstructionPacket(anchor, rotation, mirror));
    }

    /* ------------------------------------------------------------------ */
    /*  RMB — custom tool actions                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Intercepts right-click when a custom Construction Planner tool is active.
     */
    @Inject(method = "onMouseInput", at = @At("HEAD"), cancellable = true)
    private void ccr$handleCustomToolRMB(int button, boolean pressed, CallbackInfoReturnable<Boolean> cir) {
        if (!active || !isDeployed() || !pressed || button != 1) return;

        ConstructionToolState.CustomTool tool = ConstructionToolState.getActiveTool();
        if (tool == ConstructionToolState.CustomTool.NONE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Ensure player still holds the planner
        ItemStack mainHand = mc.player.getMainHandItem();
        if (!AllItems.INSTANCE.getCONSTRUCTION_PLANNER().isIn(mainHand)) {
            ConstructionToolState.setActiveTool(ConstructionToolState.CustomTool.NONE);
            return;
        }

        if (tool == ConstructionToolState.CustomTool.CONSTRUCT) {
            ccr$sendConstructionPacket(mainHand);
            mc.player.displayClientMessage(
                Component.translatable("gui.cbbees.schematic.construction_started")
                    .withStyle(style -> style.withColor(0x00FF00)),
                true
            );
            ConstructionToolState.setActiveTool(ConstructionToolState.CustomTool.NONE);
            cir.setReturnValue(true);
        } else if (tool == ConstructionToolState.CustomTool.UNSELECT) {
            // Clear both server-side and client-side so Create's SchematicHandler deactivates
            PacketDistributor.sendToServer(UnselectSchematicPacket.Companion.getINSTANCE());
            ConstructionPlannerItem.Companion.clearSchematic(mainHand);
            mc.player.displayClientMessage(
                Component.translatable("gui.cbbees.tool.unselect.done")
                    .withStyle(style -> style.withColor(0xFFAA88)),
                true
            );
            ConstructionToolState.setActiveTool(ConstructionToolState.CustomTool.NONE);
            cir.setReturnValue(true);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Key shortcuts                                                      */
    /* ------------------------------------------------------------------ */

    @Inject(method = "onKeyInput", at = @At("HEAD"))
    private void ccr$handleKeys(int key, boolean pressed, CallbackInfo ci) {
        if (!active || !isDeployed() || !pressed) return;

        // R key — construction shortcut (Construction Planner only)
        if (AllKeys.INSTANCE.getSTART_ACTION().matches(key, 0)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ItemStack mainHand = mc.player.getMainHandItem();
                if (AllItems.INSTANCE.getCONSTRUCTION_PLANNER().isIn(mainHand)) {
                    ccr$sendConstructionPacket(mainHand);
                    mc.player.displayClientMessage(
                        Component.translatable("gui.cbbees.schematic.construction_started")
                            .withStyle(style -> style.withColor(0x00FF00)),
                        true
                    );
                }
            }
        }

        // Backspace — stop tasks (any item)
        if (AllKeys.INSTANCE.getSTOP_ACTION().matches(key, 0)) {
            PacketDistributor.sendToServer(StopTasksPacket.getINSTANCE());
        }
    }
}
