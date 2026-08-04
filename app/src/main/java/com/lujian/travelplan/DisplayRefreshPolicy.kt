package com.lujian.travelplan

internal data class DisplayModeSpec(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

internal object DisplayRefreshPolicy {
    fun preferredModeId(
        currentWidth: Int,
        currentHeight: Int,
        modes: List<DisplayModeSpec>,
    ): Int? = modes
        .asSequence()
        .filter { it.width == currentWidth && it.height == currentHeight }
        .filter { it.refreshRate.isFinite() && it.refreshRate > 0f }
        .maxByOrNull { it.refreshRate }
        ?.id
}
