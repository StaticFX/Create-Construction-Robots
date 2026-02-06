package de.devin.ccr.content.beehive

import com.simibubi.create.foundation.block.IBE
import com.simibubi.create.content.kinetics.base.KineticBlock
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel
import de.devin.ccr.registry.AllBlockEntityTypes
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class MechanicalBeehiveBlock(properties: Properties) : KineticBlock(properties), IBE<MechanicalBeehiveBlockEntity>, ICogWheel {
    
    override fun hasShaftTowards(world: LevelReader, pos: BlockPos, state: BlockState, face: Direction): Boolean {
        return face == Direction.DOWN
    }

    override fun getRotationAxis(state: BlockState): Axis {
        return Axis.Y
    }
    
    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        
        withBlockEntityDo(level, pos) { be ->
            val menuProvider = object : net.minecraft.world.MenuProvider {
                override fun getDisplayName() = Component.translatable("block.ccr.mechanical_beehive")
                override fun createMenu(id: Int, inv: Inventory, player: Player) = 
                    MechanicalBeehiveMenu(id, inv, be)
            }
            player.openMenu(menuProvider) { buf ->
                buf.writeBlockPos(pos)
            }
        }
        
        return InteractionResult.CONSUME
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: Item.TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)
        tooltipComponents.add(Component.translatable("tooltip.ccr.mechanical_beehive.stress")
            .withStyle(ChatFormatting.GOLD))
    }

    override fun getBlockEntityType(): BlockEntityType<out MechanicalBeehiveBlockEntity> {
        return AllBlockEntityTypes.MECHANICAL_BEEHIVE.get()
    }

    override fun getBlockEntityClass(): Class<MechanicalBeehiveBlockEntity> {
        return MechanicalBeehiveBlockEntity::class.java
    }
}
