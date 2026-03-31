package de.devin.cbbees.network

import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.items.AllItems
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

class UnselectSchematicPacket {
    companion object {
        val INSTANCE = UnselectSchematicPacket()

        fun encode(pkt: UnselectSchematicPacket, buf: FriendlyByteBuf) { }
        fun decode(buf: FriendlyByteBuf) = UnselectSchematicPacket()

        fun handleServer(pkt: UnselectSchematicPacket, player: ServerPlayer) {
            val stack = player.mainHandItem
            if (!AllItems.CONSTRUCTION_PLANNER.isIn(stack)) return
            ConstructionPlannerItem.clearSchematic(stack)
        }
    }
}
