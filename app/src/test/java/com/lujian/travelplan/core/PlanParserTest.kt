package com.lujian.travelplan.core

import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.parser.DalianTemplateParser
import com.lujian.travelplan.parser.GenericHtmlParser
import com.lujian.travelplan.parser.LujianJsonParser
import com.lujian.travelplan.parser.ParseRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlanParserTest {
    @Test
    fun `旅笺 JSON 元数据解析为增强计划`() {
        val html = """
            <html><head><title>东京春日</title></head><body>
            <script id="lujian-plan" type="application/json">
            {"schemaVersion":1,"title":"东京春日","destinations":[{"name":"东京","countryCode":"JP","latitude":35.6762,"longitude":139.6503}],"days":[{"id":"d1","label":"4月1日","title":"抵达东京","items":[{"id":"i1","time":"14:00","title":"浅草散步","category":"景点","cost":"免费","notes":"雷门集合"}]}]}
            </script></body></html>
        """.trimIndent()

        val result = LujianJsonParser().parse(ParseRequest("tokyo.html", "text/html", html))

        assertNotNull(result)
        assertEquals(PlanCapability.ENHANCED, result!!.capability)
        assertEquals("东京春日", result.title)
        assertEquals("JP", result.destinations.single().countryCode)
        assertEquals("浅草散步", result.days.single().items.single().title)
    }

    @Test
    fun `旅笺目的地字符串数组也可正常接入`() {
        val html = """
            <html><body><script id="lujian-plan" type="application/json">
            {"schemaVersion":1,"title":"大连慢旅行","destinations":["大连"],"days":[{"id":"d1","label":"第一天","title":"抵达","items":[]}]}
            </script></body></html>
        """.trimIndent()

        val result = LujianJsonParser().parse(ParseRequest("dalian.html", "text/html", html))

        assertNotNull(result)
        assertEquals("大连", result!!.destinations.single().name)
    }

    @Test
    fun `旅笺每日地图与预算数据完整接入`() {
        val html = """
            <html><body><script id="lujian-plan" type="application/json">
            {
              "schemaVersion":1,
              "title":"大连慢旅行",
              "destinations":[{"name":"大连"}],
              "budget":"约 3000 元",
              "baseArea":"中山区",
              "places":[{"id":"p1","name":"星海广场","coordinates":{"latitude":38.8817,"longitude":121.5880},"mapLinks":{"amap":"https://uri.amap.com/marker?position=121.5880,38.8817"}}],
              "days":[{"id":"d1","label":"9月25日","title":"海边慢游","budget":"约 300 元","items":[{"id":"i1","time":"10:00","title":"星海广场","category":"attraction","cost":"免费","notes":"沿海散步","placeId":"p1","transport":"步行"}]}]
            }
            </script></body></html>
        """.trimIndent()

        val result = LujianJsonParser().parse(ParseRequest("dalian.html", "text/html", html))!!

        assertEquals("约 3000 元", result.budget)
        assertEquals("中山区", result.baseArea)
        assertEquals("约 300 元", result.days.single().budget)
        assertEquals("p1", result.days.single().items.single().placeId)
        assertEquals("步行", result.days.single().items.single().transport)
        assertEquals(38.8817, result.places.single().latitude!!, 0.0001)
        assertEquals(121.5880, result.places.single().longitude!!, 0.0001)
        assertEquals("https://uri.amap.com/marker?position=121.5880,38.8817", result.places.single().mapLinks.amap)
    }

    @Test
    fun `旅笺越界坐标不会进入原生地图`() {
        val html = """
            <html><body><script id="lujian-plan" type="application/json">
            {"schemaVersion":1,"title":"异常坐标","destinations":[{"name":"测试地","latitude":91,"longitude":181}],"places":[{"id":"p1","name":"测试点","latitude":-91,"longitude":-181}],"days":[{"id":"d1","label":"第一天","title":"测试","items":[]}]}
            </script></body></html>
        """.trimIndent()

        val result = LujianJsonParser().parse(ParseRequest("invalid-coordinate.html", "text/html", html))!!

        assertEquals(null, result.destinations.single().latitude)
        assertEquals(null, result.destinations.single().longitude)
        assertEquals(null, result.places.single().latitude)
        assertEquals(null, result.places.single().longitude)
    }

    @Test
    fun `大连模板从日期列和行程卡解析结构化内容`() {
        val html = """
            <html><head><title>大连旅行计划 · 9月25–29日</title></head><body>
            <div class="logo-title">大连旅行计划</div>
            <div class="day-col">
              <div class="day-name">Day 1</div><div class="day-date">9月25日 周三</div>
              <div class="stop-card" data-cat="hotel"><span class="card-time">08:30</span><h3 class="card-title">酒店早餐</h3><div class="card-cost">含在房费</div><span class="cat-badge">住宿</span></div>
            </div>
            </body></html>
        """.trimIndent()

        val result = DalianTemplateParser().parse(ParseRequest("大连.html", "text/html", html))

        assertNotNull(result)
        assertEquals(PlanCapability.ENHANCED, result!!.capability)
        assertEquals("大连旅行计划", result.title)
        assertEquals("酒店早餐", result.days.single().items.single().title)
    }

    @Test
    fun `普通 HTML 仅保留标题并标记只读`() {
        val result = GenericHtmlParser().parse(
            ParseRequest("notes.html", "text/html", "<html><head><title>随手记</title></head><body>正文</body></html>")
        )

        assertEquals(PlanCapability.VIEW_ONLY, result.capability)
        assertEquals("随手记", result.title)
        assertEquals(0, result.days.size)
    }

    @Test
    fun `普通 HTML 的旅笺地理标签直接生成带坐标目的地`() {
        val html = """
            <html><head><title>杭州周末</title>
            <meta name="lujian:destination" content="杭州">
            <meta name="lujian:country-code" content="CN">
            <meta name="lujian:latitude" content="30.2741">
            <meta name="lujian:longitude" content="120.1551">
            </head><body>西湖散步</body></html>
        """.trimIndent()

        val result = GenericHtmlParser().parse(ParseRequest("hangzhou.html", "text/html", html))

        assertEquals("杭州", result.destinations.single().name)
        assertEquals("CN", result.destinations.single().countryCode)
        assertEquals(30.2741, result.destinations.single().latitude!!, 0.0001)
        assertEquals(120.1551, result.destinations.single().longitude!!, 0.0001)
    }

    @Test
    fun `没有标签时从标题识别常见旅游城市及坐标`() {
        val result = GenericHtmlParser().parse(
            ParseRequest(
                "plan.html",
                "text/html",
                "<html><head><title>青岛三日旅行计划</title></head><body><h1>海边慢游</h1></body></html>",
            ),
        )

        assertEquals("青岛", result.destinations.single().name)
        assertEquals("CN", result.destinations.single().countryCode)
        assertNotNull(result.destinations.single().latitude)
        assertNotNull(result.destinations.single().longitude)
    }
}
