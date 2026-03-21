package de.devin.cbbees.content.domain.events

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
            ServerBeeNetworkManager.registerWorker(PortableBeeHive(player))
        }
    }

    @SubscribeEvent
    fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity
        if (player.level().isClientSide || player.tickCount % 40 != 0) return

        val pool = ServerBeeNetworkManager

        val isAlreadyRegistered =
            pool.getNetworks().flatMap { it.hives }.any { it is PortableBeeHive && it.player.uuid == player.uuid }

        if (hasPortableHive(player)) {
            if (!isAlreadyRegistered) {
                pool.registerWorker(PortableBeeHive(player))
            }
        } else {
            if (isAlreadyRegistered) {
                pool.unregisterWorker(player.uuid)
            }
        }
    }

    private fun hasPortableHive(player: net.minecraft.world.entity.player.Player): Boolean {
        return CuriosApi.getCuriosHelper().findFirstCurio(player) { it.item is PortableBeehiveItem }.isPresent
    }
}