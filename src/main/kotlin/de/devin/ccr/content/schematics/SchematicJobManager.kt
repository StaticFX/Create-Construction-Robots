package de.devin.ccr.content.schematics

import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Manages active schematic construction and deconstruction jobs.
 * This ensures that the same schematic isn't being built twice at the same time.
 */
object SchematicJobManager {

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

    private val activeJobs = mutableSetOf<SchematicJobKey>()

    fun isJobActive(playerUuid: UUID, schematicStack: ItemStack): Boolean {
        val key = createJobKey(playerUuid, schematicStack) ?: return false
        return activeJobs.contains(key)
    }

    fun isJobActive(key: SchematicJobKey): Boolean {
        return activeJobs.contains(key)
    }

    fun registerJob(key: SchematicJobKey) {
        activeJobs.add(key)
    }

    fun markComplete(playerUuid: UUID, schematicFile: String, anchorX: Int, anchorY: Int, anchorZ: Int) {
        activeJobs.remove(SchematicJobKey(playerUuid, schematicFile, anchorX, anchorY, anchorZ))
    }

    fun markAllComplete(playerUuid: UUID) {
        activeJobs.removeAll { it.playerUuid == playerUuid }
    }

    fun createJobKey(playerUuid: UUID, schematicStack: ItemStack): SchematicJobKey? {
        val schematicFile = schematicStack.get(com.simibubi.create.AllDataComponents.SCHEMATIC_FILE) ?: return null
        val anchor = schematicStack.get(com.simibubi.create.AllDataComponents.SCHEMATIC_ANCHOR) ?: return null
        return SchematicJobKey(playerUuid, schematicFile, anchor.x, anchor.y, anchor.z)
    }
}
