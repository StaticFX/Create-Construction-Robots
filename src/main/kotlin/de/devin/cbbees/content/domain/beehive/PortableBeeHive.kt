package de.devin.cbbees.content.domain.beehive

import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.registry.AllDataComponents
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.upgrades.BeeContext
import de.devin.cbbees.config.CBBeesConfig
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
 * Also implements BeeSource to allow contributing bees from multiple sources.
 */
class PortableBeeHive(val player: Player) : BeeHive {
    private val activeBees = mutableSetOf<UUID>()

    override fun getActiveBeeCount(): Int = activeBees.size

    override fun acceptBatch(batch: TaskBatch): Boolean {
        if (getAvailableBeeCount() <= 0) {
            return false
        }
        if (getActiveBeeCount() >= getBeeContext().maxActiveRobots) return false

        val beeItem = consumeBee()
        if (beeItem.isEmpty) return false
        val spawned = spawnBee(beeItem, batch)
        if (!spawned) {
            // Return the bee to the backpack if deployment failed
            addBee(beeItem)
        }
        return spawned
    }

    private fun spawnBee(beeItem: ItemStack, batch: TaskBatch): Boolean {
        val bee = MechanicalBeeEntity(AllEntityTypes.MECHANICAL_BEE.get(), player.level()).apply {
            setOwner(player.uuid)
            setPos(player.position().add(0.0, 1.0, 0.0))
            this.networkId = this@PortableBeeHive.network().id
        }

        // Always charge honey to wind the spring for deployment
        val ctx = getBeeContext()
        val honeyCost =
            (CBBeesConfig.portableHoneyPerRewind.get() * ctx.fuelConsumptionMultiplier).toInt().coerceAtLeast(1)
        consumeHoney(honeyCost)
        bee.springTension = 1.0f

        bee.setHomeId(player.uuid)
        bee.brain.setMemory(BeeMemoryModules.HIVE_POS.get(), player.blockPosition())
        bee.brain.setMemory(BeeMemoryModules.HIVE_INSTANCE.get(), Optional.of(this))
        bee.brain.setMemory(BeeMemoryModules.CURRENT_TASK.get(), Optional.of(batch))

        batch.assignToRobot(bee)

        player.level().addFreshEntity(bee)
        activeBees.add(bee.uuid)
        return true
    }

    override fun onBeeRemoved(bee: net.minecraft.world.entity.Entity) {
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

    override fun rechargeSpring(ctx: BeeContext): Int {
        val honeyCost =
            (CBBeesConfig.portableHoneyPerRewind.get() * ctx.fuelConsumptionMultiplier).toInt().coerceAtLeast(1)
        consumeHoney(honeyCost)
        return super.rechargeSpring(ctx)
    }

    override fun chargeReturnFuel(springDeficit: Float, ctx: BeeContext) {
        if (springDeficit <= 0f) return
        val honeyCost =
            (springDeficit * CBBeesConfig.portableHoneyPerRewind.get() * ctx.fuelConsumptionMultiplier).toInt()
                .coerceAtLeast(1)
        consumeHoney(honeyCost)
    }

    fun consumeHoney(amount: Int): Int {
        if (player.isCreative) return amount
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return 0
        val stored = backpack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0)
        val toConsume = minOf(amount, stored)
        backpack.set(AllDataComponents.HONEY_FUEL.get(), stored - toConsume)
        return toConsume
    }

    fun hasHoney(amount: Int): Boolean {
        if (player.isCreative) return true
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return false
        return backpack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0) >= amount
    }

    fun addBee(item: ItemStack): Boolean {
        val backpackItemStack = getBackpackStack()
        if (backpackItemStack.isEmpty) return false
        return (backpackItemStack.item as PortableBeehiveItem).addRobot(backpackItemStack, item)
    }

    override fun consumeBee(): ItemStack {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return ItemStack.EMPTY
        return (backpack.item as PortableBeehiveItem).consumeBee(backpack)
    }

    // BeeSource implementation
    override fun getAvailableBeeCount(): Int {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return 0
        return (backpack.item as PortableBeehiveItem).getTotalRobotCount(backpack)
    }

    override fun returnBee(item: ItemStack): Boolean {
        return addBee(item)
    }

    override fun walkTarget(): WalkTarget {
        return WalkTarget(player, 1.0f, 0)
    }

    override fun sync() {}


    private fun getBackpackStack(): ItemStack {
        val curiosResult = CuriosApi.getCuriosHelper().findFirstCurio(player) { it.item is PortableBeehiveItem }
        if (curiosResult.isPresent) {
            return curiosResult.get().stack()
        }
        return ItemStack.EMPTY
    }
}
