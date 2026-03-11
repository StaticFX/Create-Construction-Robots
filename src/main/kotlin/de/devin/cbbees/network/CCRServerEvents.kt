package de.devin.cbbees.network

import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.task.TaskStatus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import de.devin.cbbees.util.ServerSide

/**
 * Server-side event handler for cbbees.
 *
 * Handles periodic task progress sync to clients.
 */
@ServerSide
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
        val gameTime = server.overworld().gameTime

        GlobalJobPool.tick(gameTime)
        ServerBeeNetworkManager.getNetworks().forEach { it.cleanupReservations(gameTime) }
        for (player in server.playerList.players) {
            HiveJobsSyncPacket.sendPlayerSnapshotTo(player)

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
        ServerBeeNetworkManager.unregisterWorker(event.entity.uuid)
    }

    /**
     * Clears networks on server stop to prevent stale data between world loads.
     */
    @SubscribeEvent
    @JvmStatic
    fun onServerStopping(event: ServerStoppingEvent) {
        ServerBeeNetworkManager.getNetworks().forEach { it.clearReservations() }
        ServerBeeNetworkManager.clear()
        GlobalJobPool.clear()
        BeeDebug.clear()
    }
}
