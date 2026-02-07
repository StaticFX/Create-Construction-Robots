package de.devin.ccr.content.schematics

import java.util.UUID

/**
 * Data class representing a unique schematic construction job.
 * Uses schematic file name and anchor position to identify duplicates.
 */
data class SchematicJobKey(
    val playerUuid: UUID,
    val schematicFile: String,
    val anchorX: Int,
    val anchorY: Int,
    val anchorZ: Int
)
