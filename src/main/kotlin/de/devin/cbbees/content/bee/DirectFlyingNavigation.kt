package de.devin.cbbees.content.bee

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.level.Level
import net.minecraft.world.level.pathfinder.Path

/**
 * Lightweight navigation for mechanical bees that skips A* pathfinding entirely.
 *
 * Since mechanical bees have no collision (push/isPushable are no-ops) and fly freely,
 * there's no need for the expensive A* grid search that [FlyingPathNavigation] performs.
 * Instead, we create a trivial single-node path directly to the target and let
 * [MoveToTargetSink] drive the mob toward it via [net.minecraft.world.entity.ai.control.FlyingMoveControl].
 *
 * If a bee gets stuck (e.g. inside a block), [de.devin.cbbees.content.bee.brain.behavior.StuckSafetyBehavior] teleports it.
 */
class DirectFlyingNavigation(mob: Mob, level: Level) : FlyingPathNavigation(mob, level) {

    override fun createPath(pos: BlockPos, accuracy: Int): Path? {
        return createDirectPath(pos)
    }

    override fun createPath(target: net.minecraft.world.entity.Entity, accuracy: Int): Path? {
        return createDirectPath(target.blockPosition())
    }

    private fun createDirectPath(target: BlockPos): Path? {
        val node = net.minecraft.world.level.pathfinder.Node(target.x, target.y, target.z)
        node.walkedDistance = 0f
        node.costMalus = 0f
        return Path(listOf(node), target, true)
    }
}
