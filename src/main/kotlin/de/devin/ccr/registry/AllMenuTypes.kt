package de.devin.ccr.registry

import com.tterrag.registrate.builders.MenuBuilder.ForgeMenuFactory
import com.tterrag.registrate.builders.MenuBuilder.ScreenFactory
import com.tterrag.registrate.util.entry.MenuEntry
import com.tterrag.registrate.util.nullness.NonNullSupplier
import de.devin.ccr.CreateCCR
import de.devin.ccr.content.backpack.BackpackContainer
import de.devin.ccr.content.backpack.BackpackScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.MenuAccess
import net.minecraft.world.inventory.AbstractContainerMenu

object AllMenuTypes {
    
    val CONSTRUCTOR_BACKPACK: MenuEntry<BackpackContainer> = register(
        "constructor_backpack",
        { type, containerId, inv, buf -> BackpackContainer(type, containerId, inv, buf!!) },
        { ScreenFactory { menu, inv, title -> BackpackScreen(menu, inv, title) } }
    )
    
    private fun <C : AbstractContainerMenu, S> register(
        name: String,
        factory: ForgeMenuFactory<C>,
        screenFactory: NonNullSupplier<ScreenFactory<C, S>>
    ): MenuEntry<C> where S : Screen, S : MenuAccess<C> {
        return CreateCCR.REGISTRATE
            .menu(name, factory, screenFactory)
            .register()
    }
    
    fun register() {
        // Called to classload and register all menu types
    }
}
