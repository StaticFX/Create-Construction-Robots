package de.devin.ccr.content.domain.beehive

import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.bee.MechanicalBeeTier
import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * Interface for any entity that can contribute bees to work on jobs.
 *
 * A BeeSource represents a location from which bees can be deployed to work on tasks.
 * Multiple BeeSource instances can contribute bees to the same job, allowing for
 * cooperative work between beehives and backpacks.
 */
interface BeeHive {
    /**
     * Unique identifier for this bee source.
     */
    val sourceId: UUID

    /**
     * The world/level this source exists in.
     */
    val sourceWorld: Level

    /**
     * The position of this source in the world.
     */
    val sourcePosition: BlockPos

    /**
     * Gets the number of bees currently available in this source.
     * This is the count of bees that can be deployed for work.
     */
    fun getAvailableBeeCount(): Int

    /**
     * Gets the bee context (upgrades, stats) for this source.
     */
    fun getBeeContext(): BeeContext

    /**
     * Attempts to consume a bee from this source for deployment.
     * @return the tier of the bee consumed, or null if no bees available.
     */
    fun consumeBee(): MechanicalBeeTier?

    /**
     * Attempts to return a bee to this source.
     * @return true if the bee was successfully returned, false if the source is full.
     */
    fun returnBee(tier: MechanicalBeeTier): Boolean

    /**
     * Gets the maximum range at which bees from this source can work.
     * Jobs outside this range cannot be worked by bees from this source.
     */
    fun getWorkRange(): Double {
        return getBeeContext().workRange
    }

    /**
     * Checks if a position is within the work range of this source.
     */
    fun isInRange(pos: BlockPos): Boolean {
        val dx = pos.x - sourcePosition.x
        val dy = pos.y - sourcePosition.y
        val dz = pos.z - sourcePosition.z
        val distSq = dx * dx + dy * dy + dz * dz
        val range = getWorkRange()
        return distSq <= range * range
    }

    /**
     * Gets the maximum number of bees this source can contribute to a single job.
     * This is determined by upgrades and the source's capacity.
     */
    fun getMaxContributionBees(): Int {
        return getBeeContext().maxContributedBees
    }

    /**
     * Attempts to accept a task for processing by this BeeHive.
     * The task will only be accepted if it meets the criteria determined by the hive,
     * such as available resources, range constraints, and capacity.
     *
     * @param task The bee task to be evaluated and potentially accepted.
     * @return True if the task was successfully accepted by the hive, false otherwise.
     */
    fun acceptTask(task: BeeTask): Boolean

    /**
     * Marks the given task as completed by the given bee.
     * @return optionally next task to be processed, or null if all tasks are completed.
     */
    fun notifyTaskCompleted(task: BeeTask, bee: MechanicalBeeEntity): BeeTask?

    /**
     * Called when a bee from this source is spawned.
     */
    fun onBeeSpawned(bee: MechanicalBeeEntity) {}

    /**
     * Called when a bee from this source is removed/returned.
     */
    fun onBeeRemoved(bee: MechanicalBeeEntity) {}
}