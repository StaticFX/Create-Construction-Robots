package de.devin.cbbees.content.domain.action.impl

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Action that deposits the bee's inventory contents at a logistics drop-off port.
 * Appended as the last task in removal batches so bees return picked-up items.
 *
 * The target port is resolved dynamically via [onActivate] when this task becomes
 * current, since the best port may change at runtime.
 */
class DropOffItemsAction(initialPos: BlockPos) : BeeAction {

    private var _pos: BlockPos = initialPos
    override val pos: BlockPos get() = _pos

    /** When true, items were already dropped in onActivate — execute() is a no-op. */
    private var alreadyDropped = false

    override fun onActivate(bee: MechanicalBeeEntity) {
        val port = bee.network()?.findDropOff(ItemStack.EMPTY)
        if (port != null) {
            _pos = port.pos
        } else {
            // No port available — drop items immediately and skip flying anywhere
            BeeDebug.log(bee, "DropOff: no port available, dropping items on ground")
            bee.dropInventory()
            _pos = bee.blockPosition()
            alreadyDropped = true
        }
    }

    override fun execute(level: Level, bee: MechanicalBeeEntity, context: BeeContext): Boolean {
        if (alreadyDropped) return true

        val contents = bee.getInventoryContents()
        if (contents.isEmpty()) return true

        val port = bee.network()?.findDropOff(contents.first())

        if (port == null) {
            BeeDebug.log(bee, "DropOff: no port, dropping ${contents.size} stack(s) on ground")
            for (item in contents) {
                bee.removeFromInventory(item, item.count)
                val itemEntity = ItemEntity(level, bee.x, bee.y, bee.z, item.copy())
                level.addFreshEntity(itemEntity)
            }
            return true
        }

        BeeDebug.log(bee, "DropOff: depositing ${contents.size} stack(s) at port ${port.pos}")
        for (item in contents) {
            val remainder = port.addItemStack(item.copy())
            if (!remainder.isEmpty) {
                val itemEntity = ItemEntity(
                    level,
                    port.pos.x + 0.5,
                    port.pos.y + 0.5,
                    port.pos.z + 0.5,
                    remainder
                )
                level.addFreshEntity(itemEntity)
            }
            bee.removeFromInventory(item, item.count)
        }
        return true
    }

    override fun shouldReturnAfter(context: BeeContext): Boolean = false

    override fun getDescription(): String = "Dropping off items at (${pos.x}, ${pos.y}, ${pos.z})"
}
