package de.devin.cbbees.content.bee.debug

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object BeeDebugCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("cbbees")
                .then(
                    Commands.literal("debug")
                        .executes { ctx ->
                            val player = ctx.source.playerOrException
                            val enabled = BeeDebug.toggle(player)
                            val msg = if (enabled)
                                Component.literal("Bee debug mode enabled").withStyle(ChatFormatting.GREEN)
                            else
                                Component.literal("Bee debug mode disabled").withStyle(ChatFormatting.RED)
                            ctx.source.sendSuccess({ msg }, false)
                            1
                        }
                )
        )
    }
}
