package de.devin.cbbees.content.domain.action

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import net.minecraft.world.item.ItemStack

/**
 * Interface for actions that require items to be consumed from the bee's inventory.
 * Implemented by actions like placing blocks, belts, or fertilizing crops.
 */
interface ItemConsumingAction {
    val requiredItems: List<ItemStack>

    fun hasItems(bee: MechanicalBeeEntity): Boolean {
        return requiredItems.all { req ->
            bee.countInInventory(req) >= req.count
        }
    }

    fun consumeItems(bee: MechanicalBeeEntity): Boolean {
        if (!hasItems(bee)) return false
        for (req in requiredItems) {
            bee.removeFromInventory(req, req.count)
        }
        return true
    }
}
