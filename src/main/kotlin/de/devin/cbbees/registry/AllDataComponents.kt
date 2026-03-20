package de.devin.cbbees.registry

import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.util.ExtraCodecs
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object AllDataComponents {

    private val REGISTER: DeferredRegister.DataComponents =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateBuzzyBeez.ID)

    val HONEY_FUEL: DeferredHolder<DataComponentType<*>, DataComponentType<Int>> =
        REGISTER.registerComponentType("honey_fuel") { builder ->
            builder.persistent(ExtraCodecs.NON_NEGATIVE_INT)
                .networkSynchronized(ByteBufCodecs.VAR_INT)
        }

    fun register(bus: IEventBus) {
        REGISTER.register(bus)
    }
}
