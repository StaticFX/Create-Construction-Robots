package de.devin.ccr.content.robots

import de.devin.ccr.content.backpack.PortableBeehiveItem
import de.devin.ccr.content.schematics.BeeTask
import de.devin.ccr.content.schematics.BeeTaskManager
import com.simibubi.create.content.equipment.armor.BacktankUtil
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.FlyingMob
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.FlyingMoveControl
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import top.theillusivec4.curios.api.CuriosApi
import java.util.*

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
 * The robot entity is tied to a specific [owner] player and will discard itself if the owner is not found.
 */
class MechanicalBeeEntity(entityType: EntityType<out FlyingMob>, level: Level) : FlyingMob(entityType, level),
    GeoEntity {

    companion object {
        private val OWNER_UUID: EntityDataAccessor<Optional<UUID>> = SynchedEntityData.defineId(MechanicalBeeEntity::class.java, EntityDataSerializers.OPTIONAL_UUID)
        private val HOME_ID: EntityDataAccessor<Optional<UUID>> = SynchedEntityData.defineId(MechanicalBeeEntity::class.java, EntityDataSerializers.OPTIONAL_UUID)
        private val TASK_POS: EntityDataAccessor<Optional<BlockPos>> = SynchedEntityData.defineId(MechanicalBeeEntity::class.java, EntityDataSerializers.OPTIONAL_BLOCK_POS)
        private val IS_WORKING: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(MechanicalBeeEntity::class.java, EntityDataSerializers.BOOLEAN)

        /** Shared task managers per player UUID */
        val playerTaskManagers = mutableMapOf<UUID, BeeTaskManager>()
        
        /** Shared home registry (transient) */
        val activeHomes = mutableMapOf<UUID, IBeeHome>()

        /** Maximum ticks before teleporting to target */
        const val MAX_STUCK_TICKS = 60  // 3 seconds

        /** Maximum air the bee can carry */
        const val MAX_AIR = 600 // 30 seconds of flight

        fun createAttributes(): AttributeSupplier.Builder {
            return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.6)  // Increased speed
                .add(Attributes.MOVEMENT_SPEED, 0.3)
        }
    }

    var currentState: BeeState = BeeState.IDLE
    var currentTask: BeeTask? = null
    private var home: IBeeHome? = null
    var taskManager: BeeTaskManager? = null

    private val geoCache = GeckoLibUtil.createInstanceCache(this)

    /** Calculated stats for this robot based on backpack upgrades */
    private var robotContext: de.devin.ccr.content.upgrades.BeeContext? = null
    
    /** Items the robot is currently carrying for the task */
    val carriedItems: MutableList<ItemStack> = mutableListOf()

    val inventoryManager = BeeInventoryManager(this)
    
    /** Whether this robot has already been returned to backpack (prevents double-return) */
    private var hasBeenReturned = false

    /** Whether we have notified the home about our presence */
    private var homeNotified = false
    
    /** Last position for stuck detection */
    var lastPosition: Vec3 = Vec3.ZERO
    
    /** Ticks spent stuck (not making progress) */
    var stuckTicks = 0

    /** Current air level of the bee */
    var air: Int = MAX_AIR

    init {
        this.moveControl = FlyingMoveControl(this, 20, true)
    }

    override fun registerGoals() {
        this.goalSelector.addGoal(1, de.devin.ccr.content.robots.goals.BeeExecuteTaskGoal(this))
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "controller", 5) { event ->
                event.setAndContinue(RawAnimation.begin().thenLoop("flying"))
            }
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache? {
        return geoCache
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(OWNER_UUID, Optional.empty())
        builder.define(HOME_ID, Optional.empty())
        builder.define(TASK_POS, Optional.empty())
        builder.define(IS_WORKING, false)
    }

    override fun createNavigation(level: Level): PathNavigation {
        val navigation = FlyingPathNavigation(this, level)
        navigation.setCanOpenDoors(false)
        navigation.setCanPassDoors(false)
        return navigation
    }

    override fun remove(reason: RemovalReason) {
        if (!level().isClientSide && homeNotified) {
            getHome()?.onBeeRemoved(this)
        }
        super.remove(reason)
    }

    fun setHome(home: IBeeHome) {
        this.home = home
        this.entityData.set(HOME_ID, Optional.of(home.getHomeId()))
        this.taskManager = home.taskManager
        home.getOwner()?.let { setOwner(it.uuid) }
        activeHomes[home.getHomeId()] = home
    }

    fun getHome(): IBeeHome? {
        if (home != null) return home
        val id = entityData.get(HOME_ID).orElse(null) ?: return null
        
        // Try to recover home from registry
        home = activeHomes[id]
        
        // If it was a player home, try to recover it
        if (home == null) {
            val ownerUuid = getOwnerUUID()
            if (ownerUuid != null) {
                val player = level().getPlayerByUUID(ownerUuid) as? ServerPlayer
                if (player != null) {
                    val playerHome = PlayerBeeHome(player)
                    setHome(playerHome)
                }
            }
        }
        
        return home
    }

    fun setOwner(uuid: UUID) {
        this.entityData.set(OWNER_UUID, Optional.of(uuid))
    }

    fun getOwnerUUID(): UUID? = entityData.get(OWNER_UUID).orElse(null)

    override fun tick() {
        super.tick()
        
        if (!level().isClientSide) {
            // Ensure home is initialized
            val currentHome = getHome()
            
            // Discard if no home can be found/recovered
            if (currentHome == null) {
                this.discard()
                return
            }

            if (!homeNotified) {
                currentHome.onBeeSpawned(this)
                homeNotified = true
            }

            // Update stats from home
            if (robotContext == null || tickCount % 100 == 0) {
                robotContext = currentHome.getBeeContext()
            }

            // Consume air when active
            if (currentState != BeeState.IDLE) {
                consumeAir(1)
            }
        }
    }

    override fun push(entity: net.minecraft.world.entity.Entity) {}
    override fun doPush(entity: net.minecraft.world.entity.Entity) {}
    override fun isPushable(): Boolean = false

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        getOwnerUUID()?.let { compound.putUUID("Owner", it) }
        entityData.get(HOME_ID).ifPresent { compound.putUUID("HomeId", it) }
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        if (compound.hasUUID("Owner")) {
            setOwner(compound.getUUID("Owner"))
        }
        if (compound.hasUUID("HomeId")) {
            entityData.set(HOME_ID, Optional.of(compound.getUUID("HomeId")))
        }
    }

    /**
     * Gets the owner player entity.
     */
    internal fun getOwnerPlayer(): ServerPlayer? {
        return getOwnerUUID()?.let { level().getPlayerByUUID(it) } as? ServerPlayer
    }

    fun getBeeContext(): de.devin.ccr.content.upgrades.BeeContext = robotContext ?: getHome()?.getBeeContext() ?: de.devin.ccr.content.upgrades.BeeContext()

    /**
     * Refills air from the home.
     * @return true if air was refilled or already full
     */
    fun refillAirFromHive(): Boolean {
        val currentHome = getHome() ?: return false
        
        val needed = MAX_AIR - air
        if (needed <= 0) return true
        
        val taken = currentHome.consumeAir(needed)
        air += taken
        return taken > 0 || air > 0
    }

    /**
     * Consumes air for flying/working.
     */
    fun consumeAir(amount: Int) {
        val ownerPlayer = getOwnerPlayer()
        if (ownerPlayer != null && ownerPlayer.isCreative) return
        
        air = maxOf(0, air - amount)
    }

    fun setTaskPos(pos: BlockPos?) {
        entityData.set(TASK_POS, Optional.ofNullable(pos))
    }

    fun setWorking(working: Boolean) {
        entityData.set(IS_WORKING, working)
    }

    fun resetStuckTicks() {
        stuckTicks = 0
    }

    fun incrementStuckTicks() {
        stuckTicks++
    }

    /**
     * Gets the maximum number of items this robot can carry per trip.
     */
    fun getCarryCapacity(): Int = getBeeContext().carryCapacity

    /**
     * Gets the maximum work distance from the home.
     */
    fun getMaxWorkRange(): Double = getBeeContext().workRange
    
    /**
     * Gets the speed multiplier from upgrades.
     */
    fun getSpeedMultiplier(): Double = getBeeContext().speedMultiplier

    /**
     * Returns this robot to the home and removes the entity.
     * If the home is full, drops the robot as an item instead.
     */
    fun returnToBackpackAndDiscard(player: net.minecraft.world.entity.player.Player) {
        if (hasBeenReturned) return
        hasBeenReturned = true
        
        val currentHome = getHome()
        if (currentHome != null && currentHome.addBee()) {
            // Success
        } else {
            dropRobotItemAndDiscard()
        }
        
        // Remove this entity
        discard()
    }
    
    /**
     * Drops a robot item at the current position and removes the entity.
     * Used when the home cannot be found or is full.
     */
    fun dropRobotItemAndDiscard() {
        if (hasBeenReturned) return
        hasBeenReturned = true
        
        // Drop robot item at current position
        val robotStack = ItemStack(de.devin.ccr.items.AllItems.MECHANICAL_BEE.get(), 1)
        val itemEntity = net.minecraft.world.entity.item.ItemEntity(
            level(),
            x,
            y,
            z,
            robotStack
        )
        level().addFreshEntity(itemEntity)
        
        // Remove this entity
        discard()
    }
}
