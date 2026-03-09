package de.devin.cbbees.content.bee.debug

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.network.BeeDebugSyncPacket
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

/**
 * Tracks which players have bee debug mode enabled.
 * When enabled, bee behaviors send chat messages describing what each bee is doing.
 */
object BeeDebug {

    private val enabledPlayers = mutableSetOf<UUID>()

    fun toggle(player: ServerPlayer): Boolean {
        val enabled = if (enabledPlayers.remove(player.uuid)) false
        else { enabledPlayers.add(player.uuid); true }
        PacketDistributor.sendToPlayer(player, BeeDebugSyncPacket(enabled))
        return enabled
    }

    fun isEnabled(player: ServerPlayer): Boolean = player.uuid in enabledPlayers

    fun clear() = enabledPlayers.clear()

    /**
     * Sends a debug message about a bee to all nearby players with debug enabled.
     */
    fun log(bee: MechanicalBeeEntity, message: String) {
        if (enabledPlayers.isEmpty()) return
        val level = bee.level()
        if (level.isClientSide) return

        val server = level.server ?: return
        val beeLabel = bee.tier.id.replaceFirstChar { it.uppercase() }
        val shortId = bee.uuid.toString().substring(0, 4)
        val text = Component.literal("[$beeLabel $shortId] ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(message).withStyle(ChatFormatting.GRAY))

        for (uuid in enabledPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            if (player.level() == level && player.blockPosition().closerThan(bee.blockPosition(), 128.0)) {
                player.sendSystemMessage(text)
            }
        }
    }
}
