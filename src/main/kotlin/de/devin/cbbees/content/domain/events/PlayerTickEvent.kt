package de.devin.cbbees.content.domain.events

import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import top.theillusivec4.curios.api.CuriosApi
import de.devin.cbbees.util.ServerSide

@ServerSide
class PlayerTickEvent {

    @SubscribeEvent
    fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity
        if (player.level().isClientSide || player.tickCount % 40 != 0) return

        val pool = ServerBeeNetworkManager

        val hasPortableHive = player.inventory.items.any { it.item is PortableBeehiveItem } ||
                CuriosApi.getCuriosHelper().findFirstCurio(player) { it.item is PortableBeehiveItem }.isPresent

        val isAlreadyRegistered =
            pool.getNetworks().flatMap { it.hives }.any { it is PortableBeeHive && it.player.uuid == player.uuid }

        if (hasPortableHive) {
            if (!isAlreadyRegistered) {
                pool.registerWorker(PortableBeeHive(player))
            }
        } else {
            if (isAlreadyRegistered) {
                pool.unregisterWorker(player.uuid)
            }
        }
    }
}