package de.devin.cbbees.registry;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeFactory;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;

import de.devin.cbbees.CreateBuzzyBeez;
import de.devin.cbbees.content.processing.GlueingRecipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import java.util.Optional;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 1.20.1 — uses RegistryObject, Container (not RecipeInput), no RecipeHolder.
 */
public class AllCBeesRecipeTypes {

	private static final DeferredRegister<RecipeSerializer<?>> SERIALIZER_REGISTER =
		DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, CreateBuzzyBeez.ID);
	private static final DeferredRegister<RecipeType<?>> TYPE_REGISTER =
		DeferredRegister.create(Registries.RECIPE_TYPE, CreateBuzzyBeez.ID);

	public static final RecipeTypeEntry GLUEING = registerProcessing("glueing", GlueingRecipe::new);

	private static RecipeTypeEntry registerProcessing(String name, ProcessingRecipeFactory<?> factory) {
		ResourceLocation id = CreateBuzzyBeez.INSTANCE.asResource(name);
		RegistryObject<RecipeSerializer<?>> serializer =
			SERIALIZER_REGISTER.register(name, () -> new ProcessingRecipeSerializer<>(factory));
		RegistryObject<RecipeType<?>> type =
			TYPE_REGISTER.register(name, () -> RecipeType.simple(id));
		return new RecipeTypeEntry(id, serializer, type);
	}

	public static void register(IEventBus modEventBus) {
		SERIALIZER_REGISTER.register(modEventBus);
		TYPE_REGISTER.register(modEventBus);
	}

	public static class RecipeTypeEntry implements IRecipeTypeInfo {
		private final ResourceLocation id;
		private final RegistryObject<RecipeSerializer<?>> serializer;
		private final RegistryObject<RecipeType<?>> type;

		public RecipeTypeEntry(
			ResourceLocation id,
			RegistryObject<RecipeSerializer<?>> serializer,
			RegistryObject<RecipeType<?>> type
		) {
			this.id = id;
			this.serializer = serializer;
			this.type = type;
		}

		@Override
		public ResourceLocation getId() {
			return id;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends RecipeSerializer<?>> T getSerializer() {
			return (T) serializer.get();
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends RecipeType<?>> T getType() {
			return (T) type.get();
		}

		@SuppressWarnings("unchecked")
		public <C extends Container, R extends Recipe<C>> Optional<R> find(C inv, Level level) {
			return level.getRecipeManager().getRecipeFor((RecipeType<R>) type.get(), inv, level);
		}
	}
}
