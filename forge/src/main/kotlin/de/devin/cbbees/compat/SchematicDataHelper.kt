package de.devin.cbbees.compat

import net.minecraft.core.BlockPos
import net.minecraft.nbt.NbtUtils
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation

/**
 * Forge 1.20.1 version — uses NBT tags on ItemStack.
 *
 * Create 1.20.1 stores schematic data as NBT tags on the item:
 * - "File" (String)
 * - "Owner" (String)
 * - "Deployed" (Boolean)
 * - "Anchor" (CompoundTag via NbtUtils)
 * - "Rotation" (String enum name)
 * - "Mirror" (String enum name)
 */
object SchematicDataHelper {

    @JvmStatic
    fun getFile(stack: ItemStack): String? =
        stack.tag?.getString("File")?.takeIf { it.isNotEmpty() }

    @JvmStatic
    fun hasFile(stack: ItemStack): Boolean =
        stack.tag?.contains("File") == true && stack.tag!!.getString("File").isNotEmpty()

    @JvmStatic
    fun getOwner(stack: ItemStack): String? =
        stack.tag?.getString("Owner")?.takeIf { it.isNotEmpty() }

    @JvmStatic
    fun isDeployed(stack: ItemStack): Boolean =
        stack.tag?.getBoolean("Deployed") ?: false

    @JvmStatic
    fun getAnchor(stack: ItemStack): BlockPos {
        val tag = stack.tag ?: return BlockPos.ZERO
        return if (tag.contains("Anchor")) NbtUtils.readBlockPos(tag.getCompound("Anchor")) else BlockPos.ZERO
    }

    @JvmStatic
    fun getRotation(stack: ItemStack): Rotation {
        val name = stack.tag?.getString("Rotation") ?: return Rotation.NONE
        return try { Rotation.valueOf(name) } catch (_: Exception) { Rotation.NONE }
    }

    @JvmStatic
    fun getMirror(stack: ItemStack): Mirror {
        val name = stack.tag?.getString("Mirror") ?: return Mirror.NONE
        return try { Mirror.valueOf(name) } catch (_: Exception) { Mirror.NONE }
    }

    @JvmStatic
    fun setPlacement(
        stack: ItemStack,
        file: String,
        owner: String,
        anchor: BlockPos,
        rotation: Rotation,
        mirror: Mirror
    ) {
        val tag = stack.orCreateTag
        tag.putString("File", file)
        tag.putString("Owner", owner)
        tag.putBoolean("Deployed", true)
        tag.put("Anchor", NbtUtils.writeBlockPos(anchor))
        tag.putString("Rotation", rotation.name)
        tag.putString("Mirror", mirror.name)
    }

    @JvmStatic
    fun setAnchor(stack: ItemStack, anchor: BlockPos) {
        stack.orCreateTag.put("Anchor", NbtUtils.writeBlockPos(anchor))
    }

    @JvmStatic
    fun setRotation(stack: ItemStack, rotation: Rotation) {
        stack.orCreateTag.putString("Rotation", rotation.name)
    }

    @JvmStatic
    fun setMirror(stack: ItemStack, mirror: Mirror) {
        stack.orCreateTag.putString("Mirror", mirror.name)
    }

    @JvmStatic
    fun setDeployed(stack: ItemStack, deployed: Boolean) {
        stack.orCreateTag.putBoolean("Deployed", deployed)
    }

    @JvmStatic
    fun clearAll(stack: ItemStack) {
        val tag = stack.tag ?: return
        tag.remove("File")
        tag.remove("Owner")
        tag.remove("Deployed")
        tag.remove("Anchor")
        tag.remove("Rotation")
        tag.remove("Mirror")
        tag.remove("Bounds")
    }
}
