package de.devin.cbbees.config

import net.neoforged.neoforge.common.ModConfigSpec

object CBeesConfig {
    val SPEC: ModConfigSpec

    val maxBeesPerHive: ModConfigSpec.IntValue
    val beePickupItems: ModConfigSpec.BooleanValue

    init {
        val builder = ModConfigSpec.Builder()

        builder.comment("Mechanical Beehive Settings")
            .push("beehive")

        maxBeesPerHive = builder
            .comment("Maximum number of active bees per hive")
            .defineInRange("maxBeesPerHive", 16, 1, 64)

        builder.pop()

        builder.comment("Bee Behavior Settings")
            .push("behavior")

        beePickupItems = builder
            .comment("Whether bees pick up item drops when breaking blocks")
            .define("beePickupItems", true)

        builder.pop()

        SPEC = builder.build()
    }
}
