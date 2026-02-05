package de.devin.ccr.content.schematics

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.backpack.ConstructorBackpackItem
import de.devin.ccr.content.robots.ConstructorRobotEntity
import de.devin.ccr.items.AllItems
import de.devin.ccr.registry.AllEntityTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * Manages robot construction and deconstruction processes.
 *
 * Key responsibilities:
 * - Coordinates the start of construction and deconstruction jobs.
 * - Manages robot deployment (spawning) and recovery (returning to backpack).
 */
object RobotConstructionManager {

    fun stopAllTasks(player: ServerPlayer) {
        val tm = ConstructorRobotEntity.playerTaskManagers[player.uuid]
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

        val backpackStack = findBackpack(player) ?: run {
            player.displayClientMessage(Component.translatable("ccr.construction.no_backpack"), true)
            return
        }

        val backpackItem = backpackStack.item as ConstructorBackpackItem
        val robotCount = backpackItem.getTotalRobotCount(backpackStack)
        if (robotCount <= 0) {
            player.displayClientMessage(Component.translatable("ccr.construction.no_robots"), true)
            return
        }

        val handler = SchematicRobotHandler(player.level())
        if (handler.loadSchematic(schematicStack)) {
            val tasks = handler.generateBuildTasks()
            if (tasks.isNotEmpty()) {
                val tm = ConstructorRobotEntity.playerTaskManagers.getOrPut(player.uuid) { RobotTaskManager() }
                tm.addTasks(tasks)
                tm.sortTasks(BottomUpSorter())

                SchematicJobManager.createJobKey(player.uuid, schematicStack)?.let {
                    SchematicJobManager.registerJob(it)
                }

                spawnRobots(player, backpackStack)
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

        val backpackStack = findBackpack(player) ?: run {
            player.displayClientMessage(Component.translatable("ccr.construction.no_backpack"), true)
            return
        }

        val backpackItem = backpackStack.item as ConstructorBackpackItem
        if (backpackItem.getTotalRobotCount(backpackStack) <= 0) {
            player.displayClientMessage(Component.translatable("ccr.construction.no_robots"), true)
            return
        }

        val tasks = SchematicRobotHandler(player.level()).generateRemovalTasks(pos1, pos2)
        if (tasks.isNotEmpty()) {
            val tm = ConstructorRobotEntity.playerTaskManagers.getOrPut(player.uuid) { RobotTaskManager() }
            tm.addTasks(tasks)
            tm.sortTasks(TopDownSorter())
            SchematicJobManager.registerJob(jobKey)
            spawnRobots(player, backpackStack)

            player.displayClientMessage(Component.translatable("ccr.deconstruction.started", tasks.size), true)
        } else {
            player.displayClientMessage(Component.translatable("ccr.deconstruction.no_blocks"), true)
        }
    }

    fun returnRobotToBackpack(player: Player): Boolean {
        val backpackStack = findBackpack(player)
        if (backpackStack != null && (backpackStack.item as ConstructorBackpackItem).addRobot(backpackStack)) {
            return true
        }
        dropRobotItem(player)
        return false
    }

    private fun dropRobotItem(player: Player) {
        val itemEntity = ItemEntity(player.level(), player.x, player.y + 0.5, player.z, ItemStack(AllItems.CONSTRUCTOR_ROBOT.get(), 1))
        player.level().addFreshEntity(itemEntity)
    }

    private fun findBackpack(player: Player): ItemStack? {
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (stack.item is ConstructorBackpackItem) return stack
        }
        // TODO: Check Curios
        return null
    }

    private fun spawnRobots(player: ServerPlayer, backpackStack: ItemStack) {
        val backpackItem = backpackStack.item as ConstructorBackpackItem
        val parallelUpgrades = backpackItem.getUpgradeCount(backpackStack, de.devin.ccr.content.upgrades.UpgradeType.PARALLEL_PROCESSOR)
        val maxRobots = 4 + (parallelUpgrades * 2)
        val toSpawn = minOf(backpackItem.getTotalRobotCount(backpackStack), maxRobots)

        for (i in 0 until toSpawn) {
            if (!backpackItem.consumeRobot(backpackStack)) break
            AllEntityTypes.CONSTRUCTOR_ROBOT.create(player.level())?.apply {
                setPos(player.x, player.y + 1.5, player.z)
                setOwner(player.uuid)
                player.level().addFreshEntity(this)
            }
        }
    }
}
