package de.devin.cbbees.content.beehive.client

import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.foundation.utility.RaycastHelper
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.network.BeeNetworkManager
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
        val networkId = be.networkId
        if (networkId == null) {
            renderSingleHiveRange(be)
        } else {
            val network = BeeNetworkManager.getNetwork(networkId)
            if (network == null) {
                renderSingleHiveRange(be)
            } else {
                network.hives.forEachIndexed { index, hive ->
                    renderHiveRange(hive.sourcePosition, hive.getWorkRange(), "network_$index", network.color)
                }
            }
        }
    }

    private fun renderSingleHiveRange(be: MechanicalBeehiveBlockEntity) {
        renderHiveRange(be.blockPos, be.getWorkRange(), outlineSlot, RANGE_COLOR)
    }

    private fun renderHiveRange(center: net.minecraft.core.BlockPos, maxRange: Double, slot: Any, color: Int) {
        if (maxRange <= 0) return

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
            .chaseAABB(slot, box)
            .colored(color)
            .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
            .lineWidth(1 / 16f)
    }
}
