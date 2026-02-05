package de.devin.ccr.network

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object AllPackets {
    
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("ccr")
        
        // Construction packet - starts building from a schematic
        registrar.playToServer(
            StartConstructionPacket.TYPE,
            StartConstructionPacket.STREAM_CODEC,
            StartConstructionPacket.Companion::handle
        )
        
        // Deconstruction packet - removes blocks within schematic bounds
        registrar.playToServer(
            StartDeconstructionPacket.TYPE,
            StartDeconstructionPacket.STREAM_CODEC,
            StartDeconstructionPacket.Companion::handle
        )

        // Stop packet - cancels all active robot tasks
        registrar.playToServer(
            StopTasksPacket.TYPE,
            StopTasksPacket.STREAM_CODEC,
            StopTasksPacket.Companion::handle
        )
        
        // Task progress sync packet - sends task progress from server to client
        registrar.playToClient(
            TaskProgressSyncPacket.TYPE,
            TaskProgressSyncPacket.STREAM_CODEC,
            TaskProgressSyncPacket.Companion::handle
        )
    }
}
