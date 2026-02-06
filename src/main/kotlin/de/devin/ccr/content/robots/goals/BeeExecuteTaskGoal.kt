package de.devin.ccr.content.robots.goals

import de.devin.ccr.content.robots.MechanicalBeeEntity
import de.devin.ccr.content.robots.BeeState
import de.devin.ccr.content.schematics.BeeTask
import de.devin.ccr.content.schematics.BeeTaskManager
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.phys.Vec3
import java.util.*

/**
 * Goal for finding and executing tasks.
 * Uses a state machine for clean logic flow.
 */
class BeeExecuteTaskGoal(private val robot: MechanicalBeeEntity) : Goal() {
    private var workTicks = 0

    init {
        this.flags = EnumSet.of(Flag.MOVE)
    }

    override fun canUse(): Boolean = true

    override fun tick() {
        val ownerPlayer = robot.getOwnerPlayer()
        if (ownerPlayer == null) {
            robot.dropRobotItemAndDiscard()
            return
        }

        val tm = robot.taskManager ?: return

        if (robot.air <= 0 && robot.currentState != BeeState.RETURNING_TO_PLAYER) {
            robot.currentState = BeeState.RETURNING_TO_PLAYER
            if (robot.currentTask != null) {
                tm.failTask(robot.id)
                robot.currentTask = null
                robot.setTaskPos(null)
                robot.setWorking(false)
            }
        }
        
        when (robot.currentState) {
            BeeState.IDLE -> handleIdleState(ownerPlayer, tm)
            BeeState.FETCHING_ITEMS -> handleFetchingState(ownerPlayer, tm)
            BeeState.TRAVELING_TO_WORK -> handleTravelingState()
            BeeState.WORKING -> handleWorkingState()
            BeeState.RETURNING_TO_PLAYER -> handleReturningState(ownerPlayer, tm)
        }
    }

    private fun handleIdleState(player: ServerPlayer, tm: BeeTaskManager) {
        if (robot.air < (MechanicalBeeEntity.MAX_AIR / 4)) {
            robot.currentState = BeeState.RETURNING_TO_PLAYER
            return
        }

        if (tm.hasPendingTasks()) {
            if (player.isCreative) {
                val nextTask = tm.getNextTask(robot.id, emptyList(), true)
                if (nextTask != null) {
                    assignTask(nextTask)
                    robot.currentState = BeeState.TRAVELING_TO_WORK
                }
            } else {
                robot.currentState = BeeState.FETCHING_ITEMS
            }
        } else {
            robot.currentState = BeeState.RETURNING_TO_PLAYER
        }
    }

    private fun handleFetchingState(player: ServerPlayer, tm: BeeTaskManager) {
        val playerPos = player.position().add(0.0, 1.5, 0.0)
        if (robot.distanceToSqr(playerPos) > 4.0) {
            flyTowards(playerPos)
            return
        }

        val nextTask = tm.getNextTask(robot.id, emptyList(), false) 
        if (nextTask != null) {
            val required = nextTask.action.requiredItems
            if (required.isEmpty() || robot.inventoryManager.pickUpItems(required, robot.getBeeContext(), robot.carriedItems)) {
                assignTask(nextTask)
                robot.currentState = BeeState.TRAVELING_TO_WORK
            } else {
                tm.failTask(robot.id)
                robot.currentState = BeeState.IDLE
            }
        } else {
            robot.currentState = BeeState.IDLE
        }
    }

    private fun handleTravelingState() {
        val task = robot.currentTask
        if (task == null) {
            robot.currentState = BeeState.IDLE
            return
        }
        val targetVec = Vec3.atCenterOf(task.targetPos)
        
        if (robot.distanceToSqr(targetVec) > 2.0) {
            flyTowards(targetVec)
        } else {
            robot.setDeltaMovement(Vec3.ZERO)
            robot.currentState = BeeState.WORKING
            workTicks = 0
            task.action.onStart(robot)
        }
    }

    private fun handleWorkingState() {
        val task = robot.currentTask
        if (task == null) {
            robot.currentState = BeeState.IDLE
            return
        }
        
        val context = robot.getBeeContext()
        val requiredTicks = (task.action.getWorkTicks(context) / robot.getSpeedMultiplier()).toInt().coerceAtLeast(0)
        
        if (workTicks >= requiredTicks) {
            performWork()
            completeTask()
        } else {
            task.action.onTick(robot, workTicks)
            workTicks++
        }
    }

    private fun handleReturningState(player: ServerPlayer, tm: BeeTaskManager) {
        val playerPos = player.position().add(0.0, 1.5, 0.0)
        if (robot.distanceToSqr(playerPos) > 4.0) {
            flyTowards(playerPos)
        } else {
            robot.setDeltaMovement(Vec3.ZERO)
            
            // Deposit items if carrying any
            if (robot.carriedItems.isNotEmpty()) {
                robot.inventoryManager.depositItems(robot.carriedItems, robot.getBeeContext())
            }
            
            // Refill air
            val refilled = robot.refillAirFromHive()
            
            // Only stop returning if we managed to empty our hands
            if (robot.carriedItems.isEmpty()) {
                if (tm.hasPendingTasks() && robot.air > (MechanicalBeeEntity.MAX_AIR / 2)) {
                    robot.currentState = BeeState.IDLE
                } else if (robot.air <= 0 && !refilled) {
                    // Out of air and can't refill - go home
                    robot.returnToBackpackAndDiscard(player)
                } else if (!tm.hasPendingTasks()) {
                    robot.returnToBackpackAndDiscard(player)
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
        val tm = robot.taskManager ?: return
        val task = robot.currentTask ?: return
        tm.completeTask(robot.id)
        robot.currentTask = null
        
        // Only clear items if they were consumed (PlaceAction handles its own consumption logic usually, 
        // but here we just clear carried items if it wasn't a return-trip action)
        if (task.action.requiredItems.isNotEmpty()) {
            robot.carriedItems.clear()
        }
        
        robot.setTaskPos(null)
        robot.setWorking(false)
        
        val context = robot.getBeeContext()
        if (task.action.shouldReturnAfter(context)) {
            robot.currentState = BeeState.RETURNING_TO_PLAYER
        } else {
            robot.currentState = BeeState.IDLE
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
        
        robot.setDeltaMovement(direction.scale(speed))
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
