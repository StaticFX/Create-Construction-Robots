package de.devin.ccr.registry

import com.tterrag.registrate.util.entry.EntityEntry
import de.devin.ccr.CreateCCR
import de.devin.ccr.content.robots.MechanicalBeeEntity
import de.devin.ccr.content.robots.MechanicalBeeRenderer
import com.tterrag.registrate.util.nullness.NonNullFunction
import net.minecraft.world.entity.MobCategory

object AllEntityTypes {

    val MECHANICAL_BEE: EntityEntry<MechanicalBeeEntity> = CreateCCR.REGISTRATE
        .entity<MechanicalBeeEntity>("mechanical_bee", ::MechanicalBeeEntity, MobCategory.MISC)
        .properties { b -> b.sized(0.5f, 0.5f).fireImmune() }
        .attributes { MechanicalBeeEntity.createAttributes() }
        .renderer { NonNullFunction { MechanicalBeeRenderer(it) } }
        .register()

    fun register() {
        // Just to trigger class loading
    }
}
