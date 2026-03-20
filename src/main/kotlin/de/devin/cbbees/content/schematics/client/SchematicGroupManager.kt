package de.devin.cbbees.content.schematics.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.CreateBuzzyBeez
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import java.io.IOException
import java.nio.file.Files

/**
 * Client-side manager for schematic group assignments.
 *
 * Persists a `groups.json` file in the schematics directory that maps
 * schematic filenames to hierarchical group paths (e.g. "Medieval/Towers").
 * Groups exist implicitly when referenced — no separate creation step needed.
 */
@OnlyIn(Dist.CLIENT)
object SchematicGroupManager {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val groupsFile = CreatePaths.SCHEMATICS_DIR.resolve("groups.json")

    /** filename → group path (e.g. "Medieval/Towers"). Empty string = root. */
    private val groups = mutableMapOf<String, String>()
    private var loaded = false

    fun load() {
        groups.clear()
        if (!Files.exists(groupsFile)) {
            loaded = true
            return
        }
        try {
            val json = JsonParser.parseString(Files.readString(groupsFile)).asJsonObject
            val groupsObj = json.getAsJsonObject("groups") ?: return
            for ((key, value) in groupsObj.entrySet()) {
                groups[key] = value.asString
            }
        } catch (e: Exception) {
            CreateBuzzyBeez.LOGGER.warn("Failed to load groups.json", e)
        }
        loaded = true
    }

    fun save() {
        try {
            val json = JsonObject()
            json.addProperty("version", 1)
            val groupsObj = JsonObject()
            for ((key, value) in groups.toSortedMap()) {
                groupsObj.addProperty(key, value)
            }
            json.add("groups", groupsObj)
            Files.createDirectories(groupsFile.parent)
            Files.writeString(groupsFile, gson.toJson(json))
        } catch (e: IOException) {
            CreateBuzzyBeez.LOGGER.error("Failed to save groups.json", e)
        }
    }

    fun ensureLoaded() {
        if (!loaded) load()
    }

    /** Returns the group path for a schematic filename, or empty string for root. */
    fun getGroup(filename: String): String {
        ensureLoaded()
        return groups[filename] ?: ""
    }

    /** Assigns a schematic to a group path. Empty path moves to root (removes entry). */
    fun setGroup(filename: String, groupPath: String) {
        ensureLoaded()
        val trimmed = groupPath.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            groups.remove(filename)
        } else {
            groups[filename] = trimmed
        }
        save()
    }

    /** Removes group assignment (moves to root). */
    fun removeGroup(filename: String) {
        ensureLoaded()
        groups.remove(filename)
        save()
    }

    /** Transfers group assignment from one filename to another (used when renaming). */
    fun renameEntry(oldFilename: String, newFilename: String) {
        ensureLoaded()
        val group = groups.remove(oldFilename) ?: return
        groups[newFilename] = group
        save()
    }

    /** Prunes entries for schematic files that no longer exist on disk. */
    fun reconcile(available: List<String>) {
        ensureLoaded()
        val availableSet = available.toSet()
        val removed = groups.keys.filter { it !in availableSet }
        if (removed.isNotEmpty()) {
            removed.forEach { groups.remove(it) }
            save()
        }
    }

    /**
     * Returns items at the given group level.
     * @return Pair of (subgroup display names, schematic filenames)
     */
    fun getItemsAtLevel(groupPath: String): Pair<List<String>, List<String>> {
        ensureLoaded()
        val prefix = if (groupPath.isEmpty()) "" else "$groupPath/"
        val subgroups = mutableSetOf<String>()
        val schematics = mutableListOf<String>()

        return Pair(subgroups.toList().sorted(), schematics)
    }

    /**
     * Returns items at the given group level from a provided list of available schematics.
     * @return Pair of (subgroup display names, schematic filenames)
     */
    fun getItemsAtLevel(groupPath: String, availableSchematics: List<String>): Pair<List<String>, List<String>> {
        ensureLoaded()
        val prefix = if (groupPath.isEmpty()) "" else "$groupPath/"
        val subgroups = mutableSetOf<String>()
        val schematics = mutableListOf<String>()

        for (filename in availableSchematics) {
            val fileGroup = groups[filename] ?: ""

            if (groupPath.isEmpty()) {
                // At root: schematics with no group or whose group doesn't match any prefix
                if (fileGroup.isEmpty()) {
                    schematics.add(filename)
                } else {
                    // Extract the first segment as a subgroup
                    val firstSegment = fileGroup.split("/")[0]
                    subgroups.add(firstSegment)
                }
            } else {
                if (fileGroup == groupPath) {
                    // Exact match — schematic is directly at this level
                    schematics.add(filename)
                } else if (fileGroup.startsWith(prefix)) {
                    // Deeper — extract next segment as subgroup
                    val remainder = fileGroup.removePrefix(prefix)
                    val nextSegment = remainder.split("/")[0]
                    subgroups.add(nextSegment)
                }
                // else: doesn't belong at this level
            }
        }

        return Pair(subgroups.toList().sorted(), schematics.sorted())
    }

    /** Returns all unique group paths currently in use. */
    fun getAllGroupPaths(): Set<String> {
        ensureLoaded()
        val paths = mutableSetOf<String>()
        for (groupPath in groups.values) {
            // Add the path and all parent paths
            val parts = groupPath.split("/")
            for (i in parts.indices) {
                paths.add(parts.subList(0, i + 1).joinToString("/"))
            }
        }
        return paths
    }

    /** Returns all raw group assignments (for display/debugging). */
    fun getAllAssignments(): Map<String, String> {
        ensureLoaded()
        return groups.toMap()
    }
}
