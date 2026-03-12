package de.devin.cbbees.registry

import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.core.registries.Registries
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import java.util.function.Supplier

object AllEffects {

    private val EFFECTS: DeferredRegister<MobEffect> =
        DeferredRegister.create(Registries.MOB_EFFECT, CreateBuzzyBeez.ID)

    /**
     * Applied to bees when spawned from a hive. Amplifier scales with the hive's
     * RPM-based speed multiplier. Boosts both flying and ground movement speed.
     */
    val HIVE_SPEED: DeferredHolder<MobEffect, MobEffect> = EFFECTS.register("hive_speed", Supplier {
        object : MobEffect(MobEffectCategory.BENEFICIAL, 0xF0C43F) {}
            .addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                CreateBuzzyBeez.asResource("effect.hive_speed.movement"),
                0.20,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            )
            .addAttributeModifier(
                Attributes.FLYING_SPEED,
                CreateBuzzyBeez.asResource("effect.hive_speed.flying"),
                0.20,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            )
    })

    fun register() {
        EFFECTS.register(MOD_BUS)
    }
}
