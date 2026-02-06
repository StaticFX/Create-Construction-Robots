package de.devin.ccr.content.schematics

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.backpack.PortableBeehiveItem
import de.devin.ccr.content.robots.MechanicalBeeEntity
import de.devin.ccr.items.AllItems
import de.devin.ccr.content.robots.IBeeHome
import de.devin.ccr.content.robots.PlayerBeeHome
import de.devin.ccr.registry.AllEntityTypes
import java.util.UUID
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * Manages bee construction and deconstruction processes.
 *
 * Key responsibilities:
 * - Coordinates the start of construction and deconstruction jobs.
 * - Manages bee deployment (spawning) and recovery (returning to beehive).
 */
object BeeWorkManager {

    fun stopAllTasks(player: ServerPlayer) {
        val tm = MechanicalBeeEntity.playerTaskManagers[player.uuid]
        tm?.cancelAll()
        SchematicJobManager.markAllComplete(player.uuid)

        player.displayClientMessage(
            Component.translatable("ccr.construction.stopped"),
            true
        )
    }

    fun startConstruction(player: ServerPlayer, schematicStack: ItemStack) {
        if (SchematicJobManager.isJobActive(player.uuid, schematicStack)) {
            player.displayClientMessage(Component.translatable("ccr.construction.already_active"), true)
            return
        }

        val home = PlayerBeeHome(player)
        val backpackStack = findBeehive(player) ?: run {
            player.displayClientMessage(Component.translatable("ccr.construction.no_beehive"), true)
            return
        }

        val backpackItem = backpackStack.item as PortableBeehiveItem
        val robotCount = backpackItem.getTotalRobotCount(backpackStack)
        if (robotCount <= 0) {
            player.displayClientMessage(Component.translatable("ccr.construction.no_bees"), true)
            return
        }

        val handler = SchematicRobotHandler(player.level())
        if (handler.loadSchematic(schematicStack)) {
            val jobId = UUID.randomUUID()
            val tasks = handler.generateBuildTasks(jobId)
            if (tasks.isNotEmpty()) {
                val tm = home.taskManager
                tm.addTasks(tasks)
                tm.sortTasks(BottomUpSorter())

                SchematicJobManager.createJobKey(player.uuid, schematicStack)?.let {
                    SchematicJobManager.registerJob(it)
                }

                spawnBees(home)
                schematicStack.shrink(1)

                player.displayClientMessage(Component.translatable("ccr.construction.started", tasks.size), true)
                CreateCCR.LOGGER.info("Construction started for player ${player.name.string}")
            } else {
                player.displayClientMessage(Component.translatable("ccr.construction.no_tasks"), true)
            }
        } else {
            player.displayClientMessage(Component.translatable("ccr.construction.load_failed"), true)
        }
    }

    fun startDeconstruction(player: ServerPlayer, pos1: net.minecraft.core.BlockPos, pos2: net.minecraft.core.BlockPos) {
        val jobKey = SchematicJobManager.SchematicJobKey(player.uuid, "deconstruct_area", pos1.x, pos1.y, pos1.z)
        if (SchematicJobManager.isJobActive(jobKey)) {
            player.displayClientMessage(Component.translatable("ccr.deconstruction.already_active"), true)
            return
        }

        val home = PlayerBeeHome(player)
        val backpackStack = findBeehive(player) ?: run {
            player.displayClientMessage(Component.translatable("ccr.construction.no_beehive"), true)
            return
        }

        val backpackItem = backpackStack.item as PortableBeehiveItem
        if (backpackItem.getTotalRobotCount(backpackStack) <= 0) {
            player.displayClientMessage(Component.translatable("ccr.construction.no_bees"), true)
            return
        }

        val jobId = UUID.randomUUID()
        val tasks = SchematicRobotHandler(player.level()).generateRemovalTasks(pos1, pos2, jobId)
        if (tasks.isNotEmpty()) {
            val tm = home.taskManager
            tm.addTasks(tasks)
            tm.sortTasks(TopDownSorter())
            SchematicJobManager.registerJob(jobKey)
            spawnBees(home)

            player.displayClientMessage(Component.translatable("ccr.deconstruction.started", tasks.size), true)
        } else {
            player.displayClientMessage(Component.translatable("ccr.deconstruction.no_blocks"), true)
        }
    }

    fun returnBeeToBeehive(player: Player): Boolean {
        val home = PlayerBeeHome(player as? ServerPlayer ?: return false)
        return home.addBee()
    }

    private fun dropBeeItem(player: Player) {
        val itemEntity = ItemEntity(player.level(), player.x, player.y + 0.5, player.z, ItemStack(AllItems.MECHANICAL_BEE.get(), 1))
        player.level().addFreshEntity(itemEntity)
    }

    private fun findBeehive(player: Player): ItemStack? {
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (stack.item is PortableBeehiveItem) return stack
        }
        // TODO: Check Curios
        return null
    }

    fun spawnBees(home: IBeeHome) {
        val context = home.getBeeContext()
        val maxRobots = context.maxActiveRobots
        
        // Count currently active bees for this home
        val activeCount = MechanicalBeeEntity.activeHomes.values.count { it.getHomeId() == home.getHomeId() }
        val toSpawn = maxRobots - activeCount

        for (i in 0 until toSpawn) {
            if (!home.consumeBee()) break
            AllEntityTypes.MECHANICAL_BEE.create(home.world)?.apply {
                setPos(home.position.x + 0.5, home.position.y + 1.5, home.position.z + 0.5)
                setHome(home)
                home.world.addFreshEntity(this)
            }
        }
    }
}
