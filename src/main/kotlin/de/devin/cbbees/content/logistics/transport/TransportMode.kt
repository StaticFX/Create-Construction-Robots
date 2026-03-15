package de.devin.cbbees.content.logistics.transport

import net.minecraft.util.StringRepresentable

enum class TransportMode : StringRepresentable {
    PROVIDER, REQUESTER;

    override fun getSerializedName(): String = name.lowercase()
}
