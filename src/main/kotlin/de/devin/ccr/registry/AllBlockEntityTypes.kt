package de.devin.ccr.registry

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual
import com.tterrag.registrate.util.entry.BlockEntityEntry
import de.devin.ccr.CreateCCR
import de.devin.ccr.blocks.AllBlocks
import de.devin.ccr.content.beehive.MechanicalBeehiveBlockEntity

import de.devin.ccr.content.beehive.MechanicalBeehiveRenderer
import com.tterrag.registrate.util.nullness.NonNullFunction
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer

object AllBlockEntityTypes {

    val MECHANICAL_BEEHIVE: BlockEntityEntry<MechanicalBeehiveBlockEntity> = CreateCCR.REGISTRATE
        .blockEntity("mechanical_beehive", ::MechanicalBeehiveBlockEntity)
        .visual({ SingleAxisRotatingVisual.of(AllPartialModels.SHAFTLESS_COGWHEEL) }, false)
        .validBlocks(AllBlocks.MECHANICAL_BEEHIVE)
        .renderer { NonNullFunction { MechanicalBeehiveRenderer(it) } }
        .register()

    fun register() {}
}
