package de.devin.ccr.content.bee.goals

import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.domain.GlobalJobPool
import de.devin.ccr.content.domain.bee.InternalBeeState
import de.devin.ccr.content.domain.beehive.PlayerBeeHive
import de.devin.ccr.content.domain.task.BeeTask
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.phys.Vec3
import java.util.*

/**
 * Goal for finding and executing tasks.
 * Uses a state machine for clean logic flow.
 * 
 * Bees get tasks from the GlobalJobPool, which contains jobs from
 * both stationary beehives and player backpacks.
 */
class BeeExecuteTaskGoal(private val robot: MechanicalBeeEntity) : Goal() {
    private var workTicks = 0

    init {
        this.flags = EnumSet.of(Flag.MOVE)
    }

    override fun canUse(): Boolean = true

    override fun tick() {
        // If the home is a player, we need the player to be online
        val home = robot.beehive
        val ownerPlayer = robot.getOwnerPlayer()
        
        if (home is PlayerBeeHive && ownerPlayer == null) {
            robot.dropRobotItemAndDiscard()
            return
        }
        
        when (robot.currentState) {
            InternalBeeState.IDLE -> handleIdleState(ownerPlayer)
            InternalBeeState.FETCHING_ITEMS -> handleFetchingState(ownerPlayer)
            InternalBeeState.TRAVELING_TO_WORK -> handleTravelingState()
            InternalBeeState.WORKING -> handleWorkingState()
            InternalBeeState.RETURNING_TO_HOME -> handleReturningState(ownerPlayer)
        }
    }

    private fun handleIdleState(player: ServerPlayer?) {
        // Check GlobalJobPool for jobs this bee's home can help with
        val home = robot.beehive
        val task = GlobalJobPool.getTaskForBee(home, robot.id)
        if (task != null) {
            // Pre-calculate and consume air for the trip
            if (!home.consumeAirForTrip(task.targetPos)) {
                task.release()
                player?.displayClientMessage(Component.translatable("ccr.construction.no_air").withStyle(ChatFormatting.RED), true)
                robot.currentState = InternalBeeState.RETURNING_TO_HOME
                return
            }

            assignTask(task)

            // Use creative mode check if player is online and creative, or if it's a stationary hive (never creative)
            val isCreative = player?.isCreative == true
            if (isCreative || task.action.requiredItems.isEmpty()) {
                robot.currentState = InternalBeeState.TRAVELING_TO_WORK
            } else {
                robot.currentState = InternalBeeState.FETCHING_ITEMS
            }
            return
        }

        // No tasks available anywhere - return to player/home
        robot.currentState = InternalBeeState.RETURNING_TO_HOME
    }

    private fun handleFetchingState(player: ServerPlayer?) {
        val home = robot.getHome()
        val fetchPos = if (player != null) {
            player.position().add(0.0, 1.5, 0.0)
        } else {
            Vec3.atCenterOf(home?.position ?: robot.blockPosition()).add(0.0, 1.0, 0.0)
        }

        if (robot.distanceToSqr(fetchPos) > 4.0) {
            flyTowards(fetchPos)
            return
        }

        val task = robot.currentTask
        if (task != null) {
            val required = task.action.requiredItems
            if (required.isEmpty() || robot.inventoryManager.pickUpItems(required, robot.getBeeContext(), robot.carriedItems)) {
                robot.currentState = InternalBeeState.TRAVELING_TO_WORK
            } else {
                // Return task to job pool
                task.release()
                player?.displayClientMessage(Component.translatable("ccr.construction.missing_items").withStyle(ChatFormatting.RED), true)
                robot.currentTask = null
                robot.currentState = InternalBeeState.IDLE
            }
        } else {
            robot.currentState = InternalBeeState.IDLE
        }
    }

    private fun handleTravelingState() {
        val task = robot.currentTask
        if (task == null) {
            robot.currentState = InternalBeeState.IDLE
            return
        }
        val targetVec = Vec3.atCenterOf(task.targetPos)
        
        if (robot.distanceToSqr(targetVec) > 2.0) {
            flyTowards(targetVec)
        } else {
            robot.setDeltaMovement(Vec3.ZERO)
            robot.currentState = InternalBeeState.WORKING
            workTicks = 0
            task.action.onStart(robot)
        }
    }

