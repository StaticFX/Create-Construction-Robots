package de.devin.ccr.registry

import com.tterrag.registrate.util.entry.EntityEntry
import de.devin.ccr.CreateCCR
import de.devin.ccr.content.robots.ConstructorRobotEntity
import de.devin.ccr.content.robots.ConstructorRobotRenderer
import com.tterrag.registrate.util.nullness.NonNullFunction
import net.minecraft.world.entity.MobCategory

object AllEntityTypes {

    val CONSTRUCTOR_ROBOT: EntityEntry<ConstructorRobotEntity> = CreateCCR.REGISTRATE
        .entity<ConstructorRobotEntity>("constructor_robot", ::ConstructorRobotEntity, MobCategory.MISC)
        .properties { b -> b.sized(0.5f, 0.5f).fireImmune() }
        .attributes { ConstructorRobotEntity.createAttributes() }
        .renderer { NonNullFunction { ConstructorRobotRenderer(it) } }
        .register()

    fun register() {
        // Just to trigger class loading
    }
}
