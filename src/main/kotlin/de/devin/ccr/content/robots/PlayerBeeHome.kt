package de.devin.ccr.content.robots

import com.simibubi.create.content.equipment.armor.BacktankUtil
import de.devin.ccr.content.backpack.PortableBeehiveItem
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
 * Also implements BeeSource to allow contributing bees to jobs from multiple sources.
 */
class PlayerBeeHome(val player: ServerPlayer) : IBeeHome, BeeSource {
    init {
        // Register player as a bee source so they can contribute to jobs
        BeeContributionManager.registerSource(this)
    }

    override val world: Level get() = player.level()
    override val position: BlockPos get() = player.blockPosition()
    
    // BeeSource implementation
    override val sourceId: UUID get() = player.uuid
    override val sourceWorld: Level get() = player.level()
    override val sourcePosition: BlockPos get() = player.blockPosition()

    override fun getBeeContext(): BeeContext {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return BeeContext()
        return (backpack.item as PortableBeehiveItem).getBeeContext(backpack)
    }

    override fun getActiveBeeCount(): Int {
        return MechanicalBeeEntity.getActiveBeeCount(player.uuid)
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

    override fun addBee(tier: MechanicalBeeTier): Boolean {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return false
        return (backpack.item as PortableBeehiveItem).addRobot(backpack, tier)
    }

    override fun consumeBee(): MechanicalBeeTier? {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return null
        
        // Creative mode players don't consume bees, return ANDESITE as default
        if (player.isCreative) return MechanicalBeeTier.ANDESITE
        
        return (backpack.item as PortableBeehiveItem).consumeRobot(backpack)
    }
    
    // BeeSource implementation
    override fun getAvailableBeeCount(): Int {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return 0
        
        // Creative mode players have infinite bees (represented by a large number)
        if (player.isCreative) return 100
        
        return (backpack.item as PortableBeehiveItem).getTotalRobotCount(backpack)
    }
    
    override fun returnBee(tier: MechanicalBeeTier): Boolean {
        return addBee(tier)
    }
    
    // Resolve diamond inheritance between IBeeHome and BeeSource
    override fun onBeeSpawned(bee: MechanicalBeeEntity) {
        // Default implementation - can be extended if needed
    }
    
    override fun onBeeRemoved(bee: MechanicalBeeEntity) {
        // Default implementation - can be extended if needed
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
        // Check Curios slots for backpack - this is the only way to "wear" it
        val curiosResult = CuriosApi.getCuriosHelper().findFirstCurio(player) { it.item is PortableBeehiveItem }
        if (curiosResult.isPresent) {
            return curiosResult.get().stack()
        }
        
        return ItemStack.EMPTY
    }
}
