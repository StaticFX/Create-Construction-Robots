package de.devin.ccr.spriteshifts

import com.simibubi.create.foundation.block.connected.AllCTTypes
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry
import com.simibubi.create.foundation.block.connected.CTSpriteShifter
import com.simibubi.create.foundation.block.connected.CTType
import de.devin.ccr.CreateCCR
import net.createmod.catnip.render.SpriteShiftEntry
import net.createmod.catnip.render.SpriteShifter

object AllSpriteShifts {

    val TUNGSTEN_CASING = omni("tungsten_casing")


        //

    fun omni(name: String): CTSpriteShiftEntry {
        return getCT(AllCTTypes.OMNIDIRECTIONAL, name)
    }

    fun get(originalLocation: String, targetLocation: String): SpriteShiftEntry {
        return SpriteShifter.get(CreateCCR.asResource(originalLocation), CreateCCR.asResource(targetLocation))
    }

    private fun getCT(type: CTType, blockTextureName: String, connectedTextureName: String): CTSpriteShiftEntry {
        return CTSpriteShifter.getCT(
            type, CreateCCR.asResource("block/$blockTextureName"),
            CreateCCR.asResource("block/" + connectedTextureName + "_connected")
        )
    }

    private fun getCT(type: CTType, blockTextureName: String): CTSpriteShiftEntry {
        return getCT(type, blockTextureName, blockTextureName)
    }

}