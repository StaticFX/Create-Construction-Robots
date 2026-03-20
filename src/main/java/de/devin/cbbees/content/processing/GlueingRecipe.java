package de.devin.cbbees.content.processing;

import javax.annotation.ParametersAreNonnullByDefault;

import de.devin.cbbees.registry.AllCBeesRecipeTypes;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;

import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

@ParametersAreNonnullByDefault
public class GlueingRecipe extends StandardProcessingRecipe<SingleRecipeInput> {

	public GlueingRecipe(ProcessingRecipeParams params) {
		super(AllCBeesRecipeTypes.GLUEING, params);
	}

	@Override
	public boolean matches(SingleRecipeInput inv, Level worldIn) {
		if (inv.isEmpty())
			return false;
		return ingredients.get(0)
			.test(inv.getItem(0));
	}

	@Override
	protected int getMaxInputCount() {
		return 1;
	}

	@Override
	protected int getMaxOutputCount() {
		return 12;
	}
}
