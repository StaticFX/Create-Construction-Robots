package de.devin.cbbees.registry

import de.devin.cbbees.CreateBuzzyBeez
import dev.engine_room.flywheel.lib.model.baked.PartialModel

object AllPartialModels {
    private fun partial(path: String): PartialModel {
        return PartialModel.of(CreateBuzzyBeez.asResource("block/$path"))
    }

    fun init() {}
}
