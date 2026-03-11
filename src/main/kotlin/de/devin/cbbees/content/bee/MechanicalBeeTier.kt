package de.devin.cbbees.content.bee

import de.devin.cbbees.items.AllItems
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.Item

/**
 * Capabilities of a mechanical bee based on its tier.
 * @property flySpeed The base flying speed of the bee.
 * @property blockDestroySpeed The speed at which the bee destroys blocks (multiplier).
 */
data class BeeCapabilities(
    val flySpeedModifier: Float,
    val blockDestroySpeed: Float,
    val workRange: Double,
    val inventorySize: Int,
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
        BeeCapabilities(1.0f, 1.0f, 2.0, 1),
        { AllItems.ANDESITE_BEE.get() }
    ),
    BRASS(
        "brass",
        "Brass Encased",
        BeeCapabilities(2.0f, 2.0f, 2.5, 1),
        { AllItems.BRASS_BEE.get() }
    ),
    STURDY(
        "sturdy",
        "Sturdy",
        BeeCapabilities(3.0f, 4.0f, 3.0, 1),
        { AllItems.STURDY_BEE.get() }
    );

    override fun getSerializedName(): String = id
}
