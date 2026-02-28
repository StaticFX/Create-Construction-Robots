package de.devin.cbbees.content.beehive

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.*
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.network.ClientBeeNetworkManager
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
import de.devin.cbbees.content.upgrades.BeeContext
import de.devin.cbbees.content.upgrades.BeeUpgradeItem
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.registry.AllEntityTypes
import net.createmod.catnip.lang.Lang
import net.createmod.catnip.lang.LangNumberFormat
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.client.ClientNeoForgeMod
import net.neoforged.neoforge.items.ItemStackHandler
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper
import java.util.*
import kotlin.math.abs

class MechanicalBeehiveBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    KineticBlockEntity(type, pos, state), IHaveGoggleInformation, BeeHive {

    override val id: UUID get() = homeId
    override val world: Level get() = getLevel()!!
    override val pos: BlockPos get() = blockPos

    private var homeId = UUID.randomUUID()

    /** Set of active bee UUIDs (server side only) */
    private val activeBees = mutableSetOf<UUID>()

    override fun getActiveBeeCount(): Int = activeBees.size

    val beeInventory = object : ItemStackHandler(9) {
        override fun onContentsChanged(slot: Int) = sync()
        override fun isItemValid(slot: Int, stack: ItemStack) = stack.item is MechanicalBeeItem
    }


    val inventory = CombinedInvWrapper(beeInventory)

    val instructions = mutableListOf<BeeInstruction>()

    override fun onLoad() {
        super.onLoad()
        if (level != null) {
            addToNetwork(level!!)
        }
    }

    override fun destroy() {
        removeFromNetwork(level!!)
        super.destroy()
    }

    fun spawnBee(tier: MechanicalBeeTier, batch: TaskBatch): Boolean {
        val bee = MechanicalBeeEntity(AllEntityTypes.MECHANICAL_BEE.get(), level!!).apply {
            this.tier = tier
            setPos(Vec3.atCenterOf(blockPos.above()))
            this.networkId = this@MechanicalBeehiveBlockEntity.network().id
        }

        bee.getBrain().setMemory(BeeMemoryModules.HIVE_POS.get(), this.blockPos)
        bee.getBrain().setMemory(BeeMemoryModules.HIVE_INSTANCE.get(), Optional.of(this))
        bee.getBrain().setMemory(BeeMemoryModules.CURRENT_TASK.get(), batch)

        batch.assignToRobot(bee)

        level!!.addFreshEntity(bee)
        activeBees.add(bee.uuid)
        sync()

        return true
    }

    override fun acceptBatch(batch: TaskBatch): Boolean {
        if (getAvailableBeeCount() <= 0) {
            return false // Safety check
        }

        this.setChanged()

        val beeTier = consumeBee() ?: return false
        return spawnBee(beeTier, batch)
    }

    override fun notifyTaskCompleted(task: BeeTask, bee: MechanicalBeeEntity): TaskBatch? {
        val nextBatch = GlobalJobPool.workBacklog(this)

        nextBatch?.assignToRobot(bee)

        return nextBatch
    }

    override fun onBeeRemoved(bee: MechanicalBeeEntity) {
        if (activeBees.remove(bee.uuid)) {
            sync()
        }
    }

    override fun walkTarget(): WalkTarget {
        return WalkTarget(Vec3.atCenterOf(blockPos.above()), 1.0f, 0)
    }


    override var networkId: UUID = UUID.randomUUID()
        set(value) {
            if (field == value) return
            val old = field
            field = value
            onNetworkIdChanged(old, value)
        }

    override fun sync() {
        setChanged()
        sendData()
    }

    override fun onSpeedChanged(previousSpeed: Float) {
        super.onSpeedChanged(previousSpeed)
        if (level != null && !level!!.isClientSide) {
            ServerBeeNetworkManager.registerWorker(this)
        }
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        tag.putUUID("HomeId", homeId)
        tag.putUUID("NetworkId", networkId)

        val activeBeesList = ListTag()
        activeBees.forEach { uuid ->
            val comp = CompoundTag()
            comp.putUUID("Id", uuid)
            activeBeesList.add(comp)
        }
        tag.put("ActiveBees", activeBeesList)

        tag.putInt("ActiveBeeCount", activeBees.size)
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
        if (tag.hasUUID("NetworkId")) {
            networkId = tag.getUUID("NetworkId")
        }

        activeBees.clear()
        if (tag.contains("ActiveBees", Tag.TAG_LIST.toInt())) {
            val list = tag.getList("ActiveBees", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val comp = list.getCompound(i)
                if (comp.hasUUID("Id")) {
                    activeBees.add(comp.getUUID("Id"))
                }
            }
        }
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
            context.workRange = (6 + rpm.toDouble())
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
                sync()
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
                sync()
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

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        // Show kinetic stats (speed, stress) from KineticBlockEntity
        super<KineticBlockEntity>.addToGoggleTooltip(tooltip, isPlayerSneaking)

        Lang.builder("cbbees").translate("gui.goggles.beehive_stats")
            .forGoggles(tooltip)

        // Network Info
        val net = network()
        net.let { n ->
            Lang.builder("cbbees").translate("gui.goggles.beehive.network")
                .style(ChatFormatting.GRAY)
                .add(Lang.builder("cbbees").text(n.name).style(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1)
        }

        // Flying Bees
        Lang.builder("cbbees").translate("gui.goggles.beehive.flying")
            .style(ChatFormatting.GRAY)
            .add(
                Lang.builder("cbbees").text(ChatFormatting.GOLD, LangNumberFormat.format(activeBees.count().toDouble()))
            )
            .forGoggles(tooltip, 1)

        // Stored Bees
        val storedBees = getAvailableBeeCount()

        Lang.builder("cbbees").translate("gui.goggles.beehive.stored")
            .style(ChatFormatting.GRAY)
            .add(Lang.builder("cbbees").text(ChatFormatting.GOLD, LangNumberFormat.format(storedBees.toDouble())))
            .forGoggles(tooltip, 1)

        // Capacity
        val context = getBeeContext()
        Lang.builder("cbbees").translate("gui.goggles.beehive.capacity")
            .style(ChatFormatting.GRAY)
            .add(
                Lang.builder("cbbees")
                    .text(ChatFormatting.GOLD, LangNumberFormat.format(context.maxActiveRobots.toDouble()))
            )
            .forGoggles(tooltip, 1)

        return true
    }

    override fun getIcon(isPlayerSneaking: Boolean): ItemStack {
        return AllItems.ANDESITE_BEE.asStack()
    }

    fun getMaterialSource(): MaterialSource {
        return WirelessMaterialSource(
            world,
            listOf(pos.below(), pos.north(), pos.south(), pos.east(), pos.west())
        )
    }
}
