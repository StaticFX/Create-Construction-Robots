package de.devin.ccr.content.beehive

import com.mojang.blaze3d.systems.RenderSystem
import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen
import com.simibubi.create.foundation.gui.widget.IconButton
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class MechanicalBeehiveScreen(
    menu: MechanicalBeehiveMenu,
    playerInventory: Inventory,
    title: Component
) : AbstractSimiContainerScreen<MechanicalBeehiveMenu>(menu, playerInventory, title) {

    private lateinit var addInstructionButton: IconButton
    private val instructionButtons = mutableListOf<InstructionWidget>()

    override fun init() {
        setWindowSize(
            AllGuiTextures.FILTER.width,
            AllGuiTextures.FILTER.height + AllGuiTextures.PLAYER_INVENTORY.height + 20
        )
        super.init()

        addInstructionButton = IconButton(leftPos + imageWidth - 25, topPos + 10, AllIcons.I_ADD)
        addInstructionButton.withCallback<IconButton>(Runnable {
            // Open a sub-menu to select instruction type? 
            // For now just add a default fertilize instruction
            menu.content.instructions.add(BeeInstruction())
            menu.content.sendData()
            refreshInstructions()
        })
        addRenderableWidget(addInstructionButton)

        refreshInstructions()
    }

    private fun refreshInstructions() {
        // Clear old widgets
        instructionButtons.forEach { removeWidget(it) }
        instructionButtons.clear()

        val startX = leftPos + 80
        val startY = topPos + 15

        menu.content.instructions.forEachIndexed { index, instruction ->
            val widget = InstructionWidget(startX, startY + index * 22, instruction, index) {
                menu.content.instructions.removeAt(index)
                menu.content.sendData()
                refreshInstructions()
            }
            instructionButtons.add(widget)
            addRenderableWidget(widget)
        }
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos
        val y = topPos

        AllGuiTextures.FILTER.render(guiGraphics, x, y)
        renderPlayerInventory(
            guiGraphics,
            x + (imageWidth - AllGuiTextures.PLAYER_INVENTORY.width) / 2,
            y + AllGuiTextures.FILTER.height + 10
        )

        // Slots
        for (i in 0 until 18) {
            val slot = menu.slots[i]
            AllGuiTextures.TOOLBELT_SLOT.render(guiGraphics, x + slot.x - 3, y + slot.y - 3)
        }

        // Title
        guiGraphics.drawString(font, title, x + 15, y + 4, 0x592424, false)
    }

    inner class InstructionWidget(
        x: Int,
        y: Int,
        val instruction: BeeInstruction,
        val index: Int,
        val onDelete: () -> Unit
    ) : IconButton(x, y, AllIcons.I_TRASH) {
        private val beeUp = IconButton(x + 150, y, AllIcons.I_PRIORITY_HIGH)
        private val beeDown = IconButton(x + 130, y, AllIcons.I_PRIORITY_LOW)
        private val rangeUp = IconButton(x + 200, y, AllIcons.I_PRIORITY_HIGH)
        private val rangeDown = IconButton(x + 180, y, AllIcons.I_PRIORITY_LOW)

        init {
            withCallback<IconButton>(Runnable { onDelete() })

            beeUp.withCallback<IconButton>(Runnable {
                instruction.beeCount = (instruction.beeCount + 1).coerceAtMost(32)
                menu.content.sendData()
            })
            beeDown.withCallback<IconButton>(Runnable {
                instruction.beeCount = (instruction.beeCount - 1).coerceAtLeast(1)
                menu.content.sendData()
            })
            rangeUp.withCallback<IconButton>(Runnable {
                instruction.range = (instruction.range + 1).coerceAtMost(64)
                menu.content.sendData()
            })
            rangeDown.withCallback<IconButton>(Runnable {
                instruction.range = (instruction.range - 1).coerceAtLeast(1)
                menu.content.sendData()
            })
        }

        override fun setX(x: Int) {
            super.setX(x)
            beeUp.setX(x + 150)
            beeDown.setX(x + 130)
            rangeUp.setX(x + 200)
            rangeDown.setX(x + 180)
        }

        override fun setY(y: Int) {
            super.setY(y)
            beeUp.setY(y)
            beeDown.setY(y)
            rangeUp.setY(y)
            rangeDown.setY(y)
        }

        override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTicks)
            beeUp.render(guiGraphics, mouseX, mouseY, partialTicks)
            beeDown.render(guiGraphics, mouseX, mouseY, partialTicks)
            rangeUp.render(guiGraphics, mouseX, mouseY, partialTicks)
            rangeDown.render(guiGraphics, mouseX, mouseY, partialTicks)

            guiGraphics.drawString(font, "${instruction.type.displayName.string}", x + 25, y + 5, 0xEEEEEE, false)
            guiGraphics.drawString(font, "${instruction.beeCount}", x + 142, y + 5, 0xFFFFFF, false)
            guiGraphics.drawString(font, "${instruction.range}", x + 192, y + 5, 0xFFFFFF, false)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (beeUp.mouseClicked(mouseX, mouseY, button)) return true
            if (beeDown.mouseClicked(mouseX, mouseY, button)) return true
            if (rangeUp.mouseClicked(mouseX, mouseY, button)) return true
            if (rangeDown.mouseClicked(mouseX, mouseY, button)) return true
            return super.mouseClicked(mouseX, mouseY, button)
        }
    }
}
