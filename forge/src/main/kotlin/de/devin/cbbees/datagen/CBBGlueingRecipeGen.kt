package de.devin.cbbees.datagen

import com.simibubi.create.api.data.recipe.ProcessingRecipeGen
import com.simibubi.create.content.processing.recipe.ProcessingRecipe
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.registry.AllCBeesRecipeTypes
import net.minecraft.data.PackOutput

class CBBGlueingRecipeGen(
    output: PackOutput,
    defaultNamespace: String
) : ProcessingRecipeGen(output, defaultNamespace) {

    override fun getRecipeType(): IRecipeTypeInfo = AllCBeesRecipeTypes.GLUEING

    val MECHANICAL_BEE = create<ProcessingRecipe<*>>("mechanical_bee") { builder ->
        builder.require(AllItems.MECHANICAL_BEE_CHASSIS.get())
            .output(AllItems.MECHANICAL_BEE.get())
    }

    val MECHANICAL_BUMBLE_BEE = create<ProcessingRecipe<*>>("mechanical_bumble_bee") { builder ->
        builder.require(AllItems.MECHANICAL_BUMBLE_BEE_CHASSIS.get())
            .output(AllItems.MECHANICAL_BUMBLE_BEE.get())
    }
}
