package de.devin.ccr.content.backpack.client

import de.devin.ccr.content.backpack.BackpackTooltipData
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent
import net.neoforged.bus.api.SubscribeEvent

/**
 * Client-side event handler for general CCR features.
 */
object CCRClientEvents {

    @SubscribeEvent
    @JvmStatic
    fun registerTooltipComponents(event: RegisterClientTooltipComponentFactoriesEvent) {
        event.register(BackpackTooltipData::class.java) { data: BackpackTooltipData -> BackpackTooltipComponent(data) }
    }
}
