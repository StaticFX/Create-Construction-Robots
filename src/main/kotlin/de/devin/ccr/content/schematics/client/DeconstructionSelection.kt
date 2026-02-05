package de.devin.ccr.content.schematics.client

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Holds the current selection state for deconstruction.
 */
object DeconstructionSelection {
    /** First corner of the selection */
    var firstPos: BlockPos? = null
    
    /** Second corner of the selection */
    var secondPos: BlockPos? = null
    
    /** Currently targeted block position */
    var selectedPos: BlockPos? = null
    
    /** Selection range when using CTRL */
    var range = 10

    /**
     * Discards the current selection.
     */
    fun discard() {
        firstPos = null
        secondPos = null
    }

    /**
     * Gets the current selection bounding box based on selection state.
     */
    fun getSelectionBox(): AABB? {
        val first = firstPos
        val second = secondPos
        val selected = selectedPos
        
        return when {
            second != null && first != null -> {
                AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(second)).expandTowards(1.0, 1.0, 1.0)
            }
            first != null -> {
                if (selected != null) {
                    AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(selected)).expandTowards(1.0, 1.0, 1.0)
                } else {
                    AABB(first)
                }
            }
            selected != null -> AABB(selected)
            else -> null
        }
    }

    fun isComplete(): Boolean = firstPos != null && secondPos != null
}
