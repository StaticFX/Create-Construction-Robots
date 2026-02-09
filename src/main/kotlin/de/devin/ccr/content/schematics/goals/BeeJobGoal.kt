package de.devin.ccr.content.schematics.goals

import de.devin.ccr.content.domain.task.BeeTask

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * Interface to abstract different types of bee jobs (construction, deconstruction, etc.).
 */
interface BeeJobGoal {
    fun createJobKey(playerUuid: UUID): Any?
    fun generateTasks(jobId: UUID, level: Level): List<BeeTask>
    fun getCenterPos(level: Level, tasks: List<BeeTask>): BlockPos
    
    fun getStartMessage(taskCount: Int): Component
    fun getAlreadyActiveMessage(): Component
    fun getNoTasksMessage(): Component
    
    fun validate(level: Level): Component? = null
    fun onJobStarted(player: ServerPlayer) {}
}
