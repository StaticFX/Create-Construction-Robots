package de.devin.cbbees.content.upgrades

/**
 * Data class representing the calculated stats for constructor robots
 * based on the installed upgrades in a backpack.
 */
data class BeeContext(
    var speedMultiplier: Double = 1.0,
    var carryCapacity: Int = 1,
    var workRange: Double = 32.0,
    var maxActiveRobots: Int = 4,
    var silkTouchEnabled: Boolean = false,
    var breakSpeedMultiplier: Double = 1.0,
    /** Maximum number of bees this source can contribute to a single job */
    var maxContributedBees: Int = 4,
    var fuelConsumptionMultiplier: Double = 1.0,
    /** Higher RPM → tighter wound spring → less drain per action */
    var springEfficiency: Double = 1.0
)
