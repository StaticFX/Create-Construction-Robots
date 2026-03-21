package de.devin.cbbees.ponder

import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import com.tterrag.registrate.util.nullness.NonNullSupplier
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.logistics.ports.LogisticPortBlock
import de.devin.cbbees.content.logistics.ports.PortState
import de.devin.cbbees.content.logistics.ports.PortType
import de.devin.cbbees.registry.AllEntityTypes
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

object BeeScenes {

    fun mechanicalBeehive(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)

        scene.title("mechanical_beehive", "The Mechanical Beehive")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val shaftPos = util.grid().at(3, 1, 2)
        val hivePos = util.grid().at(2, 1, 2)

        // Show the cogwheel/shaft
        scene.world().showSection(util.select().position(shaftPos), Direction.DOWN)
        scene.idle(5)

        // Show the mechanical beehive
        scene.world().showSection(util.select().position(hivePos), Direction.DOWN)
        scene.idle(5)

        // Start rotation
        scene.world().setKineticSpeed(util.select().position(shaftPos), 64f)
        scene.world().setKineticSpeed(util.select().position(hivePos), 64f)
        scene.idle(10)

        scene.overlay().showText(60)
            .text("The Mechanical Beehive is the heart of your mechanical bees.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hivePos, Direction.WEST))

        scene.idle(60)

        scene.overlay().showText(60)
            .text("Beehives receive a unique Network ID when placed down: 0161")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hivePos, Direction.WEST))

        scene.idle(60)

        val hive2Pos = util.grid().at(0, 1, 2)

        scene.world().showSection(util.select().position(hive2Pos), Direction.DOWN)
        scene.world().setKineticSpeed(util.select().position(hive2Pos), 64f)

        scene.idle(10)

        scene.overlay().showOutlineWithText(util.select().position(hive2Pos), 60)
            .text("Beehives placed in the same network join the same network.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hive2Pos, Direction.DOWN))

        scene.idle(60)

        scene.world().hideSection(util.select().position(hive2Pos), Direction.UP)

        val funnelPos = util.grid().at(2, 2, 2)
        scene.world().showSection(util.select().position(funnelPos), Direction.DOWN)

        scene.idle(10)

        scene.overlay().showOutlineWithText(util.select().position(funnelPos), 60)
            .text("The funnel is used to insert bees into your network.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(funnelPos, Direction.DOWN))

        scene.idle(60)

        scene.markAsFinished()
    }

    fun introLogisticsPort(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("logistics_port", "The Logistics Port")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val vaultPos = util.grid().at(2, 1, 2)
        scene.world().showSection(util.select().position(vaultPos), Direction.DOWN)

        scene.idle(5)

        val portPos = util.grid().at(2, 2, 2)
        scene.world().showSection(util.select().position(portPos), Direction.DOWN)

        scene.idle(5)

        scene.overlay().showOutlineWithText(util.select().position(portPos), 90)
            .text("The Logistics Port can be placed on any kind of inventory. A green bulb means it can interact with the inventory.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(portPos, Direction.DOWN))

        scene.idle(90)

        scene.overlay().showOutlineWithText(util.select().position(portPos), 60)
            .text("You can switch the port type to INSERT, by interacting with a wrench, to make it accept items.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(portPos, Direction.DOWN))

        scene.idle(10)

        scene.world().modifyBlock(portPos, { state ->
            state.setValue(
                LogisticPortBlock.PORT_TYPE,
                PortType.INSERT
            ) as BlockState
        }, false)

        scene.idle(50)

        scene.markAsFinished()
    }

    private fun flyEntity(
        scene: CreateSceneBuilder,
        bee: net.createmod.ponder.api.element.ElementLink<net.createmod.ponder.api.element.EntityElement>,
        from: Vec3,
        to: Vec3,
        ticks: Int
    ) {
        val yaw = (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0).toFloat()
        scene.world().modifyEntity(bee) { entity ->
            entity.yRot = yaw
            entity.yRotO = yaw
            entity.yHeadRot = yaw
            entity.yHeadRot = yaw
        }
        for (i in 1..ticks) {
            val t = i.toDouble() / ticks
            scene.world().modifyEntity(bee) { entity ->
                val x = from.x + (to.x - from.x) * t
                val y = from.y + (to.y - from.y) * t
                val z = from.z + (to.z - from.z) * t
                entity.setPos(x, y, z)
            }
            scene.idle(1)
        }
    }

    fun logisticsPortWithBees(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("logistics_port_with_bees", "The Logistics Port with Bees")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val vaultPos = util.grid().at(1, 1, 3)
        val portPos = util.grid().at(1, 2, 3)
        val hivePos = util.grid().at(3, 1, 1)

        scene.world().showSection(util.select().position(vaultPos), Direction.DOWN)
        scene.world().showSection(util.select().position(portPos), Direction.DOWN)
        scene.world().showSection(util.select().position(hivePos), Direction.DOWN)

        scene.overlay().showOutlineWithText(util.select().position(portPos), 60)
            .text("When a Bee is spawned to work on a task, it will automatically find a Logistics Port to gather its items. Ports with a higher priority will be preferred.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hivePos, Direction.DOWN))

        scene.idle(60)

        val beeSpawn = util.vector().centerOf(hivePos).add(0.0, 1.0, 0.0)
        val bee = scene.world().createEntity { level ->
            val entity = AllEntityTypes.MECHANICAL_BEE.create(level)!!
            entity.setPos(beeSpawn.x, beeSpawn.y, beeSpawn.z)
            entity.setNoAi(true)
            entity.noPhysics = true
            entity
        }

        scene.overlay().showOutlineWithText(util.select().position(portPos), 60)
            .text("When a bee focuses on a port, it will start moving towards it, the port will signal this with a glowing bulb.")
            .pointAt(util.vector().blockSurface(portPos, Direction.DOWN))
            .placeNearTarget()
            .attachKeyFrame()

        scene.world().modifyBlock(portPos, { state ->
            state.setValue(
                LogisticPortBlock.PORT_STATE,
                PortState.BUSY
            ) as BlockState
        }, false)

        scene.idle(40)

        // Fly bee from hive to port
        val portTarget = util.vector().centerOf(portPos).add(0.0, 0.5, 0.0)
        flyEntity(scene, bee, beeSpawn, portTarget, 60)

        scene.idle(20)

        scene.overlay().showOutlineWithText(util.select().position(portPos), 120)
            .text("The bee will pickup the items from the port, and then fly to its destination.")

        scene.idle(60)

        // Fly bee from port to wood placement
        val woodPos = util.grid().at(3, 1, 3)
        val woodTarget = util.vector().centerOf(woodPos).add(0.0, 1.0, 0.0)
        flyEntity(scene, bee, portTarget, woodTarget, 50)

        scene.world().showSection(util.select().position(woodPos), Direction.DOWN)

        scene.idle(20)

        // Fly bee back to hive
        flyEntity(scene, bee, woodTarget, beeSpawn, 50)

        // Remove the bee when it arrives home
        scene.world().modifyEntity(bee) { it.discard() }

        scene.idle(20)

        scene.markAsFinished()
    }

}
