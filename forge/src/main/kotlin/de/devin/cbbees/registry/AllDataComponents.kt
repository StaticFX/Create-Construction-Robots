package de.devin.cbbees.registry

import net.minecraftforge.eventbus.api.IEventBus

/**
 * Forge 1.20.1 — DataComponents don't exist.
 * Honey fuel values are stored via NBT, abstracted by HoneyFuelHelper.
 */
object AllDataComponents {
    fun register(bus: IEventBus) {
        // No-op: DataComponents are a 1.21+ feature
    }
}
