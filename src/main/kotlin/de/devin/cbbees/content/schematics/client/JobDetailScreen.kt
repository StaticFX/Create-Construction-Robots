package de.devin.cbbees.content.schematics.client

import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import de.devin.cbbees.content.beehive.client.ClientJobCache
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.network.CancelJobPacket
import de.devin.cbbees.network.RequestPlayerJobsPacket
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import de.devin.cbbees.network.NetworkHelper
import java.util.UUID

/**
 * A small overlay screen showing details for a single construction job.
 * Opened by clicking on a job's bounding box outline in the world.
 */
class JobDetailScreen(private val jobId: UUID) : Screen(Component.translatable("gui.cbbees.job_detail.title")) {

    companion object {
        private const val PANEL_WIDTH = 220
        private const val PANEL_HEIGHT = 115
    }

    private var job: ClientJobInfo? = null
    private var refreshTicks = 0

    override fun init() {
        super.init()
        NetworkHelper.sendToServer(RequestPlayerJobsPacket())
        refreshJob()
    }

    private fun refreshJob() {
        job = ConstructionRenderer.getJobInfo(jobId)
        clearWidgets()

        val x = (width - PANEL_WIDTH) / 2
        val y = (height - PANEL_HEIGHT) / 2

        if (job != null) {
            val btnW = 80
            addRenderableWidget(Button.builder(Component.translatable("gui.cbbees.job_detail.cancel")) {
                NetworkHelper.sendToServer(CancelJobPacket(jobId))
                onClose()
            }.bounds(x + PANEL_WIDTH / 2 - btnW / 2, y + PANEL_HEIGHT - 26, btnW, 20).build())
        }
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // No full-screen dimming — this is a small overlay panel
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val x = (width - PANEL_WIDTH) / 2
        val y = (height - PANEL_HEIGHT) / 2

        // Panel background
        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF707070.toInt())
        guiGraphics.fill(x + 2, y + 2, x + PANEL_WIDTH - 2, y + PANEL_HEIGHT - 2, 0xFFC6C6C6.toInt())

        // Terminal background
        guiGraphics.fill(x + 6, y + 6, x + PANEL_WIDTH - 6, y + PANEL_HEIGHT - 6, 0xCC000000.toInt())

        val j = job
        if (j == null) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.cbbees.job_detail.not_found"),
                x + PANEL_WIDTH / 2,
                y + PANEL_HEIGHT / 2 - 4,
                0x909090
            )
        } else {
            val textX = x + 12
            var textY = y + 12

            // Job name
            guiGraphics.drawString(font, "> Job ${j.name}", textX, textY, 0xFF44FF44.toInt(), false)
            textY += 14

            // Progress bar
            val pct = if (j.total == 0) 1f else j.completed.toFloat() / j.total
            val barW = PANEL_WIDTH - 90
            val barX = textX + 4
            guiGraphics.fill(barX - 1, textY - 1, barX + barW + 1, textY + 7, 0xFFAAAAAA.toInt())
            guiGraphics.fill(barX, textY, barX + barW, textY + 6, 0xFF000000.toInt())
            val filled = (barW * pct).toInt()
            guiGraphics.fill(barX, textY, barX + filled, textY + 6, 0xFF00FF00.toInt())

            val progressText = "[${j.completed}/${j.total}] ${(pct * 100).toInt()}%"
            guiGraphics.drawString(font, progressText, barX + barW + 6, textY - 1, 0xFF44FF44.toInt(), false)
            textY += 14

            // Status
            val statusColor = if (j.reason != null) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
            val statusText = if (j.reason != null) "! STUCK: ${j.reason}" else "  Status: ${j.status}"
            guiGraphics.drawString(font, statusText, textX, textY, statusColor, false)
            textY += 14

            // Batches info
            val activeBatches = j.batches.count { it.status != "COMPLETED" }
            guiGraphics.drawString(font, "  Batches: ${activeBatches} active", textX, textY, 0xFFAAAAAA.toInt(), false)
        }
    }

    override fun tick() {
        super.tick()
        refreshTicks++
        if (refreshTicks >= 10) {
            refreshTicks = 0
            NetworkHelper.sendToServer(RequestPlayerJobsPacket())
        }
        val latest = ConstructionRenderer.getJobInfo(jobId)
        if (latest != job) refreshJob()

        // Close if job was completed/cancelled
        if (latest == null && job != null) {
            onClose()
        }
    }

    override fun isPauseScreen(): Boolean = false
}
