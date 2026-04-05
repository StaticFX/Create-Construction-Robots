package de.devin.cbbees.network

import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.TransportDispatcher
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import de.devin.cbbees.util.ServerSide

/**
 * Server-side event handler for cbbees.
 */
@ServerSide
object CCRServerEvents {

    private var tickCounter = 0
    private var syncCounter = 0
    private var purgeCounter = 0

    /**
     * Called every server tick.
     */
    @SubscribeEvent
    @JvmStatic
    fun onServerTick(event: ServerTickEvent.Post) {
        tickCounter++

        // Core logic every 10 ticks (0.5 seconds)
        if (tickCounter < 10) return
        tickCounter = 0

        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() ?: return
        val gameTime = server.overworld().gameTime

        GlobalJobPool.tick(gameTime)
        TransportDispatcher.tick(gameTime)
        ServerBeeNetworkManager.getNetworks().forEach { it.cleanupReservations(gameTime) }

        // Purge stale components every 200 ticks (10 seconds) — stale components are rare
        purgeCounter++
        if (purgeCounter >= 20) {
            purgeCounter = 0
            ServerBeeNetworkManager.getNetworks().forEach { it.purgeStaleComponents() }
        }

        // Sync packets every 40 ticks (2 seconds) to reduce network and serialization overhead
        syncCounter++
        if (syncCounter >= 4) {
            syncCounter = 0
            for (player in server.playerList.players) {
                HiveJobsSyncPacket.sendPlayerSnapshotTo(player)
                // Only send network topology when it actually changed
                if (ServerBeeNetworkManager.dirty) {
                    NetworkSyncPacket.sendTo(player)
                }
            }
            if (ServerBeeNetworkManager.dirty) {
                ServerBeeNetworkManager.clearDirty()
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
        TransportDispatcher.clear()
        BeeDebug.clear()
        PlannerUploadPacket.shutdown()
    }
}
