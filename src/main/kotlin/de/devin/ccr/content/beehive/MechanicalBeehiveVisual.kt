package de.devin.ccr.content.beehive

import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import java.util.function.Consumer

class MechanicalBeehiveVisual(context: VisualizationContext, blockEntity: MechanicalBeehiveBlockEntity, partialTick: Float) :
    SingleAxisRotatingVisual<MechanicalBeehiveBlockEntity>(context, blockEntity, partialTick, Models.partial(com.simibubi.create.AllPartialModels.SHAFTLESS_COGWHEEL)),
    SimpleDynamicVisual {

    override fun beginFrame(ctx: DynamicVisual.Context?) {}
}