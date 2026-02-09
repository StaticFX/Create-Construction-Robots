package de.devin.ccr.content.beehive

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import de.devin.ccr.content.bee.*
import de.devin.ccr.content.domain.GlobalJobPool
import de.devin.ccr.content.domain.bee.BeeContributionManager
import de.devin.ccr.content.domain.beehive.BeeHive
import de.devin.ccr.content.upgrades.BeeContext
import de.devin.ccr.items.AllItems
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
import kotlin.math.min

class MechanicalBeehiveBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) : 
    KineticBlockEntity(type, pos, state), IHaveGoggleInformation, BeeHive {
    
    val world: Level get() = getLevel()!!
    val position: BlockPos get() = blockPos
    
    override val sourceId: UUID get() = homeId
    override val sourceWorld: Level get() = getLevel()!!
    override val sourcePosition: BlockPos get() = blockPos
    
    private var homeId = UUID.randomUUID()
    private var maintenanceJobId: UUID? = null

    /** Set of active bee UUIDs (server side only) */
    private val activeBees = mutableSetOf<UUID>()
    
    /** Count of active bees synced to clients */
    private var activeBeeCount = 0

    val beeInventory = object : ItemStackHandler(9) {
        override fun onContentsChanged(slot: Int) = setChanged()
        override fun isItemValid(slot: Int, stack: ItemStack) = stack.item is MechanicalBeeItem
    }
    
    val inventory = CombinedInvWrapper(beeInventory)

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

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        tag.putUUID("HomeId", homeId)
        tag.putInt("ActiveBeeCount", activeBeeCount)
        tag.put("BeeInv", beeInventory.serializeNBT(registries))

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

        instructions.clear()
        val instList = tag.getList("Instructions", Tag.TAG_COMPOUND.toInt())
        for (i in 0 until instList.size) {
            instructions.add(BeeInstruction.deserializeNBT(instList.getCompound(i)))
        }
    }

    override fun getBeeContext(): BeeContext {
        val context = BeeContext()

        val rpm = abs(getSpeed())
        if (rpm > 0) {
            context.speedMultiplier *= (1.0 + (rpm / 256.0))
            // 8x RPM multiplier for bee count as requested
            // Base is usually 4 from context, we add based on RPM
            val extraRobots = (rpm / 8.0).toInt()
            context.maxActiveRobots += extraRobots
            context.workRange = min((6 + rpm / 10).toDouble(), 32.0)
            context.maxContributedBees += extraRobots
        } else {
            context.maxActiveRobots = 0
            context.maxContributedBees = 0
            context.workRange = 0.0
        }

        return context
    }

    fun addBee(tier: MechanicalBeeTier): Boolean {
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

    fun getMaterialSource(): MaterialSource {
        return WirelessMaterialSource(world, listOf(position.below(), position.north(), position.south(), position.east(), position.west()))
    }
}
