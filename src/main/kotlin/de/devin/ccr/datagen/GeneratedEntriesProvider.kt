package de.devin.ccr.datagen

import com.simibubi.create.AllDamageTypes
import com.simibubi.create.AllEnchantments
import com.simibubi.create.infrastructure.worldgen.AllBiomeModifiers
import com.simibubi.create.infrastructure.worldgen.AllConfiguredFeatures
import com.simibubi.create.infrastructure.worldgen.AllPlacedFeatures
import de.devin.ccr.CreateCCR
import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.core.registries.Registries
import net.minecraft.data.PackOutput
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.concurrent.CompletableFuture

private val BUILDER = RegistrySetBuilder()
    .add(Registries.ENCHANTMENT, AllEnchantments::bootstrap)
    .add(Registries.DAMAGE_TYPE, AllDamageTypes::bootstrap)
    .add(Registries.CONFIGURED_FEATURE, AllConfiguredFeatures::bootstrap)
    .add(Registries.PLACED_FEATURE, AllPlacedFeatures::bootstrap)
    .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, AllBiomeModifiers::bootstrap)

class GeneratedEntriesProvider(output: PackOutput, registries: CompletableFuture<HolderLookup.Provider>):
    DatapackBuiltinEntriesProvider(output, registries, BUILDER, setOf(CreateCCR.ID)) {

    override fun getName(): String {
        return "Create Construction Robots Generated Entries"
    }
}