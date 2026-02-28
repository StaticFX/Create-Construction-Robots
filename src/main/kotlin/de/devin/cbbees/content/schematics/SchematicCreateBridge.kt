package de.devin.cbbees.content.schematics

import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.schematics.SchematicPrinter
import com.simibubi.create.content.schematics.requirement.ItemRequirement
import com.simibubi.create.foundation.utility.BlockHelper
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.action.impl.PickupItemAction
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import de.devin.cbbees.util.ServerSide

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
@ServerSide
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
            CreateBuzzyBeez.LOGGER.warn("Cannot load empty schematic")
            return false
        }

        // Check if this is a valid schematic item
        if (!blueprint.has(AllDataComponents.SCHEMATIC_FILE)) {
            CreateBuzzyBeez.LOGGER.warn("ItemStack is not a valid schematic")
            return false
        }

        // Check if schematic is deployed (has anchor position)
        if (blueprint.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false) == false) {
            CreateBuzzyBeez.LOGGER.warn("Schematic is not deployed - place it in the world first")
            return false
        }

        try {
            printer.loadSchematic(blueprint, level, true)

            if (printer.isErrored) {
                CreateBuzzyBeez.LOGGER.error("Failed to load schematic - printer reported error")
                return false
            }

            if (printer.isWorldEmpty) {
                CreateBuzzyBeez.LOGGER.warn("Schematic is empty")
                return false
            }

            schematicStack = blueprint.copy()
            isLoaded = true
            CreateBuzzyBeez.LOGGER.info("Schematic loaded successfully")
            return true

        } catch (e: Exception) {
            CreateBuzzyBeez.LOGGER.error("Exception loading schematic: ${e.message}")
            return false
        }
    }

    /**
     * Generate all placement tasks from the loaded schematic.
     *
     * Uses a two-pass priority system mirroring Create's SchematiCannon:
     * 1. **Normal blocks** (high priority): Solid support blocks placed bottom-up.
     * 2. **Deferred blocks** (low priority): Brittle/dependent blocks placed after supports.
     *
     * Special handling:
     * - Multi-block duplicates (upper door halves, bed heads) are skipped.
     * - Belt blocks are deferred and placed individually via [BlockHelper.placeSchematicBlock]
     *   which uses flag 2 (no neighbor updates). The schematic's block entity data provides
     *   controller/beltLength/index so no special belt reconstruction is needed.
     *
     * @param job The job to assign the tasks to
     * @return List of TaskBatches for building the schematic
     */
    fun generateBuildTasks(job: BeeJob): List<TaskBatch> {
        if (!isLoaded) {
            CreateBuzzyBeez.LOGGER.warn("No schematic loaded")
            return emptyList()
        }

        val batches = mutableListOf<TaskBatch>()

        // Iterate through all blocks in the schematic using Create's three-pass printer
        // (BLOCKS -> DEFERRED_BLOCKS -> ENTITIES)
        while (printer.isLoaded && !printer.isErrored && printer.advanceCurrentPos()) {
            if (!printer.shouldPlaceCurrent(level)) continue

            val requirement = printer.currentRequirement
            val items = getItemsFromRequirement(requirement)

            printer.handleCurrentTarget({ pos, state, blockEntity ->
                if (state == null || state.isAir) return@handleCurrentTarget

                // Skip secondary parts of multi-block structures
                if (BlockPlacementClassifier.shouldSkipBlock(state)) return@handleCurrentTarget

                // All blocks (including belts) use the two-pass priority system.
                // Belts are classified as deferred, so they're placed after their support shafts.
                // BlockHelper.placeSchematicBlock() uses flag 2 for belts (no neighbor updates),
                // and the schematic's block entity data already contains controller/beltLength/index.
                val priority = BlockPlacementClassifier.calculatePriority(pos, state)
                val tag = prepareBlockEntityData(state, blockEntity)
                val buildTask = BeeTask.place(
                    pos = pos,
                    state = state,
                    items = items,
                    priority = priority,
                    tag = tag,
                    job = job
                )

                val tasksInBatch = mutableListOf<BeeTask>()

                if (items.isNotEmpty()) {
                    val port = ServerBeeNetworkManager.findProviderFor(level, items[0], pos)
                    if (port != null) {
                        val pickupAction = PickupItemAction(port.pos, items)
                        tasksInBatch.add(BeeTask(pickupAction, job, buildTask.priority + 1))
                    }
                }

                tasksInBatch.add(buildTask)
                batches.add(TaskBatch(tasksInBatch, job, buildTask.targetPos))
            }, { _, _ ->
                // TODO Add entity handling (armor stands, item frames, etc.)
            })
        }

        return batches
    }

    /**
     * Prepares block entity data for placement using Create's safe NBT processing.
     */
    private fun prepareBlockEntityData(state: BlockState, blockEntity: BlockEntity?): net.minecraft.nbt.CompoundTag? {
        if (blockEntity == null) return null
        return BlockHelper.prepareBlockEntityData(blockEntity.level ?: level, state, blockEntity)
    }

    /**
     * Generate removal tasks for an area
     * @param corner1 First corner of the area
     * @param corner2 Second corner of the area
     * @param job The job to assign the tasks to
     * @return List of RobotTasks for removing blocks in the area
     */
    fun generateRemovalTasks(corner1: BlockPos, corner2: BlockPos, job: BeeJob): List<TaskBatch> {
        val batches = mutableListOf<TaskBatch>()

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
                        val task = BeeTask.remove(
                            pos = pos,
                            priority = calculateRemovalPriority(pos, maxY),
                            job = job
                        )
                        batches.add(TaskBatch(listOf(task), job, pos))
                    }
                }
            }
        }

        return batches
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
