package de.devin.ccr.ponder

import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks

object BeeScenes {
    fun intro(scene: SceneBuilder, util: SceneBuildingUtil) {
        scene.title("mechanical_bee", "Introduction to Mechanical Bees")
        scene.configureBasePlate(0, 0, 5)
        scene.world().showSection(util.select().everywhere(), Direction.UP)
        scene.idle(10)
        
        scene.overlay().showText(100)
            .text("Mechanical Bees are small automatons that can build and dismantle structures.")
            .pointAt(util.vector().centerOf(2, 1, 2))
            .placeNearTarget()
        scene.idle(20)
    }

    fun portableHive(scene: SceneBuilder, util: SceneBuildingUtil) {
        scene.title("portable_beehive", "The Portable Beehive")
        scene.configureBasePlate(0, 0, 5)
        scene.world().replaceBlocks(util.select().everywhere(), Blocks.AIR.defaultBlockState(), false)
        scene.world().setBlocks(util.select().fromTo(0, 0, 0, 4, 0, 4), Blocks.GRASS_BLOCK.defaultBlockState(), false)
        scene.world().showSection(util.select().everywhere(), Direction.UP)
        scene.idle(10)
        
        scene.overlay().showText(100)
            .text("Wear the Portable Beehive to deploy bees from your inventory.")
            .pointAt(util.vector().centerOf(2, 1, 2))
            .placeNearTarget()
        scene.idle(20)
    }

    fun stationaryHive(scene: SceneBuilder, util: SceneBuildingUtil) {
        scene.title("mechanical_beehive", "The Mechanical Beehive")
        scene.configureBasePlate(0, 0, 5)
        scene.world().replaceBlocks(util.select().everywhere(), Blocks.AIR.defaultBlockState(), false)
        scene.world().setBlocks(util.select().fromTo(0, 0, 0, 4, 0, 4), Blocks.GRASS_BLOCK.defaultBlockState(), false)
        scene.world().showSection(util.select().everywhere(), Direction.UP)
        scene.idle(10)
        
        scene.overlay().showText(100)
            .text("The Stationary Beehive can be programmed with instructions for long-term automation.")
            .pointAt(util.vector().centerOf(2, 1, 2))
            .placeNearTarget()
        scene.idle(20)
    }
}
