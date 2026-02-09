package de.devin.ccr.content.schematics.goals

import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.domain.RemoveAction
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import java.util.UUID

class MaintenanceDeconstructionGoal(private val center: BlockPos, private val range: Int) : BeeJobGoal {
    override fun createJobKey(playerUuid: UUID): Any? = null
    override fun getStartMessage(taskCount: Int): Component = Component.empty()
    override fun getAlreadyActiveMessage(): Component = Component.empty()
    override fun getNoTasksMessage(): Component = Component.empty()

    override fun generateTasks(jobId: UUID, level: Level): List<BeeTask> {
        val tasks = mutableListOf<BeeTask>()
        val radius = range.coerceAtMost(32)
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = center.offset(x, y, z)
                    if (pos == center) continue
                    
                    val state = level.getBlockState(pos)
                    if (!state.isAir && state.getDestroySpeed(level, pos) >= 0) {
                        tasks.add(BeeTask(RemoveAction(), pos, 0).apply { this.jobId = jobId })
                    }
                }
            }
        }
        return tasks
    }

    override fun getCenterPos(level: Level, tasks: List<BeeTask>): BlockPos = center
}
