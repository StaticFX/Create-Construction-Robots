package de.devin.ccr.content.beehive

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component

/**
 * Represents an instruction for a stationary beehive.
 */
data class BeeInstruction(
    var type: InstructionType = InstructionType.FERTILIZE,
    var beeCount: Int = 1,
    var range: Int = 16
) {
    fun serializeNBT(nbt: CompoundTag) {
        nbt.putString("Type", type.name)
        nbt.putInt("BeeCount", beeCount)
        nbt.putInt("Range", range)
    }

    companion object {
        fun deserializeNBT(nbt: CompoundTag): BeeInstruction {
            return BeeInstruction(
                InstructionType.valueOf(nbt.getString("Type")),
                nbt.getInt("BeeCount"),
                nbt.getInt("Range")
            )
        }
    }
}

enum class InstructionType(val translationKey: String) {
    FERTILIZE("gui.ccr.instruction.fertilize"),
    DECONSTRUCT("gui.ccr.instruction.deconstruct"),
    // CLEAN_FALLEN_ITEMS("gui.ccr.instruction.clean_items")
    ;

    val displayName: Component get() = Component.translatable(translationKey)
}
