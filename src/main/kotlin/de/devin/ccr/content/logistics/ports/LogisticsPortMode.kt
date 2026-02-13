package de.devin.ccr.content.logistics.ports

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions
import de.devin.ccr.icons.AllCCRIcons

enum class LogisticsPortMode(val guiIcon: AllCCRIcons) : INamedIconOptions {

    PICK_UP(AllCCRIcons.LP_PICK_UP),
    DROP_OFF(AllCCRIcons.LP_DROP_OFF),
    FORCED_PICK_UP(AllCCRIcons.LP_FORCE_PICKUP), ;

    override fun getIcon(): AllCCRIcons {
        return this.guiIcon
    }

    override fun getTranslationKey(): String {
        return "gui.ccr.logistics.port_mode." + name.lowercase()
    }
}