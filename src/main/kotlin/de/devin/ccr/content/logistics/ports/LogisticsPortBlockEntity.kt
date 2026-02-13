package de.devin.ccr.content.logistics.ports

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour
import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.domain.LogisticsManager
import de.devin.ccr.content.domain.logistics.LogisticsPort
import de.devin.ccr.content.logistics.ports.LogisticPortBlock.Companion.PORT_STATE
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
    SmartBlockEntity(type, pos, state), LogisticsPort {

    lateinit var filteringBehavior: FilteringBehaviour
    lateinit var scrollOptionBehavior: ScrollOptionBehaviour<LogisticsPortMode>

    override val sourceId: UUID get() = homeId
    override val sourceWorld: Level get() = getLevel()!!
    override val sourcePosition: BlockPos get() = blockPos

    private var registeredAsSource = false
    private val homeId = UUID.randomUUID()

    var filterStack: ItemStack = ItemStack.EMPTY
    var selectionMode = LogisticsPortMode.PICK_UP

    // This is what the bees will call
    fun getInventory(level: Level): IItemHandler? {
        val attachedDir = LogisticPortBlock.getConnectedDirection(blockState).opposite
        val attachedPos = blockPos.relative(attachedDir)
        return level.getCapability(Capabilities.ItemHandler.BLOCK, attachedPos, attachedDir.opposite)
    }

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        filteringBehavior = FilteringBehaviour(this, LogisticsPortFilterValueBox()).withCallback { onFilterChanged() }
        scrollOptionBehavior = ScrollOptionBehaviour(
            LogisticsPortMode::class.java,
            Component.translatable("gui.ccr.logistics.port_mode.title"),
            this,
            LogisticsPortSelectionValueBox()
        )
        scrollOptionBehavior.withCallback { selectionMode = LogisticsPortMode.entries[it] }

        behaviours.add(scrollOptionBehavior)
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
        if (!level.isClientSide && !registeredAsSource) {
            LogisticsManager.registerPort(this)
            registeredAsSource = true
        }
    }

    override fun destroy() {
        super.destroy()
        if (!level?.isClientSide!! && registeredAsSource) {
            LogisticsManager.unregisterPort(this)
            registeredAsSource = false
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        filterStack = ItemStack.parseOptional(registries, tag.getCompound("Filter"))
        super.loadAdditional(tag, registries)
    }

    override fun getMode(): LogisticsPortMode {
        return scrollOptionBehavior.get()
    }

    override fun getFilter(): ItemStack {
        return filterStack
    }

    override fun getItemHandler(level: Level): IItemHandler? {
        return getInventory(level)
    }

    override fun isValidForDropOff(): Boolean {
        if (selectionMode != LogisticsPortMode.DROP_OFF) return false
        if (portState() == PortState.INVALID) return false
        return true
    }

    private fun portState() = blockState.getValue(PORT_STATE)

    override fun isValidForPickup(): Boolean {
        if (selectionMode == LogisticsPortMode.DROP_OFF) return false
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

    override fun addItemStack(stack: ItemStack): Boolean {
        val level = level ?: return false
        val handler = getItemHandler(level) ?: return false

        // ItemHandlerHelper.insertItemStacked handles:
        // 1. Finding existing stacks to merge with.
        // 2. Finding empty slots.
        // 3. Returning the "remainder" that didn't fit.
        val remainder = ItemHandlerHelper.insertItemStacked(handler, stack, false)

        // If the remainder is smaller than the input, at least some were added.
        // If you want to return true ONLY if the whole stack was added:
        return remainder.count < stack.count
    }
}