package de.devin.ccr

import com.simibubi.create.foundation.data.CreateRegistrate
import com.simibubi.create.foundation.item.ItemDescription
import com.simibubi.create.foundation.item.KineticStats
import com.simibubi.create.foundation.item.TooltipModifier
import com.simibubi.create.api.stress.BlockStressValues
import net.createmod.catnip.lang.FontHelper
import de.devin.ccr.blocks.AllBlocks
import de.devin.ccr.content.backpack.client.CCRClientEvents
import de.devin.ccr.content.backpack.client.TaskProgressClientEvents
import de.devin.ccr.content.schematics.client.DeconstructionClientEvents
import de.devin.ccr.datagen.CCRDatagen
import de.devin.ccr.items.AllItems
import de.devin.ccr.network.AllPackets
import de.devin.ccr.network.CCRServerEvents
import de.devin.ccr.ponder.CCRPonderPlugin
import de.devin.ccr.registry.AllBlockEntityTypes
import de.devin.ccr.registry.AllEntityTypes
import de.devin.ccr.registry.AllKeys
import de.devin.ccr.registry.AllMenuTypes
import de.devin.ccr.tabs.AllCreativeModeTabs
import net.createmod.ponder.foundation.PonderIndex
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
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import de.devin.ccr.content.beehive.MechanicalBeehiveVisual
import de.devin.ccr.content.beehive.client.BeehiveRangeHandler

/**
 * Main mod class.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Mod(CreateCCR.ID)
object CreateCCR {
    const val ID = "ccr"

    val REGISTRATE: CreateRegistrate = CreateRegistrate.create(ID)
        .setTooltipModifierFactory { item ->
            ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
        }

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        REGISTRATE.registerEventListeners(MOD_BUS)

        AllCreativeModeTabs.register()
        REGISTRATE.setCreativeTab(AllCreativeModeTabs.BASE_MOD_TAB)
        AllBlocks.register()
        AllItems.register()
        AllEntityTypes.register()
        AllBlockEntityTypes.register()
        AllMenuTypes.register()

        MOD_BUS.addListener<RegisterPayloadHandlersEvent> {
            AllPackets.register(it)
        }
        
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient) {
            MOD_BUS.register(CCRClientEvents::class.java)
            NeoForge.EVENT_BUS.register(DeconstructionClientEvents::class.java)
            NeoForge.EVENT_BUS.register(TaskProgressClientEvents::class.java)
            NeoForge.EVENT_BUS.register(BeehiveRangeHandler::class.java)
            MOD_BUS.addListener<FMLClientSetupEvent> { onClientSetup(it) }
            MOD_BUS.addListener<RegisterKeyMappingsEvent> { AllKeys.register(it) }
        }
        
        MOD_BUS.addListener<GatherDataEvent> { CCRDatagen.gatherData(it) }
        MOD_BUS.addListener<FMLCommonSetupEvent> { onCommonSetup(it) }
        
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
        
        // Register Ponder plugin
        PonderIndex.addPlugin(CCRPonderPlugin())
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Common setup...")
    }
}
