package de.devin.ccr.content.domain.beehive

import com.simibubi.create.content.equipment.armor.BacktankUtil
import de.devin.ccr.content.backpack.PortableBeehiveItem
import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.bee.MechanicalBeeTier
import de.devin.ccr.content.bee.brain.BeeMemoryModules
import de.devin.ccr.content.domain.GlobalJobPool
import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.domain.task.TaskStatus
import de.devin.ccr.content.upgrades.BeeContext
import de.devin.ccr.registry.AllEntityTypes
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import top.theillusivec4.curios.api.CuriosApi
import java.util.*

/**
 * Implementation of IBeeHome that wraps a player and their portable beehive (backpack).
 * Also implements BeeSource to allow contributing bees to jobs from multiple sources.
 */
class PlayerBeeHive(val player: ServerPlayer) : BeeHive {
    init {
        // Register player as a bee source so they can contribute to jobs
        GlobalJobPool.registerWorker(this)
    }

    override fun acceptTask(task: BeeTask): Boolean {
        if (getAvailableBeeCount() <= 0) {
            return false
        }

        val beeTier = consumeBee() ?: return false
        val bee = MechanicalBeeEntity(AllEntityTypes.MECHANICAL_BEE.get(), player.level()).apply {
            tier = beeTier
            setOwner(player.uuid)
            setPos(player.position().add(0.0, 1.0, 0.0))
        }

        bee.brain.setMemory(BeeMemoryModules.HIVE_POS.get(), player.blockPosition())
        bee.brain.setMemory(BeeMemoryModules.HIVE_INSTANCE.get(), Optional.of(this))
        bee.brain.setMemory(BeeMemoryModules.CURRENT_TASK.get(), Optional.of(task))

        task.status = TaskStatus.IN_PROGRESS

        player.level().addFreshEntity(bee)
        return true
    }

    override fun notifyTaskCompleted(task: BeeTask, bee: MechanicalBeeEntity): BeeTask? {
        task.complete()
        val nextTask = GlobalJobPool.workBacklog(this)

        if (nextTask != null) {
            nextTask.assignToRobot(bee)
        }

        return nextTask
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