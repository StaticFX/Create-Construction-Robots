package de.devin.cbbees.shapes

import com.simibubi.create.AllShapes
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.shapes.VoxelShape

object AllShapes {

    val LOGISTICS_PORT = shape(1.0, 0.0, 1.0, 15.0, 6.0, 15.0).forDirectional()

    private fun shape(shape: VoxelShape): AllShapes.Builder {
        return AllShapes.Builder(shape)
    }

    private fun shape(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): AllShapes.Builder {
        return shape(cuboid(x1, y1, z1, x2, y2, z2))
    }

    private fun cuboid(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): VoxelShape {
        return Block.box(x1, y1, z1, x2, y2, z2)
    }

}