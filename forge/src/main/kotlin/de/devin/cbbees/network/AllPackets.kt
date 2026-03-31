package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.Supplier
import net.minecraftforge.network.NetworkEvent

/**
 * Forge 1.20.1 packet registration using SimpleChannel.
 * Delegates encode/decode/handle to shared packet companion methods.
 */
object AllPackets {

    private const val PROTOCOL_VERSION = "1"

    val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(CreateBuzzyBeez.ID, "main"),
        { PROTOCOL_VERSION },
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    )

    private var index = 0

    fun register() {
        serverBound(StartConstructionPacket::class.java,
            StartConstructionPacket.Companion::encode, StartConstructionPacket.Companion::decode,
            StartConstructionPacket.Companion::handleServer)

        serverBound(StartDeconstructionPacket::class.java,
            StartDeconstructionPacket.Companion::encode, StartDeconstructionPacket.Companion::decode,
            StartDeconstructionPacket.Companion::handleServer)

        serverBound(StopTasksPacket::class.java,
            StopTasksPacket.Companion::encode, StopTasksPacket.Companion::decode,
            StopTasksPacket.Companion::handleServer)

        serverBound(RequestHiveJobsPacket::class.java,
            RequestHiveJobsPacket.Companion::encode, RequestHiveJobsPacket.Companion::decode,
            RequestHiveJobsPacket.Companion::handleServer)

        serverBound(CancelJobPacket::class.java,
            CancelJobPacket.Companion::encode, CancelJobPacket.Companion::decode,
            CancelJobPacket.Companion::handleServer)

        serverBound(SelectSchematicPacket::class.java,
            SelectSchematicPacket.Companion::encode, SelectSchematicPacket.Companion::decode,
            SelectSchematicPacket.Companion::handleServer)

        serverBound(UnselectSchematicPacket::class.java,
            UnselectSchematicPacket.Companion::encode, UnselectSchematicPacket.Companion::decode,
            UnselectSchematicPacket.Companion::handleServer)

        serverBound(InstantConstructionPacket::class.java,
            InstantConstructionPacket.Companion::encode, InstantConstructionPacket.Companion::decode,
            InstantConstructionPacket.Companion::handleServer)

        serverBound(PlannerUploadPacket::class.java,
            PlannerUploadPacket.Companion::encode, PlannerUploadPacket.Companion::decode,
            PlannerUploadPacket.Companion::handleServer)

        serverBound(RequestPlayerJobsPacket::class.java,
            RequestPlayerJobsPacket.Companion::encode, RequestPlayerJobsPacket.Companion::decode,
            RequestPlayerJobsPacket.Companion::handleServer)

        clientBound(HiveJobsSyncPacket::class.java,
            HiveJobsSyncPacket.Companion::encode, HiveJobsSyncPacket.Companion::decode,
            HiveJobsSyncPacket.Companion::handleClient)

        clientBound(NetworkSyncPacket::class.java,
            NetworkSyncPacket.Companion::encode, NetworkSyncPacket.Companion::decode,
            NetworkSyncPacket.Companion::handleClient)

        clientBound(BeeDebugSyncPacket::class.java,
            BeeDebugSyncPacket.Companion::encode, BeeDebugSyncPacket.Companion::decode,
            BeeDebugSyncPacket.Companion::handleClient)
    }

    private fun <T> serverBound(
        clazz: Class<T>,
        encode: (T, FriendlyByteBuf) -> Unit,
        decode: (FriendlyByteBuf) -> T,
        handle: (T, ServerPlayer) -> Unit
    ) {
        CHANNEL.registerMessage(index++, clazz, encode, decode) { pkt, ctx ->
            ctx.get().enqueueWork { ctx.get().sender?.let { handle(pkt, it) } }
            ctx.get().packetHandled = true
        }
    }

    private fun <T> clientBound(
        clazz: Class<T>,
        encode: (T, FriendlyByteBuf) -> Unit,
        decode: (FriendlyByteBuf) -> T,
        handle: (T) -> Unit
    ) {
        CHANNEL.registerMessage(index++, clazz, encode, decode) { pkt, ctx ->
            ctx.get().enqueueWork { handle(pkt) }
            ctx.get().packetHandled = true
        }
    }
}
