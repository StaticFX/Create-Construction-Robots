package de.devin.ccr.content.backpack

import de.devin.ccr.content.bee.MechanicalBeeItem
import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.domain.GlobalJobPool
import de.devin.ccr.content.upgrades.BeeUpgradeItem
import de.devin.ccr.network.TaskProgressSyncPacket
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents
import net.neoforged.neoforge.network.PacketDistributor
import top.theillusivec4.curios.api.CuriosApi

/**
 * Container/Menu for the Constructor Backpack.
 * 
 * Layout:
 * - Robot slots (4 slots)
 * - Upgrade slots (6 slots)
 * - Bottom section: Player inventory
 */
class BeehiveContainer : AbstractContainerMenu {
    
    private val playerInventory: Inventory
    val backpackStack: ItemStack
    private val backpackInventory: Container
    
    // Constructor for client-side (from network) - used by Registrate
    constructor(type: MenuType<*>, containerId: Int, playerInventory: Inventory, extraData: RegistryFriendlyByteBuf)
        : super(type, containerId) {
        this.playerInventory = playerInventory
        // On client, we read the slot index where the backpack is located
        val slotIndex = extraData.readVarInt()
        this.backpackStack = playerInventory.getItem(slotIndex)
        this.backpackInventory = SimpleContainer(PortableBeehiveItem.TOTAL_SLOTS)
        
        setupSlots()
    }
    
    // Constructor for server-side (when opening from item)
    constructor(type: MenuType<*>, containerId: Int, playerInventory: Inventory, backpackStack: ItemStack) 
        : super(type, containerId) {
        this.playerInventory = playerInventory
        this.backpackStack = backpackStack
        this.backpackInventory = SimpleContainer(PortableBeehiveItem.TOTAL_SLOTS)
        
        // Load contents from the backpack item
        val contents = backpackStack.get(DataComponents.CONTAINER)
        if (contents != null) {
            val items = NonNullList.withSize(PortableBeehiveItem.TOTAL_SLOTS, ItemStack.EMPTY)
            contents.copyInto(items)
            for (i in 0 until items.size) {
                backpackInventory.setItem(i, items[i])
            }
        }
        
        setupSlots()
    }
    
