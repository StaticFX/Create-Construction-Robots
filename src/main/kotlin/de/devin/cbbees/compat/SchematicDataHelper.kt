package de.devin.cbbees.compat

import com.simibubi.create.AllDataComponents
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation

/**
 * Centralizes schematic data access on ItemStacks.
 * On 1.21.1+: uses Create's DataComponents.
 * On 1.20.1:  uses NBT tags (via preprocessor).
 */
object SchematicDataHelper {

    @JvmStatic
    fun getFile(stack: ItemStack): String? = stack.get(AllDataComponents.SCHEMATIC_FILE)

    @JvmStatic
    fun hasFile(stack: ItemStack): Boolean = stack.has(AllDataComponents.SCHEMATIC_FILE)

    @JvmStatic
    fun getOwner(stack: ItemStack): String? = stack.get(AllDataComponents.SCHEMATIC_OWNER)

    @JvmStatic
    fun isDeployed(stack: ItemStack): Boolean =
        stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false)

    @JvmStatic
    fun getAnchor(stack: ItemStack): BlockPos =
        stack.getOrDefault(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO)

    @JvmStatic
    fun getRotation(stack: ItemStack): Rotation =
        stack.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE)

    @JvmStatic
    fun getMirror(stack: ItemStack): Mirror =
        stack.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE)

    @JvmStatic
    fun setPlacement(
        stack: ItemStack,
        file: String,
        owner: String,
        anchor: BlockPos,
        rotation: Rotation,
        mirror: Mirror
    ) {
        stack.set(AllDataComponents.SCHEMATIC_FILE, file)
        stack.set(AllDataComponents.SCHEMATIC_OWNER, owner)
        stack.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)
        stack.set(AllDataComponents.SCHEMATIC_ANCHOR, anchor)
        stack.set(AllDataComponents.SCHEMATIC_ROTATION, rotation)
        stack.set(AllDataComponents.SCHEMATIC_MIRROR, mirror)
    }

    @JvmStatic
    fun setAnchor(stack: ItemStack, anchor: BlockPos) {
        stack.set(AllDataComponents.SCHEMATIC_ANCHOR, anchor)
    }

    @JvmStatic
    fun setRotation(stack: ItemStack, rotation: Rotation) {
        stack.set(AllDataComponents.SCHEMATIC_ROTATION, rotation)
    }

    @JvmStatic
    fun setMirror(stack: ItemStack, mirror: Mirror) {
        stack.set(AllDataComponents.SCHEMATIC_MIRROR, mirror)
    }

    @JvmStatic
    fun setDeployed(stack: ItemStack, deployed: Boolean) {
        stack.set(AllDataComponents.SCHEMATIC_DEPLOYED, deployed)
    }

    @JvmStatic
    fun clearAll(stack: ItemStack) {
        stack.remove(AllDataComponents.SCHEMATIC_FILE)
        stack.remove(AllDataComponents.SCHEMATIC_OWNER)
        stack.remove(AllDataComponents.SCHEMATIC_DEPLOYED)
        stack.remove(AllDataComponents.SCHEMATIC_ANCHOR)
        stack.remove(AllDataComponents.SCHEMATIC_ROTATION)
        stack.remove(AllDataComponents.SCHEMATIC_MIRROR)
        stack.remove(AllDataComponents.SCHEMATIC_BOUNDS)
    }
}
