package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.registration.PayloadRegistrar

/**
 * NeoForge 1.21.1 packet registration.
 * Wraps shared plain packet classes with [CustomPacketPayload] via [Payload].
 */
object AllPackets {

    /** Wrapper that adapts any packet class to NeoForge's CustomPacketPayload system. */
    class Payload<T : Any>(
        val packet: T,
        private val payloadType: CustomPacketPayload.Type<Payload<T>>
    ) : CustomPacketPayload {
        override fun type() = payloadType
    }

    private val types = mutableMapOf<Class<*>, CustomPacketPayload.Type<*>>()

    /** Wraps a shared packet for sending via NeoForge's PacketDistributor. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> wrapPayload(packet: T): CustomPacketPayload {
        val type = types[packet::class.java] as? CustomPacketPayload.Type<Payload<T>>
            ?: error("Unregistered packet type: ${packet::class.java.simpleName}")
        return Payload(packet, type)
    }

    fun register(event: RegisterPayloadHandlersEvent) {
        val r = event.registrar("ccr")

        serverBound<StartConstructionPacket>(r, "start_construction",
            StartConstructionPacket.Companion::encode, StartConstructionPacket.Companion::decode,
            StartConstructionPacket.Companion::handleServer)

        serverBound<StartDeconstructionPacket>(r, "start_deconstruction",
            StartDeconstructionPacket.Companion::encode, StartDeconstructionPacket.Companion::decode,
            StartDeconstructionPacket.Companion::handleServer)

        serverBound<StopTasksPacket>(r, "stop_tasks",
            StopTasksPacket.Companion::encode, StopTasksPacket.Companion::decode,
            StopTasksPacket.Companion::handleServer)

        serverBound<RequestHiveJobsPacket>(r, "hive_jobs_req",
            RequestHiveJobsPacket.Companion::encode, RequestHiveJobsPacket.Companion::decode,
            RequestHiveJobsPacket.Companion::handleServer)

        serverBound<CancelJobPacket>(r, "cancel_job",
            CancelJobPacket.Companion::encode, CancelJobPacket.Companion::decode,
            CancelJobPacket.Companion::handleServer)

        serverBound<SelectSchematicPacket>(r, "select_schematic",
            SelectSchematicPacket.Companion::encode, SelectSchematicPacket.Companion::decode,
            SelectSchematicPacket.Companion::handleServer)

        serverBound<UnselectSchematicPacket>(r, "unselect_schematic",
            UnselectSchematicPacket.Companion::encode, UnselectSchematicPacket.Companion::decode,
            UnselectSchematicPacket.Companion::handleServer)

        serverBound<InstantConstructionPacket>(r, "instant_construction",
            InstantConstructionPacket.Companion::encode, InstantConstructionPacket.Companion::decode,
            InstantConstructionPacket.Companion::handleServer)

        serverBound<PlannerUploadPacket>(r, "planner_upload",
            PlannerUploadPacket.Companion::encode, PlannerUploadPacket.Companion::decode,
            PlannerUploadPacket.Companion::handleServer)

        serverBound<RequestPlayerJobsPacket>(r, "player_jobs_req",
            RequestPlayerJobsPacket.Companion::encode, RequestPlayerJobsPacket.Companion::decode,
            RequestPlayerJobsPacket.Companion::handleServer)

        clientBound<HiveJobsSyncPacket>(r, "hive_jobs_sync",
            HiveJobsSyncPacket.Companion::encode, HiveJobsSyncPacket.Companion::decode,
            HiveJobsSyncPacket.Companion::handleClient)

        clientBound<NetworkSyncPacket>(r, "network_sync",
            NetworkSyncPacket.Companion::encode, NetworkSyncPacket.Companion::decode,
            NetworkSyncPacket.Companion::handleClient)

        clientBound<BeeDebugSyncPacket>(r, "bee_debug_sync",
            BeeDebugSyncPacket.Companion::encode, BeeDebugSyncPacket.Companion::decode,
            BeeDebugSyncPacket.Companion::handleClient)
    }

    private inline fun <reified T : Any> serverBound(
        r: PayloadRegistrar, name: String,
        crossinline encode: (T, FriendlyByteBuf) -> Unit,
        crossinline decode: (FriendlyByteBuf) -> T,
        crossinline handle: (T, ServerPlayer) -> Unit
    ) {
        val type = CustomPacketPayload.Type<Payload<T>>(CreateBuzzyBeez.asResource(name))
        types[T::class.java] = type
        r.playToServer(type, StreamCodec.of(
            { buf, w -> encode(w.packet, buf) },
            { buf -> Payload(decode(buf), type) }
        )) { payload, ctx ->
            ctx.enqueueWork { (ctx.player() as? ServerPlayer)?.let { handle(payload.packet, it) } }
        }
    }

    private inline fun <reified T : Any> clientBound(
        r: PayloadRegistrar, name: String,
        crossinline encode: (T, FriendlyByteBuf) -> Unit,
        crossinline decode: (FriendlyByteBuf) -> T,
        crossinline handle: (T) -> Unit
    ) {
        val type = CustomPacketPayload.Type<Payload<T>>(CreateBuzzyBeez.asResource(name))
        types[T::class.java] = type
        r.playToClient(type, StreamCodec.of(
            { buf, w -> encode(w.packet, buf) },
            { buf -> Payload(decode(buf), type) }
        )) { payload, ctx ->
            ctx.enqueueWork { handle(payload.packet) }
        }
    }
}
