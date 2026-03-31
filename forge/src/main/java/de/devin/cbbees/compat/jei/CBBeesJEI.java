package de.devin.cbbees.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.simibubi.create.AllItems;
import com.simibubi.create.compat.jei.CreateJEI;
import com.simibubi.create.compat.jei.DoubleItemIcon;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.ProcessingViaFanCategory;

import de.devin.cbbees.content.processing.GlueingRecipe;
import de.devin.cbbees.registry.AllCBeesRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Forge 1.20.1 override:
 * - ResourceLocation constructor instead of fromNamespaceAndPath
 * - CreateRecipeCategory.Info record instead of CreateRecipeCategory.Builder
 * - CreateJEI.consumeTypedRecipes for recipe gathering
 */
@JeiPlugin
public class CBBeesJEI implements IModPlugin {

	private static final ResourceLocation ID = new ResourceLocation("cbbees", "jei_plugin");

	private final List<CreateRecipeCategory<?>> allCategories = new ArrayList<>();

	@Override
	public ResourceLocation getPluginUid() {
		return ID;
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		allCategories.clear();

		RecipeType<GlueingRecipe> recipeType = new RecipeType<>(
			new ResourceLocation("cbbees", "fan_glueing"), GlueingRecipe.class);

		Supplier<List<GlueingRecipe>> recipesSupplier = () -> {
			List<GlueingRecipe> recipes = new ArrayList<>();
			CreateJEI.<GlueingRecipe>consumeTypedRecipes(recipes::add, AllCBeesRecipeTypes.GLUEING.getType());
			return recipes;
		};

		List<Supplier<? extends ItemStack>> catalysts = new ArrayList<>();
		catalysts.add(ProcessingViaFanCategory.getFan("fan_glueing"));

		CreateRecipeCategory.Info<GlueingRecipe> info = new CreateRecipeCategory.Info<>(
			recipeType,
			net.createmod.catnip.lang.Lang.builder("cbbees").translate("recipe.fan_glueing").component(),
			new EmptyBackground(178, 72),
			new DoubleItemIcon(() -> new ItemStack(AllItems.PROPELLER.get()), () -> new ItemStack(Items.HONEY_BOTTLE)),
			recipesSupplier,
			catalysts
		);

		CreateRecipeCategory<GlueingRecipe> glueing = new FanGlueingCategory(info);
		allCategories.add(glueing);

		registration.addRecipeCategories(allCategories.toArray(IRecipeCategory[]::new));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		allCategories.forEach(c -> c.registerRecipes(registration));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		allCategories.forEach(c -> c.registerCatalysts(registration));
	}
}
