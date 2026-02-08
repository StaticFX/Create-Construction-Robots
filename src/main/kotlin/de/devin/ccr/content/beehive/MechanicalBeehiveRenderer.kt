package de.devin.ccr.content.beehive

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.world.level.block.state.BlockState

class MechanicalBeehiveRenderer(context: BlockEntityRendererProvider.Context) : 
    KineticBlockEntityRenderer<MechanicalBeehiveBlockEntity>(context) {

    override fun getRotatedModel(be: MechanicalBeehiveBlockEntity, state: BlockState): SuperByteBuffer {
        return CachedBuffers.partial(AllPartialModels.SHAFTLESS_COGWHEEL, state)
    }

}
