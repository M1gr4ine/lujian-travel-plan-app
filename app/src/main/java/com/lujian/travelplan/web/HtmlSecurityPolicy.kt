package com.lujian.travelplan.web

import com.lujian.travelplan.model.PlanCapability

data class HtmlSecurityConfig(
    val javaScriptEnabled: Boolean,
    val fileAccessEnabled: Boolean,
    val cleartextAllowed: Boolean,
    val nativeBridgeEnabled: Boolean,
)

object HtmlSecurityPolicy {
    fun resolve(
        capability: PlanCapability,
        compatibilityMode: Boolean,
    ): HtmlSecurityConfig = HtmlSecurityConfig(
        javaScriptEnabled = compatibilityMode,
        fileAccessEnabled = false,
        cleartextAllowed = false,
        nativeBridgeEnabled = false,
    )
}
