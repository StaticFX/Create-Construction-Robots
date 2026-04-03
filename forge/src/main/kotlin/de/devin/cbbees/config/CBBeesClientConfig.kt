package de.devin.cbbees.config

import net.minecraftforge.common.ForgeConfigSpec

object CBBeesClientConfig {
    val SPEC: ForgeConfigSpec

    val showConstructionGhosts: ForgeConfigSpec.BooleanValue
    val showSchematicPreview: ForgeConfigSpec.BooleanValue
    val showBeehiveRange: ForgeConfigSpec.BooleanValue
    val showBeeTargetLines: ForgeConfigSpec.BooleanValue
    val ghostBlockOpacity: ForgeConfigSpec.DoubleValue
    val renderGhostBlockEntities: ForgeConfigSpec.BooleanValue
    val maxGhostBlockEntities: ForgeConfigSpec.IntValue

    init {
        val builder = ForgeConfigSpec.Builder()

        builder.comment("Rendering & Performance Toggles")
            .push("rendering")

        showConstructionGhosts = builder
            .comment("Show ghost blocks for active construction jobs")
            .define("showConstructionGhosts", true)

        showSchematicPreview = builder
            .comment("Show ghost block preview while browsing schematics")
            .define("showSchematicPreview", true)

        showBeehiveRange = builder
            .comment("Show beehive range overlay when looking at a hive")
            .define("showBeehiveRange", true)

        showBeeTargetLines = builder
            .comment("Show bee target lines when wearing goggles")
            .define("showBeeTargetLines", true)

        ghostBlockOpacity = builder
            .comment("Opacity for all ghost block rendering (0.05 = nearly invisible, 1.0 = fully opaque)")
            .defineInRange("ghostBlockOpacity", 0.5, 0.05, 1.0)

        renderGhostBlockEntities = builder
            .comment("Render block entities (chests, signs, gearboxes, etc.) in ghost previews. Disable for better performance on large schematics.")
            .define("renderGhostBlockEntities", false)

        maxGhostBlockEntities = builder
            .comment("Maximum number of block entities to render per ghost preview. Lower values improve performance.")
            .defineInRange("maxGhostBlockEntities", 64, 1, 1024)

        builder.pop()

        SPEC = builder.build()
    }
}
