package de.devin.ccr.content.robots.goals

import de.devin.ccr.content.robots.ConstructorRobotEntity
import de.devin.ccr.content.robots.RobotState
import de.devin.ccr.content.robots.PlaceAction
import de.devin.ccr.content.robots.RemoveAction
import de.devin.ccr.content.schematics.RobotTask
import de.devin.ccr.content.schematics.RobotTaskManager
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
class RobotExecuteTaskGoal(private val robot: ConstructorRobotEntity) : Goal() {
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
        
        when (robot.currentState) {
            RobotState.IDLE -> handleIdleState(ownerPlayer, tm)
            RobotState.FETCHING_ITEMS -> handleFetchingState(ownerPlayer, tm)
            RobotState.TRAVELING_TO_WORK -> handleTravelingState()
            RobotState.WORKING -> handleWorkingState()
            RobotState.RETURNING_TO_PLAYER -> handleReturningState(ownerPlayer, tm)
        }
    }

    private fun handleIdleState(player: ServerPlayer, tm: RobotTaskManager) {
        if (tm.hasPendingTasks()) {
            if (player.isCreative) {
                val nextTask = tm.getNextTask(robot.id, emptyList(), true)
                if (nextTask != null) {
                    assignTask(nextTask)
                    robot.currentState = RobotState.TRAVELING_TO_WORK
                }
            } else {
                robot.currentState = RobotState.FETCHING_ITEMS
            }
        } else {
            robot.currentState = RobotState.RETURNING_TO_PLAYER
        }
    }

    private fun handleFetchingState(player: ServerPlayer, tm: RobotTaskManager) {
        val playerPos = player.position().add(0.0, 1.5, 0.0)
        if (robot.distanceToSqr(playerPos) > 4.0) {
            flyTowards(playerPos)
            return
        }

        val nextTask = tm.getNextTask(robot.id, emptyList(), false) 
        if (nextTask != null) {
            if (nextTask.type == RobotTask.TaskType.PLACE) {
                if (robot.inventoryManager.pickUpItems(nextTask.requiredItems, robot.getRobotContext(), robot.carriedItems)) {
                    assignTask(nextTask)
                    robot.currentState = RobotState.TRAVELING_TO_WORK
                } else {
                    tm.failTask(robot.id)
                    robot.currentState = RobotState.IDLE
                }
            } else {
                assignTask(nextTask)
                robot.currentState = RobotState.TRAVELING_TO_WORK
            }
        } else {
            robot.currentState = RobotState.IDLE
        }
    }

    private fun handleTravelingState() {
        val task = robot.currentTask
        if (task == null) {
            robot.currentState = RobotState.IDLE
            return
        }
        val targetVec = Vec3.atCenterOf(task.targetPos)
        
        if (robot.distanceToSqr(targetVec) > 2.0) {
            flyTowards(targetVec)
        } else {
            robot.setDeltaMovement(Vec3.ZERO)
            robot.currentState = RobotState.WORKING
            workTicks = 0
        }
    }

    private fun handleWorkingState() {
        val task = robot.currentTask
        if (task == null) {
            robot.currentState = RobotState.IDLE
            return
        }
        
        if (task.type == RobotTask.TaskType.PLACE) {
            performWork()
            completeTask()
        } else {
            workTicks++
            if (robot.level() is ServerLevel) {
                (robot.level() as ServerLevel).sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    task.targetPos.x + 0.5, task.targetPos.y + 0.5, task.targetPos.z + 0.5,
                    2, 0.2, 0.2, 0.2, 0.05
                )
            }

            val breakSpeedMultiplier = robot.getRobotContext().breakSpeedMultiplier
            val requiredTicks = (ConstructorRobotEntity.BASE_BREAK_TICKS * breakSpeedMultiplier / robot.getSpeedMultiplier()).toInt().coerceAtLeast(1)
            if (workTicks >= requiredTicks) {
                performWork()
                completeTask()
            }
        }
    }

    private fun handleReturningState(player: ServerPlayer, tm: RobotTaskManager) {
        val playerPos = player.position().add(0.0, 1.5, 0.0)
        if (robot.distanceToSqr(playerPos) > 4.0) {
            flyTowards(playerPos)
        } else {
            robot.setDeltaMovement(Vec3.ZERO)
            
            // Deposit items if carrying any
            if (robot.carriedItems.isNotEmpty()) {
                robot.inventoryManager.depositItems(robot.carriedItems, robot.getRobotContext())
            }
            
            // Only stop returning if we managed to empty our hands
            if (robot.carriedItems.isEmpty()) {
                if (tm.hasPendingTasks()) {
                    robot.currentState = RobotState.IDLE
                } else {
                    robot.returnToBackpackAndDiscard(player)
                }
            }
        }
    }

    private fun assignTask(task: RobotTask) {
        robot.currentTask = task
        robot.setTaskPos(task.targetPos)
        robot.setWorking(true)
        robot.resetStuckTicks()
    }

    private fun completeTask() {
        val tm = robot.taskManager ?: return
        val task = robot.currentTask
        tm.completeTask(robot.id)
        robot.currentTask = null
        
        // Only clear items if it was a placement task (consumed)
        if (task?.type == RobotTask.TaskType.PLACE) {
            robot.carriedItems.clear()
        }
        
        robot.setTaskPos(null)
        robot.setWorking(false)
        
        val context = robot.getRobotContext()
        if (task?.type == RobotTask.TaskType.REMOVE && !context.pickupEnabled) {
            robot.currentState = RobotState.IDLE
        } else {
            robot.currentState = RobotState.RETURNING_TO_PLAYER
        }
    }

    private fun performWork() {
        val task = robot.currentTask ?: return
        val context = robot.getRobotContext()
        
        val action = when (task.type) {
            RobotTask.TaskType.PLACE -> PlaceAction()
            RobotTask.TaskType.REMOVE -> RemoveAction(robot)
        }
        
        action.execute(robot.level(), task.targetPos, task, context)
    }

    private fun flyTowards(target: Vec3) {
        val speed = 0.4 * robot.getSpeedMultiplier()
        val currentPos = robot.position()
        val direction = target.subtract(currentPos).normalize()
        
        robot.setDeltaMovement(direction.scale(speed))
        lookAt(target)

        if (currentPos.distanceToSqr(robot.lastPosition) < 0.01) {
            robot.incrementStuckTicks()
            if (robot.stuckTicks >= ConstructorRobotEntity.MAX_STUCK_TICKS) {
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
