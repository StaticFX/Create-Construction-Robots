package de.devin.ccr.content.robots

import com.simibubi.create.content.equipment.armor.BacktankUtil
import de.devin.ccr.content.backpack.PortableBeehiveItem
import de.devin.ccr.content.schematics.BeeTaskManager
import de.devin.ccr.content.upgrades.BeeContext
import de.devin.ccr.content.upgrades.UpgradeType
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import top.theillusivec4.curios.api.CuriosApi
import java.util.*

/**
 * Implementation of IBeeHome that wraps a player and their portable beehive (backpack).
 */
class PlayerBeeHome(val player: ServerPlayer) : IBeeHome {
    override val world: Level get() = player.level()
    override val position: BlockPos get() = player.blockPosition()
    override val taskManager: BeeTaskManager 
        get() = MechanicalBeeEntity.playerTaskManagers.getOrPut(player.uuid) { BeeTaskManager() }

    override fun getBeeContext(): BeeContext {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return BeeContext()
        return (backpack.item as PortableBeehiveItem).getBeeContext(backpack)
    }

    override fun consumeAir(amount: Int): Int {
        if (player.isCreative) return amount
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return 0
        
        val available = BacktankUtil.getAir(backpack)
        val toConsume = minOf(amount, available)
        BacktankUtil.consumeAir(player, backpack, toConsume)
        return toConsume
    }

    override fun addBee(): Boolean {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return false
        return (backpack.item as PortableBeehiveItem).addRobot(backpack)
    }

    override fun consumeBee(): Boolean {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return false
        return (backpack.item as PortableBeehiveItem).consumeRobot(backpack)
    }

    override fun getMaterialSource(): MaterialSource {
        val sources = mutableListOf<MaterialSource>()
        sources.add(PlayerMaterialSource(player))
        
        val context = getBeeContext()
        if (context.wirelessLinkEnabled) {
            // We'd need a way to scan for wireless storages here, similar to BeeInventoryManager
            // For now, let's keep it simple or delegate to BeeInventoryManager's logic
        }
        
        return CompositeMaterialSource(sources)
    }

    override fun getHomeId(): UUID = player.uuid

    override fun getOwner(): Player? = player

    private fun getBackpackStack(): ItemStack {
        // Check Curios slots for backpack
        val curiosResult = CuriosApi.getCuriosHelper().findFirstCurio(player) { it.item is PortableBeehiveItem }
        if (curiosResult.isPresent) {
            return curiosResult.get().stack()
        }
        
        // Check main inventory
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (stack.item is PortableBeehiveItem) return stack
        }
        
        return ItemStack.EMPTY
    }
}
