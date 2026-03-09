package de.devin.cbbees.content.logistics.ports

import com.simibubi.create.AllBlocks
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour
import de.devin.cbbees.content.bee.MechanicalBeeEntity
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
import java.util.*

class LogisticPortBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    SmartBlockEntity(type, pos, state), LogisticsPort, IHaveGoggleInformation {

    lateinit var filteringBehavior: FilteringBehaviour

    lateinit var priorityBehavior: ScrollValueBehaviour

    override var id: UUID = UUID.randomUUID()
    override val world: Level get() = getLevel()!!
    override val pos: BlockPos get() = blockPos

    override var networkId: UUID = UUID.randomUUID()
        set(value) {
            if (field == value) return
            val old = field
            field = value
            onNetworkIdChanged(old, value)
        }

    var filterStack: ItemStack = ItemStack.EMPTY
    var priority = 0

    private data class PortReservation(val items: List<ItemStack>, val tick: Long)
    private val reservations = mutableMapOf<UUID, PortReservation>()

    // This is what the bees will call
    fun getInventory(level: Level): IItemHandler? {
        val attachedDir = LogisticPortBlock.getConnectedDirection(blockState).opposite
        val attachedPos = blockPos.relative(attachedDir)
        return level.getCapability(Capabilities.ItemHandler.BLOCK, attachedPos, attachedDir.opposite)
    }

    private fun getAttachesPos(): BlockPos {
        val attachedDir = LogisticPortBlock.getConnectedDirection(blockState).opposite
        return blockPos.relative(attachedDir)
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

    override fun onLoad() {
        super.onLoad()
        if (level != null) {
            addToNetwork(level!!)
        }
    }

    override fun priority(): Int = priority

    override fun remove() {
        removeFromNetwork(level!!)
        super.remove()
    }

    override fun destroy() {
        removeFromNetwork(level!!)
        super.destroy()
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(tag, registries, clientPacket)
        if (tag.hasUUID("lp_id")) {
            id = tag.getUUID("lp_id")
        }
        if (tag.hasUUID("NetworkId")) {
            networkId = tag.getUUID("NetworkId")
        }
        if (tag.contains("Filter")) {
            filterStack = ItemStack.parseOptional(registries, tag.getCompound("Filter"))
        }
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        tag.putUUID("lp_id", id)
        tag.putUUID("NetworkId", networkId)
        if (!filterStack.isEmpty) {
            tag.put("Filter", filterStack.save(registries))
        }
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
        if (portType() != PortType.EXTRACT) return false
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

    fun hasCreativeCrate() = level?.let { AllBlocks.CREATIVE_CRATE.has(it.getBlockState(getAttachesPos())) } ?: false

    override fun hasItemStack(stack: ItemStack): Boolean {
        val level = level ?: return false

        if (hasCreativeCrate()) return true

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
        if (hasCreativeCrate()) return true

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
        return net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, stack, false)
    }

    override fun hasAvailableItemStack(stack: ItemStack, excludeBeeId: UUID?): Boolean {
        val level = level ?: return false
        if (hasCreativeCrate()) return true

        val handler = getItemHandler(level) ?: return false
        var physical = 0
        for (i in 0 until handler.slots) {
            val slotStack = handler.getStackInSlot(i)
            if (!slotStack.isEmpty && ItemStack.isSameItemSameComponents(slotStack, stack)) {
                physical += slotStack.count
            }
        }

        val reserved = reservations
            .filter { excludeBeeId == null || it.key != excludeBeeId }
            .values.flatMap { it.items }
            .filter { ItemStack.isSameItemSameComponents(it, stack) }
            .sumOf { it.count }

        return physical - reserved >= stack.count
    }

    override fun reserve(beeId: UUID, items: List<ItemStack>, tick: Long) {
        reservations[beeId] = PortReservation(items, tick)
        updateBusyState()
    }

    override fun releaseReservation(beeId: UUID) {
        reservations.remove(beeId)
        updateBusyState()
    }

    override fun cleanupReservations(currentTick: Long, maxAge: Long) {
        val sizeBefore = reservations.size
        reservations.entries.removeAll { currentTick - it.value.tick > maxAge }
        if (reservations.size != sizeBefore) updateBusyState()
    }

    override fun clearReservations() {
        val hadReservations = reservations.isNotEmpty()
        reservations.clear()
        if (hadReservations) updateBusyState()
    }

    private fun updateBusyState() {
        val level = level ?: return
        if (level.isClientSide) return
        val currentState = blockState.getValue(PORT_STATE)
        if (currentState == PortState.INVALID) return

        val targetState = if (reservations.isNotEmpty()) PortState.BUSY else PortState.VALID
        if (currentState != targetState) {
            level.setBlock(blockPos, blockState.setValue(PORT_STATE, targetState), 3)
        }
    }

    override fun sync() {
        setChanged()
        sendData()
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        val network = network()
        Lang.builder("cbbees").translate("gui.goggles.beehive.network")
            .style(ChatFormatting.GRAY)
            .add(Lang.builder("cbbees").text(network.name).style(ChatFormatting.GOLD))
            .forGoggles(tooltip)
        return true
    }
}