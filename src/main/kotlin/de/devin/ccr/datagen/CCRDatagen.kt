package de.devin.ccr.datagen

import de.devin.ccr.CreateCCR
import net.neoforged.neoforge.data.event.GatherDataEvent

object CCRDatagen {

    fun gatherData(event: GatherDataEvent) {
        // Check if this event is for our mod (following Create's pattern)
        if (event.modContainer.modId != CreateCCR.ID)
            return

        val generator = event.generator
        val output = generator.packOutput
        val lookupProvider = event.lookupProvider

        CreateCCR.LOGGER.info("Gathering data...")

        // Create and register the generated entries provider
        val generatedEntriesProvider = GeneratedEntriesProvider(output, lookupProvider)
        generator.addProvider(event.includeServer(), generatedEntriesProvider)
    }
}