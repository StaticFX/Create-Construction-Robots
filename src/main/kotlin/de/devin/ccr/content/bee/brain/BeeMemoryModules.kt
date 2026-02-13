package de.devin.ccr.content.bee.brain

import de.devin.ccr.CreateCCR
import de.devin.ccr.content.domain.beehive.BeeHive
import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.domain.task.TaskBatch
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import java.util.*

object BeeMemoryModules {
    val HIVE_POS = CreateCCR.REGISTRATE.generic("hive_pos", Registries.MEMORY_MODULE_TYPE) {
        MemoryModuleType(Optional.of(BlockPos.CODEC))
    }.register()

    // Non persistent
    val CURRENT_TASK = CreateCCR.REGISTRATE.generic("current_task", Registries.MEMORY_MODULE_TYPE) {
        MemoryModuleType<TaskBatch>(Optional.empty())
    }.register()

    val HIVE_INSTANCE = CreateCCR.REGISTRATE.generic("hive_instance", Registries.MEMORY_MODULE_TYPE) {
        MemoryModuleType<BeeHive>(Optional.empty())
    }.register()

    fun register() {}
}