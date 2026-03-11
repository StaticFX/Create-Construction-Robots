package de.devin.cbbees.content.schematics.client

import net.createmod.catnip.levelWrappers.SchematicLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.BoundingBox

/**
 * A virtual [SchematicLevel] populated with ghost blocks for construction preview rendering.
 *
 * Uses anchor = ZERO so local coordinates equal global coordinates.
 * Blocks are stored directly into the map with tight bounds to avoid
 * iterating empty space during rendering.
 */
class GhostBlockLevel(original: Level) : SchematicLevel(original) {

    fun populate(ghosts: Map<BlockPos, BlockState>) {
        blocks.clear()
        blockEntities.clear()
        renderedBlockEntities.clear()

        for ((pos, state) in ghosts) {
            blocks[pos] = state
        }

        if (blocks.isNotEmpty()) {
            bounds = BoundingBox(
                blocks.keys.minOf { it.x },
                blocks.keys.minOf { it.y },
                blocks.keys.minOf { it.z },
                blocks.keys.maxOf { it.x },
                blocks.keys.maxOf { it.y },
                blocks.keys.maxOf { it.z }
            )
        }

        // Force creation of BlockEntities so SchematicRenderer's constructor
        // can pick them up from getRenderedBlockEntities() for BE rendering.
        for (pos in blocks.keys.toList()) {
            getBlockEntity(pos)
        }
    }
}
