package de.devin.ccr.registry

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer
import com.tterrag.registrate.util.entry.BlockEntityEntry
import com.tterrag.registrate.util.nullness.NonNullFunction
import de.devin.ccr.CreateCCR
import de.devin.ccr.blocks.AllBlocks
import de.devin.ccr.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.ccr.content.beehive.MechanicalBeehiveRenderer
import de.devin.ccr.content.logistics.ports.LogisticPortBlockEntity
import de.devin.ccr.content.logistics.ports.LogisticsPortRenderer

object AllBlockEntityTypes {

    val MECHANICAL_BEEHIVE: BlockEntityEntry<MechanicalBeehiveBlockEntity> = CreateCCR.REGISTRATE
        .blockEntity("mechanical_beehive", ::MechanicalBeehiveBlockEntity)
        .visual({ SingleAxisRotatingVisual.of(AllPartialModels.SHAFTLESS_COGWHEEL) }, false)
        .validBlocks(AllBlocks.MECHANICAL_BEEHIVE)
        .renderer { NonNullFunction { MechanicalBeehiveRenderer(it) } }
        .register()

    val LOGISTICS_PORT: BlockEntityEntry<LogisticPortBlockEntity> = CreateCCR.REGISTRATE
        .blockEntity("logistics_port", ::LogisticPortBlockEntity)
        .validBlocks(AllBlocks.LOGISTICS_PORT)
        .renderer { NonNullFunction { LogisticsPortRenderer(it) } }
        .register()

    fun register() {}
}
