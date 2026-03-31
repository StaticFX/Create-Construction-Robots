package de.devin.cbbees.content.backpack.client

import de.devin.cbbees.content.backpack.BeehiveTooltipData
import de.devin.cbbees.content.domain.network.ClientBeeNetworkManager
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

/**
 * Forge 1.20.1 override for CCRClientEvents.
 *
 * Differences from NeoForge:
 * - Uses net.minecraftforge.client.event.ClientPlayerNetworkEvent (has LoggingOut inner class)
 * - Uses net.minecraftforge.event.entity.player.PlayerEvent (has PlayerChangedDimensionEvent inner class)
 * - Uses event.entity from EntityEvent superclass
 */
object CCRClientEvents {

    @SubscribeEvent
    @JvmStatic
    fun registerTooltipComponents(event: RegisterClientTooltipComponentFactoriesEvent) {
        event.register(BeehiveTooltipData::class.java) { data: BeehiveTooltipData -> BeehiveTooltipComponent(data) }
    }
}

/**
 * Event handler for Forge EVENT_BUS events related to bee networks on client.
 */
object BeeNetworkClientEvents {
    @SubscribeEvent
    @JvmStatic
    fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        ClientBeeNetworkManager.clear()
    }

    @SubscribeEvent
    @JvmStatic
    fun onDimensionChange(event: PlayerEvent.PlayerChangedDimensionEvent) {
        if (event.entity.level().isClientSide) {
            ClientBeeNetworkManager.clear()
        }
    }
}
