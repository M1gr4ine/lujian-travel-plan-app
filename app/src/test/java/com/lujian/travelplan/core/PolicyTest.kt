package com.lujian.travelplan.core

import com.lujian.travelplan.map.MapViewportMode
import com.lujian.travelplan.map.MapViewportPolicy
import com.lujian.travelplan.model.DestinationDraft
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.web.HtmlSecurityPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyTest {
    @Test
    fun `没有境外目的地时使用中国视野`() {
        val destinations = listOf(DestinationDraft("大连", "CN", 38.914, 121.614))

        assertEquals(MapViewportMode.CHINA, MapViewportPolicy.resolve(destinations))
        assertEquals(MapViewportMode.CHINA, MapViewportPolicy.resolve(emptyList()))
    }

    @Test
    fun `任一境外目的地使首页切换全球视野`() {
        val destinations = listOf(
            DestinationDraft("大连", "CN", 38.914, 121.614),
            DestinationDraft("东京", "JP", 35.6762, 139.6503),
        )

        assertEquals(MapViewportMode.WORLD, MapViewportPolicy.resolve(destinations))
    }

    @Test
    fun `普通 HTML 默认禁用脚本且不开放文件访问`() {
        val policy = HtmlSecurityPolicy.resolve(PlanCapability.VIEW_ONLY, compatibilityMode = false)

        assertEquals(false, policy.javaScriptEnabled)
        assertEquals(false, policy.fileAccessEnabled)
        assertEquals(false, policy.cleartextAllowed)
    }

    @Test
    fun `兼容模式只开启脚本仍不开放文件访问`() {
        val policy = HtmlSecurityPolicy.resolve(PlanCapability.VIEW_ONLY, compatibilityMode = true)

        assertEquals(true, policy.javaScriptEnabled)
        assertEquals(false, policy.fileAccessEnabled)
        assertEquals(false, policy.nativeBridgeEnabled)
    }
}
