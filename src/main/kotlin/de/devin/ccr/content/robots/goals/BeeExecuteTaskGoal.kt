package de.devin.ccr.content.robots.goals

import de.devin.ccr.content.robots.PlayerBeeHome
import de.devin.ccr.content.robots.BeeSource
import de.devin.ccr.content.robots.MechanicalBeeEntity
import de.devin.ccr.content.robots.BeeState
import de.devin.ccr.content.schematics.BeeJob
import de.devin.ccr.content.schematics.BeeTask
import de.devin.ccr.content.schematics.GlobalJobPool
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
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
        val home = robot.getHome()
        val ownerPlayer = robot.getOwnerPlayer()
        
        if (home is PlayerBeeHome && ownerPlayer == null) {
            robot.dropRobotItemAndDiscard()
            return
        }

        if (robot.air <= 0 && robot.currentState != BeeState.RETURNING_TO_PLAYER) {
            robot.currentState = BeeState.RETURNING_TO_PLAYER
            if (robot.currentTask != null) {
                // Task will be failed/returned to pool by the job system
                robot.currentTask = null
                robot.setTaskPos(null)
                robot.setWorking(false)
            }
        }
        
        when (robot.currentState) {
            BeeState.IDLE -> handleIdleState(ownerPlayer)
            BeeState.FETCHING_ITEMS -> handleFetchingState(ownerPlayer)
            BeeState.TRAVELING_TO_WORK -> handleTravelingState()
            BeeState.WORKING -> handleWorkingState()
            BeeState.RETURNING_TO_PLAYER -> handleReturningState(ownerPlayer)
        }
    }

    private fun handleIdleState(player: ServerPlayer?) {
        if (robot.air < (MechanicalBeeEntity.MAX_AIR / 4)) {
            robot.currentState = BeeState.RETURNING_TO_PLAYER
            return
        }
        
        // Check GlobalJobPool for jobs this bee's home can help with
        val home = robot.getHome()
        if (home is BeeSource) {
            val task = GlobalJobPool.getTaskForBee(home, robot.id)
            if (task != null) {
                assignTask(task)
                
                // Use creative mode check if player is online and creative, or if it's a stationary hive (never creative)
                val isCreative = player?.isCreative == true
                if (isCreative || task.action.requiredItems.isEmpty()) {
                    robot.currentState = BeeState.TRAVELING_TO_WORK
                } else {
                    robot.currentState = BeeState.FETCHING_ITEMS
                }
                return
            }
        }
        
        // No tasks available anywhere - return to player/home
        robot.currentState = BeeState.RETURNING_TO_PLAYER
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
                robot.currentState = BeeState.TRAVELING_TO_WORK
            } else {
                // Return task to job pool
                task.cancel()
                robot.currentTask = null
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
            
            // Refill air
            val refilled = robot.refillAirFromHive()
            
            // Check if there are any global jobs available that this bee's home can help with
            val hasTasks = hasAvailableGlobalJobs()
            
            // Only stop returning if we managed to empty our hands
            if (robot.carriedItems.isEmpty()) {
                if (hasTasks && robot.air > (MechanicalBeeEntity.MAX_AIR / 2)) {
                    robot.currentState = BeeState.IDLE
                } else if (robot.air <= 0 && !refilled) {
                    // Out of air and can't refill - go home
                    robot.returnToHomeAndDiscard()
                } else if (!hasTasks) {
                    robot.returnToHomeAndDiscard()
                }
            }
        }
    }
    
    /**
     * Checks if there are any global jobs available that this bee's home can help with.
     */
    private fun hasAvailableGlobalJobs(): Boolean {
        val home = robot.getHome()
        if (home !is BeeSource) return false
        
        val jobsInRange = GlobalJobPool.findJobsForSource(home)
        return jobsInRange.any { job ->
            job.status == BeeJob.JobStatus.IN_PROGRESS && job.canStart() && job.getNextTask() != null
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
