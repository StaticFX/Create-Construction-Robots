package de.devin.ccr.network

import de.devin.ccr.content.robots.PlayerBeeHome
import de.devin.ccr.content.robots.BeeContributionManager
import de.devin.ccr.content.robots.MechanicalBeeEntity
import de.devin.ccr.content.schematics.BeeTask
import de.devin.ccr.content.schematics.GlobalJobPool
import net.minecraft.server.level.ServerPlayer
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
                val completedTasks = jobs.sumOf { it.tasks.count { t -> t.status == BeeTask.TaskStatus.COMPLETED } }
                
                // jobProgress map: jobId -> (completed, total)
                val jobProgress = jobs.associate { it.jobId to (it.tasks.count { t -> t.status == BeeTask.TaskStatus.COMPLETED } to it.tasks.size) }
                
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
        BeeContributionManager.unregisterSource(event.entity.uuid)
    }
}
