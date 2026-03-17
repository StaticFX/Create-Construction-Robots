package de.devin.cbbees.network

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

        registrar.playToServer(
            RequestHiveJobsPacket.TYPE,
            RequestHiveJobsPacket.STREAM_CODEC,
            RequestHiveJobsPacket.Companion::handle
        )
        registrar.playToClient(
            HiveJobsSyncPacket.TYPE,
            HiveJobsSyncPacket.STREAM_CODEC,
            HiveJobsSyncPacket.Companion::handle
        )
        registrar.playToServer(
            CancelJobPacket.TYPE,
            CancelJobPacket.STREAM_CODEC,
            CancelJobPacket.Companion::handle
        )
        registrar.playToClient(
            BeeDebugSyncPacket.TYPE,
            BeeDebugSyncPacket.STREAM_CODEC,
            BeeDebugSyncPacket.Companion::handle
        )
        registrar.playToServer(
            SelectSchematicPacket.TYPE,
            SelectSchematicPacket.STREAM_CODEC,
            SelectSchematicPacket.Companion::handle
        )
        registrar.playToServer(
            UnselectSchematicPacket.TYPE,
            UnselectSchematicPacket.STREAM_CODEC,
            UnselectSchematicPacket.Companion::handle
        )
        registrar.playToServer(
            InstantConstructionPacket.TYPE,
            InstantConstructionPacket.STREAM_CODEC,
            InstantConstructionPacket.Companion::handle
        )
    }
}
