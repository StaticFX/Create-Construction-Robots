package de.devin.cbbees.content.bee

import com.mojang.serialization.Dynamic
import de.devin.cbbees.content.bee.brain.MechanicalBumbleBrainProvider
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.network.ClientBeeNetworkManager
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.items.AllItems
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.FlyingMoveControl
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Mechanical Bumble Bee entity — a logistics transport bee.
 *
 * Slower than Mechanical Bees but carries more items (9 slots).
 * Shuttles items between EXTRACT and INSERT logistics ports.
 */
class MechanicalBumbleBeeEntity(entityType: EntityType<out PathfinderMob>, level: Level) : PathfinderMob(entityType, level),
    GeoEntity, NetworkedBee {

    companion object {
        private val BEEHIVE_ID: EntityDataAccessor<Optional<UUID>> =
            SynchedEntityData.defineId(MechanicalBumbleBeeEntity::class.java, EntityDataSerializers.OPTIONAL_UUID)
        private val TARGET_POS: EntityDataAccessor<Optional<BlockPos>> =
            SynchedEntityData.defineId(MechanicalBumbleBeeEntity::class.java, EntityDataSerializers.OPTIONAL_BLOCK_POS)
        private val SPRING_TENSION: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(MechanicalBumbleBeeEntity::class.java, EntityDataSerializers.FLOAT)

        const val WORK_RANGE: Double = 2.5
        const val INVENTORY_SIZE: Int = 3

        fun createAttributes(): AttributeSupplier.Builder {
            return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.FLYING_SPEED, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
        }
    }

    private val geoCache = GeckoLibUtil.createInstanceCache(this)

    /** 9-slot inventory for hauling items */
    var inventory = SimpleContainer(INVENTORY_SIZE)
        private set

    var networkId: UUID = UUID.randomUUID()

    /** Tick when spring recharge completes at hive. -1 = not recharging. */
    var rechargeFinishTick: Long = -1

    val workRange: Double = WORK_RANGE

    var springTension: Float
        get() = entityData.get(SPRING_TENSION)
        set(value) = entityData.set(SPRING_TENSION, value.coerceIn(0.0f, 1.0f))

    /**
     * Consumes spring tension for an action. BumbleBee uses flat rates (no BeeContext).
     * Returns false if spring is already empty. Drains to 0 if insufficient for a full action.
     */
    fun consumeSpring(baseDrain: Double): Boolean {
        if (springTension <= 0f) return false
        val effectiveDrain = baseDrain.toFloat()
        springTension = (springTension - effectiveDrain).coerceAtLeast(0f)
        return true
    }

    override fun network(): BeeNetwork? {
        return if (level().isClientSide) {
            ClientBeeNetworkManager.getNetwork(networkId)
        } else {
            ServerBeeNetworkManager.getNetwork(networkId, level())
        }
    }

    init {
        this.moveControl = FlyingMoveControl(this, 40, true)
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "controller", 5) { event ->
                event.setAndContinue(RawAnimation.begin().thenLoop("idle"))
            }
        )
    }

    override fun customServerAiStep() {
        this.level().profiler.push("mechanicalBumbleBrain")
        this.getBrain().tick(this.level() as ServerLevel, this)
        this.level().profiler.pop()
        super.customServerAiStep()
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache? {
        return geoCache
    }

    override fun brainProvider(): Brain.Provider<*> {
        return MechanicalBumbleBrainProvider.brain()
    }

    @Suppress("UNCHECKED_CAST")
    override fun makeBrain(dynamic: Dynamic<*>): Brain<MechanicalBumbleBeeEntity> {
        val brain = this.brainProvider().makeBrain(dynamic)
        return MechanicalBumbleBrainProvider.makeBrain(brain as Brain<MechanicalBumbleBeeEntity>)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getBrain(): Brain<MechanicalBumbleBeeEntity> {
        return super.getBrain() as Brain<MechanicalBumbleBeeEntity>
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(BEEHIVE_ID, Optional.empty())
        builder.define(TARGET_POS, Optional.empty())
        builder.define(SPRING_TENSION, 1.0f)
    }

    override fun createNavigation(level: Level): PathNavigation {
        val navigation = FlyingPathNavigation(this, level)
        navigation.setCanOpenDoors(false)
        navigation.setCanPassDoors(true)
        return navigation
    }

    /**
     * Custom travel for flying navigation.
     * PathfinderMob uses gravity-based travel by default, so we override
     * to use aerial movement factors for smooth flight.
     */
    override fun travel(travelVector: net.minecraft.world.phys.Vec3) {
        if (this.isControlledByLocalInstance()) {
            if (this.isInWater()) {
                this.moveRelative(0.02f, travelVector)
                this.move(MoverType.SELF, this.deltaMovement)
                this.deltaMovement = this.deltaMovement.scale(0.8)
            } else if (this.isInLava()) {
                this.moveRelative(0.02f, travelVector)
                this.move(MoverType.SELF, this.deltaMovement)
                this.deltaMovement = this.deltaMovement.scale(0.5)
            } else {
                this.moveRelative(if (this.onGround()) 0.1f else 0.04f, travelVector)
                this.move(MoverType.SELF, this.deltaMovement)
                this.deltaMovement = this.deltaMovement.scale(0.91)
            }
        }
        this.calculateEntityAnimation(false)
    }

    fun setHomeId(uuid: UUID) {
        this.entityData.set(BEEHIVE_ID, Optional.of(uuid))
    }

    fun beehive(): BeeHive? {
        val brain = this.getBrain()
        val fromMemory = brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).getOrNull()
        if (fromMemory != null) return fromMemory

        if (level().isClientSide) return null

        val hiveId = this.entityData.get(BEEHIVE_ID).getOrNull() ?: return null
        val hive = ServerBeeNetworkManager.findHive(hiveId)
        if (hive != null) {
            brain.setMemory(BeeMemoryModules.HIVE_INSTANCE.get(), Optional.of(hive))
        }
        return hive
    }

    override fun remove(reason: RemovalReason) {
        if (!level().isClientSide) {
            network()?.releaseReservations(this.uuid)
            beehive()?.onBeeRemoved(this)
        }
        super.remove(reason)
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        syncTargetPos()
        if (rechargeFinishTick < 0) {
            BeeSeparation.applyFlightOffset(this)
        }

        // Drain spring while flying
        if (deltaMovement.lengthSqr() > 0.001) {
            consumeSpring(CBBeesConfig.springDrainFlight.get())
        }
    }

    private fun syncTargetPos() {
        val brain = getBrain()
        val walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null)
        if (walkTarget != null) {
            entityData.set(TARGET_POS, Optional.of(walkTarget.target.currentBlockPosition()))
        } else {
            entityData.set(TARGET_POS, Optional.empty())
        }
    }

    override fun getTargetPos(): BlockPos? = entityData.get(TARGET_POS).orElse(null)

    // Fly through water — no swimming, no water drag
    override fun isInWater(): Boolean = false

    @Deprecated("Overrides deprecated MC method", level = DeprecationLevel.WARNING)
    override fun isPushedByFluid(): Boolean = false

    override fun push(entity: Entity) { /* no-op */
    }

    override fun doPush(entity: Entity) { /* no-op */
    }

    @Deprecated("Overrides deprecated MC method", level = DeprecationLevel.WARNING)
    override fun isPushable(): Boolean = false

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        entityData.get(BEEHIVE_ID).ifPresent { compound.putUUID("HomeId", it) }
        compound.putUUID("NetworkId", networkId)
        compound.putFloat("SpringTension", springTension)
        compound.putLong("RechargeFinishTick", rechargeFinishTick)

        val itemsTag = ListTag()
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty) {
                val slotTag = stack.save(registryAccess()) as CompoundTag
                slotTag.putInt("Slot", i)
                itemsTag.add(slotTag)
            }
        }
        compound.put("BeeInventory", itemsTag)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        if (compound.hasUUID("HomeId")) {
            entityData.set(BEEHIVE_ID, Optional.of(compound.getUUID("HomeId")))
        }
        if (compound.hasUUID("NetworkId")) {
            networkId = compound.getUUID("NetworkId")
        }
        if (compound.contains("SpringTension")) {
            springTension = compound.getFloat("SpringTension")
        }
        if (compound.contains("RechargeFinishTick")) {
            rechargeFinishTick = compound.getLong("RechargeFinishTick")
        }

        if (compound.contains("BeeInventory")) {
            val itemsTag = compound.getList("BeeInventory", 10)
            for (j in 0 until itemsTag.size) {
                val slotTag = itemsTag.getCompound(j)
                val slot = slotTag.getInt("Slot")
                if (slot in 0 until inventory.containerSize) {
                    inventory.setItem(slot, ItemStack.parseOptional(registryAccess(), slotTag))
                }
            }
        }
    }

    override fun getName(): Component {
        return Component.translatable("entity.cbbees.mechanical_bumble_bee")
    }

    /**
     * Inserts a stack into the bee's inventory. Returns the remainder that didn't fit.
     */
    fun addToInventory(stack: ItemStack): ItemStack {
        var remaining = stack.copy()
        for (i in 0 until inventory.containerSize) {
            if (remaining.isEmpty) return ItemStack.EMPTY
            val slotStack = inventory.getItem(i)
            if (!slotStack.isEmpty && ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                val canAdd = minOf(remaining.count, slotStack.maxStackSize - slotStack.count)
                if (canAdd > 0) {
                    slotStack.grow(canAdd)
                    remaining.shrink(canAdd)
                }
            }
        }
        for (i in 0 until inventory.containerSize) {
            if (remaining.isEmpty) return ItemStack.EMPTY
            if (inventory.getItem(i).isEmpty) {
                inventory.setItem(i, remaining.copy())
                return ItemStack.EMPTY
            }
        }
        return remaining
    }

    fun getInventoryContents(): List<ItemStack> {
        val list = mutableListOf<ItemStack>()
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty) list.add(stack)
        }
        return list
    }

    fun isInventoryEmpty(): Boolean {
        for (i in 0 until inventory.containerSize) {
            if (!inventory.getItem(i).isEmpty) return false
        }
        return true
    }

    fun isInventoryFull(): Boolean {
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (stack.isEmpty || stack.count < stack.maxStackSize) return false
        }
        return true
    }

    fun removeFromInventory(stack: ItemStack, count: Int): Boolean {
        var toRemove = count
        for (i in 0 until inventory.containerSize) {
            if (toRemove <= 0) break
            val slotStack = inventory.getItem(i)
            if (!slotStack.isEmpty && ItemStack.isSameItemSameComponents(slotStack, stack)) {
                val removed = minOf(slotStack.count, toRemove)
                slotStack.shrink(removed)
                toRemove -= removed
                if (slotStack.isEmpty) inventory.setItem(i, ItemStack.EMPTY)
            }
        }
        return toRemove <= 0
    }

    /**
     * Drops a mechanical bumble bee item at the current position and removes the entity.
     */
    fun dropBeeItemAndDiscard() {
        val beeItemStack = ItemStack(AllItems.MECHANICAL_BUMBLE_BEE.get(), 1)
        val itemEntity = ItemEntity(level(), x, y, z, beeItemStack)
        level().addFreshEntity(itemEntity)

        // Drop any carried items too
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty) {
                val dropEntity = ItemEntity(level(), x, y, z, stack.copy())
                level().addFreshEntity(dropEntity)
                inventory.setItem(i, ItemStack.EMPTY)
            }
        }

        discard()
    }
}
