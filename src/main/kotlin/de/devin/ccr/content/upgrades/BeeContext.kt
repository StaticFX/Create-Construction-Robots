package de.devin.ccr.content.upgrades

/**
 * Data class representing the calculated stats for constructor robots
 * based on the installed upgrades in a backpack.
 */
data class BeeContext(
    var speedMultiplier: Double = 1.0,
    var carryCapacity: Int = 1,
    var workRange: Double = 32.0,
    var maxActiveRobots: Int = 4,
    var wirelessLinkEnabled: Boolean = false,
    var precisionEnabled: Boolean = false,
    var silkTouchEnabled: Boolean = false,
    var pickupEnabled: Boolean = false,
    var breakSpeedMultiplier: Double = 1.0,
    /** Maximum number of bees this source can contribute to a single job */
    var maxContributedBees: Int = 4,
    var airConsumptionMultiplier: Double = 1.0
)
