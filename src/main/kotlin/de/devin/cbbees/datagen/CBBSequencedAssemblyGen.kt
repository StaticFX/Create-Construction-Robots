package de.devin.cbbees.datagen

import com.simibubi.create.api.data.recipe.SequencedAssemblyRecipeGen
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe
import com.simibubi.create.content.kinetics.press.PressingRecipe
import de.devin.cbbees.items.AllItems
import net.minecraft.advancements.critereon.ItemPredicate.Builder.item
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.world.item.Items
import java.util.concurrent.CompletableFuture

class CBBSequencedAssemblyGen(
    output: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>, defaultNamespace: String
) : SequencedAssemblyRecipeGen(output, registries, defaultNamespace) {

    val MECHANICAL_BEE_CHASSIS = create("mechanical_bee_chassis", {
        it.require { Items.HONEYCOMB }.transitionTo { AllItems.INCOMPLETE_MECHANICAL_BEE.get() }
            .addOutput(AllItems.MECHANICAL_BEE_CHASSIS.get(), 1f)
            .addStep(::DeployerApplicationRecipe) { it.require { com.simibubi.create.AllItems.ELECTRON_TUBE.get() } }
            .addStep(::DeployerApplicationRecipe) { it.require { com.simibubi.create.AllItems.BRASS_SHEET.get() } }
            .addStep(::PressingRecipe) { it }
    })

}