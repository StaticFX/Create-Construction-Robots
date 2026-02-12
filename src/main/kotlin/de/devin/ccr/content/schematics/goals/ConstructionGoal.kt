package de.devin.ccr.content.schematics.goals

import de.devin.ccr.content.domain.job.BeeJob
import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.schematics.SchematicJobKey
import de.devin.ccr.content.schematics.SchematicCreateBridge
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.UUID

class ConstructionGoal(private val schematicStack: ItemStack) : BeeJobGoal {
    override fun createJobKey(playerUuid: UUID): Any? {
        val schematicFile = schematicStack.get(com.simibubi.create.AllDataComponents.SCHEMATIC_FILE) ?: return null
        val anchor = schematicStack.get(com.simibubi.create.AllDataComponents.SCHEMATIC_ANCHOR) ?: return null
        return SchematicJobKey(playerUuid, schematicFile, anchor.x, anchor.y, anchor.z)
    }

    override fun validate(level: Level): Component? {
        val handler = SchematicCreateBridge(level)
        if (!handler.loadSchematic(schematicStack)) {
            return Component.translatable("ccr.construction.load_failed")
        }
        return null
    }

    override fun generateTasks(job: BeeJob): List<BeeTask> {
        val handler = SchematicCreateBridge(job.level)
        return if (handler.loadSchematic(schematicStack)) {
            handler.generateBuildTasks(job)
        } else {
            emptyList()
        }
    }

    override fun getCenterPos(level: Level, tasks: List<BeeTask>): BlockPos {
        val handler = SchematicCreateBridge(level)
        if (handler.loadSchematic(schematicStack)) {
            return handler.getAnchor() ?: (if (tasks.isNotEmpty()) tasks[0].targetPos else BlockPos.ZERO)
        }
        return if (tasks.isNotEmpty()) tasks[0].targetPos else BlockPos.ZERO
    }

    override fun getStartMessage(taskCount: Int): Component =
        Component.translatable("ccr.construction.started", taskCount)

    override fun getAlreadyActiveMessage(): Component =
        Component.translatable("ccr.construction.already_active")

    override fun getNoTasksMessage(): Component =
        Component.translatable("ccr.construction.no_tasks")

    override fun onJobStarted(player: ServerPlayer) {
        // Only shrink if we're not in creative mode
        if (!player.isCreative) {
            schematicStack.shrink(1)
        }
    }
}
