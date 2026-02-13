package de.devin.ccr.content.logistics.ports

import net.minecraft.util.StringRepresentable

enum class PortState : StringRepresentable {
    VALID, INVALID, BUSY;

    override fun getSerializedName(): String = name.lowercase()
}