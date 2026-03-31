package de.devin.cbbees.mixin;

import com.simibubi.create.content.schematics.client.SchematicHandler;
import de.devin.cbbees.compat.SchematicDataHelper;
import de.devin.cbbees.items.AllItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link SchematicHandler#findBlueprintInHand(Player)} to also
 * recognize the Construction Planner item as a valid schematic holder.
 *
 * <p>Create's original method only accepts {@code AllItems.SCHEMATIC}, so
 * without this mixin the placement overlay won't activate for our item.</p>
 */
@Mixin(value = SchematicHandler.class, remap = false)
public abstract class SchematicHandlerFindMixin {

    @Unique
    private static final Logger cbbees$LOGGER = LogManager.getLogger("cbbees/FindMixin");

    @Unique
    private static boolean cbbees$logged = false;

    @Shadow
    private ItemStack activeSchematicItem;

    @Shadow
    private int activeHotbarSlot;

    /**
     * If Create's check returns null (item not a Create SchematicItem),
     * we re-check for the Construction Planner.
     */
    @Inject(method = "findBlueprintInHand", at = @At("RETURN"), cancellable = true)
    private void ccr$findConstructionPlanner(Player player, CallbackInfoReturnable<ItemStack> cir) {
        if (cir.getReturnValue() != null) return;

        ItemStack stack = player.getMainHandItem();
        boolean isPlanner = AllItems.INSTANCE.getCONSTRUCTION_PLANNER().isIn(stack);
        if (isPlanner) {
            boolean hasFile = SchematicDataHelper.hasFile(stack);
            boolean isDeployed = SchematicDataHelper.isDeployed(stack);
            if (!cbbees$logged) {
                cbbees$LOGGER.info("[FindMixin] Planner detected: hasFile={}, isDeployed={}, tag={}",
                        hasFile, isDeployed, stack.getTag());
                cbbees$logged = true;
            }
            if (hasFile && isDeployed) {
                activeSchematicItem = stack;
                activeHotbarSlot = player.getInventory().selected;
                cir.setReturnValue(stack);
                cbbees$logged = false; // reset so next state change logs
            }
        } else {
            cbbees$logged = false;
        }
    }
}
