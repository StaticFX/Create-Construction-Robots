package de.devin.ccr.content.beehive

import de.devin.ccr.content.schematics.goals.BeeJobGoal
import de.devin.ccr.content.schematics.goals.FertilizeGoal
import de.devin.ccr.content.schematics.goals.MaintenanceDeconstructionGoal
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
    FERTILIZE("gui.ccr.instruction.fertilize") {
        override fun getGoal(pos: BlockPos, range: Int): BeeJobGoal = FertilizeGoal(pos, range)
    },
    DECONSTRUCT("gui.ccr.instruction.deconstruct") {
        override fun getGoal(pos: BlockPos, range: Int): BeeJobGoal = MaintenanceDeconstructionGoal(pos, range)
    },
    // CLEAN_FALLEN_ITEMS("gui.ccr.instruction.clean_items")
    ;

    val displayName: Component get() = Component.translatable(translationKey)

    abstract fun getGoal(pos: BlockPos, range: Int): BeeJobGoal
}
