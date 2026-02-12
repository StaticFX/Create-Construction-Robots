package de.devin.ccr.content.bee

import com.mojang.serialization.Dynamic
import de.devin.ccr.content.bee.brain.BeeBrainProvider
import de.devin.ccr.content.bee.brain.BeeMemoryModules
import de.devin.ccr.content.domain.bee.BeeInventoryManager
import de.devin.ccr.content.domain.beehive.BeeHive
import de.devin.ccr.content.upgrades.BeeContext
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.FlyingMob
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.FlyingMoveControl
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.schedule.Activity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.damagesource.DamageContainer
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Entity representation of the Constructor Robot.
 *
 * Constructor Robots are flying autonomous entities that perform tasks assigned by a [BeeTaskManager].
 * Their primary lifecycle involves:
 * 1. Spawning from a Constructor Backpack when a schematic construction is initiated.
 * 2. Fetching a task from the owner's task manager.
 * 3. Picking up a single item from inventory (upgrades allow more items per trip).
 * 4. Flying to the target block position.
 * 5. Placing blocks instantly or breaking blocks quickly.
 * 6. Returning to the player to pick up more items for the next task.
 *
 * The robot entity is tied to a specific [owner] (player/beehive) and will discard itself if the owner is not found.
 */
class MechanicalBeeEntity(entityType: EntityType<out FlyingMob>, level: Level) : FlyingMob(entityType, level),
    GeoEntity {

    companion object {
        private val OWNER_UUID: EntityDataAccessor<Optional<UUID>> =
            SynchedEntityData.defineId(MechanicalBeeEntity::class.java, EntityDataSerializers.OPTIONAL_UUID)
        private val BEEHIVE_ID: EntityDataAccessor<Optional<UUID>> =
            SynchedEntityData.defineId(MechanicalBeeEntity::class.java, EntityDataSerializers.OPTIONAL_UUID)
        private val TIER: EntityDataAccessor<String> =
            SynchedEntityData.defineId(MechanicalBeeEntity::class.java, EntityDataSerializers.STRING)

        /** Maximum ticks before teleporting to target */
        const val MAX_STUCK_TICKS = 60  // 3 seconds

        fun createAttributes(): AttributeSupplier.Builder {
            return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.FLYING_SPEED, 0.6)  // Increased speed
                .add(Attributes.MOVEMENT_SPEED, 0.3)
        }
    }

    private val geoCache = GeckoLibUtil.createInstanceCache(this)

    /** Calculated stats for this robot based on backpack upgrades */
    private var robotContext: BeeContext? = null

    /** Items the robot is currently carrying for the task */
    val carriedItems: MutableList<ItemStack> = mutableListOf()

    val inventoryManager = BeeInventoryManager(this)

    init {
        this.moveControl = FlyingMoveControl(this, 20, true)
    }

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (source.entity is Player && (source.entity as Player).isCreative) {
            return super.hurt(source, amount)
        }

        return false
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "controller", 5) { event ->
                val brain = this.getBrain()
                val animation = when {
                    brain.isActive(Activity.WORK) -> RawAnimation.begin().thenLoop("flying")
                    brain.isActive(Activity.REST) -> RawAnimation.begin().thenLoop("flying")
                    // If moving, play flying
                    deltaMovement.lengthSqr() > 0.0001 -> RawAnimation.begin().thenPlay("flying_start")
                        .thenLoop("flying")

                    else -> RawAnimation.begin().thenLoop("flying_idle")
                }
                event.setAndContinue(animation)
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

    var tier: MechanicalBeeTier
        get() = MechanicalBeeTier.valueOf(entityData.get(TIER).uppercase())
        set(value) {
            entityData.set(TIER, value.name.lowercase())
            getAttribute(Attributes.FLYING_SPEED)?.baseValue = 1.0 * value.capabilities.flySpeedModifier
        }

    override fun brainProvider(): Brain.Provider<*> {
        return BeeBrainProvider.brain()
    }

    override fun makeBrain(dynamic: Dynamic<*>): Brain<MechanicalBeeEntity> {
        val brain = this.brainProvider().makeBrain(dynamic)
        return BeeBrainProvider.makeBrain(brain as Brain<MechanicalBeeEntity>)
    }

    override fun getBrain(): Brain<MechanicalBeeEntity> {
        return super.getBrain() as Brain<MechanicalBeeEntity>
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(OWNER_UUID, Optional.empty())
        builder.define(BEEHIVE_ID, Optional.empty())
        builder.define(TIER, MechanicalBeeTier.ANDESITE.name.lowercase())
    }

    override fun createNavigation(level: Level): PathNavigation {
        val navigation = FlyingPathNavigation(this, level)
        navigation.setCanOpenDoors(false)
        navigation.setCanPassDoors(true)
        return navigation
    }

    fun beehive(): BeeHive? {
        return this.getBrain().getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).getOrNull()
    }

    override fun remove(reason: RemovalReason) {
        if (!level().isClientSide) {
            beehive()?.onBeeRemoved(this)
        }
        super.remove(reason)
    }

    fun setOwner(uuid: UUID) {
        this.entityData.set(OWNER_UUID, Optional.of(uuid))
    }

    fun getOwnerUUID(): UUID? = entityData.get(OWNER_UUID).orElse(null)

    override fun tick() {
        super.tick()
        if (level().isClientSide) return

        if (robotContext == null || tickCount % 100 == 0) {
            robotContext = beehive()?.getBeeContext()
        }
    }

    override fun push(entity: net.minecraft.world.entity.Entity) {}
    override fun doPush(entity: net.minecraft.world.entity.Entity) {}
    override fun isPushable(): Boolean = false

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        getOwnerUUID()?.let { compound.putUUID("Owner", it) }
        entityData.get(BEEHIVE_ID).ifPresent { compound.putUUID("HomeId", it) }
        compound.putString("Tier", tier.name)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        if (compound.hasUUID("Owner")) {
            setOwner(compound.getUUID("Owner"))
        }
        if (compound.hasUUID("HomeId")) {
            //TODO receive beehive instance from global registry and add it here to bee
            entityData.set(BEEHIVE_ID, Optional.of(compound.getUUID("HomeId")))
        }
        if (compound.contains("Tier")) {
            tier = MechanicalBeeTier.valueOf(compound.getString("Tier"))
        }
    }

    override fun getName(): Component {
        return Component.translatable("entity.ccr.mechanical_bee.${tier.id}")
    }

    /**
     * Gets the owner player entity.
     */
    internal fun getOwnerPlayer(): ServerPlayer? {
        return getOwnerUUID()?.let { level().getPlayerByUUID(it) } as? ServerPlayer
    }

    fun getBeeContext(): BeeContext = robotContext ?: BeeContext()

    /**
     * Gets the maximum number of items this robot can carry per trip.
     */
    fun getCarryCapacity(): Int = getBeeContext().carryCapacity

    /**
     * Drops a robot item at the current position and removes the entity.
     * Used when the home cannot be found or is full.
     */
    fun dropBeeItemAndDiscard() {
        val item = tier.item()
        val beeItemStack = ItemStack(item, 1)
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
