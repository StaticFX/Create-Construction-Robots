package de.devin.cbbees.network

import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.TransportDispatcher
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.server.ServerLifecycleHooks

/**
 * Forge 1.20.1 server-side event handler for cbbees.
 */
object CCRServerEvents {

    private var tickCounter = 0
    private var syncCounter = 0

    @SubscribeEvent
    @JvmStatic
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        tickCounter++

        if (tickCounter < 10) return
        tickCounter = 0

        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        val gameTime = server.overworld().gameTime

        GlobalJobPool.tick(gameTime)
        TransportDispatcher.tick(gameTime)
        ServerBeeNetworkManager.getNetworks().forEach { it.cleanupReservations(gameTime) }

        syncCounter++
        if (syncCounter >= 4) {
            syncCounter = 0
            for (player in server.playerList.players) {
                HiveJobsSyncPacket.sendPlayerSnapshotTo(player)
                NetworkSyncPacket.sendTo(player)
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        ServerBeeNetworkManager.unregisterWorker(event.entity.uuid)
    }

    @SubscribeEvent
    @JvmStatic
    fun onServerStopping(event: ServerStoppingEvent) {
        ServerBeeNetworkManager.getNetworks().forEach { it.clearReservations() }
        ServerBeeNetworkManager.clear()
        GlobalJobPool.clear()
        TransportDispatcher.clear()
        BeeDebug.clear()
        PlannerUploadPacket.shutdown()
    }
}
