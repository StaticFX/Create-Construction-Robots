package de.devin.ccr.blocks

import com.simibubi.create.api.stress.BlockStressValues
import com.tterrag.registrate.util.entry.BlockEntry
import de.devin.ccr.CreateCCR
import de.devin.ccr.content.beehive.MechanicalBeehiveBlock
import net.minecraft.world.level.block.Blocks


object AllBlocks {
    fun register() {}

    val MECHANICAL_BEEHIVE: BlockEntry<MechanicalBeehiveBlock> = CreateCCR.REGISTRATE.block(
        "mechanical_beehive"
    ) { MechanicalBeehiveBlock(it) }
        .initialProperties { Blocks.IRON_BLOCK }
        .properties { p -> p.noOcclusion() }
        .blockstate { c, p ->
            p.simpleBlock(
                c.getEntry(),
                p.models().getExistingFile(p.modLoc("block/mechanical_beehive/block"))
            )
        }
        .onRegister { block -> BlockStressValues.IMPACTS.register(block) { 256.0 } }
        .item()
        .model { c, p -> p.withExistingParent(c.name, p.modLoc("block/mechanical_beehive/item")) }
        .build()
        .register()


}


