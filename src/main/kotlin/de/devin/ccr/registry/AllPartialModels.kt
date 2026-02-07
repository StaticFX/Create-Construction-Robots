package de.devin.ccr.registry

import de.devin.ccr.CreateCCR
import dev.engine_room.flywheel.lib.model.baked.PartialModel

object AllPartialModels {
    val BEEHIVE_BASE = partial("mechanical_beehive/base")
    val BEEHIVE_ROTATING_PART = partial("mechanical_beehive/rotating_part")
    val BEEHIVE_TOP = partial("mechanical_beehive/top")
    val BEEHIVE_DOOR = partial("mechanical_beehive/door")

    private fun partial(path: String): PartialModel {
        return PartialModel(CreateCCR.asResource("block/$path"))
    }

    fun init() {}
}
