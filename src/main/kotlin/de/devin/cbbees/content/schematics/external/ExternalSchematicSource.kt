package de.devin.cbbees.content.schematics.external

import java.util.concurrent.CompletableFuture

/**
 * Represents an external source of schematics, such as createmod.com.
 *
 * Implementations provide browsing, downloading, and uploading of schematics
 * from a remote server. All network operations return [CompletableFuture] to
 * avoid blocking the game thread.
 */
interface ExternalSchematicSource {

    companion object {
        /**
         * The currently active external schematic source.
         * Set during mod init once the implementation is available.
         */
        var active: ExternalSchematicSource? = null
    }

    /**
     * Human-readable name of this source (e.g. "createmod.com").
     */
    val name: String

    /**
     * Base URL of the external service, used for attribution.
     */
    val baseUrl: String

    /**
     * Fetches a paginated list of available schematics from the remote source.
     *
     * @param query optional search query to filter results
     * @param page page number (0-indexed)
     * @param pageSize number of results per page
     * @return a future containing the search results
     */
    fun listSchematics(query: String = "", page: Int = 0, pageSize: Int = 20): CompletableFuture<SchematicListResult>

    /**
     * Downloads the raw NBT bytes of a schematic without saving to disk.
     * Used for in-memory preview before committing to a permanent download.
     *
     * @param schematic the schematic entry to download
     * @return a future containing the raw (GZIPped) NBT bytes
     */
    fun downloadBytes(schematic: RemoteSchematic): CompletableFuture<ByteArray>

    /**
     * Downloads a schematic by its remote ID and saves it to the local schematics folder.
     *
     * @param schematic the schematic entry to download
     * @return a future containing the local filename once saved
     */
    fun download(schematic: RemoteSchematic): CompletableFuture<String>

    /**
     * Uploads a local schematic file to the remote source.
     *
     * @param localFilename the filename in the local schematics directory
     * @return a future containing the remote schematic entry once uploaded
     */
    fun upload(localFilename: String): CompletableFuture<RemoteSchematic>
}

/**
 * A schematic entry from a remote source.
 */
data class RemoteSchematic(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val downloads: Int,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val thumbnailUrl: String? = null,
    val views: Int = 0,
    val blockCount: Int = 0,
    val rating: String = "",
    val ratingCount: Int = 0,
    val createmodVersion: String = "",
    val minecraftVersion: String = "",
    val categories: List<String> = emptyList(),
    val createdHumanReadable: String = ""
)

/**
 * Paginated result from a remote schematic listing.
 */
data class SchematicListResult(
    val schematics: List<RemoteSchematic>,
    val totalPages: Int,
    val page: Int,
    val pageSize: Int
) {
    val hasNextPage: Boolean get() = page < totalPages - 1
    val hasPreviousPage: Boolean get() = page > 0
}
