package de.devin.ccr.network

import de.devin.ccr.content.domain.GlobalJobPool
import de.devin.ccr.content.domain.beehive.PlayerBeeHive
import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.domain.task.TaskStatus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Server-side event handler for CCR.
 *
 * Handles periodic task progress sync to clients.
 */
object CCRServerEvents {

    private var tickCounter = 0

    /**
     * Called every server tick.
     * Sends task progress sync packets to players with active tasks.
     */
    @SubscribeEvent
    @JvmStatic
    fun onServerTick(event: ServerTickEvent.Post) {
        tickCounter++

        // Send sync every 10 ticks (0.5 seconds)
        if (tickCounter < 10) return
        tickCounter = 0

        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() ?: return
        for (player in server.playerList.players) {
            val jobs = GlobalJobPool.getAllJobs().filter { it.ownerId == player.uuid }
            if (jobs.isNotEmpty()) {
                val totalTasks = jobs.sumOf { it.tasks.size }
                val completedTasks = jobs.sumOf { it.tasks.count { t -> t.status == TaskStatus.COMPLETED } }

                // jobProgress map: jobId -> (completed, total)
                val jobProgress =
                    jobs.associate { it.jobId to (it.tasks.count { t -> t.status == TaskStatus.COMPLETED } to it.tasks.size) }

                val packet = TaskProgressSyncPacket(
                    globalTotal = totalTasks,
                    globalCompleted = completedTasks,
                    jobProgress = jobProgress
                )
                PacketDistributor.sendToPlayer(player, packet)
            }
        }
    }

    /**
     * Unregisters players as bee sources when they log out.
     */
    @SubscribeEvent
    @JvmStatic
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        // Handled by BeeHive implementations if needed, 
        // but PlayerBeeHive should ideally be unregistered here if it's not a block entity
        GlobalJobPool.workers.removeIf { it is PlayerBeeHive && it.player.uuid == event.entity.uuid }
    }
}
