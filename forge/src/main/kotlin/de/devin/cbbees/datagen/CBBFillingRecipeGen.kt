package de.devin.cbbees.datagen

import com.simibubi.create.AllRecipeTypes
import com.simibubi.create.api.data.recipe.ProcessingRecipeGen
import com.simibubi.create.content.processing.recipe.ProcessingRecipe
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo
import de.devin.cbbees.items.AllItems
import net.minecraft.data.PackOutput
import net.minecraft.world.item.ItemStack

class CBBFillingRecipeGen(
    output: PackOutput,
    defaultNamespace: String
) : ProcessingRecipeGen(output, defaultNamespace) {

    override fun getRecipeType(): IRecipeTypeInfo = AllRecipeTypes.FILLING

    val PORTABLE_BEEHIVE_HONEY = create<ProcessingRecipe<*>>("portable_beehive_honey") { builder ->
        val result = ItemStack(AllItems.PORTABLE_BEEHIVE.get())
        result.orCreateTag.putInt("HoneyFuel", 100)
        builder.require(com.simibubi.create.AllFluids.HONEY.get().source, 250)
            .require(AllItems.PORTABLE_BEEHIVE.get())
            .output(result)
    }
}
