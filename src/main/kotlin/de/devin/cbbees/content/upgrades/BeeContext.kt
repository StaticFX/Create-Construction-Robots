package de.devin.cbbees.content.upgrades

import de.devin.cbbees.config.CBBeesConfig

/**
 * Data class representing the calculated stats for constructor robots
 * based on the installed upgrades in a backpack.
 */
data class BeeContext(
    var speedMultiplier: Double = 1.0,
    var carryCapacity: Int = 1,
    var workRange: Double = CBBeesConfig.defaultWorkRange.get(),
    var maxActiveRobots: Int = CBBeesConfig.defaultMaxActiveRobots.get(),
    var silkTouchEnabled: Boolean = false,
    var dropItemsEnabled: Boolean = false,
    var breakSpeedMultiplier: Double = 1.0,
    /** Maximum number of bees this source can contribute to a single job */
    var maxContributedBees: Int = CBBeesConfig.defaultMaxActiveRobots.get(),
    var fuelConsumptionMultiplier: Double = 1.0,
    /** Higher RPM → tighter wound spring → less drain per action */
    var springEfficiency: Double = 1.0
)