    private fun setupSlots() {
        // Updated positions for Create-style GUI (FILTER + PLAYER_INVENTORY backgrounds)
        // FILTER background is 214x99, PLAYER_INVENTORY is 176x108
        // 4px gap between them
        
        // Robot slots (1 row of 4) - positioned in FILTER background area
        // Centered: (214 - (4 * 22)) / 2 = 63
        for (i in 0 until PortableBeehiveItem.ROBOT_SLOTS) {
            val x = 63 + (i * 22)
            val y = 30
            addSlot(RobotSlot(backpackInventory, i, x, y))
        }
        
        // Upgrade slots (1 row of 6 below robots)
        // Centered: (214 - (6 * 22)) / 2 = 41
        for (i in 0 until PortableBeehiveItem.UPGRADE_SLOTS) {
            val x = 41 + (i * 22)
            val y = 60
            addSlot(UpgradeSlot(backpackInventory, PortableBeehiveItem.ROBOT_SLOTS + i, x, y))
        }
        
        // Add player inventory slots
        // FILTER height (99) + gap (4) = 103, then PLAYER_INVENTORY starts
        // PLAYER_INVENTORY is centered: (214 - 176) / 2 = 19 offset
        // Player inventory slots start at y=103+18=121 (18px from top of PLAYER_INVENTORY for label)
        val invX = 19 + 7  // Center offset + standard inventory padding
        val invY = 103 + 18
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, invX + col * 18, invY + row * 18))
            }
        }
        
        // Add player hotbar slots
        for (col in 0 until 9) {
            addSlot(Slot(playerInventory, col, invX + col * 18, invY + 58))
        }
    }
    
    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        var result = ItemStack.EMPTY
        val slot = slots.getOrNull(index) ?: return result
        
        if (slot.hasItem()) {
            val slotStack = slot.item
            result = slotStack.copy()
            
            val backpackSlotCount = PortableBeehiveItem.TOTAL_SLOTS
            
            if (index < backpackSlotCount) {
                // Moving from backpack to player inventory
                if (!moveItemStackTo(slotStack, backpackSlotCount, slots.size, true)) {
                    return ItemStack.EMPTY
                }
            } else {
                // Moving from player inventory to backpack
                // Try robot slots first, then upgrade slots
                if (slotStack.item is MechanicalBeeItem) {
                    if (!moveItemStackTo(slotStack, 0, PortableBeehiveItem.ROBOT_SLOTS, false)) {
                        return ItemStack.EMPTY
                    }
                } else if (slotStack.item is BeeUpgradeItem) {
                    if (!moveItemStackTo(slotStack, PortableBeehiveItem.ROBOT_SLOTS, backpackSlotCount, false)) {
                        return ItemStack.EMPTY
                    }
                } else {
                    return ItemStack.EMPTY
                }
            }
            
            if (slotStack.isEmpty) {
                slot.set(ItemStack.EMPTY)
            } else {
                slot.setChanged()
            }
        }
        
        return result
    }
    
    override fun stillValid(player: Player): Boolean {
        // Check if player still has the backpack
        return playerInventory.contains(backpackStack) || 
               isBackpackEquippedInCurios(player)
    }
    
    private fun isBackpackEquippedInCurios(player: Player): Boolean {
        val curiosResult = CuriosApi.getCuriosHelper().findFirstCurio(player) { it == backpackStack }
        return curiosResult.isPresent
    }
    
    override fun removed(player: Player) {
        super.removed(player)
        saveBackpackContents()
    }
    
    private fun saveBackpackContents() {
        // Save the container contents back to the backpack item
        val items = mutableListOf<ItemStack>()
        for (i in 0 until backpackInventory.containerSize) {
            items.add(backpackInventory.getItem(i))
        }
        backpackStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items))
    }
    
    /**
     * Slot that only accepts robot items
     */
    inner class RobotSlot(container: Container, index: Int, x: Int, y: Int) : Slot(container, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean {
            return stack.item is MechanicalBeeItem
        }
        
        override fun getMaxStackSize(): Int = MechanicalBeeItem.MAX_STACK_SIZE
    }
    
    /**
     * Slot that only accepts upgrade items
     */
    inner class UpgradeSlot(container: Container, index: Int, x: Int, y: Int) : Slot(container, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean {
            return stack.item is BeeUpgradeItem
        }
        
        override fun getMaxStackSize(): Int = 1
    }
    
    override fun clickMenuButton(player: Player, id: Int): Boolean {
        // Logic removed as per requirement to move Start button to Schematic HUD
        return false
    }
    
    private var syncTickCounter = 0
    
    override fun broadcastChanges() {
        super.broadcastChanges()
        
        // Send task progress sync every 10 ticks (0.5 seconds)
        syncTickCounter++
        if (syncTickCounter >= 10) {
            syncTickCounter = 0
            sendTaskProgressSync()
        }
    }
    
    private fun sendTaskProgressSync() {
        val player = playerInventory.player
        if (player !is ServerPlayer) return
        
        val jobs = GlobalJobPool.getAllJobs().filter { it.ownerId == player.uuid }
        if (jobs.isNotEmpty()) {
            val totalTasks = jobs.sumOf { it.tasks.size }
            val completedTasks = jobs.sumOf { it.tasks.count { t -> t.status == BeeTask.TaskStatus.COMPLETED } }
            
            // jobProgress map: jobId -> (completed, total)
            val jobProgress = jobs.associate { it.jobId to (it.tasks.count { t -> t.status == BeeTask.TaskStatus.COMPLETED } to it.tasks.size) }
            
            val packet = TaskProgressSyncPacket(
                globalTotal = totalTasks,
                globalCompleted = completedTasks,
                jobProgress = jobProgress
            )
            PacketDistributor.sendToPlayer(player, packet)
        } else {
            // No active tasks - send empty progress
            val packet = TaskProgressSyncPacket(
                globalTotal = 0,
                globalCompleted = 0,
                jobProgress = emptyMap()
            )
            PacketDistributor.sendToPlayer(player, packet)
        }
    }
}