    private fun handleWorkingState() {
        val task = robot.currentTask
        if (task == null) {
            robot.currentState = InternalBeeState.IDLE
            return
        }
        
        val context = robot.getBeeContext()
        val requiredTicks = (task.action.getWorkTicks(context) / robot.getWorkSpeedMultiplier()).toInt().coerceAtLeast(0)
        
        if (workTicks >= requiredTicks) {
            performWork()
            completeTask()
        } else {
            task.action.onTick(robot, workTicks)
            workTicks++
        }
    }

    private fun handleReturningState(player: ServerPlayer?) {
        val home = robot.getHome()
        val returnPos = if (player != null) {
            player.position().add(0.0, 1.5, 0.0)
        } else {
            Vec3.atCenterOf(home?.position ?: robot.blockPosition()).add(0.0, 1.0, 0.0)
        }

        if (robot.distanceToSqr(returnPos) > 4.0) {
            flyTowards(returnPos)
        } else {
            robot.setDeltaMovement(Vec3.ZERO)
            
            // Deposit items if carrying any
            if (robot.carriedItems.isNotEmpty()) {
                robot.inventoryManager.depositItems(robot.carriedItems, robot.getBeeContext())
            }
            
            // Check if there are any global jobs available that this bee's home can help with
            val hasTasks = hasAvailableGlobalJobs()
            
            // Only stop returning if we managed to empty our hands
            if (robot.carriedItems.isEmpty()) {
                if (hasTasks) {
                    robot.currentState = InternalBeeState.IDLE
                } else {
                    robot.returnToHomeAndDiscard()
                }
            }
        }
    }

    private fun assignTask(task: BeeTask) {
        robot.currentTask = task
        robot.setTaskPos(task.targetPos)
        robot.setWorking(true)
        robot.resetStuckTicks()
    }

    private fun completeTask() {
        val task = robot.currentTask ?: return
        
        // Mark the task as complete
        task.complete()
        
        // Check if the job is now complete
        val jobId = task.jobId
        if (jobId != null) {
            GlobalJobPool.getJob(jobId)?.let { job ->
                if (job.isComplete()) {
                    job.complete()
                }
            }
        }
        
        robot.currentTask = null
        
        // Only clear items if they were consumed
        if (task.action.requiredItems.isNotEmpty()) {
            robot.carriedItems.clear()
        }
        
        robot.setTaskPos(null)
        robot.setWorking(false)
        
        val context = robot.getBeeContext()
        if (task.action.shouldReturnAfter(context)) {
            robot.currentState = InternalBeeState.RETURNING_TO_HOME
        } else {
            robot.currentState = InternalBeeState.IDLE
        }
    }

    private fun performWork() {
        val task = robot.currentTask ?: return
        val context = robot.getBeeContext()
        
        task.action.execute(robot.level(), task.targetPos, robot, context)
    }

    private fun flyTowards(target: Vec3) {
        val speed = 0.4 * robot.getSpeedMultiplier()
        val currentPos = robot.position()
        val direction = target.subtract(currentPos).normalize()

        robot.deltaMovement = direction.scale(speed)
        lookAt(target)

        if (currentPos.distanceToSqr(robot.lastPosition) < 0.01) {
            robot.incrementStuckTicks()
            if (robot.stuckTicks >= MechanicalBeeEntity.MAX_STUCK_TICKS) {
                robot.teleportTo(target.x, target.y, target.z)
                robot.resetStuckTicks()
            }
        } else {
            robot.resetStuckTicks()
        }
        robot.lastPosition = currentPos
    }

    private fun lookAt(target: Vec3) {
        val dx = target.x - robot.x
        val dy = target.y - robot.y
        val dz = target.z - robot.z
        val horizontalDist = kotlin.math.sqrt(dx * dx + dz * dz)
        robot.xRot = (-(kotlin.math.atan2(dy, horizontalDist) * (180.0 / Math.PI))).toFloat()
        robot.yRot = (kotlin.math.atan2(dz, dx) * (180.0 / Math.PI)).toFloat() - 90f
    }

    override fun canContinueToUse(): Boolean = true
    override fun isInterruptable(): Boolean = false
}
