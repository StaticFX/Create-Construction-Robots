package de.devin.ccr.content.robots

import de.devin.ccr.content.backpack.ConstructorBackpackItem
import de.devin.ccr.content.schematics.RobotTask
import de.devin.ccr.content.schematics.RobotTaskManager
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.AnimationState
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.FlyingMob
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.FlyingMoveControl
import net.minecraft.world.entity.ai.goal.Goal
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
 * Constructor Robots are flying autonomous entities that perform tasks assigned by a [RobotTaskManager].
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
class ConstructorRobotEntity(entityType: EntityType<out FlyingMob>, level: Level) : FlyingMob(entityType, level),
    GeoEntity {

    companion object {
        private val OWNER_UUID: EntityDataAccessor<Optional<UUID>> = SynchedEntityData.defineId(ConstructorRobotEntity::class.java, EntityDataSerializers.OPTIONAL_UUID)
        private val TASK_POS: EntityDataAccessor<Optional<BlockPos>> = SynchedEntityData.defineId(ConstructorRobotEntity::class.java, EntityDataSerializers.OPTIONAL_BLOCK_POS)
        private val IS_WORKING: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(ConstructorRobotEntity::class.java, EntityDataSerializers.BOOLEAN)

        /** Shared task managers per player UUID */
        val playerTaskManagers = mutableMapOf<UUID, RobotTaskManager>()
        
        /** Base number of items a robot can carry per trip */
        private const val BASE_CARRY_CAPACITY = 1
        
        /** Additional items per Speed Coil upgrade */
        private const val CARRY_PER_SPEED_COIL = 1
        
        /** Base ticks to break a block (faster than placement was before) */
        const val BASE_BREAK_TICKS = 5

        /** Maximum ticks before teleporting to target */
        const val MAX_STUCK_TICKS = 60  // 3 seconds

        fun createAttributes(): AttributeSupplier.Builder {
            return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.6)  // Increased speed
                .add(Attributes.MOVEMENT_SPEED, 0.3)
        }
    }

    var currentState: RobotState = RobotState.IDLE
    var currentTask: RobotTask? = null
    private var owner: UUID? = null
    var taskManager: RobotTaskManager? = null

    private val geoCache = GeckoLibUtil.createInstanceCache(this)

    /** Calculated stats for this robot based on backpack upgrades */
    private var robotContext: de.devin.ccr.content.upgrades.RobotContext? = null
    
    /** Items the robot is currently carrying for the task */
    val carriedItems: MutableList<ItemStack> = mutableListOf()

    val inventoryManager = RobotInventoryManager(this)
    
    /** Whether this robot has already been returned to backpack (prevents double-return) */
    private var hasBeenReturned = false
    
    /** Last position for stuck detection */
    var lastPosition: Vec3 = Vec3.ZERO
    
    /** Ticks spent stuck (not making progress) */
    var stuckTicks = 0

    init {
        this.moveControl = FlyingMoveControl(this, 20, true)
    }

    override fun registerGoals() {
        this.goalSelector.addGoal(1, de.devin.ccr.content.robots.goals.RobotExecuteTaskGoal(this))
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
        builder.define(TASK_POS, Optional.empty())
        builder.define(IS_WORKING, false)
    }

    override fun createNavigation(level: Level): PathNavigation {
        val navigation = FlyingPathNavigation(this, level)
        navigation.setCanOpenDoors(false)
        navigation.setCanPassDoors(false)
        return navigation
    }

    fun setOwner(uuid: UUID) {
        this.owner = uuid
        this.entityData.set(OWNER_UUID, Optional.of(uuid))
        this.taskManager = playerTaskManagers.getOrPut(uuid) { RobotTaskManager() }
    }

    fun getOwnerUUID(): UUID? = owner ?: entityData.get(OWNER_UUID).orElse(null)

    override fun tick() {
        super.tick()
        
        if (!level().isClientSide) {
            // Initialize owner from entity data if needed
            if (owner == null) {
                owner = entityData.get(OWNER_UUID).orElse(null)
                owner?.let { 
                    taskManager = playerTaskManagers.getOrPut(it) { RobotTaskManager() }
                }
            }
            
            // Discard if no owner
            if (owner == null) {
                this.discard()
                return
            }

            // Update stats from backpack
            if (robotContext == null || tickCount % 100 == 0) {
                val backpack = getBackpackStack()
                if (!backpack.isEmpty) {
                    robotContext = (backpack.item as ConstructorBackpackItem).getRobotContext(backpack)
                }
            }
        }
    }

    override fun push(entity: net.minecraft.world.entity.Entity) {}
    override fun doPush(entity: net.minecraft.world.entity.Entity) {}
    override fun isPushable(): Boolean = false

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        owner?.let { compound.putUUID("Owner", it) }
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        if (compound.hasUUID("Owner")) {
            setOwner(compound.getUUID("Owner"))
        }
    }

    /**
     * Gets the backpack item stack from the owner.
     */
    private fun getBackpackStack(): ItemStack {
        val ownerPlayer = getOwnerPlayer() ?: return ItemStack.EMPTY
        
        // Check Curios slots for backpack
        val curiosResult = CuriosApi.getCuriosHelper().findFirstCurio(ownerPlayer) { it.item is ConstructorBackpackItem }
        if (curiosResult.isPresent) {
            return curiosResult.get().stack()
        }
        
        // Check main inventory
        for (i in 0 until ownerPlayer.inventory.containerSize) {
            val stack = ownerPlayer.inventory.getItem(i)
            if (stack.item is ConstructorBackpackItem) return stack
        }
        
        return ItemStack.EMPTY
    }

    /**
     * Gets the owner player entity.
     */
    internal fun getOwnerPlayer(): ServerPlayer? {
        return owner?.let { level().getPlayerByUUID(it) } as? ServerPlayer
    }

    fun getRobotContext(): de.devin.ccr.content.upgrades.RobotContext = robotContext ?: de.devin.ccr.content.upgrades.RobotContext()

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
    fun getCarryCapacity(): Int = robotContext?.carryCapacity ?: BASE_CARRY_CAPACITY

    /**
     * Gets the maximum work distance from the player.
     */
    fun getMaxWorkRange(): Double = robotContext?.workRange ?: 32.0
    
    /**
     * Gets the speed multiplier from upgrades.
     */
    fun getSpeedMultiplier(): Double = robotContext?.speedMultiplier ?: 1.0

    /**
     * Returns this robot to the player's backpack and removes the entity.
     * If the backpack is full, drops the robot as an item instead.
     */
    fun returnToBackpackAndDiscard(player: net.minecraft.world.entity.player.Player) {
        if (hasBeenReturned) return
        hasBeenReturned = true
        
        // Use the RobotConstructionManager to handle the return logic
        de.devin.ccr.content.schematics.RobotConstructionManager.returnRobotToBackpack(player)
        
        // Remove this entity
        discard()
    }
    
    /**
     * Drops a robot item at the current position and removes the entity.
     * Used when the owner cannot be found.
     */
    fun dropRobotItemAndDiscard() {
        if (hasBeenReturned) return
        hasBeenReturned = true
        
        // Drop robot item at current position
        val robotStack = ItemStack(de.devin.ccr.items.AllItems.CONSTRUCTOR_ROBOT.get(), 1)
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
