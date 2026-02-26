package de.devin.cbbees.content.beehive.client

import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.content.domain.job.HiveSnapshot
import net.minecraft.core.BlockPos

object ClientJobCache {
    private val byHive = mutableMapOf<BlockPos, HiveSnapshot>()
    fun update(pos: BlockPos, snapshot: HiveSnapshot) {
        byHive[pos] = snapshot
    }

    fun get(pos: BlockPos): HiveSnapshot? = byHive[pos]

    fun getAllJobs(): List<ClientJobInfo> = byHive.values.flatMap { it.jobs }
}
