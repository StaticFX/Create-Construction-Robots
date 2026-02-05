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
        private const val BASE_BREAK_TICKS = 5

        fun createAttributes(): AttributeSupplier.Builder {
            return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.6)  // Increased speed
                .add(Attributes.MOVEMENT_SPEED, 0.3)
        }
    }

    enum class RobotState {
        IDLE,
        FETCHING_ITEMS,
        TRAVELING_TO_WORK,
        WORKING,
        RETURNING_TO_PLAYER
    }

    private var currentState: RobotState = RobotState.IDLE
    private var currentTask: RobotTask? = null
    private var owner: UUID? = null
    private var taskManager: RobotTaskManager? = null

    private val geoCache = GeckoLibUtil.createInstanceCache(this)

    /** Calculated stats for this robot based on backpack upgrades */
    private var robotContext: de.devin.ccr.content.upgrades.RobotContext? = null
    
    /** Items the robot is currently carrying for the task */
    private var carriedItems: MutableList<ItemStack> = mutableListOf()
    
    /** Whether this robot has already been returned to backpack (prevents double-return) */
    private var hasBeenReturned = false
    
    /** Last position for stuck detection */
    private var lastPosition: Vec3 = Vec3.ZERO
    
    /** Ticks spent stuck (not making progress) */
    private var stuckTicks = 0
    
    /** Maximum ticks before teleporting to target */
    private val MAX_STUCK_TICKS = 60  // 3 seconds

    /** Cache for wireless storage inventory locations */
    private var cachedWirelessStorages: MutableList<BlockPos> = mutableListOf()
    private var wirelessScanCooldown = 0
    private val WIRELESS_SCAN_INTERVAL = 100 // 5 seconds

    init {
        this.moveControl = FlyingMoveControl(this, 20, true)
    }

    override fun registerGoals() {
        this.goalSelector.addGoal(1, RobotExecuteTaskGoal())
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
     * Gets the owner player entity.
     */
    private fun getOwnerPlayer(): ServerPlayer? {
        return owner?.let { level().getPlayerByUUID(it) } as? ServerPlayer
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
     * Gets the maximum number of items this robot can carry per trip.
     */
    private fun getCarryCapacity(): Int = robotContext?.carryCapacity ?: BASE_CARRY_CAPACITY

    /**
     * Gets the maximum work distance from the player.
     */
    private fun getMaxWorkRange(): Double = robotContext?.workRange ?: 32.0
    
    /**
     * Gets the speed multiplier from upgrades.
     */
    private fun getSpeedMultiplier(): Double = robotContext?.speedMultiplier ?: 1.0

    /**
     * Gets a material source that checks all available inventories.
     */
    private fun getMaterialSource(): MaterialSource {
        val ownerPlayer = getOwnerPlayer() ?: return CompositeMaterialSource(emptyList())
        val sources = mutableListOf<MaterialSource>()
        
        // 1. Player inventory
        sources.add(PlayerMaterialSource(ownerPlayer))
        
        // 2. Wireless Link (if enabled)
        if (robotContext?.wirelessLinkEnabled == true) {
            if (wirelessScanCooldown <= 0) {
                scanForWirelessStorages(ownerPlayer)
                wirelessScanCooldown = WIRELESS_SCAN_INTERVAL
            } else {
                wirelessScanCooldown--
            }
            sources.add(WirelessMaterialSource(level(), cachedWirelessStorages))
        }
        
        return CompositeMaterialSource(sources)
    }

    /**
     * Picks up items for the current task using the material source.
     */
    private fun pickUpItems(required: List<ItemStack>): Boolean {
        val ownerPlayer = getOwnerPlayer() ?: return false
        if (ownerPlayer.isCreative) return true
        
        val source = getMaterialSource()
        val carryCapacity = getCarryCapacity()
        var itemsPickedUp = 0
        
        carriedItems.clear()
        
        for (req in required) {
            if (req.isEmpty) continue
            if (itemsPickedUp >= carryCapacity) break
            
            val toPickUp = minOf(req.count, carryCapacity - itemsPickedUp)
            val extracted = source.extractItems(req, toPickUp)
            if (!extracted.isEmpty) {
                carriedItems.add(extracted)
                itemsPickedUp += extracted.count
            }
        }
        
        val totalRequired = required.sumOf { it.count }
        return itemsPickedUp >= totalRequired
    }
    
    /**
     * Returns this robot to the player's backpack and removes the entity.
     * If the backpack is full, drops the robot as an item instead.
     */
    private fun returnToBackpackAndDiscard(player: net.minecraft.world.entity.player.Player) {
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
    private fun dropRobotItemAndDiscard() {
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

    /**
     * Scans for inventories around the player for Wireless Link.
     * This is an expensive operation, so it's called infrequently.
     */
    private fun scanForWirelessStorages(player: ServerPlayer) {
        cachedWirelessStorages.clear()
        val range = 16
        val center = player.blockPosition()
        
        for (x in -range..range) {
            for (y in -range..range) {
                for (z in -range..range) {
                    val pos = center.offset(x, y, z)
                    val handler = level().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null)
                    if (handler != null) {
                        cachedWirelessStorages.add(pos)
                    }
                }
            }
        }
    }

    /**
     * Goal for finding and executing tasks.
     * Uses a state machine for clean logic flow.
     */
    inner class RobotExecuteTaskGoal : Goal() {
        private var workTicks = 0

        init {
            this.flags = EnumSet.of(Flag.MOVE)
        }

        override fun canUse(): Boolean = true

        override fun tick() {
            val ownerPlayer = getOwnerPlayer()
            if (ownerPlayer == null) {
                dropRobotItemAndDiscard()
                return
            }

            val tm = taskManager ?: return
            
            when (currentState) {
                RobotState.IDLE -> handleIdleState(ownerPlayer, tm)
                RobotState.FETCHING_ITEMS -> handleFetchingState(ownerPlayer, tm)
                RobotState.TRAVELING_TO_WORK -> handleTravelingState()
                RobotState.WORKING -> handleWorkingState()
                RobotState.RETURNING_TO_PLAYER -> handleReturningState(ownerPlayer, tm)
            }
        }

        private fun handleIdleState(player: ServerPlayer, tm: RobotTaskManager) {
            if (tm.hasPendingTasks()) {
                // In creative mode, we go straight to work
                if (player.isCreative) {
                    val nextTask = tm.getNextTask(id, emptyList(), true)
                    if (nextTask != null) {
                        assignTask(nextTask)
                        currentState = RobotState.TRAVELING_TO_WORK
                    }
                } else {
                    currentState = RobotState.FETCHING_ITEMS
                }
            } else {
                currentState = RobotState.RETURNING_TO_PLAYER
            }
        }

        private fun handleFetchingState(player: ServerPlayer, tm: RobotTaskManager) {
            // Stay near player while fetching
            val playerPos = player.position().add(0.0, 1.5, 0.0)
            if (distanceToSqr(playerPos) > 4.0) {
                flyTowards(playerPos)
                return
            }

            // Try to get a task that we have materials for
            // We use a dummy list since pickUpItems handles actual extraction
            val nextTask = tm.getNextTask(id, emptyList(), false) 
            if (nextTask != null) {
                if (nextTask.type == RobotTask.TaskType.PLACE) {
                    if (pickUpItems(nextTask.requiredItems)) {
                        assignTask(nextTask)
                        currentState = RobotState.TRAVELING_TO_WORK
                    } else {
                        // Can't get items, fail and wait
                        tm.failTask(id)
                        currentState = RobotState.IDLE
                    }
                } else {
                    // Remove tasks don't need items
                    assignTask(nextTask)
                    currentState = RobotState.TRAVELING_TO_WORK
                }
            } else {
                // No tasks available for our materials
                currentState = RobotState.IDLE
            }
        }

        private fun handleTravelingState() {
            val task = currentTask
            if (task == null) {
                currentState = RobotState.IDLE
                return
            }
            val targetVec = Vec3.atCenterOf(task.targetPos)
            
            if (distanceToSqr(targetVec) > 2.0) {
                flyTowards(targetVec)
            } else {
                setDeltaMovement(Vec3.ZERO)
                currentState = RobotState.WORKING
                workTicks = 0
            }
        }

        private fun handleWorkingState() {
            val task = currentTask
            if (task == null) {
                currentState = RobotState.IDLE
                return
            }
            
            if (task.type == RobotTask.TaskType.PLACE) {
                performWork()
                completeTask()
            } else {
                workTicks++
                // Visual effects
                if (level() is ServerLevel) {
                    (level() as ServerLevel).sendParticles(
                        ParticleTypes.ELECTRIC_SPARK,
                        task.targetPos.x + 0.5, task.targetPos.y + 0.5, task.targetPos.z + 0.5,
                        2, 0.2, 0.2, 0.2, 0.05
                    )
                }

                val requiredTicks = (BASE_BREAK_TICKS / getSpeedMultiplier()).toInt().coerceAtLeast(1)
                if (workTicks >= requiredTicks) {
                    performWork()
                    completeTask()
                }
            }
        }

        private fun handleReturningState(player: ServerPlayer, tm: RobotTaskManager) {
            val playerPos = player.position().add(0.0, 1.5, 0.0)
            if (distanceToSqr(playerPos) > 4.0) {
                flyTowards(playerPos)
            } else {
                setDeltaMovement(Vec3.ZERO)
                if (tm.hasPendingTasks()) {
                    currentState = RobotState.IDLE
                } else {
                    returnToBackpackAndDiscard(player)
                }
            }
        }

        private fun assignTask(task: RobotTask) {
            currentTask = task
            entityData.set(TASK_POS, Optional.of(task.targetPos))
            entityData.set(IS_WORKING, true)
            stuckTicks = 0
        }

        private fun completeTask() {
            val tm = taskManager ?: return
            tm.completeTask(id)
            currentTask = null
            carriedItems.clear()
            entityData.set(TASK_POS, Optional.empty())
            entityData.set(IS_WORKING, false)
            currentState = RobotState.RETURNING_TO_PLAYER
        }

        private fun performWork() {
            val task = currentTask ?: return
            val context = robotContext ?: de.devin.ccr.content.upgrades.RobotContext()
            
            val action = when (task.type) {
                RobotTask.TaskType.PLACE -> PlaceAction()
                RobotTask.TaskType.REMOVE -> RemoveAction()
            }
            
            action.execute(level(), task.targetPos, task, context)
        }

        private fun flyTowards(target: Vec3) {
            val speed = 0.4 * getSpeedMultiplier()
            val currentPos = position()
            val direction = target.subtract(currentPos).normalize()
            
            setDeltaMovement(direction.scale(speed))
            lookAt(target)

            // Stuck detection
            if (currentPos.distanceToSqr(lastPosition) < 0.01) {
                stuckTicks++
                if (stuckTicks >= MAX_STUCK_TICKS) {
                    teleportTo(target.x, target.y, target.z)
                    stuckTicks = 0
                }
            } else {
                stuckTicks = 0
            }
            lastPosition = currentPos
        }

        private fun lookAt(target: Vec3) {
            val dx = target.x - x
            val dy = target.y - y
            val dz = target.z - z
            val horizontalDist = kotlin.math.sqrt(dx * dx + dz * dz)
            xRot = (-(kotlin.math.atan2(dy, horizontalDist) * (180.0 / Math.PI))).toFloat()
            yRot = (kotlin.math.atan2(dz, dx) * (180.0 / Math.PI)).toFloat() - 90f
        }

        override fun canContinueToUse(): Boolean = true
        override fun isInterruptable(): Boolean = false
    }
}
