package de.devin.ccr.content.beehive

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import de.devin.ccr.content.robots.*
import de.devin.ccr.content.schematics.BeeJob
import de.devin.ccr.content.schematics.BeeTask
import de.devin.ccr.content.schematics.GlobalJobPool
import de.devin.ccr.content.upgrades.BeeContext
import de.devin.ccr.content.upgrades.BeeUpgradeItem
import de.devin.ccr.items.AllItems
import de.devin.ccr.registry.AllEntityTypes
import net.createmod.catnip.lang.Lang
import net.createmod.catnip.lang.LangNumberFormat
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.items.ItemStackHandler
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper
import java.util.*
import kotlin.math.abs

class MechanicalBeehiveBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) : 
    KineticBlockEntity(type, pos, state), IBeeHome, IHaveGoggleInformation, BeeSource {
    
    override val world: Level get() = getLevel()!!
    override val position: BlockPos get() = blockPos
    
    // BeeSource implementation
    override val sourceId: UUID get() = homeId
    override val sourceWorld: Level get() = getLevel()!!
    override val sourcePosition: BlockPos get() = blockPos
    
    private var homeId = UUID.randomUUID()
    private var scanCooldown = 0
    private var maintenanceJobId: UUID? = null

    /** Set of active bee UUIDs (server side only) */
    private val activeBees = mutableSetOf<UUID>()
    
    /** Count of active bees synced to clients */
    private var activeBeeCount = 0

    val beeInventory = object : ItemStackHandler(9) {
        override fun onContentsChanged(slot: Int) = setChanged()
        override fun isItemValid(slot: Int, stack: ItemStack) = stack.item is MechanicalBeeItem
    }
    
    val upgradeInventory = object : ItemStackHandler(9) {
        override fun onContentsChanged(slot: Int) = setChanged()
        override fun isItemValid(slot: Int, stack: ItemStack) = stack.item is BeeUpgradeItem
    }
    
    val inventory = CombinedInvWrapper(beeInventory, upgradeInventory)

    val instructions = mutableListOf<BeeInstruction>()
    
    /** Flag to track if we've registered with BeeContributionManager */
    private var registeredAsSource = false

    override fun setLevel(level: net.minecraft.world.level.Level) {
        super.setLevel(level)
        // Register with BeeContributionManager so we can contribute to jobs
        if (!level.isClientSide && !registeredAsSource) {
            BeeContributionManager.registerSource(this)
            registeredAsSource = true
        }
    }
    
    override fun destroy() {
        // Unregister from BeeContributionManager when destroyed
        if (!getLevel()!!.isClientSide) {
            if (registeredAsSource) {
                BeeContributionManager.unregisterSource(sourceId)
                registeredAsSource = false
            }
            maintenanceJobId?.let { GlobalJobPool.unregisterJob(it) }
        }
        super.destroy()
    }

    override fun tick() {
        super.tick()
        if (world.isClientSide) return
        
        // Don't work if no rotational power or overstressed
        if (getSpeed() == 0f) return
        
        if (scanCooldown <= 0) {
            scanForWork()
            scanForGlobalJobs()
            scanCooldown = 100 // 5 seconds
        } else {
            scanCooldown--
        }
        
        // Always try to spawn bees for jobs we're contributing to (including our own)
        spawnBeesForGlobalJobs()
    }
    
    /**
     * Scans for jobs in the GlobalJobPool that this beehive can contribute to.
     * This allows the beehive to help with player-initiated construction/deconstruction.
     */
    private fun scanForGlobalJobs() {
        val jobsInRange = GlobalJobPool.findJobsForSource(this)
        
        for (job in jobsInRange) {
            if (job.status == BeeJob.JobStatus.WAITING_FOR_BEES || job.status == BeeJob.JobStatus.IN_PROGRESS) {
                // Contribute bees to this job if we haven't already maxed out
                val currentContribution = job.getContribution(sourceId)
                val maxContribution = getMaxContributedBees()
                val availableBees = getAvailableBeeCount()
                
                if (currentContribution < maxContribution && availableBees > 0) {
                    val maxTargetBees = maxOf(job.requiredBeeCount, job.tasks.size)
                    val remainingNeeded = maxTargetBees - job.contributedBees
                    
                    var toContribute = minOf(availableBees, maxContribution - currentContribution)
                    if (job.tasks.isNotEmpty()) {
                        toContribute = minOf(toContribute, remainingNeeded)
                    }
                    
                    if (toContribute > 0) {
                        job.addContribution(sourceId, toContribute)
                    }
                }
            }
        }
    }
    
    /**
     * Spawns bees to work on global jobs this beehive is contributing to.
     */
    private fun spawnBeesForGlobalJobs() {
        val jobsInRange = GlobalJobPool.findJobsForSource(this)
        
        for (job in jobsInRange) {
            if (job.status != BeeJob.JobStatus.IN_PROGRESS) continue
            if (!job.canStart()) continue
            
            val contribution = job.getContribution(sourceId)
            if (contribution <= 0) continue
            
            // Count active bees for this home
            val activeCount = getActiveBeeCount()
            val context = getBeeContext()
            val maxRobots = context.maxActiveRobots
            
            // Spawn bees up to our contribution or max active limit
            val canSpawn = minOf(contribution, maxRobots - activeCount)
            
            for (i in 0 until canSpawn) {
                val tier = consumeBee() ?: break
                
                AllEntityTypes.MECHANICAL_BEE.create(world)?.apply {
                    this.tier = tier
                    setPos(position.x + 0.5, position.y + 1.5, position.z + 0.5)
                    setHome(this@MechanicalBeehiveBlockEntity)
                    // The bee will check GlobalJobPool for tasks via BeeExecuteTaskGoal
                    world.addFreshEntity(this)
                }
            }
        }
    }

    private fun scanForWork() {
        if (instructions.isEmpty()) {
            maintenanceJobId?.let { GlobalJobPool.unregisterJob(it) }
            maintenanceJobId = null
            return
        }
        
        // Create or get maintenance job
        var job = maintenanceJobId?.let { GlobalJobPool.getJob(it) }
        if (job == null) {
            val newId = UUID.randomUUID()
            job = BeeJob(newId, blockPos, 1)
            GlobalJobPool.registerJob(job, world)
            maintenanceJobId = newId
        }

        for (instruction in instructions) {
            val goal = instruction.type.getGoal(blockPos, instruction.range)
            val newTasks = goal.generateTasks(job.jobId, world)
            
            // Filter out tasks that are already in the job and not completed/failed
            val filteredTasks = newTasks.filter { task ->
                job.tasks.none { it.targetPos == task.targetPos && it.status != BeeTask.TaskStatus.COMPLETED && it.status != BeeTask.TaskStatus.FAILED }
            }
            
            if (filteredTasks.isNotEmpty()) {
                job.addTasks(filteredTasks)
            }
        }
    }


    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        tag.putUUID("HomeId", homeId)
        tag.putInt("ActiveBeeCount", activeBeeCount)
        tag.put("BeeInv", beeInventory.serializeNBT(registries))
        tag.put("UpgradeInv", upgradeInventory.serializeNBT(registries))
        
        val instList = ListTag()
        instructions.forEach { 
            val instTag = CompoundTag()
            it.serializeNBT(instTag)
            instList.add(instTag)
        }
        tag.put("Instructions", instList)
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(tag, registries, clientPacket)
        if (tag.hasUUID("HomeId")) {
            homeId = tag.getUUID("HomeId")
        }
        activeBeeCount = tag.getInt("ActiveBeeCount")
        beeInventory.deserializeNBT(registries, tag.getCompound("BeeInv"))
        upgradeInventory.deserializeNBT(registries, tag.getCompound("UpgradeInv"))
        
        instructions.clear()
        val instList = tag.getList("Instructions", Tag.TAG_COMPOUND.toInt())
        for (i in 0 until instList.size) {
            instructions.add(BeeInstruction.deserializeNBT(instList.getCompound(i)))
        }
    }

    override fun getBeeContext(): BeeContext {
        val context = BeeContext()
        for (i in 0 until upgradeInventory.slots) {
            val stack = upgradeInventory.getStackInSlot(i)
            if (!stack.isEmpty && stack.item is de.devin.ccr.content.upgrades.BeeUpgradeItem) {
                val upgrade = stack.item as de.devin.ccr.content.upgrades.BeeUpgradeItem
                upgrade.upgradeType.logic.apply(context, 1)
            }
        }

        // RPM based speed and robot limit
        val rpm = abs(getSpeed())
        if (rpm > 0) {
            context.speedMultiplier *= (1.0 + (rpm / 256.0))
            // 8x RPM multiplier for bee count as requested
            // Base is usually 4 from context, we add based on RPM
            val extraRobots = (rpm / 8.0).toInt()
            context.maxActiveRobots += extraRobots
            context.maxContributedBees += extraRobots
        } else {
            context.maxActiveRobots = 0
            context.maxContributedBees = 0
        }

        return context
    }

    override fun consumeAir(amount: Int): Int {
        // Mechanical Beehive uses kinetic power instead of pressurized air.
        // If it has rotational power, it provides unlimited "air" to bees.
        return if (abs(getSpeed()) > 0) amount else 0
    }

    override fun getActiveBeeCount(): Int = activeBeeCount

    override fun addBee(tier: MechanicalBeeTier): Boolean {
        val item = tier.item()
        for (i in 0 until beeInventory.slots) {
            val stack = beeInventory.getStackInSlot(i)
            if (stack.isEmpty) {
                beeInventory.setStackInSlot(i, ItemStack(item, 1))
                return true
            } else if (stack.item == item && stack.count < stack.maxStackSize) {
                stack.grow(1)
                return true
            }
        }
        return false
    }

    override fun consumeBee(): MechanicalBeeTier? {
        for (i in 0 until beeInventory.slots) {
            val stack = beeInventory.getStackInSlot(i)
            if (!stack.isEmpty && stack.item is MechanicalBeeItem) {
                val tier = (stack.item as MechanicalBeeItem).tier
                stack.shrink(1)
                return tier
            }
        }
        return null
    }

    override fun returnBee(tier: MechanicalBeeTier): Boolean {
        return addBee(tier)
    }

    // BeeSource implementation
    override fun getAvailableBeeCount(): Int {
        var count = 0
        for (i in 0 until beeInventory.slots) {
            val stack = beeInventory.getStackInSlot(i)
            if (!stack.isEmpty) {
                count += stack.count
            }
        }
        return count
    }

    override fun onBeeSpawned(bee: MechanicalBeeEntity) {
        if (activeBees.add(bee.uuid)) {
            activeBeeCount = activeBees.size
            sendData()
        }
    }

    override fun onBeeRemoved(bee: MechanicalBeeEntity) {
        if (activeBees.remove(bee.uuid)) {
            activeBeeCount = activeBees.size
            sendData()
        }
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        // Show kinetic stats (speed, stress) from KineticBlockEntity
        super<KineticBlockEntity>.addToGoggleTooltip(tooltip, isPlayerSneaking)
        
        Lang.builder("ccr").translate("gui.goggles.beehive_stats")
            .forGoggles(tooltip)
        
        // Flying Bees
        Lang.builder("ccr").translate("gui.goggles.beehive.flying")
            .style(ChatFormatting.GRAY)
            .add(Lang.builder("ccr").text(ChatFormatting.GOLD, LangNumberFormat.format(activeBeeCount.toDouble())))
            .forGoggles(tooltip, 1)

        // Stored Bees
        val storedBees = (0 until beeInventory.slots).sumOf { beeInventory.getStackInSlot(it).count }
        Lang.builder("ccr").translate("gui.goggles.beehive.stored")
            .style(ChatFormatting.GRAY)
            .add(Lang.builder("ccr").text(ChatFormatting.GOLD, LangNumberFormat.format(storedBees.toDouble())))
            .forGoggles(tooltip, 1)

        // Capacity
        val context = getBeeContext()
        Lang.builder("ccr").translate("gui.goggles.beehive.capacity")
            .style(ChatFormatting.GRAY)
            .add(Lang.builder("ccr").text(ChatFormatting.GOLD, LangNumberFormat.format(context.maxActiveRobots.toDouble())))
            .forGoggles(tooltip, 1)

        return true
    }

    override fun getIcon(isPlayerSneaking: Boolean): ItemStack {
        return AllItems.ANDESITE_BEE.asStack()
    }

    override fun getMaterialSource(): MaterialSource {
        return WirelessMaterialSource(world, listOf(position.below(), position.north(), position.south(), position.east(), position.west()))
    }

    override fun getHomeId(): UUID = homeId
}
