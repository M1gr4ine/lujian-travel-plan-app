package com.lujian.travelplan.core

import com.lujian.travelplan.map.MapCameraPolicy
import com.lujian.travelplan.map.MapViewportMode
import com.lujian.travelplan.map.MapViewportPolicy
import com.lujian.travelplan.importing.HtmlTitleCoverExtractor
import com.lujian.travelplan.importing.PlanReindexPolicy
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.model.DestinationDraft
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.model.PlanItemDraft
import com.lujian.travelplan.model.PlanPlaceDraft
import com.lujian.travelplan.ui.screens.PlanSelectionPolicy
import com.lujian.travelplan.ui.screens.PlanReaderPresentation
import com.lujian.travelplan.ui.screens.PlanReaderPage
import com.lujian.travelplan.ui.screens.buildDailyMapRoute
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
    fun `HTML 封面优先保留显式品牌与主标题并排除操作和说明`() {
        val html = """
            <html><head><style>.hero h1{font-size:64px}</style></head><body>
            <header class="hero" data-lujian-cover>
              <div class="hero-top"><div class="brand">✈️ 大连旅行计划</div><button>保存页面</button></div>
              <h1>五天说走就走，把大连吃个痛快。</h1>
              <p>这段说明不应出现在封面。</p>
            </header>
            </body></html>
        """.trimIndent()

        val cover = HtmlTitleCoverExtractor.extract(html)

        assertTrue(cover!!.contains("✈️ 大连旅行计划"))
        assertTrue(cover.contains("五天说走就走，把大连吃个痛快。"))
        assertFalse(cover.contains("保存页面"))
        assertFalse(cover.contains("这段说明不应出现在封面。"))
        assertEquals(null, HtmlTitleCoverExtractor.extract("<html><body><p>没有标题</p></body></html>"))
    }

    @Test
    fun `行程类别保留原标签并提供对应 emoji`() {
        assertEquals("🏨 hotel", PlanReaderPresentation.categoryLabel("hotel"))
        assertEquals("🍜 restaurant", PlanReaderPresentation.categoryLabel("restaurant"))
        assertEquals("🏖️ 景点", PlanReaderPresentation.categoryLabel("景点"))
        assertEquals("✦ 其他", PlanReaderPresentation.categoryLabel("其他"))
    }

    @Test
    fun `原生计划阅读器提供行程每日地图与预算页签`() {
        assertEquals(
            listOf("🗓️ 行程", "🗺️ 每日地图", "💰 预算"),
            PlanReaderPage.entries.map { it.label },
        )
    }

    @Test
    fun `每日地图按卡片顺序关联地点坐标`() {
        val plan = ParsedPlan(
            title = "大连慢旅行",
            capability = PlanCapability.ENHANCED,
            days = listOf(
                PlanDayDraft(
                    id = "day-1",
                    label = "第一天",
                    title = "海边慢游",
                    items = listOf(
                        PlanItemDraft("item-1", "10:00", "星海广场", "attraction", null, null, placeId = "place-1"),
                        PlanItemDraft("item-2", "12:00", "午餐", "restaurant", null, null),
                    ),
                ),
            ),
            places = listOf(PlanPlaceDraft("place-1", "星海广场", latitude = 38.8817, longitude = 121.5880)),
        )

        val route = buildDailyMapRoute(plan, plan.days.single())

        assertEquals(listOf("item-1", "item-2"), route.map { it.itemId })
        assertEquals(38.8817, route.first().latitude!!, 0.0001)
        assertEquals(null, route.last().latitude)
    }

    @Test
    fun `旧增强计划仅在没有用户生成版时重建结构化数据`() {
        val parsed = ParsedPlan(title = "旧计划", capability = PlanCapability.ENHANCED)
        val unedited = StoredPlan(1, parsed, "plan.html", "plans/1/raw.html", null, null, false, 0, 0)
        val edited = unedited.copy(generatedPath = "plans/1/generated.html")
        val viewOnly = unedited.copy(parsed = parsed.copy(capability = PlanCapability.VIEW_ONLY))

        assertTrue(PlanReindexPolicy.shouldRefreshStructuredData(unedited))
        assertFalse(PlanReindexPolicy.shouldRefreshStructuredData(edited))
        assertFalse(PlanReindexPolicy.shouldRefreshStructuredData(viewOnly))
    }

    @Test
    fun `重建结构化数据保留人工确认的目的地坐标`() {
        val current = ParsedPlan(
            title = "旧计划",
            capability = PlanCapability.ENHANCED,
            destinations = listOf(DestinationDraft("大连", "CN", 38.9, 121.6)),
        )
        val refreshed = current.copy(
            destinations = listOf(DestinationDraft("大连", null, null, null)),
            days = listOf(PlanDayDraft("day-1", "第一天", "海边", emptyList())),
        )

        val merged = PlanReindexPolicy.mergePreservingResolvedDestinations(current, refreshed)

        assertEquals(38.9, merged.destinations.single().latitude!!, 0.0001)
        assertEquals(121.6, merged.destinations.single().longitude!!, 0.0001)
        assertEquals("CN", merged.destinations.single().countryCode)
        assertEquals(1, merged.days.size)
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
