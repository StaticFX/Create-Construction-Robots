package de.devin.ccr.content.schematics.goals

import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.schematics.SchematicJobKey
import de.devin.ccr.content.schematics.SchematicCreateBridge
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import java.util.UUID

class DeconstructionGoal(private val pos1: BlockPos, private val pos2: BlockPos) : BeeJobGoal {
    override fun createJobKey(playerUuid: UUID): Any? =
        SchematicJobKey(playerUuid, "deconstruct_area", pos1.x, pos1.y, pos1.z)

    override fun generateTasks(jobId: UUID, level: Level): List<BeeTask> =
        SchematicCreateBridge(level).generateRemovalTasks(pos1, pos2, jobId)

    override fun getCenterPos(level: Level, tasks: List<BeeTask>): BlockPos =
        BlockPos(
            (pos1.x + pos2.x) / 2,
            (pos1.y + pos2.y) / 2,
            (pos1.z + pos2.z) / 2
        )

    override fun getStartMessage(taskCount: Int): Component =
        Component.translatable("ccr.deconstruction.started", taskCount)

    override fun getAlreadyActiveMessage(): Component =
        Component.translatable("ccr.deconstruction.already_active")

    override fun getNoTasksMessage(): Component =
        Component.translatable("ccr.deconstruction.no_blocks")
}
