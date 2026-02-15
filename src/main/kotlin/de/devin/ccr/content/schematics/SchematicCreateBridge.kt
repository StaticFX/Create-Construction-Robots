package de.devin.ccr.content.schematics

import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.schematics.SchematicPrinter
import com.simibubi.create.content.schematics.requirement.ItemRequirement
import de.devin.ccr.CreateCCR
import de.devin.ccr.content.domain.GlobalJobPool
import de.devin.ccr.content.domain.action.impl.PickupItemAction
import de.devin.ccr.content.domain.job.BeeJob
import de.devin.ccr.content.domain.task.BeeTask
import de.devin.ccr.content.domain.task.TaskBatch
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Handler that bridges Create's schematic system with our robot task system.
 *
 * This utility class is responsible for:
 * - Loading Create's `.nbt` schematic files into a [SchematicPrinter].
 * - Iterating through the schematic's volume and identifying blocks that need to be placed.
 * - Generating [BeeTask] objects that represent construction actions.
 * - Identifying item requirements for each block using Create's [ItemRequirement] API.
 * - Providing utilities for area deconstruction task generation.
 */
class SchematicCreateBridge(
    private val level: Level
) {
    private val printer = SchematicPrinter()
    private var isLoaded = false
    private var schematicStack: ItemStack = ItemStack.EMPTY

    /**
     * Load a schematic from an ItemStack (Create's SchematicItem)
     * @param blueprint The schematic item stack
     * @return true if loaded successfully
     */
    fun loadSchematic(blueprint: ItemStack): Boolean {
        if (blueprint.isEmpty) {
            CreateCCR.LOGGER.warn("Cannot load empty schematic")
            return false
        }

        // Check if this is a valid schematic item
        if (!blueprint.has(AllDataComponents.SCHEMATIC_FILE)) {
            CreateCCR.LOGGER.warn("ItemStack is not a valid schematic")
            return false
        }

        // Check if schematic is deployed (has anchor position)
        if (blueprint.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false) == false) {
            CreateCCR.LOGGER.warn("Schematic is not deployed - place it in the world first")
            return false
        }

        try {
            printer.loadSchematic(blueprint, level, true)

            if (printer.isErrored) {
                CreateCCR.LOGGER.error("Failed to load schematic - printer reported error")
                return false
            }

            if (printer.isWorldEmpty) {
                CreateCCR.LOGGER.warn("Schematic is empty")
                return false
            }

            schematicStack = blueprint.copy()
            isLoaded = true
            CreateCCR.LOGGER.info("Schematic loaded successfully")
            return true

        } catch (e: Exception) {
            CreateCCR.LOGGER.error("Exception loading schematic: ${e.message}")
            return false
        }
    }

    /**
     * Generate all placement tasks from the loaded schematic
     * @param job The job to assign the tasks to
     * @return List of RobotTasks for building the schematic
     */
    fun generateBuildTasks(job: BeeJob): List<TaskBatch> {
        if (!isLoaded) {
            CreateCCR.LOGGER.warn("No schematic loaded")
            return emptyList()
        }

        val batches = mutableListOf<TaskBatch>()

        // Iterate through all blocks in the schematic
        while (printer.isLoaded && !printer.isErrored && printer.advanceCurrentPos()) {
            if (!printer.shouldPlaceCurrent(level)) continue

            val requirement = printer.currentRequirement
            val items = getItemsFromRequirement(requirement)

            // Get the block state and potential block entity data to place
            printer.handleCurrentTarget({ pos, state, blockEntity ->
                if (state != null && !state.isAir) {

                    val tag = blockEntity?.saveWithFullMetadata(level.registryAccess())
                    val buildTask = BeeTask.place(
                        pos = pos,
                        state = state,
                        items = items,
                        priority = calculatePriority(pos),
                        tag = tag,
                        job = job
                    )

                    //buildTask.requirement = { it.mechanicalBee.inventoryManager }

                    val tasksInBatch = mutableListOf<BeeTask>()

                    // Check if we need to pick up items
                    if (items.isNotEmpty()) {
                        val port = GlobalJobPool.findProviderFor(level, items[0], pos)
                        if (port != null) {
                            val pickupAction = PickupItemAction(port.sourcePosition, items)
                            tasksInBatch.add(BeeTask(pickupAction, job, buildTask.priority + 1))
                        }
                    }

                    tasksInBatch.add(buildTask)
                    batches.add(TaskBatch(tasksInBatch, job))
                }
            }, { _, _ ->
                // TODO Add entity handling... somehow
            })
        }

        return batches
    }

    /**
     * Generate removal tasks for an area
     * @param corner1 First corner of the area
     * @param corner2 Second corner of the area
     * @param job The job to assign the tasks to
     * @return List of RobotTasks for removing blocks in the area
     */
    fun generateRemovalTasks(corner1: BlockPos, corner2: BlockPos, job: BeeJob): List<BeeTask> {
        val tasks = mutableListOf<BeeTask>()

        val minX = minOf(corner1.x, corner2.x)
        val minY = minOf(corner1.y, corner2.y)
        val minZ = minOf(corner1.z, corner2.z)
        val maxX = maxOf(corner1.x, corner2.x)
        val maxY = maxOf(corner1.y, corner2.y)
        val maxZ = maxOf(corner1.z, corner2.z)

        // Iterate from top to bottom for removal
        for (y in maxY downTo minY) {
            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    val pos = BlockPos(x, y, z)
                    val state = level.getBlockState(pos)

                    // Skip air and unbreakable blocks
                    if (!state.isAir && state.getDestroySpeed(level, pos) >= 0) {
                        tasks.add(
                            BeeTask.remove(
                                pos = pos,
                                priority = calculateRemovalPriority(pos, maxY),
                                job = job
                            )
                        )
                    }
                }
            }
        }

        return tasks
    }

    /**
     * Get the anchor position of the loaded schematic
     */
    fun getAnchor(): BlockPos? {
        return if (isLoaded) printer.anchor else null
    }

    /**
     * Convert Create's ItemRequirement to a list of ItemStacks
     */
    private fun getItemsFromRequirement(requirement: ItemRequirement): List<ItemStack> {
        if (requirement.isInvalid) return emptyList()

        val items = mutableListOf<ItemStack>()

        // Get required items from the requirement
        // StackRequirement has a 'stack' field containing the ItemStack
        requirement.requiredItems.forEach { reqItem ->
            items.add(reqItem.stack.copy())
        }

        return items
    }

    /**
     * Calculate priority for placement (lower Y = higher priority for bottom-up building)
     */
    private fun calculatePriority(pos: BlockPos): Int {
        // Invert Y so lower blocks have higher priority
        return 256 - pos.y
    }

    /**
     * Calculate priority for removal (higher Y = higher priority for top-down removal)
     */
    private fun calculateRemovalPriority(pos: BlockPos, maxY: Int): Int {
        return pos.y - maxY + 256
    }

    companion object {
        /**
         * Check if an ItemStack is a valid Create schematic
         */
        fun isValidSchematic(stack: ItemStack): Boolean {
            return stack.has(AllDataComponents.SCHEMATIC_FILE)
        }

        /**
         * Check if a schematic is deployed (placed in world)
         */
        fun isSchematicDeployed(stack: ItemStack): Boolean {
            return stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false)
        }

        /**
         * Get the schematic file name
         */
        fun getSchematicName(stack: ItemStack): String? {
            return stack.get(AllDataComponents.SCHEMATIC_FILE)
        }
    }
}
