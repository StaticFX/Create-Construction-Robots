package de.devin.ccr.content.robots

import de.devin.ccr.content.backpack.ConstructorBackpackItem
import de.devin.ccr.content.upgrades.RobotContext
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.capabilities.Capabilities

/**
 * Handles inventory operations for constructor robots, including item collection
 * from player inventory and wireless storages.
 */
class RobotInventoryManager(private val robot: ConstructorRobotEntity) {

    private var cachedWirelessStorages: MutableList<BlockPos> = mutableListOf()
    private var wirelessScanCooldown = 0
    private val WIRELESS_SCAN_INTERVAL = 100 // 5 seconds

    /**
     * Picks up items for the current task.
     */
    fun pickUpItems(required: List<ItemStack>, context: RobotContext, carriedItems: MutableList<ItemStack>): Boolean {
        val ownerPlayer = robot.getOwnerPlayer() ?: return false
        if (ownerPlayer.isCreative) return true
        
        val source = getMaterialSource(ownerPlayer, context)
        val carryCapacity = context.carryCapacity
        var itemsPickedUp = 0
        
        carriedItems.clear()
        
        for (req in required) {
            if (req.isEmpty) continue
            if (itemsPickedUp >= carryCapacity) break
            
            val toPickUp = minOf(req.count, carryCapacity - itemsPickedUp)
            val extracted = source.extractItems(req, toPickUp)
            if (!extracted.isEmpty) {
                carriedItems.add(extracted)
                itemsPickedUp += extracted.count
            }
        }
        
        val totalRequired = required.sumOf { it.count }
        return itemsPickedUp >= totalRequired
    }

    /**
     * Gets a material source that checks all available inventories.
     */
    private fun getMaterialSource(ownerPlayer: ServerPlayer, context: RobotContext): MaterialSource {
        val sources = mutableListOf<MaterialSource>()
        
        // 1. Player inventory
        sources.add(PlayerMaterialSource(ownerPlayer))
        
        // 2. Wireless Link (if enabled)
        if (context.wirelessLinkEnabled) {
            if (wirelessScanCooldown <= 0) {
                scanForWirelessStorages(ownerPlayer)
                wirelessScanCooldown = WIRELESS_SCAN_INTERVAL
            } else {
                wirelessScanCooldown--
            }
            sources.add(WirelessMaterialSource(robot.level(), cachedWirelessStorages))
        }
        
        return CompositeMaterialSource(sources)
    }

    /**
     * Scans for inventories around the player for Wireless Link.
     */
    private fun scanForWirelessStorages(player: ServerPlayer) {
        cachedWirelessStorages.clear()
        val range = 16
        val center = player.blockPosition()
        val level = robot.level()
        
        for (x in -range..range) {
            for (y in -range..range) {
                for (z in -range..range) {
                    val pos = center.offset(x, y, z)
                    val handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null)
                    if (handler != null) {
                        cachedWirelessStorages.add(pos)
                    }
                }
            }
        }
    }
}
