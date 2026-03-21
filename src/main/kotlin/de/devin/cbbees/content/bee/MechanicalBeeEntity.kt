package de.devin.cbbees.content.bee

import com.mojang.serialization.Dynamic
import com.simibubi.create.AllItems
import de.devin.cbbees.content.bee.brain.BeeBrainProvider
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.network.ClientBeeNetworkManager
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.task.TaskStatus
import de.devin.cbbees.content.upgrades.BeeContext
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.items.AllItems as CBeesItems
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.FlyingMob
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.FlyingMoveControl
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.ListTag
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
 * Entity representation of the Mechanical Bee.
 *
 * Mechanical Bees are flying autonomous entities that perform tasks assigned by a [BeeTaskManager].
 * Their primary lifecycle involves:
 * 1. Spawning from a beehive when a construction job is dispatched.
 * 2. Fetching a task from the job pool.
 * 3. Picking up required items from logistics ports.
 * 4. Flying to the target block position.
 * 5. Placing blocks instantly or breaking blocks quickly.
 * 6. Returning to the hive when all tasks are done.
 */
class MechanicalBeeEntity(entityType: EntityType<out FlyingMob>, level: Level) : FlyingMob(entityType, level),
    GeoEntity, NetworkedBee {

    companion object {
        private val OWNER_UUID: EntityDataAccessor<Optional<UUID>> =
            SynchedEntityData.defineId(MechanicalBeeEntity::class.java, EntityDataSerializers.OPTIONAL_UUID)
        private val BEEHIVE_ID: EntityDataAccessor<Optional<UUID>> =
            SynchedEntityData.defineId(MechanicalBeeEntity::class.java, EntityDataSerializers.OPTIONAL_UUID)
        private val TARGET_POS: EntityDataAccessor<Optional<BlockPos>> =
            SynchedEntityData.defineId(MechanicalBeeEntity::class.java, EntityDataSerializers.OPTIONAL_BLOCK_POS)
        private val SPRING_TENSION: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(MechanicalBeeEntity::class.java, EntityDataSerializers.FLOAT)

        const val WORK_RANGE: Double = 2.5

        fun createAttributes(): AttributeSupplier.Builder {
            return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.FLYING_SPEED, 1.5)
                .add(Attributes.MOVEMENT_SPEED, 0.75)
        }
    }

    private val geoCache = GeckoLibUtil.createInstanceCache(this)

    /** Calculated stats for this robot based on backpack upgrades */
    private var beeContext: BeeContext? = null

    /** Single-slot inventory */
    var inventory = SimpleContainer(1)
        private set

    var networkId: UUID = UUID.randomUUID()

    /** Tick when spring recharge completes at hive. -1 = not recharging. */
    var rechargeFinishTick: Long = -1

    val workRange: Double = WORK_RANGE

    var springTension: Float
        get() = entityData.get(SPRING_TENSION)
        set(value) = entityData.set(SPRING_TENSION, value.coerceIn(0.0f, 1.0f))

    /**
     * Consumes spring tension for an action. Applies efficiency modifiers from [beeContext].
     * Returns false if spring is already empty. Drains to 0 if insufficient for a full action.
     */
    fun consumeSpring(baseDrain: Double): Boolean {
        if (springTension <= 0f) return false
        val ctx = getBeeContext()
        val effectiveDrain = (baseDrain / ctx.springEfficiency * ctx.fuelConsumptionMultiplier).toFloat()
        springTension = (springTension - effectiveDrain).coerceAtLeast(0f)
        return true
    }

    override fun network(): BeeNetwork? {
        return if (level().isClientSide) {
            ClientBeeNetworkManager.getNetwork(networkId)
        } else {
            ServerBeeNetworkManager.getNetwork(networkId, level()) ?: beehive()?.network()
        }
    }

    init {
        this.moveControl = FlyingMoveControl(this, 60, true)
    }

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (source.entity is Player && (source.entity as Player).isCreative) {
            return super.hurt(source, amount)
        }

        return false
    }

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
        if (level().isClientSide) return InteractionResult.SUCCESS

        val heldItem = player.getItemInHand(hand)
        if (!AllItems.WRENCH.isIn(heldItem)) return super.mobInteract(player, hand)

        // Give bee item to player or drop it
        val beeItem = ItemStack(CBeesItems.MECHANICAL_BEE.get(), 1)
        if (!player.inventory.add(beeItem)) {
            val itemEntity = ItemEntity(level(), x, y, z, beeItem)
            level().addFreshEntity(itemEntity)
        }

        discard()
        return InteractionResult.SUCCESS
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "controller", 5) { event ->
                event.setAndContinue(RawAnimation.begin().thenLoop("idle"))
            }
        )
    }

    override fun customServerAiStep() {
        this.level().profiler.push("beeBrain")
        this.getBrain().tick(this.level() as ServerLevel, this)
        this.level().profiler.pop()

        super.customServerAiStep()
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache? {
        return geoCache
    }

    override fun brainProvider(): Brain.Provider<*> {
        return BeeBrainProvider.brain()
    }

    @Suppress("UNCHECKED_CAST")
    override fun makeBrain(dynamic: Dynamic<*>): Brain<MechanicalBeeEntity> {
        val brain = this.brainProvider().makeBrain(dynamic)
        return BeeBrainProvider.makeBrain(brain as Brain<MechanicalBeeEntity>)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getBrain(): Brain<MechanicalBeeEntity> {
        return super.getBrain() as Brain<MechanicalBeeEntity>
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(OWNER_UUID, Optional.empty())
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
     * Override FlyingMob's travel to use a higher movement factor.
     * FlyingMob hardcodes 0.02f which, combined with normalization in moveRelative,
     * results in very slow movement (~4 blocks/sec).
     * Using 0.04f gives ~9 blocks/sec — fast enough to avoid stuck safety.
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
            // Release current batch so it can be retried by another bee
            val batch = getBrain().getMemory(BeeMemoryModules.CURRENT_TASK.get()).orElse(null)
            if (batch != null && batch.status != TaskStatus.COMPLETED) {
                val tick = (level() as? ServerLevel)?.gameTime ?: 0L
                batch.release(gameTick = tick)
            }
            beehive()?.onBeeRemoved(this)
        }
        super.remove(reason)
    }

    fun setOwner(uuid: UUID) {
        this.entityData.set(OWNER_UUID, Optional.of(uuid))
    }

    fun getOwnerUUID(): UUID? = entityData.get(OWNER_UUID).orElse(null)

    fun setHomeId(uuid: UUID) {
        this.entityData.set(BEEHIVE_ID, Optional.of(uuid))
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return

        syncTargetPos()
        BeeSeparation.applyFlightOffset(this)

        if (beeContext == null || tickCount % 100 == 0) {
            beeContext = beehive()?.getBeeContext()
        }

        // Keep HIVE_POS updated for portable beehives so bees track player movement
        if (tickCount % 20 == 0) {
            val hive = beehive()
            if (hive is PortableBeeHive) {
                getBrain().setMemory(BeeMemoryModules.HIVE_POS.get(), hive.player.blockPosition())
            }
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
            return
        }
        val batch = brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).orElse(null)
        val task = batch?.getCurrentTask()
        if (task != null) {
            entityData.set(TARGET_POS, Optional.of(task.targetPos))
        } else {
            entityData.set(TARGET_POS, Optional.empty())
        }
    }

    override fun getTargetPos(): BlockPos? = entityData.get(TARGET_POS).orElse(null)

    // Mechanical bees fly through water — no swimming, no water drag
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
        getOwnerUUID()?.let { compound.putUUID("Owner", it) }
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
        if (compound.hasUUID("Owner")) {
            setOwner(compound.getUUID("Owner"))
        }
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
        return Component.translatable("entity.cbbees.mechanical_bee")
    }

    /**
     * Gets the owner player entity.
     */
    internal fun getOwnerPlayer(): ServerPlayer? {
        return getOwnerUUID()?.let { level().getPlayerByUUID(it) } as? ServerPlayer
    }

    fun getBeeContext(): BeeContext = beeContext ?: BeeContext()

    /**
     * Inserts a stack into the bee's inventory. Returns the remainder that didn't fit.
     */
    fun addToInventory(stack: ItemStack): ItemStack {
        var remaining = stack.copy()
        // First pass: merge into existing matching slots
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
        // Second pass: fill empty slots
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

    fun isInventoryFull(): Boolean {
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (stack.isEmpty || stack.count < stack.maxStackSize) return false
        }
        return true
    }

    fun countInInventory(stack: ItemStack): Int {
        var count = 0
        for (i in 0 until inventory.containerSize) {
            val slotStack = inventory.getItem(i)
            if (!slotStack.isEmpty && ItemStack.isSameItemSameComponents(slotStack, stack)) {
                count += slotStack.count
            }
        }
        return count
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
     * Drops all items in the bee's inventory on the ground at its current position.
     */
    fun dropInventory() {
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty) {
                val drop = ItemEntity(level(), x, y, z, stack.copy())
                level().addFreshEntity(drop)
                inventory.setItem(i, ItemStack.EMPTY)
            }
        }
    }

    /**
     * Drops a robot item at the current position and removes the entity.
     * Used when the home cannot be found or is full.
     */
    fun dropBeeItemAndDiscard() {
        dropInventory()

        val beeItemStack = ItemStack(CBeesItems.MECHANICAL_BEE.get(), 1)
        val itemEntity = ItemEntity(
            level(),
            x,
            y,
            z,
            beeItemStack
        )
        level().addFreshEntity(itemEntity)

        // Remove this entity
        discard()
    }
}
