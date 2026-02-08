package de.devin.ccr.registry

import de.devin.ccr.CreateCCR
import dev.engine_room.flywheel.lib.model.baked.PartialModel

object AllPartialModels {
    private fun partial(path: String): PartialModel {
        return PartialModel.of(CreateCCR.asResource("block/$path"))
    }

    fun init() {}
}
