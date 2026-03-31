package de.devin.cbbees.content.domain.events

import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import net.minecraftforge.event.entity.player.PlayerEvent
import de.devin.cbbees.util.ServerSide
import top.theillusivec4.curios.api.CuriosApi

/**
 * Forge 1.20.1 override:
 * - Uses net.minecraftforge.event.entity.player.PlayerEvent instead of NeoForge's
 * - Uses event.entity (Forge PlayerEvent exposes .entity for the player)
 * - PlayerTickEvent is handled manually in CreateBuzzyBeez via TickEvent.PlayerTickEvent,
 *   so no @SubscribeEvent for onPlayerTick here (only onPlayerLoggedIn).
 */
@ServerSide
class PlayerTickEvent {

    /**
     * Registers the portable beehive immediately on login so that bees
     * loaded from disk can reconnect before the first tick-based check.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity
        if (player.level().isClientSide) return
        if (hasPortableHive(player)) {
            val hive = PortableBeeHive(player)
            hive.networkId = ServerBeeNetworkManager.stableNetworkId(player.uuid)
            ServerBeeNetworkManager.registerWorker(hive)
        }
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
