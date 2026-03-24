package de.devin.cbbees.content.schematics.external

import java.util.concurrent.CompletableFuture

/**
 * No-op implementation of [ExternalSchematicSource] for local testing.
 * Returns dummy schematics that cannot actually be downloaded.
 */
object NoOpSchematicSource : ExternalSchematicSource {

    override val name: String = "createmod.com"
    override val baseUrl: String = "https://createmod.com"

    private val dummySchematics = listOf(
        RemoteSchematic("1", "Medieval Tower", "SteamPunkBuilder", "A tall medieval watchtower with spiral staircase and lookout platform.", 1243, 7, 22, 7),
        RemoteSchematic("2", "Windmill", "CreateFanatic", "Classic Dutch-style windmill with rotating sails. Pairs well with Create's rotation system.", 892, 11, 18, 11),
        RemoteSchematic("3", "Railway Station", "TrainEnjoyer", "Small train station with platform, ticket booth, and waiting area.", 2105, 24, 10, 12),
        RemoteSchematic("4", "Steampunk Factory", "GearHead99", "Industrial factory with smokestacks, conveyor lines, and brass detailing.", 3401, 32, 16, 20),
        RemoteSchematic("5", "Cozy Cottage", "BlockByBlock", "Small countryside cottage with garden and chimney.", 567, 9, 8, 10),
        RemoteSchematic("6", "Bridge - Stone Arch", "ArchitectPro", "Elegant stone arch bridge, 20 blocks long.", 1876, 22, 9, 5),
        RemoteSchematic("7", "Clock Tower", "TimeLord_MC", "Ornate clock tower with brass casing and Create-compatible internals.", 734, 5, 28, 5),
        RemoteSchematic("8", "Market Stall Pack", "VillageBuilder", "Set of 4 market stalls with different roof styles.", 445, 16, 6, 8),
        RemoteSchematic("9", "Underground Bunker", "SurvivalExpert", "Compact underground bunker with storage, farm, and escape tunnel.", 1023, 14, 12, 14),
        RemoteSchematic("10", "Harbor Crane", "DockMaster", "Functional-looking harbor crane with brass and andesite details.", 612, 8, 20, 10),
        RemoteSchematic("11", "Greenhouse", "BotanistSteve", "Glass greenhouse with redstone-ready irrigation channels.", 389, 12, 8, 8),
        RemoteSchematic("12", "Castle Gate", "FortressKing", "Fortified castle gatehouse with portcullis and guard towers.", 2890, 18, 14, 8),
    )

    override fun listSchematics(query: String, page: Int, pageSize: Int): CompletableFuture<SchematicListResult> {
        val filtered = if (query.isBlank()) {
            dummySchematics
        } else {
            val q = query.lowercase()
            dummySchematics.filter {
                it.name.lowercase().contains(q) || it.author.lowercase().contains(q) || it.description.lowercase().contains(q)
            }
        }

        val start = page * pageSize
        val paged = filtered.drop(start).take(pageSize)

        val totalPages = (filtered.size + pageSize - 1) / pageSize
        return CompletableFuture.completedFuture(
            SchematicListResult(paged, totalPages, page, pageSize)
        )
    }

    override fun downloadBytes(schematic: RemoteSchematic): CompletableFuture<ByteArray> {
        return CompletableFuture.completedFuture(ByteArray(0))
    }

    override fun download(schematic: RemoteSchematic): CompletableFuture<String> {
        // Simulate a short download delay
        return CompletableFuture.supplyAsync {
            Thread.sleep(500)
            "${schematic.name}.nbt"
        }
    }

    override fun upload(localFilename: String): CompletableFuture<RemoteSchematic> {
        return CompletableFuture.supplyAsync {
            Thread.sleep(500)
            RemoteSchematic(
                id = "local-${System.currentTimeMillis()}",
                name = localFilename.removeSuffix(".nbt"),
                author = "You",
                description = "Uploaded from local schematics",
                downloads = 0,
                sizeX = 0, sizeY = 0, sizeZ = 0
            )
        }
    }
}
