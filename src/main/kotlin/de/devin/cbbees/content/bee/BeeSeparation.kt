package de.devin.cbbees.content.bee

import net.minecraft.world.entity.FlyingMob
import net.minecraft.world.phys.Vec3

/**
 * Applies a gentle separation force so bees don't stack on top of each other.
 *
 * Call from [FlyingMob.tick] on the server side.
 */
object BeeSeparation {

    private const val SEPARATION_RADIUS = 1.5
    private const val SEPARATION_FORCE = 0.04

    fun applySeparation(bee: FlyingMob) {
        val level = bee.level()
        val nearby = level.getEntitiesOfClass(
            FlyingMob::class.java,
            bee.boundingBox.inflate(SEPARATION_RADIUS)
        ) { it !== bee && it is NetworkedBee }

        if (nearby.isEmpty()) return

        var pushX = 0.0
        var pushY = 0.0
        var pushZ = 0.0

        for (other in nearby) {
            val dx = bee.x - other.x
            val dy = bee.y - other.y
            val dz = bee.z - other.z
            val distSq = dx * dx + dy * dy + dz * dz

            if (distSq < SEPARATION_RADIUS * SEPARATION_RADIUS && distSq > 0.001) {
                val dist = Math.sqrt(distSq)
                // Stronger push when closer
                val strength = SEPARATION_FORCE * (1.0 - dist / SEPARATION_RADIUS)
                pushX += (dx / dist) * strength
                pushY += (dy / dist) * strength
                pushZ += (dz / dist) * strength
            } else if (distSq <= 0.001) {
                // Nearly identical position — push in random direction
                pushX += (bee.random.nextDouble() - 0.5) * SEPARATION_FORCE
                pushZ += (bee.random.nextDouble() - 0.5) * SEPARATION_FORCE
            }
        }

        val current = bee.deltaMovement
        bee.deltaMovement = Vec3(
            current.x + pushX,
            current.y + pushY,
            current.z + pushZ
        )
    }

    /**
     * Returns a small random offset for spawning bees around a hive.
     */
    fun spawnOffset(random: net.minecraft.util.RandomSource): Vec3 {
        return Vec3(
            (random.nextDouble() - 0.5) * 0.8,
            random.nextDouble() * 0.3,
            (random.nextDouble() - 0.5) * 0.8
        )
    }
}
