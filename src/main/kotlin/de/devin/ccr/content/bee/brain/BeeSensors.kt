package de.devin.ccr.content.bee.brain

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.bee.brain.sensor.BeeHiveSensor
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.ai.sensing.SensorType

object BeeSensors {
    val HIVE_SENSOR = CreateCCR.REGISTRATE.generic("hive_sensor", Registries.SENSOR_TYPE) {
        SensorType { BeeHiveSensor() }
    }.register()
}