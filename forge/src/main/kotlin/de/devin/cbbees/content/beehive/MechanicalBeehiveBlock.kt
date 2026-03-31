package de.devin.cbbees.content.beehive

import com.simibubi.create.content.kinetics.base.KineticBlock
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel
import com.simibubi.create.foundation.block.IBE
import de.devin.cbbees.registry.AllBlockEntityTypes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.network.NetworkHooks

/**
 * Forge 1.20.1 override: uses `use` instead of `useWithoutItem`,
 * and `NetworkHooks.openScreen` instead of `player.openMenu` with lambda.
 */
class MechanicalBeehiveBlock(properties: Properties) : KineticBlock(properties), IBE<MechanicalBeehiveBlockEntity>,
    ICogWheel {

    override fun hasShaftTowards(world: LevelReader, pos: BlockPos, state: BlockState, face: Direction): Boolean {
        return false;
    }

    override fun getRotationAxis(state: BlockState): Axis {
        return Axis.Y
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS

        if (player is ServerPlayer) {
            withBlockEntityDo(level, pos) { be ->
                val menuProvider = object : net.minecraft.world.MenuProvider {
                    override fun getDisplayName() = Component.translatable("block.cbbees.mechanical_beehive")
                    override fun createMenu(id: Int, inv: Inventory, player: Player) =
                        MechanicalBeehiveMenu(id, inv, be)
                }
                NetworkHooks.openScreen(player, menuProvider) { buf ->
                    buf.writeBlockPos(pos)
                }
            }
        }

        return InteractionResult.CONSUME
    }

    override fun getBlockEntityType(): BlockEntityType<out MechanicalBeehiveBlockEntity> {
        return AllBlockEntityTypes.MECHANICAL_BEEHIVE.get()
    }

    override fun getBlockEntityClass(): Class<MechanicalBeehiveBlockEntity> {
        return MechanicalBeehiveBlockEntity::class.java
    }
}
