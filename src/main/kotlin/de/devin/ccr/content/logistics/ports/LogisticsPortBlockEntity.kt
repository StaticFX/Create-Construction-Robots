package de.devin.ccr.content.logistics.ports

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.IItemHandler

class LogisticPortBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    SmartBlockEntity(type, pos, state) {

    enum class PortMode { PROVIDER, RECEIVER }

    lateinit var filteringBehavior: FilteringBehaviour

    var mode = PortMode.PROVIDER
    var filter: ItemStack = ItemStack.EMPTY

    fun toggleMode(player: Player) {
        mode = if (mode == PortMode.PROVIDER) PortMode.RECEIVER else PortMode.PROVIDER
        player.displayClientMessage(Component.literal("Port Mode: $mode"), true)
        setChanged()
    }

    // This is what the bees will call
    fun getInventory(level: Level): IItemHandler? {
        val attachedDir = LogisticPortBlock.getConnectedDirection(blockState).opposite
        val attachedPos = blockPos.relative(attachedDir)
        return level.getCapability(Capabilities.ItemHandler.BLOCK, attachedPos, attachedDir.opposite)
    }

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        filteringBehavior = FilteringBehaviour(this, LogisticsPortValueBox()).withCallback { onFilterChanged() }
        behaviours.add(filteringBehavior)
    }

    private fun onFilterChanged() {
        if (!level!!.isClientSide) {
            setChanged()
            sendData()
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        mode = PortMode.valueOf(tag.getString("Mode"))
        filter = ItemStack.parseOptional(registries, tag.getCompound("Filter"))
        super.loadAdditional(tag, registries)
    }

}