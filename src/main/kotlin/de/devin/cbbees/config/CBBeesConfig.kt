package de.devin.cbbees.config

import net.neoforged.neoforge.common.ModConfigSpec

object CBBeesConfig {
    val SPEC: ModConfigSpec

    val maxBeesPerHive: ModConfigSpec.IntValue
    val beePickupItems: ModConfigSpec.BooleanValue

    // Beehive RPM scaling
    val hiveBaseRange: ModConfigSpec.IntValue
    val hiveRangePerRpm: ModConfigSpec.DoubleValue
    val hiveRpmSpeedDivisor: ModConfigSpec.DoubleValue
    val hiveRpmRobotDivisor: ModConfigSpec.DoubleValue

    // Bee defaults
    val defaultMaxActiveRobots: ModConfigSpec.IntValue
    val defaultWorkRange: ModConfigSpec.DoubleValue

    // Upgrade values
    val rapidWingsSpeedBonus: ModConfigSpec.DoubleValue
    val swarmIntelligenceBeeBonus: ModConfigSpec.IntValue
    val honeyEfficiencyBreakSpeedReduction: ModConfigSpec.DoubleValue
    val honeyEfficiencyCarryBonus: ModConfigSpec.IntValue
    val honeyEfficiencyFuelReduction: ModConfigSpec.DoubleValue

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

        hiveBaseRange = builder
            .comment("Base work range of a beehive before RPM scaling (at minimum RPM)")
            .defineInRange("hiveBaseRange", 1, 0, 128)

        hiveRangePerRpm = builder
            .comment("Work range added per RPM: range = baseRange + RPM * this (16 RPM → 5, 256 RPM → 65)")
            .defineInRange("hiveRangePerRpm", 0.25, 0.01, 10.0)

        hiveRpmSpeedDivisor = builder
            .comment("RPM divisor for speed/spring efficiency scaling: multiplier = 1 + RPM / this value")
            .defineInRange("hiveRpmSpeedDivisor", 256.0, 1.0, 1024.0)

        hiveRpmRobotDivisor = builder
            .comment("RPM divisor for extra active robots: extra bees = RPM / this value")
            .defineInRange("hiveRpmRobotDivisor", 8.0, 1.0, 256.0)

        builder.pop()

        builder.comment("Bee Behavior Settings")
            .push("behavior")

        beePickupItems = builder
            .comment("Whether bees pick up item drops when breaking blocks")
            .define("beePickupItems", true)

        defaultMaxActiveRobots = builder
            .comment("Default max active robots before RPM/upgrade scaling")
            .defineInRange("defaultMaxActiveRobots", 4, 1, 64)

        defaultWorkRange = builder
            .comment("Default work range for BeeContext before hive overrides")
            .defineInRange("defaultWorkRange", 32.0, 1.0, 256.0)

        builder.pop()

        builder.comment("Upgrade Effect Settings — controls how strong each upgrade is")
            .push("upgrades")

        rapidWingsSpeedBonus = builder
            .comment("Speed multiplier bonus per Rapid Wings upgrade (default 0.25 = +25% per upgrade)")
            .defineInRange("rapidWingsSpeedBonus", 0.25, 0.01, 2.0)

        swarmIntelligenceBeeBonus = builder
            .comment("Extra concurrent bees per Swarm Intelligence upgrade")
            .defineInRange("swarmIntelligenceBeeBonus", 8, 1, 16)

        honeyEfficiencyBreakSpeedReduction = builder
            .comment("Break speed multiplier reduction per Honey Efficiency upgrade (0.25 = 25% faster)")
            .defineInRange("honeyEfficiencyBreakSpeedReduction", 0.25, 0.01, 1.0)

        honeyEfficiencyCarryBonus = builder
            .comment("Extra carry capacity per Honey Efficiency upgrade")
            .defineInRange("honeyEfficiencyCarryBonus", 2, 1, 16)

        honeyEfficiencyFuelReduction = builder
            .comment("Fuel consumption reduction per Honey Efficiency upgrade (0.15 = 15% less fuel)")
            .defineInRange("honeyEfficiencyFuelReduction", 0.15, 0.01, 1.0)

        builder.pop()

        builder.comment("Clockwork Spring Settings — controls per-action energy drain on bees")
            .push("spring")

        springDrainPlace = builder
            .comment("Spring tension drained per block placement (~50 placements per full spring)")
            .defineInRange("springDrainPlace", 0.02, 0.0, 1.0)

        springDrainBreak = builder
            .comment("Spring tension drained per block break (~66 breaks per full spring)")
            .defineInRange("springDrainBreak", 0.015, 0.0, 1.0)

        springDrainFlight = builder
            .comment("Spring tension drained per tick of flight (~10000 ticks per full spring)")
            .defineInRange("springDrainFlight", 0.0001, 0.0, 1.0)

        springDrainPickup = builder
            .comment("Spring tension drained per item pickup (BumbleBee)")
            .defineInRange("springDrainPickup", 0.01, 0.0, 1.0)

        springDrainDeposit = builder
            .comment("Spring tension drained per item deposit (BumbleBee)")
            .defineInRange("springDrainDeposit", 0.01, 0.0, 1.0)

        springRechargeTicks = builder
            .comment("Base ticks to recharge a fully depleted spring at the hive (scales with RPM)")
            .defineInRange("springRechargeTicks", 200, 20, 2000)

        builder.pop()

        builder.comment("Honey Fuel Settings — controls fuel consumption for portable beehive")
            .push("honey_fuel")

        portableHoneyPerRewind = builder
            .comment("Honey consumed per full spring rewind in a portable beehive")
            .defineInRange("portableHoneyPerRewind", 6, 1, 1000)

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
