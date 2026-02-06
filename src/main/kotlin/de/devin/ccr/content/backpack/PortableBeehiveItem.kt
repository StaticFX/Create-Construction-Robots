package de.devin.ccr.content.backpack

import de.devin.ccr.content.robots.MechanicalBeeEntity
import de.devin.ccr.content.robots.MechanicalBeeItem
import de.devin.ccr.content.upgrades.NaturifiedUpgradeItem
import de.devin.ccr.content.upgrades.UpgradeType
import de.devin.ccr.registry.AllMenuTypes
import com.simibubi.create.content.equipment.armor.BacktankUtil
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.component.ItemContainerContents
import net.minecraft.world.level.Level
import top.theillusivec4.curios.api.SlotContext
import top.theillusivec4.curios.api.type.capability.ICurioItem
import java.util.Optional

/**
 * Data class for the backpack's tooltip preview.
 */
data class BeehiveTooltipData(val stack: ItemStack) : TooltipComponent

/**
 * Constructor Backpack - A wearable equipment item that manages constructor robots and their upgrades.
 *
 * This item can be worn in the Curios "back" slot. When opened, it provides a GUI with:
 * - 4 Slots for [MechanicalBeeItem]s.
 * - 6 Slots for [NaturifiedUpgradeItem]s.
 *
 * The backpack acts as the central hub for the mod's automated building system, storing the state
 * of the workforce and providing the interface to initiate construction tasks.
 */
class PortableBeehiveItem(properties: Properties) : Item(properties), ICurioItem {
    
