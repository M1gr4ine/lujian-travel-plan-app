package com.lujian.travelplan.core

import com.lujian.travelplan.map.MapCameraPolicy
import com.lujian.travelplan.map.MapViewportMode
import com.lujian.travelplan.map.MapViewportPolicy
import com.lujian.travelplan.map.LujianMapStyle
import com.lujian.travelplan.map.LujianPinVisual
import com.lujian.travelplan.importing.HtmlTitleCoverExtractor
import com.lujian.travelplan.importing.PlanReindexPolicy
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.model.DestinationDraft
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.model.PlanItemDraft
import com.lujian.travelplan.model.PlanMapStopDraft
import com.lujian.travelplan.model.PlanPlaceDraft
import com.lujian.travelplan.ui.screens.PlanSelectionPolicy
import com.lujian.travelplan.ui.screens.PlanReaderPresentation
import com.lujian.travelplan.ui.screens.PlanReaderPage
import com.lujian.travelplan.ui.screens.PlanReaderDayPolicy
import com.lujian.travelplan.ui.screens.buildDailyMapRoute
import com.lujian.travelplan.ui.RootTabSwipePolicy
import com.lujian.travelplan.ui.PlanNoteTransitionPolicy
import com.lujian.travelplan.web.HtmlSecurityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {
    @Test
    fun `只有从计划库进入且未开启减少动画时使用便签共享展开`() {
        assertTrue(PlanNoteTransitionPolicy.useSharedBounds("library", reduceMotion = false))
        assertFalse(PlanNoteTransitionPolicy.useSharedBounds("home", reduceMotion = false))
        assertFalse(PlanNoteTransitionPolicy.useSharedBounds("library", reduceMotion = true))
    }

    @Test
    fun `主页面左右滑切换相邻标签且不越界`() {
        assertEquals(1, RootTabSwipePolicy.adjacentIndex(currentIndex = 0, direction = 1, count = 3))
        assertEquals(1, RootTabSwipePolicy.adjacentIndex(currentIndex = 2, direction = -1, count = 3))
        assertEquals(null, RootTabSwipePolicy.adjacentIndex(currentIndex = 0, direction = -1, count = 3))
        assertEquals(null, RootTabSwipePolicy.adjacentIndex(currentIndex = 2, direction = 1, count = 3))
    }

    @Test
    fun `子控件已消费横向手势时主页面不切换`() {
        assertEquals(
            null,
            RootTabSwipePolicy.directionForGesture(
                totalX = -100f,
                totalY = 0f,
                threshold = 56f,
                childConsumed = true,
            ),
        )
        assertEquals(
            1,
            RootTabSwipePolicy.directionForGesture(
                totalX = -100f,
                totalY = 0f,
                threshold = 56f,
                childConsumed = false,
            ),
        )
    }

    @Test
    fun `首页地图拖动关闭时允许已消费的横向手势切换页签`() {
        assertEquals(
            1,
            RootTabSwipePolicy.directionForGesture(
                totalX = -100f,
                totalY = 0f,
                threshold = 56f,
                childConsumed = true,
                allowConsumedGesture = true,
            ),
        )
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
    fun `旧版桌面封面同时截取品牌块和主标题`() {
        val html = """
            <html><head><style>.hero h1{font-size:64px}</style></head><body>
            <header><div class="logo-block">
              <div class="logo-title">大连旅行计划</div>
              <div class="logo-sub">9月24日晚出发 · 5天4晚 · SOLO TRIP</div>
            </div><button>保存页面</button></header>
            <div class="hero">
              <h1>五天说走就走，<br>把大连吃个痛快。</h1>
              <p>首屏说明不属于缩略图。</p>
            </div>
            </body></html>
        """.trimIndent()

        val cover = HtmlTitleCoverExtractor.extract(html)!!

        assertTrue(cover.contains("logo-block"))
        assertTrue(cover.contains("大连旅行计划"))
        assertTrue(cover.contains("9月24日晚出发 · 5天4晚 · SOLO TRIP"))
        assertTrue(cover.contains("五天说走就走"))
        assertTrue(cover.contains("把大连吃个痛快。"))
        assertFalse(cover.contains("保存页面"))
        assertFalse(cover.contains("首屏说明不属于缩略图。"))

        val text = HtmlTitleCoverExtractor.extractText(html)!!
        assertEquals("大连旅行计划", text.brandTitle)
        assertEquals("9月24日晚出发 · 5天4晚 · SOLO TRIP", text.brandSub)
        assertEquals(listOf("五天说走就走，", "把大连吃个痛快。"), text.headlineLines)
    }

    @Test
    fun `行程类别保留原标签并提供对应 emoji`() {
        assertEquals("🏨 hotel", PlanReaderPresentation.categoryLabel("hotel"))
        assertEquals("🍜 restaurant", PlanReaderPresentation.categoryLabel("restaurant"))
        assertEquals("🏖️ 景点", PlanReaderPresentation.categoryLabel("景点"))
        assertEquals("✦ 其他", PlanReaderPresentation.categoryLabel("其他"))
    }

    @Test
    fun `原生计划阅读器提供行程地图与预算页签`() {
        assertEquals(
            listOf("🗓️ 行程", "🗺️ 地图", "💰 预算"),
            PlanReaderPage.entries.map { it.label },
        )
    }

    @Test
    fun `行程和地图页切换日期都同步横向分页器`() {
        val mapAction = PlanReaderDayPolicy.select(
            requestedIndex = 1,
            dayCount = 5,
            activePage = PlanReaderPage.MAP,
        )
        val itineraryAction = PlanReaderDayPolicy.select(
            requestedIndex = 3,
            dayCount = 5,
            activePage = PlanReaderPage.ITINERARY,
        )

        assertEquals(1, mapAction.selectedIndex)
        assertEquals(1, mapAction.pagerTarget)
        assertEquals(3, itineraryAction.selectedIndex)
        assertEquals(3, itineraryAction.pagerTarget)
    }

    @Test
    fun `地图使用内置轻量样式避免首屏串行下载远程样式`() {
        assertFalse(LujianMapStyle.USES_REMOTE_STYLE_DOCUMENT)
    }

    @Test
    fun `首页大头针几何中心与画布中心一致`() {
        assertEquals(LujianPinVisual.WIDTH / 2f, LujianPinVisual.CENTER_X, 0.001f)
        assertTrue(LujianPinVisual.HEAD_CENTER_Y < LujianPinVisual.STEM_BOTTOM_Y)
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
    fun `地图点类别优先跟随匹配的行程地点`() {
        val day = PlanDayDraft(
            id = "day-1",
            label = "第一天",
            title = "老城美食",
            items = listOf(
                PlanItemDraft("item-1", "10:00", "东关街慢拍", "attraction", null, null),
                PlanItemDraft("item-2", "12:00", "澳深鱼市午餐", "restaurant", null, null),
            ),
            mapStops = listOf(
                PlanMapStopDraft("dongguan", "东关街", "10:00", "restaurant"),
                PlanMapStopDraft("aoshen", "澳深鱼市", "12:00", "restaurant"),
            ),
        )
        val plan = ParsedPlan(
            title = "大连旅行计划",
            capability = PlanCapability.ENHANCED,
            days = listOf(day),
        )

        val route = buildDailyMapRoute(plan, day)

        assertEquals(listOf("attraction", "restaurant"), route.map { it.category })
    }

    @Test
    fun `旧增强计划含用户生成版时也会刷新兼容元数据`() {
        val parsed = ParsedPlan(title = "旧计划", capability = PlanCapability.ENHANCED)
        val unedited = StoredPlan(1, parsed, "plan.html", "plans/1/raw.html", null, null, false, 0, 0)
        val edited = unedited.copy(generatedPath = "plans/1/generated.html")
        val viewOnly = unedited.copy(parsed = parsed.copy(capability = PlanCapability.VIEW_ONLY))

        assertTrue(PlanReindexPolicy.shouldRefreshStructuredData(unedited))
        assertTrue(PlanReindexPolicy.shouldRefreshStructuredData(edited))
        assertFalse(PlanReindexPolicy.shouldRefreshStructuredData(viewOnly))
    }

    @Test
    fun `生成版刷新标题坐标和地图关联但保留用户编辑日程`() {
        val current = ParsedPlan(
            title = "大连 5天4晚旅行计划",
            capability = PlanCapability.ENHANCED,
            days = listOf(
                PlanDayDraft(
                    "day-1",
                    "9月25日",
                    "用户改过的第一天",
                    listOf(PlanItemDraft("item-1", "09:00", "用户改过的早餐", "hotel", null, "保留备注")),
                ),
            ),
        )
        val stored = StoredPlan(1, current, "plan.html", "plans/1/raw.html", "plans/1/generated.html", null, false, 0, 0)
        val refreshed = current.copy(
            title = "大连旅行计划",
            days = listOf(
                PlanDayDraft(
                    "day-1",
                    "9月25日",
                    "源页面第一天",
                    listOf(PlanItemDraft("item-1", "08:30", "源页面早餐", "hotel", null, "源备注", placeId = "place-1")),
                ),
            ),
            places = listOf(PlanPlaceDraft("place-1", "亚朵X酒店", latitude = 38.915, longitude = 121.5875)),
        )

        val merged = PlanReindexPolicy.mergeForPlan(stored, refreshed)

        assertEquals("大连旅行计划", merged.title)
        assertEquals("用户改过的第一天", merged.days.single().title)
        assertEquals("用户改过的早餐", merged.days.single().items.single().title)
        assertEquals("保留备注", merged.days.single().items.single().notes)
        assertEquals("place-1", merged.days.single().items.single().placeId)
        assertEquals(38.915, merged.places.single().latitude!!, 0.0001)
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
