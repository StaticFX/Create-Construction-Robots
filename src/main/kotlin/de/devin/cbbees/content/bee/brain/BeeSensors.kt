package de.devin.cbbees.content.bee.brain

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.brain.sensor.BeeHiveSensor
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.ai.sensing.SensorType

object BeeSensors {
    val HIVE_SENSOR = CreateBuzzyBeez.REGISTRATE.generic("hive_sensor", Registries.SENSOR_TYPE) {
        SensorType { BeeHiveSensor() }
    }.register()

    fun register() {}

}