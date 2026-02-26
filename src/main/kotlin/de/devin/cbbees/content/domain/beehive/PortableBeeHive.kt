package de.devin.cbbees.content.domain.beehive

import com.simibubi.create.content.equipment.armor.BacktankUtil
import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.MechanicalBeeTier
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.network.ClientBeeNetworkManager
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
import de.devin.cbbees.content.upgrades.BeeContext
import de.devin.cbbees.registry.AllEntityTypes
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import top.theillusivec4.curios.api.CuriosApi
import java.util.*

/**
 * Implementation of IBeeHome that wraps a player and their portable beehive (backpack).
 * Also implements BeeSource to allow contributing bees to jobs from multiple sources.
 */
class PortableBeeHive(val player: Player) : BeeHive {
    private val activeBees = mutableSetOf<UUID>()

    override fun getActiveBeeCount(): Int = activeBees.size

    override fun acceptBatch(batch: TaskBatch): Boolean {
        if (getAvailableBeeCount() <= 0) {
            return false
        }

        val beeTier = consumeBee() ?: return false
        return spawnBee(beeTier, batch)
    }

    private fun spawnBee(tier: MechanicalBeeTier, batch: TaskBatch): Boolean {
        val bee = MechanicalBeeEntity(AllEntityTypes.MECHANICAL_BEE.get(), player.level()).apply {
            this.tier = tier
            setOwner(player.uuid)
            setPos(player.position().add(0.0, 1.0, 0.0))
            this.networkId = this@PortableBeeHive.network().id
        }

        bee.brain.setMemory(BeeMemoryModules.HIVE_POS.get(), player.blockPosition())
        bee.brain.setMemory(BeeMemoryModules.HIVE_INSTANCE.get(), Optional.of(this))
        bee.brain.setMemory(BeeMemoryModules.CURRENT_TASK.get(), Optional.of(batch))

        batch.assignToRobot(bee)

        player.level().addFreshEntity(bee)
        activeBees.add(bee.uuid)
        return true
    }

    override fun onBeeRemoved(bee: MechanicalBeeEntity) {
        activeBees.remove(bee.uuid)
    }

    override fun notifyTaskCompleted(task: BeeTask, bee: MechanicalBeeEntity): TaskBatch? {
        val nextBatch = GlobalJobPool.workBacklog(this)

        nextBatch?.assignToRobot(bee)

        return nextBatch
    }

    override val id: UUID get() = player.uuid
    override val world: Level get() = player.level()
    override val pos: BlockPos get() = player.blockPosition()
    override var networkId: UUID = UUID.randomUUID()
        set(value) {
            if (field == value) return
            val old = field
            field = value
            onNetworkIdChanged(old, value)
        }

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
        if (player.isCreative) return true
        val backpackItemStack = getBackpackStack()
        if (backpackItemStack.isEmpty) return false
        return (backpackItemStack.item as PortableBeehiveItem).addRobot(backpackItemStack, tier)
    }

    override fun consumeBee(): MechanicalBeeTier? {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return null

        // Creative mode players don't consume bees, return ANDESITE as default
        if (player.isCreative) return MechanicalBeeTier.STURDY

        return (backpack.item as PortableBeehiveItem).consumeBee(backpack)
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

    override fun walkTarget(): WalkTarget {
        return WalkTarget(player, 1.0f, 0)
    }

    override fun sync() {}


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