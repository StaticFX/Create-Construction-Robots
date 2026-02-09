package de.devin.ccr.content.domain

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.backpack.PortableBeehiveItem
import de.devin.ccr.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.ccr.content.domain.bee.BeeContributionManager
import de.devin.ccr.content.domain.beehive.BeeHive
import de.devin.ccr.content.bee.MechanicalBeeTier
import de.devin.ccr.items.AllItems
import de.devin.ccr.content.domain.bee.IBeeHome
import de.devin.ccr.content.domain.beehive.PlayerBeeHive
import de.devin.ccr.content.domain.job.BeeJob
import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.schematics.goals.BeeJobGoal
import de.devin.ccr.content.schematics.goals.ConstructionGoal
import de.devin.ccr.content.schematics.goals.DeconstructionGoal
import de.devin.ccr.registry.AllEntityTypes
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Manages bee construction and deconstruction processes.
 *
 * Key responsibilities:
 * - Coordinates the start of construction and deconstruction jobs.
 * - Manages bee deployment (spawning) and recovery (returning to beehive).
 * - Supports multiple BeeSource instances contributing to the same job.
 */
object BeeWorkManager {

    fun stopAllTasks(player: ServerPlayer) {
        // Cancel all jobs owned by this player in GlobalJobPool
        GlobalJobPool.getAllJobs().filter { it.ownerId == player.uuid }.forEach { it.cancel() }

        player.displayClientMessage(
            Component.translatable("ccr.construction.stopped"),
            true
        )
    }

    fun startConstruction(player: ServerPlayer, schematicStack: ItemStack) {
        startJob(player, ConstructionGoal(schematicStack))
    }

    fun startDeconstruction(player: ServerPlayer, pos1: BlockPos, pos2: BlockPos) {
        startJob(player, DeconstructionGoal(pos1, pos2))
    }

    fun startJob(player: ServerPlayer, goal: BeeJobGoal) {
        val jobKey = goal.createJobKey(player.uuid)
        if (jobKey != null && GlobalJobPool.getJobByUniquenessKey(jobKey) != null) {
            player.displayClientMessage(goal.getAlreadyActiveMessage(), true)
            return
        }

        val error = goal.validate(player.level())
        if (error != null) {
            player.displayClientMessage(error, true)
            return
        }

        val jobId = UUID.randomUUID()
        val tasks = goal.generateTasks(jobId, player.level())
        if (tasks.isEmpty()) {
            player.displayClientMessage(goal.getNoTasksMessage(), true)
            return
        }

        val centerPos = goal.getCenterPos(player.level(), tasks)

        // Ensure player is registered as source
        val playerHome = PlayerBeeHive(player)

        // Check for available bees from any source (backpack or nearby beehives)
        val sourcesInRange = BeeContributionManager.findSourcesForJob(player.level(), centerPos)
        
        if (sourcesInRange.isEmpty()) {
            // No sources in range. Let's find why.
            val allSources = BeeContributionManager.getAllSources().filter { it.sourceWorld == player.level() }
            
            if (allSources.none { it is PlayerBeeHive && it.getAvailableBeeCount() > 0 || it is MechanicalBeehiveBlockEntity }) {
                 player.displayClientMessage(Component.translatable("ccr.construction.no_beehive"), true)
            } else {
                 player.displayClientMessage(Component.translatable("ccr.construction.out_of_range"), true)
            }
            return
        }

        val totalAvailableBees = sourcesInRange.sumOf { source ->
            minOf(source.getAvailableBeeCount(), source.getMaxContributedBees())
        }
        
        if (totalAvailableBees <= 0) {
            player.displayClientMessage(Component.translatable("ccr.construction.no_bees"), true)
            return
        }

        val globalJob = BeeJob(jobId, centerPos, 1)
        globalJob.ownerId = player.uuid
        globalJob.uniquenessKey = jobKey
        globalJob.addTasks(tasks)
        GlobalJobPool.registerJob(globalJob, player.level())

        // Gather contributions from all sources in range
        BeeContributionManager.contributeToJob(globalJob, player.level())

        CreateCCR.LOGGER.info("Registered job ${jobId} with GlobalJobPool at $centerPos")

        // Spawn bees from all contributing sources
        spawnBeesForJob(globalJob, player.level())
        
        goal.onJobStarted(player)

        player.displayClientMessage(goal.getStartMessage(tasks.size), true)
        CreateCCR.LOGGER.info("Job started for player ${player.name.string}")
    }

    fun returnBeeToBeehive(player: Player): Boolean {
        val home = PlayerBeeHive(player as? ServerPlayer ?: return false)
        return home.addBee(MechanicalBeeTier.ANDESITE)
    }

