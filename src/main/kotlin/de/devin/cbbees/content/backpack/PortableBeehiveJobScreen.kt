package de.devin.cbbees.content.backpack

import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import de.devin.cbbees.content.beehive.client.ClientJobCache
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.content.domain.job.HiveSnapshot
import de.devin.cbbees.network.CancelJobPacket
import de.devin.cbbees.network.RequestPlayerJobsPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Standalone screen for managing construction tasks from the portable beehive.
 * Shows all jobs owned by the current player with progress bars and cancel buttons.
 *
 * Uses [BlockPos.ZERO] as the cache key for player-scoped job snapshots.
 */
class PortableBeehiveJobScreen : Screen(Component.translatable("gui.cbbees.portable_beehive.jobs")) {

    companion object {
        private const val PANEL_WIDTH = 260
        private const val PANEL_HEIGHT = 180
        private const val ROW_HEIGHT = 28
        private const val HEADER_HEIGHT = 40
        private const val PADDING = 12
    }

    private var snapshot: HiveSnapshot? = null
    private var refreshTicks = 0
    private var scrollOffset = 0
    private val jobRows = mutableListOf<JobRowWidget>()

    override fun init() {
        super.init()
        PacketDistributor.sendToServer(RequestPlayerJobsPacket())
        rebuildFromCache()
    }

    private fun rebuildFromCache() {
        snapshot = ClientJobCache.get(BlockPos.ZERO)
        jobRows.clear()
        clearWidgets()

        val x = (width - PANEL_WIDTH) / 2
        val y = (height - PANEL_HEIGHT) / 2

        // Refresh button
        addRenderableWidget(IconButton(x + PANEL_WIDTH - 25, y + 4, AllIcons.I_REFRESH).apply {
            setToolTip(Component.translatable("gui.cbbees.portable_beehive.refresh"))
            withCallback<IconButton>(Runnable {
                PacketDistributor.sendToServer(RequestPlayerJobsPacket())
            })
        })

        val snap = snapshot ?: return
        val jobs = snap.jobs
        val contentY = y + HEADER_HEIGHT
        val maxVisibleRows = (PANEL_HEIGHT - HEADER_HEIGHT - 10) / ROW_HEIGHT

        val visibleJobs = jobs.drop(scrollOffset).take(maxVisibleRows)
        visibleJobs.forEachIndexed { i, job ->
            val rowY = contentY + i * ROW_HEIGHT
            val row = JobRowWidget(x + PADDING, rowY, PANEL_WIDTH - PADDING * 2, job)
            jobRows.add(row)
            addRenderableWidget(row.cancelBtn)
        }
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(guiGraphics)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val x = (width - PANEL_WIDTH) / 2
        val y = (height - PANEL_HEIGHT) / 2

        // Panel background
        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF707070.toInt())
        guiGraphics.fill(x + 2, y + 2, x + PANEL_WIDTH - 2, y + PANEL_HEIGHT - 2, 0xFFC6C6C6.toInt())

        // Title
        guiGraphics.drawString(font, title, x + 10, y + 8, 0x592424, false)

        // Job count
        val jobCount = snapshot?.jobs?.size ?: 0
        val countText = Component.translatable("gui.cbbees.portable_beehive.job_count", jobCount)
        guiGraphics.drawString(font, countText, x + 10, y + 22, 0x404040, false)

        // Terminal area background
        val termX = x + 8
        val termY = y + HEADER_HEIGHT - 4
        val termW = PANEL_WIDTH - 16
        val termH = PANEL_HEIGHT - HEADER_HEIGHT - 4
        guiGraphics.fill(termX, termY, termX + termW, termY + termH, 0xCC000000.toInt())

        if (snapshot?.jobs?.isEmpty() != false) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.cbbees.portable_beehive.no_jobs"),
                x + PANEL_WIDTH / 2,
                y + PANEL_HEIGHT / 2 - 4,
                0x909090
            )
        } else {
            // Render job rows
            for (row in jobRows) {
                row.render(guiGraphics, mouseX, mouseY, partialTick)
            }
        }

        // Render buttons on top
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun tick() {
        super.tick()
        refreshTicks++
        if (refreshTicks >= 10) {
            refreshTicks = 0
            PacketDistributor.sendToServer(RequestPlayerJobsPacket())
        }
        val latest = ClientJobCache.get(BlockPos.ZERO)
        if (latest != snapshot) rebuildFromCache()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val jobs = snapshot?.jobs ?: return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val maxVisibleRows = (PANEL_HEIGHT - HEADER_HEIGHT - 10) / ROW_HEIGHT
        val maxOffset = maxOf(0, jobs.size - maxVisibleRows)

        scrollOffset = if (scrollY < 0) {
            (scrollOffset + 1).coerceAtMost(maxOffset)
        } else {
            (scrollOffset - 1).coerceAtLeast(0)
        }
        rebuildFromCache()
        return true
    }

    override fun isPauseScreen(): Boolean = false

    private inner class JobRowWidget(
        val x: Int,
        val y: Int,
        val w: Int,
        val job: ClientJobInfo
    ) {
        val cancelBtn = IconButton(x + w - 20, y, AllIcons.I_TRASH).apply {
            setToolTip(Component.translatable("gui.cbbees.portable_beehive.cancel_job"))
            withCallback<IconButton>(Runnable {
                PacketDistributor.sendToServer(CancelJobPacket(job.jobId))
            })
        }

        fun render(gg: GuiGraphics, mouseX: Int, mouseY: Int, pt: Float) {
            val font = Minecraft.getInstance().font

            // Job name
            val name = "> Job ${job.name}"
            gg.drawString(font, name, x + 4, y, 0xFF44FF44.toInt(), false)

            // Progress bar
            val pct = if (job.total == 0) 1f else job.completed.toFloat() / job.total
            val barW = w - 80
            val barX = x + 8
            val barY = y + 10

            gg.fill(barX - 1, barY - 1, barX + barW + 1, barY + 5, 0xFFAAAAAA.toInt())
            gg.fill(barX, barY, barX + barW, barY + 4, 0xFF000000.toInt())
            val filled = (barW * pct).toInt()
            gg.fill(barX, barY, barX + filled, barY + 4, 0xFF00FF00.toInt())

            // Progress text
            val progressText = "[${job.completed}/${job.total}]"
            gg.drawString(font, progressText, barX + barW + 6, barY - 2, 0xFF44FF44.toInt(), false)

            // Status line
            val statusColor = if (job.reason != null) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
            val statusText = if (job.reason != null) "! STUCK: ${job.reason}" else "  Status: ${job.status}"
            gg.drawString(font, statusText, x + 4, y + 17, statusColor, false)
        }
    }
}
