package de.devin.cbbees.network

import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.TransportDispatcher
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.drone.DroneViewManager
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
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
        DroneViewManager.validateDrones()

        // Sync packets every 40 ticks (2 seconds) to reduce network and serialization overhead
        syncCounter++
        if (syncCounter >= 4) {
            syncCounter = 0
            for (player in server.playerList.players) {
                HiveJobsSyncPacket.sendPlayerSnapshotTo(player)
                NetworkSyncPacket.sendTo(player)
            }
        }
    }

    /**
     * Unregisters players as bee sources when they log out.
     */
    @SubscribeEvent
    @JvmStatic
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer
        if (player != null) {
            DroneViewManager.despawnDrone(player)
        }
        ServerBeeNetworkManager.unregisterWorker(event.entity.uuid)
    }

    @SubscribeEvent
    @JvmStatic
    fun onPlayerDeath(event: LivingDeathEvent) {
        val player = event.entity as? ServerPlayer ?: return
        DroneViewManager.despawnDrone(player)
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
        DroneViewManager.clear()
    }
}
