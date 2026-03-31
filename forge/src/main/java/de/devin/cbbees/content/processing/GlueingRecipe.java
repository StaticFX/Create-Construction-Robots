package de.devin.cbbees.content.processing;

import javax.annotation.ParametersAreNonnullByDefault;

import de.devin.cbbees.registry.AllCBeesRecipeTypes;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;

import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

/**
 * Forge 1.20.1 override:
 * - Extends ProcessingRecipe<Container> instead of StandardProcessingRecipe<SingleRecipeInput>
 * - ProcessingRecipeParams is ProcessingRecipeBuilder.ProcessingRecipeParams
 * - Container instead of SingleRecipeInput
 */
@ParametersAreNonnullByDefault
public class GlueingRecipe extends ProcessingRecipe<Container> {

	public GlueingRecipe(ProcessingRecipeParams params) {
		super(AllCBeesRecipeTypes.GLUEING, params);
	}

	@Override
	public boolean matches(Container inv, Level worldIn) {
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
