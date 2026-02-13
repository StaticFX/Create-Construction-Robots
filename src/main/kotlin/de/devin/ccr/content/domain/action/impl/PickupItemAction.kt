package de.devin.ccr.content.domain.action.impl

import de.devin.ccr.content.bee.MechanicalBeeEntity
import de.devin.ccr.content.domain.action.BeeAction
import de.devin.ccr.content.logistics.ports.LogisticPortBlockEntity
import de.devin.ccr.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class PickupItemAction(override val pos: BlockPos, override val requiredItems: List<ItemStack>) : BeeAction {
    override fun execute(level: Level, robot: MechanicalBeeEntity, context: BeeContext): Boolean {
        val port = level.getBlockEntity(pos) as? LogisticPortBlockEntity ?: return false

        for (stack in requiredItems) {
            if (port.hasItemStack(stack)) {
                if (port.removeItemStack(stack)) {
                    robot.carriedItems.add(stack.copy())
                }
            }
        }
        return true
    }

    override fun getDescription(): String = "Picking up items at $pos"

    override fun shouldReturnAfter(context: BeeContext): Boolean = false
}