    private fun dropBeeItem(player: Player) {
        val itemEntity = ItemEntity(player.level(), player.x, player.y + 0.5, player.z, ItemStack(AllItems.ANDESITE_BEE.get(), 1))
        player.level().addFreshEntity(itemEntity)
    }

    private fun findBeehive(player: Player): ItemStack? {
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (stack.item is PortableBeehiveItem) return stack
        }
        // TODO: Check Curios
        return null
    }

    fun spawnBees(home: IBeeHome) {
        val context = home.getBeeContext()
        val maxRobots = context.maxActiveRobots
        
        // Count currently active bees for this home
        val activeCount = home.getActiveBeeCount()
        val toSpawn = maxRobots - activeCount

        for (i in 0 until toSpawn) {
            // Prevent spawning loop if no air/power available
            if (!home.hasAir(10)) break

            val tier = home.consumeBee() ?: break
            AllEntityTypes.MECHANICAL_BEE.create(home.world)?.apply {
                this.tier = tier
                setPos(home.position.x + 0.5, home.position.y + 1.5, home.position.z + 0.5)
                setHome(home)
                home.world.addFreshEntity(this)
            }
        }
    }
    
    // ==================== Multi-Source Job System ====================
    
    /**
     * Creates a new BeeJob and registers it with the GlobalJobPool.
     * Multiple BeeSource instances can then contribute bees to this job.
     * 
     * @param level The level the job is in.
     * @param centerPos The center position of the job.
     * @param tasks The tasks for this job.
     * @param requiredBeeCount Minimum bees needed to start (default 1).
     * @return The created BeeJob.
     */
    fun createJob(level: Level, centerPos: BlockPos, tasks: List<BeeTask>, requiredBeeCount: Int = 1): BeeJob {
        val job = BeeJob(UUID.randomUUID(), centerPos, requiredBeeCount)
        job.addTasks(tasks)
        GlobalJobPool.registerJob(job, level)
        return job
    }
    
    /**
     * Starts a job by gathering contributions from all available sources in range.
     * 
     * Example: 10 Bees in Backpack + 32 bees in beehive = 42 total bees available
     * 
     * @param job The job to start.
     * @param level The level the job is in.
     * @return true if the job was started successfully.
     */
    fun startJobWithMultipleSources(job: BeeJob, level: Level): Boolean {
        // Gather contributions from all sources in range
        val contributed = BeeContributionManager.contributeToJob(job, level)
        
        if (!job.canStart()) {
            CreateCCR.LOGGER.info("Job ${job.jobId} needs ${job.requiredBeeCount} bees but only $contributed available")
            return false
        }
        
        // Spawn bees from all contributing sources
        spawnBeesForJob(job, level)
        return true
    }
    
    /**
     * Spawns bees from all sources contributing to a job.
     * Each source spawns bees up to its contribution amount.
     * 
     * @param job The job to spawn bees for.
     * @param level The level to spawn in.
     */
    fun spawnBeesForJob(job: BeeJob, level: Level) {
        for (sourceId in job.contributingSources) {
            val source = BeeContributionManager.getSource(sourceId) ?: continue
            val contribution = job.getContribution(sourceId)
            
            // Get the source as IBeeHome if possible for spawning
            val home = source as? IBeeHome ?: continue
            
            val context = home.getBeeContext()
            val maxRobots = context.maxActiveRobots
            val activeCount = home.getActiveBeeCount()
            val canSpawn = minOf(contribution, maxRobots - activeCount)
            
            for (i in 0 until canSpawn) {
                // Prevent spawning loop if no air/power available
                if (!home.hasAir(10)) break
                
                val tier = home.consumeBee() ?: break
                AllEntityTypes.MECHANICAL_BEE.create(home.world)?.apply {
                    this.tier = tier
                    setPos(home.position.x + 0.5, home.position.y + 1.5, home.position.z + 0.5)
                    setHome(home)
                    home.world.addFreshEntity(this)
                }
            }
        }
    }
    
    /**
     * Calculates the total number of bees available from all sources that can reach a position.
     * 
     * @param level The level to check.
     * @param jobPos The position of the job.
     * @return Total bee count from all sources in range.
     */
    fun calculateAvailableBees(level: Level, jobPos: BlockPos): Int {
        return BeeContributionManager.calculateTotalBees(level, jobPos)
    }
    
    /**
     * Finds all BeeSource instances that can contribute to a job at the given position.
     * 
     * @param level The level to search.
     * @param jobPos The position of the job.
     * @return List of sources in range.
     */
    fun findSourcesForJob(level: Level, jobPos: BlockPos): List<BeeHive> {
        return BeeContributionManager.findSourcesForJob(level, jobPos)
    }
    
    /**
     * Cleans up completed jobs and stale contributions.
     * Should be called periodically (e.g., every few seconds).
     */
    fun cleanup() {
        GlobalJobPool.cleanup()
        BeeContributionManager.cleanup()
    }
}
