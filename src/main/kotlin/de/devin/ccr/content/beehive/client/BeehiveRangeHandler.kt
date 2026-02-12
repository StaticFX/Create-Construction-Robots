package de.devin.ccr.content.beehive.client

import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.foundation.utility.RaycastHelper
import de.devin.ccr.content.beehive.MechanicalBeehiveBlockEntity
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent

/**
 * Handles rendering the range of a Mechanical Beehive when looked at by the player.
 */
object BeehiveRangeHandler {
    private val outlineSlot = Any()
    private const val RANGE_COLOR = 0xFFD700 // Gold color for bees

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (mc.screen != null) return

        // Raycast to see if we are looking at a Mechanical Beehive
        val trace = RaycastHelper.rayTraceRange(level, player, 20.0)
        if (trace != null && trace.type == HitResult.Type.BLOCK) {
            val be = level.getBlockEntity(trace.blockPos)
            if (be is MechanicalBeehiveBlockEntity) {
                renderRange(be)
            }
        }
    }

    private fun renderRange(be: MechanicalBeehiveBlockEntity) {
        // Get the maximum range from instructions
        val maxRange = be.getWorkRange()
        if (maxRange <= 0) return

        val center = be.blockPos

        // Render on a 2D plane as requested
        // We create a very thin box at the bottom of the beehive
        val box = AABB(
            (center.x - maxRange).toDouble(),
            center.y.toDouble(),
            (center.z - maxRange).toDouble(),
            (center.x + maxRange + 1).toDouble(),
            center.y.toDouble() + 0.05,
            (center.z + maxRange + 1).toDouble()
        )

        Outliner.getInstance()
            .chaseAABB(outlineSlot, box)
            .colored(RANGE_COLOR)
            .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
            .lineWidth(1 / 16f)
    }
}
