package de.devin.cbbees.datagen

import com.simibubi.create.AllDamageTypes
import com.simibubi.create.infrastructure.worldgen.AllBiomeModifiers
import com.simibubi.create.infrastructure.worldgen.AllConfiguredFeatures
import com.simibubi.create.infrastructure.worldgen.AllPlacedFeatures
import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.core.registries.Registries
import net.minecraft.data.PackOutput
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider
import net.minecraftforge.registries.ForgeRegistries
import java.util.concurrent.CompletableFuture

private val BUILDER = RegistrySetBuilder()
    .add(Registries.DAMAGE_TYPE, AllDamageTypes::bootstrap)
    .add(Registries.CONFIGURED_FEATURE, AllConfiguredFeatures::bootstrap)
    .add(Registries.PLACED_FEATURE, AllPlacedFeatures::bootstrap)
    .add(ForgeRegistries.Keys.BIOME_MODIFIERS, AllBiomeModifiers::bootstrap)

class GeneratedEntriesProvider(output: PackOutput, registries: CompletableFuture<HolderLookup.Provider>):
    DatapackBuiltinEntriesProvider(output, registries, BUILDER, setOf(CreateBuzzyBeez.ID)) {

    override fun getName(): String {
        return "Create Construction Robots Generated Entries"
    }
}
