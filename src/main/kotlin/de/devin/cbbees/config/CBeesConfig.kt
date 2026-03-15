package de.devin.cbbees.config

import net.neoforged.neoforge.common.ModConfigSpec

object CBeesConfig {
    val SPEC: ModConfigSpec

    val maxBeesPerHive: ModConfigSpec.IntValue
    val beePickupItems: ModConfigSpec.BooleanValue

    // Spring (clockwork) settings
    val springDrainPlace: ModConfigSpec.DoubleValue
    val springDrainBreak: ModConfigSpec.DoubleValue
    val springDrainFlight: ModConfigSpec.DoubleValue
    val springDrainPickup: ModConfigSpec.DoubleValue
    val springDrainDeposit: ModConfigSpec.DoubleValue
    val portableAirPerRewind: ModConfigSpec.IntValue
    val springRechargeTicks: ModConfigSpec.IntValue

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

        builder.comment("Clockwork Spring Settings — controls per-action energy drain on bees")
            .push("spring")

        springDrainPlace = builder
            .comment("Spring tension drained per block placement (~12 placements per full spring)")
            .defineInRange("springDrainPlace", 0.08, 0.0, 1.0)

        springDrainBreak = builder
            .comment("Spring tension drained per block break (~16 breaks per full spring)")
            .defineInRange("springDrainBreak", 0.06, 0.0, 1.0)

        springDrainFlight = builder
            .comment("Spring tension drained per tick of flight (~2000 ticks per full spring)")
            .defineInRange("springDrainFlight", 0.0005, 0.0, 1.0)

        springDrainPickup = builder
            .comment("Spring tension drained per item pickup (BumbleBee)")
            .defineInRange("springDrainPickup", 0.04, 0.0, 1.0)

        springDrainDeposit = builder
            .comment("Spring tension drained per item deposit (BumbleBee)")
            .defineInRange("springDrainDeposit", 0.04, 0.0, 1.0)

        portableAirPerRewind = builder
            .comment("Base air consumed from Backtank when rewinding spring in a portable beehive")
            .defineInRange("portableAirPerRewind", 50, 0, 1000)

        springRechargeTicks = builder
            .comment("Base ticks to recharge a fully depleted spring at the hive (scales with RPM)")
            .defineInRange("springRechargeTicks", 200, 20, 2000)

        builder.pop()

        SPEC = builder.build()
    }
}
