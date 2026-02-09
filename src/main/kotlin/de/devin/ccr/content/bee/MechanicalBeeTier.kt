package de.devin.ccr.content.bee

import de.devin.ccr.items.AllItems
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.Item

/**
 * Capabilities of a mechanical bee based on its tier.
 * @property flySpeed The base flying speed of the bee.
 * @property blockDestroySpeed The speed at which the bee destroys blocks (multiplier).
 */
data class BeeCapabilities(
    val flySpeed: Double,
    val blockDestroySpeed: Float
)

enum class MechanicalBeeTier(
    val id: String, 
    val readableName: String,
    val capabilities: BeeCapabilities,
    val item: () -> Item
) : StringRepresentable {
    ANDESITE(
        "andesite", 
        "Andesite Encased", 
        BeeCapabilities(1.0, 1.0f), 
        { AllItems.ANDESITE_BEE.get() }
    ),
    BRASS(
        "brass", 
        "Brass Encased", 
        BeeCapabilities(1.4, 2.0f), 
        { AllItems.BRASS_BEE.get() }
    ),
    STURDY(
        "sturdy", 
        "Sturdy", 
        BeeCapabilities(1.8, 4.0f), 
        { AllItems.STURDY_BEE.get() }
    );

    override fun getSerializedName(): String = id
}
