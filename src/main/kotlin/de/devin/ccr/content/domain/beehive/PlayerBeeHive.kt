package de.devin.ccr.content.domain.beehive

import com.simibubi.create.content.equipment.armor.BacktankUtil
import de.devin.ccr.content.backpack.PortableBeehiveItem
import de.devin.ccr.content.domain.bee.BeeContributionManager
import de.devin.ccr.content.bee.CompositeMaterialSource
import de.devin.ccr.content.bee.MaterialSource
import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.bee.MechanicalBeeTier
import de.devin.ccr.content.bee.PlayerMaterialSource
import de.devin.ccr.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import top.theillusivec4.curios.api.CuriosApi
import java.util.UUID
import javax.sound.sampled.Port

/**
 * Implementation of IBeeHome that wraps a player and their portable beehive (backpack).
 * Also implements BeeSource to allow contributing bees to jobs from multiple sources.
 */
class PlayerBeeHive(val player: ServerPlayer): BeeHive {
    init {
        // Register player as a bee source so they can contribute to jobs
        BeeContributionManager.registerSource(this)
    }

    // BeeSource implementation
    override val sourceId: UUID get() = player.uuid
    override val sourceWorld: Level get() = player.level()
    override val sourcePosition: BlockPos get() = player.blockPosition()

    override fun getBeeContext(): BeeContext {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return BeeContext()
        return (backpack.item as PortableBeehiveItem).getBeeContext(backpack)
    }

    fun consumeAir(amount: Int): Int {
        if (player.isCreative) return amount
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return 0

        val available = BacktankUtil.getAir(backpack)
        val toConsume = minOf(amount, available)
        BacktankUtil.consumeAir(player, backpack, toConsume)
        return toConsume
    }

    fun hasAir(amount: Int): Boolean {
        if (player.isCreative) return true
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return false
        return BacktankUtil.getAir(backpack) >= amount
    }

    fun addBee(tier: MechanicalBeeTier): Boolean {
        val backpackItemStack = getBackpackStack()
        if (backpackItemStack.isEmpty) return false
        return (backpackItemStack.item as PortableBeehiveItem).addRobot(backpackItemStack, tier)
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

    private fun getBackpackStack(): ItemStack {
        // 1. Check Curios slots for backpack
        val curiosResult = CuriosApi.getCuriosHelper().findFirstCurio(player) { it.item is PortableBeehiveItem }
        if (curiosResult.isPresent) {
            return curiosResult.get().stack()
        }

        // 3. Check main inventory as fallback
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (stack.item is PortableBeehiveItem) return stack
        }

        return ItemStack.EMPTY
    }
}