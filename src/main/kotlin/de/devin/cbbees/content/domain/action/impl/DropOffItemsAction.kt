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
 *
 * When no port is available, the bee flies to the owner player and drops items
 * at their feet (the player acts as a fallback logistics port).
 */
class DropOffItemsAction(initialPos: BlockPos) : BeeAction {

    private var _pos: BlockPos = initialPos
    override val pos: BlockPos get() {
        if (dropAtPlayer) {
            // Dynamically track the player (above head to avoid blocking vision)
            bee?.getOwnerPlayer()?.let { _pos = it.blockPosition().above(2) }
        }
        return _pos
    }

    /** When true, the bee should drop items at the owner player on arrival. */
    private var dropAtPlayer = false
    private var bee: MechanicalBeeEntity? = null

    override fun onActivate(bee: MechanicalBeeEntity) {
        this.bee = bee
        val network = bee.network()
        BeeDebug.log(bee, "DropOffAction.onActivate: network=${network?.id}, inventory=${bee.getInventoryContents().size} stacks")
        val port = network?.findDropOff(ItemStack.EMPTY)
        if (port != null) {
            _pos = port.pos
            BeeDebug.log(bee, "DropOffAction.onActivate: found port at $_pos")
        } else {
            // No port — fly to the owner player instead
            val owner = bee.getOwnerPlayer()
            BeeDebug.log(bee, "DropOffAction.onActivate: no port, ownerPlayer=${owner?.name?.string}, ownerUUID=${bee.getOwnerUUID()}")
            if (owner != null) {
                _pos = owner.blockPosition().above(2)
                dropAtPlayer = true
                BeeDebug.log(bee, "DropOffAction.onActivate: dropAtPlayer=true, target=$_pos")
            } else {
                // No port or owner — drop items on ground immediately
                BeeDebug.log(bee, "DropOffAction.onActivate: no port or owner, dropping items on ground")
                bee.dropInventory()
                _pos = bee.blockPosition()
            }
        }
    }

    override fun execute(level: Level, bee: MechanicalBeeEntity, context: BeeContext): Boolean {
        val contents = bee.getInventoryContents()
        BeeDebug.log(bee, "DropOffAction.execute: contents=${contents.size} stacks, dropAtPlayer=$dropAtPlayer")
        if (contents.isEmpty()) {
            BeeDebug.log(bee, "DropOffAction.execute: inventory empty, returning true")
            return true
        }

        if (dropAtPlayer) {
            val owner = bee.getOwnerPlayer()
            BeeDebug.log(bee, "DropOffAction.execute: dropAtPlayer=true, owner=${owner?.name?.string}")
            if (owner != null) {
                for (item in contents) {
                    val copy = item.copy()
                    val added = owner.inventory.add(copy)
                    bee.removeFromInventory(item, item.count)
                    BeeDebug.log(bee, "DropOffAction.execute: ${item.count}x ${item.item} added=$added")
                    if (!added && !copy.isEmpty) {
                        val drop = ItemEntity(level, owner.x, owner.y, owner.z, copy)
                        level.addFreshEntity(drop)
                    }
                }
            } else {
                // Owner logged off — drop at bee's position
                BeeDebug.log(bee, "DropOffAction.execute: owner null, dropping on ground")
                for (item in contents) {
                    bee.removeFromInventory(item, item.count)
                    val drop = ItemEntity(level, bee.x, bee.y, bee.z, item.copy())
                    level.addFreshEntity(drop)
                }
            }
            return true
        }

        val port = bee.network()?.findDropOff(contents.first())

        if (port == null) {
            // Port disappeared mid-flight — drop at owner or on ground
            val owner = bee.getOwnerPlayer()
            if (owner != null) {
                BeeDebug.log(bee, "DropOff: port gone, giving ${contents.size} stack(s) to player ${owner.name.string}")
                for (item in contents) {
                    val copy = item.copy()
                    bee.removeFromInventory(item, item.count)
                    if (!owner.inventory.add(copy) && !copy.isEmpty) {
                        val drop = ItemEntity(level, owner.x, owner.y, owner.z, copy)
                        level.addFreshEntity(drop)
                    }
                }
            } else {
                BeeDebug.log(bee, "DropOff: no port or owner, dropping ${contents.size} stack(s) on ground")
                for (item in contents) {
                    bee.removeFromInventory(item, item.count)
                    val itemEntity = ItemEntity(level, bee.x, bee.y, bee.z, item.copy())
                    level.addFreshEntity(itemEntity)
                }
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
