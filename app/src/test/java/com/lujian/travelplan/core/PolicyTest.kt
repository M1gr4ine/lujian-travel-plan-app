package com.lujian.travelplan.core

import com.lujian.travelplan.map.MapCameraPolicy
import com.lujian.travelplan.map.MapViewportMode
import com.lujian.travelplan.map.MapViewportPolicy
import com.lujian.travelplan.importing.HtmlTitleCoverExtractor
import com.lujian.travelplan.model.DestinationDraft
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.ui.screens.PlanSelectionPolicy
import com.lujian.travelplan.ui.RootTabSwipePolicy
import com.lujian.travelplan.web.HtmlSecurityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {
    @Test
    fun `主页面左右滑切换相邻标签且不越界`() {
        assertEquals(1, RootTabSwipePolicy.adjacentIndex(currentIndex = 0, direction = 1, count = 3))
        assertEquals(1, RootTabSwipePolicy.adjacentIndex(currentIndex = 2, direction = -1, count = 3))
        assertEquals(null, RootTabSwipePolicy.adjacentIndex(currentIndex = 0, direction = -1, count = 3))
        assertEquals(null, RootTabSwipePolicy.adjacentIndex(currentIndex = 2, direction = 1, count = 3))
    }

    @Test
    fun `中国地图初始范围完整包含大连和国境边缘`() {
        val bounds = MapCameraPolicy.boundsFor(MapViewportMode.CHINA)

        assertEquals(true, bounds.contains(38.914, 121.6147))
        assertEquals(true, bounds.contains(47.3, 73.5))
        assertEquals(true, bounds.contains(18.2, 109.5))
        assertEquals(true, bounds.contains(53.5, 134.7))
    }

    @Test
    fun `计划库管理支持单选全选和取消全选`() {
        val planIds = listOf(1L, 2L, 3L)

        assertEquals(setOf(2L), PlanSelectionPolicy.toggle(emptySet(), 2L))
        assertEquals(emptySet<Long>(), PlanSelectionPolicy.toggle(setOf(2L), 2L))
        assertEquals(planIds.toSet(), PlanSelectionPolicy.toggleAll(emptySet(), planIds))
        assertEquals(emptySet<Long>(), PlanSelectionPolicy.toggleAll(planIds.toSet(), planIds))
    }

    @Test
    fun `HTML 封面优先提取非空主标题并排除说明文字`() {
        val html = """
            <html><head><style>.hero h1{font-size:64px}</style></head><body>
            <div class="hero"><h1>五天说走就走，把大连吃个痛快。</h1><p>这段说明不应出现在封面。</p></div>
            </body></html>
        """.trimIndent()

        val cover = HtmlTitleCoverExtractor.extract(html)

        assertTrue(cover!!.contains("五天说走就走，把大连吃个痛快。"))
        assertFalse(cover.contains("这段说明不应出现在封面。"))
        assertEquals(null, HtmlTitleCoverExtractor.extract("<html><body><p>没有标题</p></body></html>"))
    }

    @Test
    fun `显式封面提取计划名副标题和分行主标题`() {
        val html = """
            <html><body>
            <header class="hero" data-lujian-cover>
              <div class="brand-title">大连旅行计划</div>
              <div class="brand-sub">9月24日晚出发 · 5天4晚 · SOLO TRIP</div>
              <h1>五天说走就走，<br>把大连吃个痛快。</h1>
              <button>不应进入封面</button>
            </header>
            </body></html>
        """.trimIndent()

        val cover = HtmlTitleCoverExtractor.extractText(html)

        assertEquals("大连旅行计划", cover!!.brandTitle)
        assertEquals("9月24日晚出发 · 5天4晚 · SOLO TRIP", cover.brandSub)
        assertEquals(listOf("五天说走就走，", "把大连吃个痛快。"), cover.headlineLines)
    }

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
