package de.devin.cbbees.compat

import com.simibubi.create.foundation.data.CreateRegistrate
import com.simibubi.create.api.registrate.CreateRegistrateRegistrationCallback
import net.minecraftforge.eventbus.api.IEventBus
import thedarkcolour.kotlinforforge.forge.MOD_BUS

/**
 * Forge 1.20.1 + KotlinForForge: overrides getModEventBus() to return the KFF
 * mod bus instead of calling FMLJavaModLoadingContext.get() which throws
 * ClassCastException when the mod loader is kotlinforforge.
 */
class KFFCreateRegistrate private constructor(modid: String) : CreateRegistrate(modid) {
    override fun getModEventBus(): IEventBus = MOD_BUS

    companion object {
        fun create(modid: String): KFFCreateRegistrate {
            val registrate = KFFCreateRegistrate(modid)
            CreateRegistrateRegistrationCallback.provideRegistrate(registrate)
            return registrate
        }
    }
}
