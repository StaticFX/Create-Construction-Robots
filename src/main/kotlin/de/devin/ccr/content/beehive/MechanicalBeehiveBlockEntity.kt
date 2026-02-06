package de.devin.ccr.content.beehive

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import de.devin.ccr.content.robots.IBeeHome
import de.devin.ccr.content.robots.MaterialSource
import de.devin.ccr.content.robots.MechanicalBeeEntity
import de.devin.ccr.content.robots.WirelessMaterialSource
import de.devin.ccr.content.schematics.BeeTask
import de.devin.ccr.content.schematics.BeeTaskManager
import de.devin.ccr.content.schematics.FertilizeAction
import de.devin.ccr.content.schematics.RemoveAction
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

class MechanicalBeehiveBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) : 
    KineticBlockEntity(type, pos, state), IBeeHome, IHaveGoggleInformation {
    
    override val world: Level get() = getLevel()!!
    override val position: BlockPos get() = getBlockPos()
    override val taskManager = BeeTaskManager()
    
    private var homeId = UUID.randomUUID()
    private var scanCooldown = 0

    /** Set of active bee UUIDs (server side only) */
    private val activeBees = mutableSetOf<UUID>()
    
    /** Count of active bees synced to clients */
    private var activeBeeCount = 0

    val beeInventory = object : ItemStackHandler(9) {
        override fun onContentsChanged(slot: Int) = setChanged()
        override fun isItemValid(slot: Int, stack: net.minecraft.world.item.ItemStack) = stack.item == AllItems.MECHANICAL_BEE.get()
    }
    
    val upgradeInventory = object : ItemStackHandler(9) {
        override fun onContentsChanged(slot: Int) = setChanged()
        override fun isItemValid(slot: Int, stack: net.minecraft.world.item.ItemStack) = stack.item is de.devin.ccr.content.upgrades.NaturifiedUpgradeItem
    }
    
    val inventory = CombinedInvWrapper(beeInventory, upgradeInventory)

    val instructions = mutableListOf<BeeInstruction>()

    override fun tick() {
        super.tick()
        if (world.isClientSide) return
        
        // Don't work if no rotational power or overstressed
        if (getSpeed() == 0f) return
        
        if (scanCooldown <= 0) {
            scanForWork()
            scanCooldown = 100 // 5 seconds
        } else {
            scanCooldown--
        }
        
        if (taskManager.hasPendingTasks()) {
            de.devin.ccr.content.schematics.BeeWorkManager.spawnBees(this)
        }
    }

    private fun scanForWork() {
        if (instructions.isEmpty()) return
        
        val context = getBeeContext()
        
        for (instruction in instructions) {
            when (instruction.type) {
                InstructionType.FERTILIZE -> scanForFertilize(instruction, context)
                InstructionType.DECONSTRUCT -> scanForDeconstruct(instruction, context)
            }
        }
    }

    private fun scanForFertilize(instruction: BeeInstruction, context: BeeContext) {
        val radius = instruction.range.coerceAtMost(32)
        val center = position
        
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = center.offset(x, y, z)
                    if (taskManager.hasTaskAt(pos)) continue
                    
                    val state = world.getBlockState(pos)
                    if (state.block is net.minecraft.world.level.block.BonemealableBlock) {
                        val bonemealable = state.block as net.minecraft.world.level.block.BonemealableBlock
                        if (bonemealable.isValidBonemealTarget(world, pos, state)) {
                            taskManager.addTask(BeeTask(
                                FertilizeAction(),
                                pos,
                                0
                            ))
                        }
                    }
                }
            }
        }
    }

    private fun scanForDeconstruct(instruction: BeeInstruction, context: BeeContext) {
        val radius = instruction.range.coerceAtMost(32)
        val center = position
        
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = center.offset(x, y, z)
                    if (pos == position) continue
                    if (taskManager.hasTaskAt(pos)) continue
                    
                    val state = world.getBlockState(pos)
                    if (!state.isAir && state.getDestroySpeed(world, pos) >= 0) {
                        // For deconstruction, we might want to prioritize certain blocks or just everything
                        taskManager.addTask(BeeTask(
                            RemoveAction(),
                            pos,
                            0
                        ))
                    }
                }
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
            if (!stack.isEmpty && stack.item is de.devin.ccr.content.upgrades.NaturifiedUpgradeItem) {
                val upgrade = stack.item as de.devin.ccr.content.upgrades.NaturifiedUpgradeItem
                upgrade.upgradeType.logic.apply(context, 1)
            }
        }

        // RPM based speed and robot limit
        val rpm = Math.abs(getSpeed())
        if (rpm > 0) {
            context.speedMultiplier *= (1.0 + (rpm / 256.0))
            // 8x RPM multiplier for bee count as requested
            // Base is usually 4 from context, we add based on RPM
            context.maxActiveRobots += (rpm / 8.0).toInt()
        } else {
            // No power, no bees? Or just base bees?
            // Usually in Create, no power means no work.
            context.maxActiveRobots = 0
        }

        return context
    }

    override fun consumeAir(amount: Int): Int {
        // Mechanical Beehive uses kinetic power instead of pressurized air.
        // If it has rotational power, it provides unlimited "air" to bees.
        return if (Math.abs(getSpeed()) > 0) amount else 0
    }

    override fun addBee(): Boolean {
        for (i in 0 until beeInventory.slots) {
            val stack = beeInventory.getStackInSlot(i)
            if (stack.isEmpty) {
                beeInventory.setStackInSlot(i, de.devin.ccr.items.AllItems.MECHANICAL_BEE.asStack())
                return true
            } else if (stack.item == de.devin.ccr.items.AllItems.MECHANICAL_BEE.get() && stack.count < stack.maxStackSize) {
                stack.grow(1)
                return true
            }
        }
        return false
    }

    override fun consumeBee(): Boolean {
        for (i in 0 until beeInventory.slots) {
            val stack = beeInventory.getStackInSlot(i)
            if (!stack.isEmpty) {
                stack.shrink(1)
                return true
            }
        }
        return false
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
        return AllItems.MECHANICAL_BEE.asStack()
    }

    override fun getMaterialSource(): MaterialSource {
        return WirelessMaterialSource(world, listOf(position.below(), position.north(), position.south(), position.east(), position.west()))
    }

    override fun getHomeId(): UUID = homeId
}
