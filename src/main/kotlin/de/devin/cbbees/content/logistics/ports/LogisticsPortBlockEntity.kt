package de.devin.cbbees.content.logistics.ports

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.domain.network.BeeNetworkManager
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.logistics.ports.LogisticPortBlock.Companion.PORT_STATE
import net.createmod.catnip.lang.Lang
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.ItemHandlerHelper
import java.util.*

class LogisticPortBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    SmartBlockEntity(type, pos, state), LogisticsPort, IHaveGoggleInformation {

    lateinit var filteringBehavior: FilteringBehaviour

    lateinit var priorityBehavior: ScrollValueBehaviour

    override val sourceId: UUID get() = homeId
    override val sourceWorld: Level get() = getLevel()!!
    override val sourcePosition: BlockPos get() = blockPos

    private var registeredAsSource = false
    private val homeId = UUID.randomUUID()

    var filterStack: ItemStack = ItemStack.EMPTY
    var priority = 0

    // This is what the bees will call
    fun getInventory(level: Level): IItemHandler? {
        val attachedDir = LogisticPortBlock.getConnectedDirection(blockState).opposite
        val attachedPos = blockPos.relative(attachedDir)
        return level.getCapability(Capabilities.ItemHandler.BLOCK, attachedPos, attachedDir.opposite)
    }

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        filteringBehavior = FilteringBehaviour(this, LogisticsPortFilterValueBox()).withCallback { onFilterChanged() }
        priorityBehavior = ScrollValueBehaviour(
            Component.translatable("gui.cbbees.logistics.priority.title"),
            this,
            LogisticsPortSelectionValueBox()
        )
            .between(0, 256)
            .withCallback {
                priority = it
            }

        behaviours.add(priorityBehavior)
        behaviours.add(filteringBehavior)
    }

    private fun onFilterChanged() {
        if (!level!!.isClientSide) {
            setChanged()
            sendData()
        }
    }

    override fun setLevel(level: Level) {
        super.setLevel(level)
        if (!registeredAsSource) {
            BeeNetworkManager.registerPort(this)
            registeredAsSource = true
        }
    }

    override fun priority(): Int = priority

    override fun destroy() {
        super.destroy()
        if (registeredAsSource) {
            BeeNetworkManager.unregisterPort(this)
            registeredAsSource = false
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        filterStack = ItemStack.parseOptional(registries, tag.getCompound("Filter"))
        super.loadAdditional(tag, registries)
    }

    override fun getFilter(): ItemStack {
        return filterStack
    }

    override fun getItemHandler(level: Level): IItemHandler? {
        return getInventory(level)
    }

    override fun getPortType(): PortType = portType()

    override fun isValidForDropOff(): Boolean {
        if (portType() != PortType.INSERT) return false
        if (portState() == PortState.INVALID) return false
        return true
    }

    private fun portState() = blockState.getValue(PORT_STATE)

    private fun portType() = blockState.getValue(LogisticPortBlock.PORT_TYPE)

    override fun isValidForPickup(): Boolean {
        if (portType() == PortType.EXTRACT) return false
        if (portState() == PortState.INVALID) return false
        return true
    }

    override fun canBeeDropOffItem(bee: MechanicalBeeEntity): Boolean {
        // TODO implement
        return true;
    }

    override fun walkTarget(): WalkTarget {
        return WalkTarget(blockPos, 1.0f, 1)
    }

    override fun hasItemStack(stack: ItemStack): Boolean {
        val level = level ?: return false

        val handler = getItemHandler(level) ?: return false

        for (i in 0 until handler.slots) {
            val stackInSlot = handler.getStackInSlot(i)

            if (!stackInSlot.isEmpty && ItemStack.isSameItemSameComponents(stackInSlot, stack)) {
                return true
            }
        }

        return false
    }

    override fun removeItemStack(stack: ItemStack): Boolean {
        val level = level ?: return false
        val handler = getItemHandler(level) ?: return false

        for (i in 0 until handler.slots) {
            val inSlot = handler.getStackInSlot(i)

            // Match item and components (NBT)
            if (!inSlot.isEmpty && ItemStack.isSameItemSameComponents(inSlot, stack)) {
                // We try to extract 1 or the stack size.
                // set 'simulate' to false to actually perform the removal.
                val extracted = handler.extractItem(i, stack.count, false)

                if (!extracted.isEmpty) {
                    return true
                }
            }
        }
        return false
    }

    override fun addItemStack(stack: ItemStack): ItemStack {
        val level = level ?: return stack
        val handler = getItemHandler(level) ?: return stack

        // ItemHandlerHelper.insertItemStacked handles:
        // 1. Finding existing stacks to merge with.
        // 2. Finding empty slots.
        // 3. Returning the "remainder" that didn't fit.
        return ItemHandlerHelper.insertItemStacked(handler, stack, false)
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        val network = BeeNetworkManager.getNetworks().find { it.ports.contains(this) }
        if (network != null) {
            Lang.builder("cbbees").translate("gui.goggles.beehive.network")
                .style(ChatFormatting.GRAY)
                .add(Lang.builder("cbbees").text(network.name).style(ChatFormatting.GOLD))
                .forGoggles(tooltip)
        }
        return true
    }
}