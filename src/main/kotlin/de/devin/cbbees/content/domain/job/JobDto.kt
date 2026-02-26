package de.devin.cbbees.content.domain.job

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

data class ClientBatchInfo(
    val status: String,
    val target: BlockPos,
    val required: List<ItemStack>,
    val assignedBeeIds: List<UUID>,
    val blockState: BlockState? = null
)

data class ClientJobInfo(
    val jobId: UUID,
    val name: String,          // short id label
    val status: String,        // JobStatus name
    val completed: Int,
    val total: Int,
    val reason: String?,       // null if not stuck
    val batches: List<ClientBatchInfo>
)

data class ClientNetworkInfo(
    val name: String,
    val activeBees: Int,
    val storedBees: Int,
    val maxBees: Int
)

data class HiveSnapshot(
    val networkInfo: ClientNetworkInfo,
    val jobs: List<ClientJobInfo>
)
