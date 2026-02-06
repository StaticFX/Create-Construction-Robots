package de.devin.ccr.blocks

import com.simibubi.create.api.stress.BlockStressValues
import com.tterrag.registrate.util.entry.BlockEntry
import de.devin.ccr.CreateCCR
import de.devin.ccr.content.beehive.MechanicalBeehiveBlock
import net.minecraft.world.level.block.Blocks


// THIS LINE IS REQUIRED FOR USING PROPERTY DELEGATES

object AllBlocks {
    fun register() {}

    val MECHANICAL_BEEHIVE: BlockEntry<MechanicalBeehiveBlock> = CreateCCR.REGISTRATE.block<MechanicalBeehiveBlock>(
        "mechanical_beehive"
    ) { MechanicalBeehiveBlock(it) }
        .initialProperties { Blocks.IRON_BLOCK }
        .onRegister { block -> BlockStressValues.IMPACTS.register(block) { 2048.0 } }
        .blockstate { c, p -> p.simpleBlock(c.get(), p.models().cubeAll(c.name, p.mcLoc("block/bee_nest_side"))) }
        .simpleItem()
        .register()

}
