package de.devin.ccr.content.logistics.ports

import net.minecraft.util.StringRepresentable

enum class PortState : StringRepresentable {
    VALID, INVALID, BUSY;

    override fun getSerializedName(): String = name.lowercase()
}

enum class PortType : StringRepresentable {
    INSERT, EXTRACT;

    override fun getSerializedName(): String = name.lowercase()
}