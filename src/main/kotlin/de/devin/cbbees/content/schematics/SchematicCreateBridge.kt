package de.devin.cbbees.content.schematics

import com.simibubi.create.AllDataComponents
import com.simibubi.create.AllBlocks
import com.simibubi.create.AllItems
import com.simibubi.create.content.schematics.SchematicPrinter
import com.simibubi.create.content.schematics.requirement.ItemRequirement
import com.simibubi.create.foundation.utility.BlockHelper
import com.simibubi.create.content.kinetics.belt.BeltBlock
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity
import com.simibubi.create.content.kinetics.belt.BeltPart
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.RotatedPillarBlock
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
    private val handledPositions = mutableSetOf<BlockPos>()

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
     * - Belt placement mirrors SchematiCannon: middle segments are ignored, and the controller
     *   segment triggers a single belt-placement action that uses [BeltConnectorItem.createBelts]
     *   and reapplies casing/cover data from the schematic.
     *
     * @param job The job to assign the tasks to
     * @return List of TaskBatches for building the schematic
     */
    fun generateBuildTasks(job: BeeJob): List<TaskBatch> {
        if (!isLoaded) {
            CreateBuzzyBeez.LOGGER.warn("No schematic loaded")
            return emptyList()
        }

        handledPositions.clear()
        val batches = mutableListOf<TaskBatch>()

        // Iterate through all blocks in the schematic using Create's three-pass printer
        // (BLOCKS -> DEFERRED_BLOCKS -> ENTITIES)
        while (printer.isLoaded && !printer.isErrored && printer.advanceCurrentPos()) {
            if (!printer.shouldPlaceCurrent(level)) continue

            val requirement = printer.currentRequirement
            val items = getItemsFromRequirement(requirement)

            printer.handleCurrentTarget({ pos, state, blockEntity ->
                if (state == null || state.isAir) return@handleCurrentTarget
                if (handledPositions.contains(pos)) return@handleCurrentTarget
                if (BlockPlacementClassifier.shouldSkipBlock(state)) return@handleCurrentTarget

                if (AllBlocks.BELT.has(state)) {
                    handleBeltPlacement(pos, state, blockEntity as? BeltBlockEntity, items, job, batches)
                } else {
                    handleRegularBlock(pos, state, blockEntity, items, job, batches)
                }
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
                        val priority = calculateRemovalPriority(pos, maxY)
                        val removeTask = BeeTask.remove(pos = pos, priority = priority, job = job)
                        val tasks = if (CBBeesConfig.beePickupItems.get()) {
                            val dropOffTask = BeeTask.dropOff(fallbackPos = pos, priority = priority, job = job)
                            listOf(removeTask, dropOffTask)
                        } else {
                            listOf(removeTask)
                        }
                        batches.add(TaskBatch(tasks, job, pos))
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

    private fun handleRegularBlock(
        pos: BlockPos,
        state: BlockState,
        blockEntity: BlockEntity?,
        items: List<ItemStack>,
        job: BeeJob,
        batches: MutableList<TaskBatch>
    ) {
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

        batches.add(TaskBatch(listOf(buildTask), job, buildTask.targetPos))
    }

    private fun handleBeltPlacement(
        pos: BlockPos,
        state: BlockState,
        beltBE: BeltBlockEntity?,
        items: List<ItemStack>,
        job: BeeJob,
        batches: MutableList<TaskBatch>
    ) {
        if (beltBE == null || !beltBE.isController) return

        val beltWorld = beltBE.level ?: level
        val controllerPos = beltBE.controller ?: pos
        val chain = BeltBlock.getBeltChain(beltWorld, controllerPos)
        if (chain.isEmpty()) return

        val beltAxis = state.getValue(BeltBlock.HORIZONTAL_FACING).axis

        val startDir = chain.getOrNull(1)?.let { it.subtract(chain.first()) }
        val endDir = chain.getOrNull(chain.size - 1)?.let { last ->
            chain.getOrNull(chain.size - 2)?.let { prev -> last.subtract(prev) }
        }

        val startShaftPos = startDir?.let { chain.first() }
        val endShaftPos = endDir?.let { chain.last() }

        val chainStates = chain.map { beltWorld.getBlockState(it) }
        val casings = mutableListOf<BeltBlockEntity.CasingType>()
        val covers = mutableListOf<Boolean>()
        chain.forEach { chainPos ->
            val segment = beltWorld.getBlockEntity(chainPos) as? BeltBlockEntity
            casings.add(segment?.casing ?: BeltBlockEntity.CasingType.NONE)
            covers.add(segment?.covered ?: false)
        }

        val priority = BlockPlacementClassifier.calculatePriority(pos, state)
        val startItems = mutableListOf<ItemStack>()
        val endItems = mutableListOf<ItemStack>()

        val startShaftTask = startShaftPos?.let { shaftPos ->
            createShaftTaskIfPresent(beltWorld, shaftPos, beltAxis, priority, job, startItems)
        }

        val endShaftTask = endShaftPos?.let { shaftPos ->
            createShaftTaskIfPresent(beltWorld, shaftPos, beltAxis, priority, job, endItems)
        }

        // Create shaft tasks for middle pulley positions (shafts inside belts)
        // BeltConnectorItem.createBelts checks for existing shafts at middle positions
        // and converts them to PULLEY parts — we need to place those shafts first
        val middleShaftTasks = mutableListOf<BeeTask>()
        for (i in 1 until chain.size - 1) {
            val chainPos = chain[i]
            val chainState = chainStates[i]
            if (AllBlocks.BELT.has(chainState)
                && chainState.hasProperty(BeltBlock.PART)
                && chainState.getValue(BeltBlock.PART) == BeltPart.PULLEY
            ) {
                val pulleyItems = mutableListOf<ItemStack>()
                val shaftTask = createShaftTaskIfPresent(beltWorld, chainPos, beltAxis, priority, job, pulleyItems)
                if (shaftTask != null) {
                    middleShaftTasks.add(shaftTask)
                }
            }
        }

        val beltItem = ItemStack(AllItems.BELT_CONNECTOR.get(), 1)

        val buildTask = BeeTask.belt(
            controllerPos = chain.first(),
            endPos = chain.last(),
            chain = chain,
            chainStates = chainStates,
            casings = casings,
            covers = covers,
            items = listOf(beltItem),
            priority = priority,
            job = job
        )

        val tasksInBatch = listOfNotNull(startShaftTask, endShaftTask) + middleShaftTasks + buildTask

        handledPositions.addAll(chain)
        if (startShaftTask != null) startShaftPos.let(handledPositions::add)
        if (endShaftTask != null) endShaftPos.let(handledPositions::add)

        batches.add(TaskBatch(tasksInBatch, job, buildTask.targetPos))
    }

    private fun createShaftTaskIfPresent(
        beltWorld: Level,
        shaftPos: BlockPos,
        beltAxis: Direction.Axis,
        priority: Int,
        job: BeeJob,
        itemCollector: MutableList<ItemStack>
    ): BeeTask? {
        val existingState = beltWorld.getBlockState(shaftPos)
        val baseShaftState = if (AllBlocks.SHAFT.has(existingState)) existingState else AllBlocks.SHAFT.defaultState

        val shaftAxis = when (beltAxis) {
            Direction.Axis.X -> Direction.Axis.Z
            Direction.Axis.Z -> Direction.Axis.X
            else -> beltAxis
        }

        val shaftState = baseShaftState.setValue(RotatedPillarBlock.AXIS, shaftAxis)
        if (!AllBlocks.SHAFT.has(shaftState)) return null

        val shaftItem = ItemStack(shaftState.block)
        if (!shaftItem.isEmpty) itemCollector.add(shaftItem)

        val shaftTag = prepareBlockEntityData(shaftState, beltWorld.getBlockEntity(shaftPos))
        return BeeTask.place(
            pos = shaftPos,
            state = shaftState,
            items = itemCollector,
            priority = priority,
            tag = shaftTag,
            job = job
        )
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
