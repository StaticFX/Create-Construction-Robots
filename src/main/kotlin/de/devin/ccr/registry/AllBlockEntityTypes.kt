package de.devin.ccr.registry

import com.tterrag.registrate.util.entry.BlockEntityEntry
import de.devin.ccr.CreateCCR
import de.devin.ccr.blocks.AllBlocks
import de.devin.ccr.content.beehive.MechanicalBeehiveBlockEntity

object AllBlockEntityTypes {

    val MECHANICAL_BEEHIVE: BlockEntityEntry<MechanicalBeehiveBlockEntity> = CreateCCR.REGISTRATE
        .blockEntity("mechanical_beehive", ::MechanicalBeehiveBlockEntity)
        .validBlocks(AllBlocks.MECHANICAL_BEEHIVE)
        .register()

    fun register() {}
}
