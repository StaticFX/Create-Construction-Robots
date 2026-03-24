package de.devin.cbbees.content.backpack

import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen
import com.simibubi.create.foundation.gui.widget.IconButton
import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

/**
 * Screen/GUI for the Constructor Backpack.
 *
 * Uses a custom 200x102 background texture with Create's PLAYER_INVENTORY below.
 *
 * Displays:
 * - 2 bee slots (stacked vertically)
 * - 4 upgrade slots (horizontal row)
 * - Honey fuel gauge
 * - Player inventory
 */
class BeehiveScreen(
    menu: BeehiveContainer,
    playerInventory: Inventory,
    title: Component
) : AbstractSimiContainerScreen<BeehiveContainer>(menu, playerInventory, title) {

    companion object {
        val PLAYER_INV = AllGuiTextures.PLAYER_INVENTORY

        val TEXTURE: ResourceLocation = CreateBuzzyBeez.asResource("textures/gui/portable_beehive.png")
        const val TEXTURE_SIZE = 256

        // Custom background dimensions
        const val BG_WIDTH = 200
        const val BG_HEIGHT = 102

        // Filled fuel bar on the atlas
        const val FUEL_U = 206
        const val FUEL_V = 1
        const val FUEL_W = 13
        const val FUEL_H = 42

        // Position of the fuel gauge within the GUI (where the empty outline is)
        const val FUEL_X = 71
        const val FUEL_Y = 24
    }

    private lateinit var confirmButton: IconButton
    private lateinit var jobsButton: IconButton

    init {
        // Set window size to accommodate custom background + player inventory
        // Custom background is 200x102, PLAYER_INVENTORY is 176x108
        // Add 4px gap between them
        imageWidth = maxOf(BG_WIDTH, PLAYER_INV.width)
        imageHeight = BG_HEIGHT + 4 + PLAYER_INV.height
    }

    override fun init() {
        super.init()

        confirmButton = IconButton(leftPos + 167, topPos + 78, AllIcons.I_CONFIRM)
        confirmButton.withCallback<IconButton>(Runnable {
            minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, 0)
        })
        addRenderableWidget(confirmButton)

        jobsButton = IconButton(leftPos + 185, topPos + 78, AllIcons.I_TOOLBOX)
        jobsButton.setToolTip(Component.translatable("gui.cbbees.portable_beehive.jobs"))
        jobsButton.withCallback<IconButton>(Runnable {
            Minecraft.getInstance().setScreen(PortableBeehiveJobScreen())
        })
        addRenderableWidget(jobsButton)
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos
        val y = topPos

        // Draw the custom background texture (200x102 at 0,0 on the atlas)
        guiGraphics.blit(
            TEXTURE,
            x, y,
            0f, 0f,
            BG_WIDTH, BG_HEIGHT,
            TEXTURE_SIZE, TEXTURE_SIZE
        )

        // Draw the player inventory background below
        val invX = leftPos + (imageWidth - PLAYER_INV.width) / 2
        val invY = topPos + BG_HEIGHT + 3
        renderPlayerInventory(guiGraphics, invX, invY)

        // Fuel gauge — render filled portion from bottom up
        val fuel = menu.fuelData.get(0)
        val maxFuel = menu.fuelData.get(1)
        if (maxFuel > 0 && fuel > 0) {
            val fillHeight = (fuel * FUEL_H / maxFuel).coerceAtMost(FUEL_H)
            val emptyHeight = FUEL_H - fillHeight
            guiGraphics.blit(
                TEXTURE,
                x + FUEL_X, y + FUEL_Y + emptyHeight,
                FUEL_U.toFloat(), (FUEL_V + emptyHeight).toFloat(),
                FUEL_W, fillHeight,
                TEXTURE_SIZE, TEXTURE_SIZE
            )
        }
    }

}