    companion object {
        const val ROBOT_SLOTS = 4
        const val UPGRADE_SLOTS = 6
        const val TOTAL_SLOTS = ROBOT_SLOTS + UPGRADE_SLOTS
        
        // NBT keys for the container
        const val TAG_INVENTORY = "BackpackInventory"
    }
    
    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)
        
        if (!level.isClientSide && player is ServerPlayer) {
            openBackpackScreen(player, usedHand)
        }
        
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }
    
    private fun openBackpackScreen(player: ServerPlayer, hand: InteractionHand) {
        // Find the slot index of the backpack in player's inventory
        val slotIndex = if (hand == InteractionHand.MAIN_HAND) {
            player.inventory.selected
        } else {
            40 // Offhand slot
        }
        
        val stack = player.getItemInHand(hand)
        
        player.openMenu(object : MenuProvider {
            override fun getDisplayName(): Component {
                return Component.translatable("container.ccr.portable_beehive")
            }
            
            override fun createMenu(containerId: Int, playerInventory: Inventory, player: Player): AbstractContainerMenu {
                return BeehiveContainer(
                    AllMenuTypes.CONSTRUCTOR_BACKPACK.get(),
                    containerId,
                    playerInventory,
                    stack
                )
            }
        }) { buf ->
            // Write the slot index to the buffer for client-side reconstruction
            buf.writeVarInt(slotIndex)
        }
    }
    
    override fun getTooltipImage(stack: ItemStack): Optional<TooltipComponent> {
        return Optional.of(BeehiveTooltipData(stack))
    }

    override fun isBarVisible(stack: ItemStack): Boolean {
        return BacktankUtil.getAir(stack) < BacktankUtil.maxAir(stack)
    }

    override fun getBarWidth(stack: ItemStack): Int {
        val maxAir = BacktankUtil.maxAir(stack)
        if (maxAir <= 0) return 0
        return Math.round(13.0f * BacktankUtil.getAir(stack) / maxAir)
    }

    override fun getBarColor(stack: ItemStack): Int {
        return 0xe1e8e2
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)
        
        // Count robots and upgrades in the backpack
        val robotCount = getRobotCount(stack)
        val upgrades = getUpgrades(stack)
        
        tooltipComponents.add(Component.translatable("tooltip.ccr.beehive.bees", robotCount, ROBOT_SLOTS).withStyle(net.minecraft.ChatFormatting.GRAY))
        
        if (upgrades.isNotEmpty()) {
            tooltipComponents.add(Component.translatable("tooltip.ccr.beehive.naturified_header").withStyle(net.minecraft.ChatFormatting.GOLD))
            for ((type, count) in upgrades) {
                tooltipComponents.add(Component.literal(" - ")
                    .append(Component.translatable(type.descriptionKey))
                    .append(Component.literal(" x$count"))
                    .withStyle(net.minecraft.ChatFormatting.BLUE))
            }
        } else {
            tooltipComponents.add(Component.translatable("tooltip.ccr.beehive.no_naturified").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
        }
    }

    /**
     * Counts the number of robots currently stored in the backpack.
     */
    fun getRobotCount(stack: ItemStack): Int {
        val contents = stack.get(DataComponents.CONTAINER) ?: return 0
        val items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY)
        contents.copyInto(items)
        return items.subList(0, ROBOT_SLOTS).count { it.item is MechanicalBeeItem && !it.isEmpty }
    }

    /**
     * Retrieves all upgrades and their counts from the backpack.
     */
    fun getUpgrades(stack: ItemStack): Map<UpgradeType, Int> {
        val contents = stack.get(DataComponents.CONTAINER) ?: return emptyMap()
        val items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY)
        contents.copyInto(items)
        
        val upgrades = mutableMapOf<UpgradeType, Int>()
        items.subList(ROBOT_SLOTS, TOTAL_SLOTS).forEach { itemStack ->
            val upgradeItem = itemStack.item as? NaturifiedUpgradeItem
            if (upgradeItem != null) {
                upgrades[upgradeItem.upgradeType] = upgrades.getOrDefault(upgradeItem.upgradeType, 0) + 1
            }
        }
        return upgrades
    }

    /**
     * Gets the total number of robots in the backpack (sum of all stacks).
     */
    fun getTotalRobotCount(stack: ItemStack): Int {
        val contents = stack.get(DataComponents.CONTAINER) ?: return 0
        val items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY)
        contents.copyInto(items)

        var count = 0
        for (i in 0 until ROBOT_SLOTS) {
            val s = items[i]
            if (!s.isEmpty && s.item is MechanicalBeeItem) {
                count += s.count
            }
        }
        return count
    }

    /**
     * Consumes a single robot from the backpack inventory.
     * @return true if a robot was consumed, false if no robots available
     */
    fun consumeRobot(stack: ItemStack): Boolean {
        val contents = stack.get(DataComponents.CONTAINER) ?: return false
        val items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY)
        contents.copyInto(items)

        for (i in 0 until ROBOT_SLOTS) {
            val s = items[i]
            if (!s.isEmpty && s.item is MechanicalBeeItem) {
                s.shrink(1)
                if (s.isEmpty) {
                    items[i] = ItemStack.EMPTY
                }
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items))
                return true
            }
        }
        return false
    }

    /**
     * Adds a single robot to the backpack inventory.
     * @return true if successful, false if backpack is full
     */
    fun addRobot(stack: ItemStack): Boolean {
        val contents = stack.get(DataComponents.CONTAINER)
        val items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY)
        contents?.copyInto(items)

        // Try to stack with existing robots first
        for (i in 0 until ROBOT_SLOTS) {
            val s = items[i]
            if (!s.isEmpty && s.item is MechanicalBeeItem && s.count < s.maxStackSize) {
                s.grow(1)
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items))
                return true
            }
        }

        // Try to find an empty slot
        for (i in 0 until ROBOT_SLOTS) {
            if (items[i].isEmpty) {
                items[i] = ItemStack(de.devin.ccr.items.AllItems.MECHANICAL_BEE.get(), 1)
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items))
                return true
            }
        }

        return false
    }

    /**
     * Gets the count of a specific upgrade type.
     */
    fun getUpgradeCount(stack: ItemStack, type: UpgradeType): Int {
        return getUpgrades(stack).getOrDefault(type, 0)
    }

    /**
     * Gets the calculated [BeeContext] for this backpack.
     */
    fun getBeeContext(stack: ItemStack): de.devin.ccr.content.upgrades.BeeContext {
        return UpgradeType.fromBackpack(stack)
    }
    
    // ICurioItem implementation
    
    override fun canEquip(slotContext: SlotContext, stack: ItemStack): Boolean {
        // Only allow in "back" slot
        return slotContext.identifier() == "back"
    }
    
    override fun canUnequip(slotContext: SlotContext, stack: ItemStack): Boolean {
        return true
    }
    
    override fun curioTick(slotContext: SlotContext, stack: ItemStack) {
        // Called every tick when worn - can be used for robot management later
        if (!slotContext.entity().level().isClientSide && slotContext.entity() is ServerPlayer) {
            val player = slotContext.entity() as ServerPlayer
            val tm = MechanicalBeeEntity.playerTaskManagers[player.uuid]
            
            if (tm != null && tm.hasPendingTasks() && !tm.hasActiveTasks()) {
                // If there are tasks but no robots are working, maybe spawn some?
                // For now, we'll wait for manual start to avoid surprise spawns
            }
        }
    }
    
    override fun onEquip(slotContext: SlotContext, prevStack: ItemStack, stack: ItemStack) {
        // Called when the backpack is equipped
    }
    
    override fun onUnequip(slotContext: SlotContext, newStack: ItemStack, stack: ItemStack) {
        // Called when the backpack is unequipped
    }
}
