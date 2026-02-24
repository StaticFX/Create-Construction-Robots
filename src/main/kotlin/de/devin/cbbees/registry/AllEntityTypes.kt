package de.devin.cbbees.registry

import com.tterrag.registrate.util.entry.EntityEntry
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.MechanicalBeeRenderer
import com.tterrag.registrate.util.nullness.NonNullFunction
import net.minecraft.world.entity.MobCategory

object AllEntityTypes {

    val MECHANICAL_BEE: EntityEntry<MechanicalBeeEntity> = CreateBuzzyBeez.REGISTRATE
        .entity("mechanical_bee", ::MechanicalBeeEntity, MobCategory.MISC)
        .properties { b -> b.sized(0.5f, 0.5f).fireImmune() }
        .attributes { MechanicalBeeEntity.createAttributes() }
        .renderer { NonNullFunction { MechanicalBeeRenderer(it) } }
        .register()

    fun register() {
        // Just to trigger class loading
    }
}
