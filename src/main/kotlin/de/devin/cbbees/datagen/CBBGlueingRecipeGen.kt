package de.devin.cbbees.datagen

import com.simibubi.create.api.data.recipe.StandardProcessingRecipeGen
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.processing.GlueingRecipe
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.registry.AllCBeesRecipeTypes
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import java.util.concurrent.CompletableFuture

class CBBGlueingRecipeGen(
    output: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>
) : StandardProcessingRecipeGen<GlueingRecipe>(output, registries, CreateBuzzyBeez.ID) {

    override fun getRecipeType(): IRecipeTypeInfo = AllCBeesRecipeTypes.GLUEING

    val MECHANICAL_BEE = create("mechanical_bee") {
        it.require(AllItems.MECHANICAL_BEE_CHASSIS.get())
            .output(AllItems.MECHANICAL_BEE.get())
    }

    val MECHANICAL_BUMBLE_BEE = create("mechanical_bumble_bee") {
        it.require(AllItems.MECHANICAL_BUMBLE_BEE_CHASSIS.get())
            .output(AllItems.MECHANICAL_BUMBLE_BEE.get())
    }
}
