package de.devin.ccr.network

import de.devin.ccr.content.robots.MechanicalBeeEntity
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
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
        
        // Iterate through all players with active task managers
        for ((playerUuid, taskManager) in MechanicalBeeEntity.playerTaskManagers) {
            // Find the player
            val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() ?: continue
            val player = server.playerList.getPlayer(playerUuid) ?: continue
            
            if (player is ServerPlayer) {
                // Send progress sync packet
                val jobProgress = taskManager.getActiveJobProgress()
                val packet = TaskProgressSyncPacket(
                    globalTotal = jobProgress.values.sumOf { it.second },
                    globalCompleted = jobProgress.values.sumOf { it.first },
                    jobProgress = jobProgress
                )
                PacketDistributor.sendToPlayer(player, packet)
            }
        }
    }
}
