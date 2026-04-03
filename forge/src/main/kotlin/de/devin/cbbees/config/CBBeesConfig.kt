package de.devin.cbbees.config

import net.minecraftforge.common.ForgeConfigSpec

object CBBeesConfig {
    val SPEC: ForgeConfigSpec

    // Features
    val enableExternalBrowser: ForgeConfigSpec.BooleanValue

    val maxBeesPerHive: ForgeConfigSpec.IntValue
    val minActiveRobotsAtRpm: ForgeConfigSpec.IntValue
    val beePickupItems: ForgeConfigSpec.BooleanValue

    // Beehive RPM scaling
    val hiveBaseRange: ForgeConfigSpec.IntValue
    val hiveRangePerRpm: ForgeConfigSpec.DoubleValue
    val hiveRpmSpeedDivisor: ForgeConfigSpec.DoubleValue
    val hiveRpmRobotDivisor: ForgeConfigSpec.DoubleValue

    // Bee defaults
    val defaultMaxActiveRobots: ForgeConfigSpec.IntValue
    val defaultWorkRange: ForgeConfigSpec.DoubleValue

    // Upgrade values
    val rapidWingsSpeedBonus: ForgeConfigSpec.DoubleValue
    val swarmIntelligenceBeeBonus: ForgeConfigSpec.IntValue
    val honeyEfficiencyBreakSpeedReduction: ForgeConfigSpec.DoubleValue
    val honeyEfficiencyCarryBonus: ForgeConfigSpec.IntValue
    val honeyEfficiencyFuelReduction: ForgeConfigSpec.DoubleValue

    // Spring (clockwork) settings
    val springDrainPlace: ForgeConfigSpec.DoubleValue
    val springDrainBreak: ForgeConfigSpec.DoubleValue
    val springDrainFlight: ForgeConfigSpec.DoubleValue
    val springDrainPickup: ForgeConfigSpec.DoubleValue
    val springDrainDeposit: ForgeConfigSpec.DoubleValue
    val springRechargeTicks: ForgeConfigSpec.IntValue

    // Honey fuel settings
    val portableHoneyPerRewind: ForgeConfigSpec.IntValue
    val portableMaxHoney: ForgeConfigSpec.IntValue
    val honeyBottleFuelValue: ForgeConfigSpec.IntValue
    val honeycombFuelValue: ForgeConfigSpec.IntValue
    val honeyBlockFuelValue: ForgeConfigSpec.IntValue

    init {
        val builder = ForgeConfigSpec.Builder()

        builder.comment("Feature Toggles")
            .push("features")

        enableExternalBrowser = builder
            .comment("Enable the 'Browse Online' button and createmod.com connectivity. Requires restart.")
            .define("enableExternalBrowser", true)

        builder.pop()

        builder.comment("Mechanical Beehive Settings")
            .push("beehive")

        maxBeesPerHive = builder
            .comment("Maximum number of active bees per hive")
            .defineInRange("maxBeesPerHive", 16, 1, 64)

        minActiveRobotsAtRpm = builder
            .comment("Minimum active bees when the hive has any RPM, so even slow shafts deploy at least this many bees")
            .defineInRange("minActiveRobotsAtRpm", 1, 0, 64)

        hiveBaseRange = builder
            .comment("Base work range of a beehive before RPM scaling (at minimum RPM)")
            .defineInRange("hiveBaseRange", 1, 0, 128)

        hiveRangePerRpm = builder
            .comment("Work range added per RPM: range = baseRange + RPM * this (16 RPM -> 5, 256 RPM -> 65)")
            .defineInRange("hiveRangePerRpm", 0.25, 0.01, 10.0)

        hiveRpmSpeedDivisor = builder
            .comment("RPM divisor for bee speed and spring efficiency: multiplier = 1 + RPM / this value")
            .defineInRange("hiveRpmSpeedDivisor", 256.0, 1.0, 1024.0)

        hiveRpmRobotDivisor = builder
            .comment("RPM divisor for workforce scaling: extra bees = RPM / this value")
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
