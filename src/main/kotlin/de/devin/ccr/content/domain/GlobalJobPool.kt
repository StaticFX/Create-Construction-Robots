package de.devin.ccr.content.domain

import de.devin.ccr.content.domain.beehive.BeeHive
import de.devin.ccr.content.domain.job.BeeJob
import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.domain.task.TaskBatch
import de.devin.ccr.content.domain.task.TaskStatus
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Global Bee Job distribution pool.
 *
 */
object GlobalJobPool : SavedData() {
    val workers = mutableSetOf<BeeHive>()
    private val jobBacklog = mutableListOf<BeeJob>()

    fun getAllJobs(): List<BeeJob> = jobBacklog

    fun registerWorker(worker: BeeHive) {
        workers.add(worker)
        this.setDirty()
    }

    fun unregisterWorker(worker: BeeHive) {
        workers.remove(worker)
        this.setDirty()
    }

    fun unregisterWorker(sourceId: UUID) {
        workers.removeIf { it.sourceId == sourceId }
        this.setDirty()
    }

    @Synchronized
    fun workBacklog(beeHive: BeeHive): TaskBatch? {
        val job = jobBacklog.filter { beeHive.isInRange(it.centerPos) }
            .sortedBy { it.centerPos.distSqr(beeHive.sourcePosition) }
            .firstOrNull { it.getNextTask() != null } ?: return null

        val task = job.getNextTask() ?: return null

        task.status = TaskStatus.PICKED
        return TaskBatch(listOf(task), job)
    }

    @Synchronized
    fun dispatchNewJob(job: BeeJob) {
        val availableHives = workers
            .filter {
                it.getAvailableBeeCount() > 0 &&
                        it.sourceWorld == job.level &&
                        it.isInRange(job.centerPos)
            }
            .sortedBy { job.centerPos.distSqr(it.sourcePosition) }

        if (availableHives.isEmpty()) {
            jobBacklog.add(job)
            return
        }

        val tasksToDistribute = job.tasks
            .filter { it.status == TaskStatus.PENDING }
            .sortedByDescending { it.priority } // Highest priority first
            .toMutableList()

        for (hive in availableHives) {
            if (tasksToDistribute.isEmpty()) break

            val capacity = minOf(hive.getAvailableBeeCount(), hive.getMaxContributionBees())
            var contributedThisLoop = 0

            while (contributedThisLoop < capacity && tasksToDistribute.isNotEmpty()) {
                val task = tasksToDistribute.first()

                task.status = TaskStatus.PICKED
                val batch = TaskBatch(listOf(task), job)
                if (hive.acceptBatch(batch)) {
                    job.addContribution(hive, 1)
                    tasksToDistribute.removeAt(0)
                    contributedThisLoop++
                } else {
                    task.status = TaskStatus.PENDING
                    break
                }
            }
        }

        if (tasksToDistribute.isNotEmpty()) jobBacklog.add(job)

        this.setDirty()
    }

    override fun save(
        p0: CompoundTag,
        p1: HolderLookup.Provider
    ): CompoundTag {
        TODO("Not yet implemented")
    }
}