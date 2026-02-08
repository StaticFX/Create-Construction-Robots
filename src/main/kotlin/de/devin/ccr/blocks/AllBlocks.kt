package de.devin.ccr.blocks

import com.simibubi.create.api.stress.BlockStressValues
import com.simibubi.create.foundation.data.AssetLookup
import com.tterrag.registrate.builders.ItemBuilder
import com.tterrag.registrate.providers.DataGenContext
import com.tterrag.registrate.providers.RegistrateItemModelProvider
import com.tterrag.registrate.util.entry.BlockEntry
import com.tterrag.registrate.util.nullness.NonNullBiConsumer
import com.tterrag.registrate.util.nullness.NonNullFunction
import de.devin.ccr.CreateCCR
import de.devin.ccr.content.beehive.MechanicalBeehiveBlock
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Blocks
import java.util.function.Supplier


// THIS LINE IS REQUIRED FOR USING PROPERTY DELEGATES

object AllBlocks {
    fun register() {}

    val MECHANICAL_BEEHIVE: BlockEntry<MechanicalBeehiveBlock> = CreateCCR.REGISTRATE.block(
        "mechanical_beehive"
    ) { MechanicalBeehiveBlock(it) }
        .initialProperties { Blocks.IRON_BLOCK }
        .properties { p -> p.noOcclusion() }
        .blockstate { c, p -> p.simpleBlock(c.getEntry(), p.models().getExistingFile(p.modLoc("block/mechanical_beehive/block"))) }
        .item()
        .model { c, p -> p.withExistingParent(c.name, p.modLoc("block/mechanical_beehive/item")) }
        .build()
        .register()


}


