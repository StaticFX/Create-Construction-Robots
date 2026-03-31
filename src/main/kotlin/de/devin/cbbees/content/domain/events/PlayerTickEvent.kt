package de.devin.cbbees.content.domain.events

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import top.theillusivec4.curios.api.CuriosApi
import de.devin.cbbees.util.ServerSide

@ServerSide
class PlayerTickEvent {

    /**
     * Registers the portable beehive immediately on login so that bees
     * loaded from disk can reconnect before the first tick-based check.
     */
    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity
        if (player.level().isClientSide) return
        if (hasPortableHive(player)) {
            val hive = PortableBeeHive(player)
            hive.networkId = ServerBeeNetworkManager.stableNetworkId(player.uuid)
            ServerBeeNetworkManager.registerWorker(hive)
        }
    }

    @SubscribeEvent
    fun onPlayerTick(event: PlayerTickEvent.Post) {
        tick(event.entity)
    }

    fun tick(player: net.minecraft.world.entity.player.Player) {
        if (player.level().isClientSide || player.tickCount % 40 != 0) return

        val pool = ServerBeeNetworkManager

        val existingHive =
            pool.getNetworks().flatMap { it.hives }.filterIsInstance<PortableBeeHive>().find { it.player.uuid == player.uuid }

        if (hasPortableHive(player)) {
            if (existingHive == null) {
                val hive = PortableBeeHive(player)
                hive.networkId = pool.stableNetworkId(player.uuid)
                pool.registerWorker(hive)
            } else {
                // Reconnect: handles joining/leaving block-based networks based on position
                pool.reconnectPortableHive(existingHive)
            }
        } else {
            if (existingHive != null) {
                pool.unregisterWorker(player.uuid)
            }
        }
    }

    private fun hasPortableHive(player: net.minecraft.world.entity.player.Player): Boolean {
        // Check Curios back slot
        val curios = CuriosApi.getCuriosHelper().findFirstCurio(player) { it.item is PortableBeehiveItem }
        if (curios.isPresent) return true
        // Check chestplate armor slot
        return player.inventory.armor[2].item is PortableBeehiveItem
    }
}