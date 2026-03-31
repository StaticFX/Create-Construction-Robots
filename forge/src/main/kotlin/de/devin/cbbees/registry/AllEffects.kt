package de.devin.cbbees.registry

import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.core.registries.Registries
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject
import thedarkcolour.kotlinforforge.forge.MOD_BUS
import java.util.UUID

/**
 * Forge 1.20.1 — uses RegistryObject and UUID-based attribute modifiers.
 */
object AllEffects {

    private val EFFECTS: DeferredRegister<MobEffect> =
        DeferredRegister.create(Registries.MOB_EFFECT, CreateBuzzyBeez.ID)

    val HIVE_SPEED: RegistryObject<MobEffect> = EFFECTS.register("hive_speed") {
        object : MobEffect(MobEffectCategory.BENEFICIAL, 0xF0C43F) {}
            .addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                UUID.nameUUIDFromBytes("cbbees:effect.hive_speed.movement".toByteArray()).toString(),
                0.20,
                AttributeModifier.Operation.MULTIPLY_BASE
            )
            .addAttributeModifier(
                Attributes.FLYING_SPEED,
                UUID.nameUUIDFromBytes("cbbees:effect.hive_speed.flying".toByteArray()).toString(),
                0.20,
                AttributeModifier.Operation.MULTIPLY_BASE
            )
    }

    fun register() {
        EFFECTS.register(MOD_BUS)
    }
}
