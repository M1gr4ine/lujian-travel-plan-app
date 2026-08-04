package com.lujian.travelplan

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayRefreshPolicyTest {
    @Test
    fun `同分辨率选择最高刷新率模式`() {
        val modes = listOf(
            DisplayModeSpec(id = 4, width = 1316, height = 2832, refreshRate = 60f),
            DisplayModeSpec(id = 2, width = 1316, height = 2832, refreshRate = 90f),
            DisplayModeSpec(id = 1, width = 1316, height = 2832, refreshRate = 120f),
            DisplayModeSpec(id = 9, width = 1080, height = 2400, refreshRate = 144f),
        )

        assertEquals(
            1,
            DisplayRefreshPolicy.preferredModeId(
                currentWidth = 1316,
                currentHeight = 2832,
                modes = modes,
            ),
        )
    }
}
