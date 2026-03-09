package de.devin.cbbees.content.domain

import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.task.TaskStatus

object StuckReasonResolver {
    fun firstReasonOrNull(network: BeeNetwork, job: BeeJob): String? {
        // 1) Any batch target out of the hive coverage
        if (job.batches.any { !network.isInRange(it.targetPosition) })
            return "Target is outside hive coverage"

        // 2) Missing resources for any pending batch
        job.batches.filter { it.status == TaskStatus.PENDING }.forEach { b ->
            val missing = b.tasks.map { it.action }
                .filterIsInstance<ItemConsumingAction>()
                .flatMap { it.requiredItems }
                .filter { req -> network.findAvailableProvider(req) == null }
            if (missing.isNotEmpty()) return "Missing resources (${missing.size})"
        }

        // 3) No free bees (all hives in network currently have none available)
        val totalAvailable = network.hives.sumOf { it.getAvailableBeeCount() }
        if (totalAvailable <= 0) return "No available bees"

        // 4) Otherwise, let it be silent
        return null
    }
}
