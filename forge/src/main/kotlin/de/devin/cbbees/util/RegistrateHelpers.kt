package de.devin.cbbees.util

import com.simibubi.create.api.behaviour.display.DisplaySource
import com.simibubi.create.api.behaviour.display.DisplaySource.BY_BLOCK
import com.simibubi.create.api.registry.CreateRegistries
import com.tterrag.registrate.builders.BlockBuilder
import com.tterrag.registrate.util.entry.RegistryEntry
import com.tterrag.registrate.util.nullness.NonNullUnaryOperator
import net.minecraft.world.level.block.Block

/**
 * Forge 1.20.1 — RegistryEntry has a single type parameter.
 */
fun <B : Block, P> displaySource(
    source: RegistryEntry<out DisplaySource>
): NonNullUnaryOperator<BlockBuilder<B, P>> {
    return NonNullUnaryOperator { builder ->
        builder.onRegisterAfter(
            CreateRegistries.DISPLAY_SOURCE
        ) { block ->
            BY_BLOCK.add(block, source.get())
        }
    }
}
