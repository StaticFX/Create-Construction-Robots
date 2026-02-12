package de.devin.ccr.content.domain.events

import de.devin.ccr.content.backpack.PortableBeehiveItem
import de.devin.ccr.content.domain.GlobalJobPool
import de.devin.ccr.content.domain.beehive.PortableBeeHive
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import top.theillusivec4.curios.api.CuriosApi

class PlayerTickEvent {

    @SubscribeEvent
    fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity
        if (player.level().isClientSide || player.tickCount % 40 != 0) return

        val pool = GlobalJobPool

        val hasPortableHive = player.inventory.items.any { it.item is PortableBeehiveItem } ||
                CuriosApi.getCuriosHelper().findFirstCurio(player) { it.item is PortableBeehiveItem }.isPresent

        val isAlreadyRegistered = pool.workers.any { it is PortableBeeHive && it.player.uuid == player.uuid }

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