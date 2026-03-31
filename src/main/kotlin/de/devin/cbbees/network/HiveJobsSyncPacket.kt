package de.devin.cbbees.network

import com.simibubi.create.AllBlocks
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.beehive.client.ClientJobCache
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.StuckReasonResolver
import de.devin.cbbees.content.domain.action.impl.PlaceBeltAction
import de.devin.cbbees.content.domain.action.impl.PlaceBlockAction
import de.devin.cbbees.content.domain.job.ClientBatchInfo
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.content.domain.job.ClientNetworkInfo
import de.devin.cbbees.content.domain.job.HiveSnapshot
import de.devin.cbbees.content.domain.job.JobStatus
import de.devin.cbbees.content.domain.job.SchematicPlacement
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

class HiveJobsSyncPacket(
    val hivePos: BlockPos,
    val snapshot: HiveSnapshot
) {
    companion object {
        fun encode(pkt: HiveJobsSyncPacket, buf: FriendlyByteBuf) {
            buf.writeBlockPos(pkt.hivePos)
            encodeSnapshot(buf, pkt.snapshot)
        }

        fun decode(buf: FriendlyByteBuf): HiveJobsSyncPacket {
            val pos = buf.readBlockPos()
            val snapshot = decodeSnapshot(buf)
            return HiveJobsSyncPacket(pos, snapshot)
        }

        // -- BlockState encoding using NBT (works in both 1.20.1 and 1.21.1) --

        private fun encodeBlockState(buf: FriendlyByteBuf, state: BlockState) {
            buf.writeNbt(NbtUtils.writeBlockState(state))
        }

        private fun decodeBlockState(buf: FriendlyByteBuf): BlockState {
            val tag = buf.readNbt() as? CompoundTag ?: CompoundTag()
            return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag)
        }

        // -- Ghost blocks --

        private fun encodeGhostBlocks(buf: FriendlyByteBuf, map: Map<BlockPos, BlockState>) {
            buf.writeVarInt(map.size)
            map.forEach { (pos, state) ->
                buf.writeBlockPos(pos)
                encodeBlockState(buf, state)
            }
        }

        private fun decodeGhostBlocks(buf: FriendlyByteBuf): Map<BlockPos, BlockState> {
            val size = buf.readVarInt()
            val map = mutableMapOf<BlockPos, BlockState>()
            repeat(size) {
                val pos = buf.readBlockPos()
                val state = decodeBlockState(buf)
                map[pos] = state
            }
            return map
        }

        // -- ItemStack list (delegates to platform-specific helper) --

        private fun encodeItemList(buf: FriendlyByteBuf, items: List<ItemStack>) {
            buf.writeVarInt(items.size)
            items.forEach { ItemStackBufHelper.write(buf, it) }
        }

        private fun decodeItemList(buf: FriendlyByteBuf): List<ItemStack> {
            val size = buf.readVarInt()
            return List(size) { ItemStackBufHelper.read(buf) }
        }

        // -- UUID list --

        private fun encodeUUIDList(buf: FriendlyByteBuf, uuids: List<UUID>) {
            buf.writeVarInt(uuids.size)
            uuids.forEach { buf.writeUUID(it) }
        }

        private fun decodeUUIDList(buf: FriendlyByteBuf): List<UUID> {
            val size = buf.readVarInt()
            return List(size) { buf.readUUID() }
        }

        // -- Batch --

        private fun encodeBatch(buf: FriendlyByteBuf, b: ClientBatchInfo) {
            buf.writeUtf(b.status)
            buf.writeBlockPos(b.target)
            encodeItemList(buf, b.required)
            encodeUUIDList(buf, b.assignedBeeIds)
            encodeGhostBlocks(buf, b.ghostBlocks)
        }

        private fun decodeBatch(buf: FriendlyByteBuf): ClientBatchInfo {
            return ClientBatchInfo(
                buf.readUtf(),
                buf.readBlockPos(),
                decodeItemList(buf),
                decodeUUIDList(buf),
                decodeGhostBlocks(buf)
            )
        }

        // -- Job --

        private fun encodeJob(buf: FriendlyByteBuf, j: ClientJobInfo) {
            buf.writeUUID(j.jobId)
            buf.writeUtf(j.name)
            buf.writeUtf(j.status)
            buf.writeVarInt(j.completed)
            buf.writeVarInt(j.total)
            buf.writeNullable(j.reason) { b, s -> b.writeUtf(s) }
            buf.writeVarInt(j.batches.size)
            j.batches.forEach { encodeBatch(buf, it) }
            buf.writeNullable(j.schematicPlacement) { b, sp ->
                b.writeUtf(sp.file)
                b.writeBlockPos(sp.anchor)
                b.writeEnum(sp.rotation)
                b.writeEnum(sp.mirror)
            }
        }

        private fun decodeJob(buf: FriendlyByteBuf): ClientJobInfo {
            val id = buf.readUUID()
            val name = buf.readUtf()
            val status = buf.readUtf()
            val completed = buf.readVarInt()
            val total = buf.readVarInt()
            val reason = buf.readNullable { it.readUtf() }
            val batchCount = buf.readVarInt()
            val batches = List(batchCount) { decodeBatch(buf) }
            val placement = buf.readNullable { b ->
                SchematicPlacement(
                    file = b.readUtf(),
                    anchor = b.readBlockPos(),
                    rotation = b.readEnum(Rotation::class.java),
                    mirror = b.readEnum(Mirror::class.java)
                )
            }
            return ClientJobInfo(id, name, status, completed, total, reason, batches, placement)
        }

        // -- Network info --

        private fun encodeNetworkInfo(buf: FriendlyByteBuf, ni: ClientNetworkInfo) {
            buf.writeUtf(ni.name)
            buf.writeVarInt(ni.activeBees)
            buf.writeVarInt(ni.storedBees)
            buf.writeVarInt(ni.maxBees)
        }

        private fun decodeNetworkInfo(buf: FriendlyByteBuf): ClientNetworkInfo {
            return ClientNetworkInfo(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
        }

        // -- Snapshot --

        private fun encodeSnapshot(buf: FriendlyByteBuf, s: HiveSnapshot) {
            encodeNetworkInfo(buf, s.networkInfo)
            buf.writeVarInt(s.jobs.size)
            s.jobs.forEach { encodeJob(buf, it) }
        }

        private fun decodeSnapshot(buf: FriendlyByteBuf): HiveSnapshot {
            val ni = decodeNetworkInfo(buf)
            val jobCount = buf.readVarInt()
            val jobs = List(jobCount) { decodeJob(buf) }
            return HiveSnapshot(ni, jobs)
        }

        // -- Ghost block collection --

        private fun collectGhostBlocks(batch: TaskBatch): Map<BlockPos, BlockState> {
            if (batch.status == TaskStatus.COMPLETED) return emptyMap()

            val ghosts = mutableMapOf<BlockPos, BlockState>()
            for (task in batch.tasks) {
                when (val action = task.action) {
                    is PlaceBlockAction -> ghosts[action.pos] = action.blockState
                    is PlaceBeltAction -> {
                        action.chain.forEachIndexed { index, pos ->
                            if (!ghosts.containsKey(pos)) {
                                val state = action.chainStates.getOrNull(index)
                                    ?: AllBlocks.BELT.defaultState
                                ghosts[pos] = state
                            }
                        }
                    }
                }
            }
            return ghosts
        }

        // -- Handler --

        fun handleClient(pkt: HiveJobsSyncPacket) {
            ClientJobCache.update(pkt.hivePos, pkt.snapshot)
        }

        // -- Server-side send helpers --

        fun sendSnapshotTo(player: ServerPlayer, hivePos: BlockPos) {
            val level = player.level() as? ServerLevel ?: return
            val be = level.getBlockEntity(hivePos) as? MechanicalBeehiveBlockEntity ?: return
            val net = ServerBeeNetworkManager.getNetworkFor(be) ?: return

            val hiveList = net.hives
            val statsActive = hiveList.sumOf { it.getActiveBeeCount() }
            val statsStored = hiveList.sumOf { it.getAvailableBeeCount() }
            val statsMax = hiveList.sumOf { it.getBeeContext().maxActiveRobots }

            val jobs = GlobalJobPool.getAllJobs()
                .filter { job ->
                    job.status != JobStatus.COMPLETED && job.status != JobStatus.CANCELLED && (
                            job.batches.any { it.assignedNetworkId == net.id } ||
                                    net.isInRange(job.centerPos)
                            )
                }
                .map { job ->
                    val completed = job.tasks.count { it.status == TaskStatus.COMPLETED }
                    val total = job.tasks.size
                    val reason = StuckReasonResolver.firstReasonOrNull(net, job)

                    val hasPlacement = job.schematicPlacement != null
                    val batches = job.batches.map { b ->
                        ClientBatchInfo(
                            status = b.status.name,
                            target = b.targetPosition,
                            required = emptyList(),
                            assignedBeeIds = emptyList(),
                            ghostBlocks = if (hasPlacement) emptyMap() else collectGhostBlocks(b)
                        )
                    }
                    ClientJobInfo(
                        jobId = job.jobId,
                        name = job.jobId.toString().substring(0, 6).uppercase(),
                        status = job.status.name,
                        completed = completed,
                        total = total,
                        reason = reason,
                        batches = batches,
                        schematicPlacement = job.schematicPlacement
                    )
                }

            val ni = ClientNetworkInfo(net.name, statsActive, statsStored, statsMax)
            NetworkHelper.sendToPlayer(player, HiveJobsSyncPacket(hivePos, HiveSnapshot(ni, jobs)))
        }

        fun sendPlayerSnapshotTo(player: ServerPlayer) {
            val jobs = GlobalJobPool.getAllJobs().filter { it.ownerId == player.uuid }
                .filter { it.status != JobStatus.COMPLETED && it.status != JobStatus.CANCELLED }

            val clientJobs = jobs.map { job ->
                val completed = job.tasks.count { it.status == TaskStatus.COMPLETED }
                val batches = job.batches.map { b ->
                    ClientBatchInfo(
                        b.status.name, b.targetPosition, emptyList(), emptyList(),
                        ghostBlocks = emptyMap()
                    )
                }
                ClientJobInfo(
                    job.jobId,
                    job.jobId.toString().substring(0, 6).uppercase(),
                    job.status.name,
                    completed,
                    job.tasks.size,
                    null,
                    batches,
                    schematicPlacement = job.schematicPlacement
                )
            }
            val snapshot = HiveSnapshot(ClientNetworkInfo("Personal", 0, 0, 0), clientJobs)
            NetworkHelper.sendToPlayer(player, HiveJobsSyncPacket(BlockPos.ZERO, snapshot))
        }
    }
}
