package de.devin.cbbees.content.bee

import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Shared contract for mechanical bee entities (construction bees and bumble bees).
 * Enables unified behaviors and reduces code duplication between bee types.
 *
 * Implementors must also be [PathfinderMob] subclasses — default implementations
 * cast `this` to `PathfinderMob` to access entity state.
 *
 * Implemented by [MechanicalBeeEntity] and [MechanicalBumbleBeeEntity].
 */
interface MechanicalBeelike : NetworkedBee {
    var springTension: Float
    var rechargeFinishTick: Long
    var hiveEntryRetries: Int

    /** Label for debug logging (e.g. "Bee", "Bumble") */
    val debugLabel: String

    val networkId: UUID
    val inventory: SimpleContainer

    /** UUID of the home beehive, read from entity data */
    val homeId: UUID?
    fun setHomeId(uuid: UUID)

    /** The item stack representing this bee type (for drops / hive entry) */
    fun beeItemStack(): ItemStack

    /** The memory module that represents "has active work" for this bee type */
    fun taskMemory(): MemoryModuleType<*>

    /** Consumes spring tension for an action. Returns false if empty. */
    fun consumeSpring(baseDrain: Double): Boolean

    // ── Default implementations ──────────────────────────────────────────

    /** Looks up the bee's hive, caching in brain memory */
    fun beehive(): BeeHive? {
        val self = this as PathfinderMob
        val fromMemory = self.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).getOrNull()
        if (fromMemory != null) return fromMemory
        if (self.level().isClientSide) return null
        val hiveId = homeId ?: return null
        val hive = ServerBeeNetworkManager.findHive(hiveId)
        if (hive != null) {
            self.brain.setMemory(BeeMemoryModules.HIVE_INSTANCE.get(), Optional.of(hive))
        }
        return hive
    }

    /** Tries to adopt into the closest available hive in the network */
    fun tryAdoptHive(exclude: BeeHive? = null): BeeHive? {
        val self = this as PathfinderMob
        val net = network() ?: return null
        val hive = net.hives
            .filter { it != exclude }
            .sortedBy { it.pos.distSqr(self.blockPosition()) }
            .firstOrNull() ?: return null
        setHomeId(hive.id)
        self.brain.setMemory(BeeMemoryModules.HIVE_INSTANCE.get(), Optional.of(hive))
        self.brain.setMemory(BeeMemoryModules.HIVE_POS.get(), hive.pos)
        return hive
    }

    /** Gets the BeeContext for recharge/fuel calculations */
    fun getBeeContextForRecharge(): BeeContext {
        return beehive()?.getBeeContext() ?: BeeContext()
    }

    /** Drops inventory + bee item on the ground and removes this entity */
    fun dropBeeItemAndDiscard(reason: String = "unknown") {
        BeeDebug.log(this, "Dropping as item: $reason")
        val self = this as PathfinderMob
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty) {
                val drop = ItemEntity(self.level(), self.x, self.y, self.z, stack.copy())
                self.level().addFreshEntity(drop)
                inventory.setItem(i, ItemStack.EMPTY)
            }
        }
        val itemEntity = ItemEntity(self.level(), self.x, self.y, self.z, beeItemStack())
        self.level().addFreshEntity(itemEntity)
        self.discard()
    }

    companion object {
        /** Shared flying travel logic — call from PathfinderMob.travel() override */
        fun travelFlying(mob: PathfinderMob, travelVector: Vec3) {
            if (mob.isControlledByLocalInstance()) {
                if (mob.isInWater()) {
                    mob.moveRelative(0.02f, travelVector)
                    mob.move(MoverType.SELF, mob.deltaMovement)
                    mob.deltaMovement = mob.deltaMovement.scale(0.8)
                } else if (mob.isInLava()) {
                    mob.moveRelative(0.02f, travelVector)
                    mob.move(MoverType.SELF, mob.deltaMovement)
                    mob.deltaMovement = mob.deltaMovement.scale(0.5)
                } else {
                    mob.moveRelative(if (mob.onGround()) 0.1f else 0.04f, travelVector)
                    mob.move(MoverType.SELF, mob.deltaMovement)
                    mob.deltaMovement = mob.deltaMovement.scale(0.91)
                }
            }
            mob.calculateEntityAnimation(false)
        }

        /** Shared flying navigation setup — call from PathfinderMob.createNavigation() override */
        fun createFlyingNavigation(mob: PathfinderMob, level: Level): PathNavigation {
            val navigation = FlyingPathNavigation(mob, level)
            navigation.setCanOpenDoors(false)
            navigation.setCanPassDoors(true)
            return navigation
        }
    }
}
