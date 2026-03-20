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
    val springRechargeTicks: ModConfigSpec.IntValue

    // Honey fuel settings
    val portableHoneyPerRewind: ModConfigSpec.IntValue
    val portableMaxHoney: ModConfigSpec.IntValue
    val honeyBottleFuelValue: ModConfigSpec.IntValue
    val honeycombFuelValue: ModConfigSpec.IntValue
    val honeyBlockFuelValue: ModConfigSpec.IntValue

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

        springRechargeTicks = builder
            .comment("Base ticks to recharge a fully depleted spring at the hive (scales with RPM)")
            .defineInRange("springRechargeTicks", 200, 20, 2000)

        builder.pop()

        builder.comment("Honey Fuel Settings — controls fuel consumption for portable beehive")
            .push("honey_fuel")

        portableHoneyPerRewind = builder
            .comment("Honey consumed per full spring rewind in a portable beehive")
            .defineInRange("portableHoneyPerRewind", 100, 1, 1000)

        portableMaxHoney = builder
            .comment("Maximum honey stored in a portable beehive")
            .defineInRange("portableMaxHoney", 400, 100, 10000)

        honeyBottleFuelValue = builder
            .comment("Honey fuel value per honey bottle")
            .defineInRange("honeyBottleFuelValue", 100, 1, 1000)

        honeycombFuelValue = builder
            .comment("Honey fuel value per honeycomb")
            .defineInRange("honeycombFuelValue", 60, 1, 1000)

        honeyBlockFuelValue = builder
            .comment("Honey fuel value per honey block")
            .defineInRange("honeyBlockFuelValue", 400, 1, 10000)

        builder.pop()

        SPEC = builder.build()
    }
}
