package de.devin.cbbees.content.bee.client

import com.simibubi.create.content.equipment.goggles.GogglesItem
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.NetworkedBee
import de.devin.cbbees.util.ClientSide
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent

/**
 * Renders a line from each mechanical bee to its current target when
 * the local player is wearing engineer's goggles and looking at the bee,
 * or for all bees when debug mode is enabled.
 */
@ClientSide
object BeeTargetLineHandler {

    private const val LINE_COLOR = 0xFFD700 // Gold

    /** Set by [de.devin.cbbees.network.BeeDebugSyncPacket] from the server. */
    @JvmStatic
    var debugEnabled = false

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (mc.screen != null) return

        if (!GogglesItem.isWearingGoggles(player)) return

        val searchBox = player.boundingBox.inflate(64.0)
        val lookedAtEntity = mc.crosshairPickEntity

        // Scan both bee types
        processEntities(level.getEntitiesOfClass(MechanicalBeeEntity::class.java, searchBox), lookedAtEntity)
        processEntities(level.getEntitiesOfClass(MechanicalBumbleBeeEntity::class.java, searchBox), lookedAtEntity)
    }

    private fun <T> processEntities(bees: List<T>, lookedAtEntity: Entity?) where T : Entity, T : NetworkedBee {
        for (bee in bees) {
            val target = bee.getTargetPos() ?: continue

            if (!debugEnabled && bee != lookedAtEntity) continue

            val start = bee.position().add(0.0, (bee.bbHeight / 2).toDouble(), 0.0)
            val end = Vec3.atCenterOf(target)

            val network = bee.network()
            val color = network?.color ?: LINE_COLOR

            Outliner.getInstance()
                .showLine("bee_target_${bee.id}", start, end)
                .colored(color)
                .lineWidth(1 / 16f)

            Outliner.getInstance()
                .chaseAABB("bee_target_block_${bee.id}", AABB(target))
                .colored(color)
                .lineWidth(1 / 16f)
        }
    }
}
