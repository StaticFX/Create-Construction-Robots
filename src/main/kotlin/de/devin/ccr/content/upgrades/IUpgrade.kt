package de.devin.ccr.content.upgrades

/**
 * Interface for backpack upgrade logic.
 */
fun interface IUpgrade {
    /**
     * Applies the effect of this upgrade to the [RobotContext].
     * 
     * @param context The context to modify.
     * @param count The number of these upgrades installed in the backpack.
     */
    fun apply(context: RobotContext, count: Int)
}
