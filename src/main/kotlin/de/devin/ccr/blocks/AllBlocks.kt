package de.devin.ccr.blocks

import com.simibubi.create.content.decoration.encasing.CasingBlock
import com.simibubi.create.foundation.data.BuilderTransformers
import com.tterrag.registrate.util.entry.BlockEntry
import de.devin.ccr.CreateCCR
import de.devin.ccr.spriteshifts.AllSpriteShifts
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor


// THIS LINE IS REQUIRED FOR USING PROPERTY DELEGATES

object AllBlocks {



    val TUNGSTEN_CASING: BlockEntry<CasingBlock> = CreateCCR.REGISTRATE.block<CasingBlock>(
        "tungsten_casing", ::CasingBlock
    )
        .properties { p: BlockBehaviour.Properties -> p.mapColor(MapColor.PODZOL) }
        .transform(BuilderTransformers.casing { AllSpriteShifts.TUNGSTEN_CASING })
        .register()

}
