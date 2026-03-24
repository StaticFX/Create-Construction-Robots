package de.devin.cbbees.content.bee.brain

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TransportTask
import net.minecraft.world.entity.player.Player
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import java.util.*

object BeeMemoryModules {
    val HIVE_POS = CreateBuzzyBeez.REGISTRATE.generic("hive_pos", Registries.MEMORY_MODULE_TYPE) {
        MemoryModuleType(Optional.of(BlockPos.CODEC))
    }.register()

    // Non persistent
    val CURRENT_TASK = CreateBuzzyBeez.REGISTRATE.generic("current_task", Registries.MEMORY_MODULE_TYPE) {
        MemoryModuleType<TaskBatch>(Optional.empty())
    }.register()

    val HIVE_INSTANCE = CreateBuzzyBeez.REGISTRATE.generic("hive_instance", Registries.MEMORY_MODULE_TYPE) {
        MemoryModuleType<BeeHive>(Optional.empty())
    }.register()

    val TRANSPORT_TASK = CreateBuzzyBeez.REGISTRATE.generic("transport_task", Registries.MEMORY_MODULE_TYPE) {
        MemoryModuleType<TransportTask>(Optional.empty())
    }.register()

    /** Set when the bee's portable beehive was removed. The bee should fly to this player and drop. */
    val RETURNING_TO_OWNER = CreateBuzzyBeez.REGISTRATE.generic("returning_to_owner", Registries.MEMORY_MODULE_TYPE) {
        MemoryModuleType<Player>(Optional.empty())
    }.register()

    fun register() {}
}
