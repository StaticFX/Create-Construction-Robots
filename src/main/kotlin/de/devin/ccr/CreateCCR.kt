package de.devin.ccr

import com.simibubi.create.foundation.data.CreateRegistrate
import de.devin.ccr.blocks.AllBlocks
import de.devin.ccr.content.backpack.client.CCRClientEvents
import de.devin.ccr.content.backpack.client.TaskProgressClientEvents
import de.devin.ccr.content.schematics.client.DeconstructionClientEvents
import de.devin.ccr.datagen.CCRDatagen
import de.devin.ccr.items.AllItems
import de.devin.ccr.network.AllPackets
import de.devin.ccr.network.CCRServerEvents
import de.devin.ccr.registry.AllEntityTypes
import de.devin.ccr.registry.AllKeys
import de.devin.ccr.registry.AllMenuTypes
import de.devin.ccr.tabs.AllCreativeModeTabs
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

/**
 * Main mod class.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Mod(CreateCCR.ID)
object CreateCCR {
    const val ID = "ccr"

    val REGISTRATE: CreateRegistrate = CreateRegistrate.create(ID)

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        REGISTRATE.registerEventListeners(MOD_BUS)

        AllCreativeModeTabs
        AllItems
        AllBlocks
        AllEntityTypes.register()
        AllMenuTypes.register()

        MOD_BUS.addListener<RegisterPayloadHandlersEvent> {
            AllPackets.register(it)
        }
        MOD_BUS.addListener<GatherDataEvent> { CCRDatagen.gatherData(it) }
        MOD_BUS.addListener<FMLClientSetupEvent> { onClientSetup(it) }
        MOD_BUS.addListener<RegisterKeyMappingsEvent> { AllKeys.register(it) }
        
        // Register client-side tooltip component factories on the mod bus
        MOD_BUS.register(CCRClientEvents::class.java)
        
        // Register server-side event handlers on the NeoForge event bus
        NeoForge.EVENT_BUS.register(CCRServerEvents::class.java)
    }

    fun asResource(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(ID, path)
    }

    /**
     * This is used for initializing client specific
     * things such as renderers and keymaps
     * Fired on the mod specific event bus.
     */
    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Initializing client...")
        
        // Register client-side event handlers on the NeoForge event bus
        // This is done manually to avoid compatibility issues with Kotlin For Forge's
        // auto-registration system which uses deprecated NeoForge APIs
        // Note: We register the class (not instance) because Kotlin object methods are static
        NeoForge.EVENT_BUS.register(DeconstructionClientEvents::class.java)
        NeoForge.EVENT_BUS.register(TaskProgressClientEvents::class.java)
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
    }

    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Hello! This is working!")
    }
}
