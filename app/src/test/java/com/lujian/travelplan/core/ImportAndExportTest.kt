package com.lujian.travelplan.core

import com.lujian.travelplan.export.MobileHtmlGenerator
import com.lujian.travelplan.importing.FileHash
import com.lujian.travelplan.importing.HtmlFileValidator
import com.lujian.travelplan.importing.HtmlValidation
import com.lujian.travelplan.map.MarkerClusterer
import com.lujian.travelplan.model.DestinationDraft
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.model.PlanItemDraft
import com.lujian.travelplan.model.PlanMapLinks
import com.lujian.travelplan.model.PlanPlaceDraft
import com.lujian.travelplan.parser.LujianJsonParser
import com.lujian.travelplan.parser.LujianHtmlDetection
import com.lujian.travelplan.parser.LujianHtmlContract
import com.lujian.travelplan.parser.ParseRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportAndExportTest {
    @Test
    fun `伪 HTML 被拒绝而八位流经内容校验可接受`() {
        val fake = HtmlFileValidator.validate(
            fileName = "假计划.html",
            mimeType = "text/html",
            bytes = "只是普通文本".toByteArray(),
        )
        val octetStream = HtmlFileValidator.validate(
            fileName = "微信文件",
            mimeType = "application/octet-stream",
            bytes = "<!doctype html><html><body>行程</body></html>".toByteArray(),
        )

        assertTrue(fake is HtmlValidation.Rejected)
        assertEquals(HtmlValidation.Accepted, octetStream)
    }

    @Test
    fun `超过五十兆的文件被拒绝`() {
        val result = HtmlFileValidator.validateMetadata(
            fileName = "large.html",
            mimeType = "text/html",
            declaredSize = 50L * 1024 * 1024 + 1,
        )

        assertTrue(result is HtmlValidation.Rejected)
    }

    @Test
    fun `SHA256 对相同内容稳定`() {
        val bytes = "大连计划".toByteArray(Charsets.UTF_8)
        assertEquals(FileHash.sha256(bytes), FileHash.sha256(bytes.copyOf()))
        assertEquals(64, FileHash.sha256(bytes).length)
    }

    @Test
    fun `移动版 HTML 可由旅笺 JSON 再次解析`() {
        val source = samplePlan()
        val html = MobileHtmlGenerator.generate(source)
        val parsed = LujianJsonParser().parse(ParseRequest("export.html", "text/html", html))
        val validation = HtmlFileValidator.validate("export.html", "text/html", html.toByteArray())

        assertFalse(html.startsWith("\uFEFF"))
        assertTrue(html.contains("charset=\"utf-8\""))
        assertTrue(html.contains("name=\"lujian:destination\" content=\"大连\""))
        assertTrue(html.contains("name=\"lujian:latitude\" content=\"38.914\""))
        assertEquals(HtmlValidation.Accepted, validation)
        assertEquals("星海广场", parsed!!.days.single().items.single().title)
        assertEquals("约 3000 元", parsed.budget)
        assertEquals("约 300 元", parsed.days.single().budget)
        assertEquals(38.8817, parsed.places.single().latitude!!, 0.0001)
        val payload = JSONObject(parsed.sourcePayloadJson!!)
        assertEquals("大连", payload.getJSONArray("places").getJSONObject(0).getString("city"))
        assertEquals("high", payload.getJSONArray("places").getJSONObject(0).getString("confidence"))
        assertEquals("source-1", payload.getJSONArray("sources").getJSONObject(0).getString("id"))
        assertEquals("source-1", payload.getJSONArray("days").getJSONObject(0)
            .getJSONArray("items").getJSONObject(0).getJSONArray("sourceIds").getString(0))
    }

    @Test
    fun `空时间类别和备注导出后仍满足增强契约`() {
        val source = samplePlan().copy(
            days = samplePlan().days.map { day ->
                day.copy(items = day.items.map { item -> item.copy(time = null, category = null, notes = null) })
            },
        )

        val html = MobileHtmlGenerator.generate(source)
        val detection = LujianHtmlContract.inspect(html)
        val parsed = LujianJsonParser().parse(ParseRequest("export.html", "text/html", html))

        assertTrue(detection is LujianHtmlDetection.Compatible)
        assertEquals("时间待定", parsed!!.days.single().items.single().time)
        assertEquals("other", parsed.days.single().items.single().category)
    }

    @Test
    fun `数据结构正常时不因缺少手机地图页而拒绝`() {
        val html = MobileHtmlGenerator.generate(samplePlan())
            .replace("id=\"mobile-map-tab\"", "id=\"mobile-map-tab-missing\"")

        val result = HtmlFileValidator.validate("plan.html", "text/html", html.toByteArray())

        assertEquals(HtmlValidation.Accepted, result)
    }

    @Test
    fun `数据结构正常时不因额外外链脚本而拒绝`() {
        val html = MobileHtmlGenerator.generate(samplePlan())
            .replace("</head>", "<script src=\"https://example.com/extra.js\"></script></head>")

        val result = HtmlFileValidator.validate("plan.html", "text/html", html.toByteArray())

        assertEquals(HtmlValidation.Accepted, result)
    }

    @Test
    fun `数据结构正常时兼容 UTF8 BOM`() {
        val html = MobileHtmlGenerator.generate(samplePlan()).toByteArray()
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + html

        val result = HtmlFileValidator.validate("plan.html", "text/html", withBom)

        assertEquals(HtmlValidation.Accepted, result)
    }

    @Test
    fun `schemaVersion 错误时拒绝增强格式`() {
        val html = MobileHtmlGenerator.generate(samplePlan())
            .replace("\"schemaVersion\":1", "\"schemaVersion\":2")

        val result = HtmlFileValidator.validate("plan.html", "text/html", html.toByteArray())

        assertTrue(result is HtmlValidation.Rejected)
        assertTrue((result as HtmlValidation.Rejected).reason.contains("schemaVersion"))
    }

    @Test
    fun `同地点的多个计划聚合为一个标记`() {
        val destinations = listOf(
            DestinationDraft("大连 A", "CN", 38.9140, 121.6147),
            DestinationDraft("大连 B", "CN", 38.9141, 121.6148),
            DestinationDraft("北京", "CN", 39.9042, 116.4074),
        )

        val clusters = MarkerClusterer.cluster(destinations, radiusKm = 0.1)

        assertEquals(2, clusters.size)
        assertEquals(2, clusters.maxOf { it.destinations.size })
    }

    private fun samplePlan() = ParsedPlan(
        title = "大连慢旅行",
        capability = PlanCapability.ENHANCED,
        destinations = listOf(DestinationDraft("大连", "CN", 38.914, 121.6147)),
        days = listOf(
            PlanDayDraft(
                id = "day-1",
                label = "9月25日",
                title = "海边散步",
                items = listOf(
                    PlanItemDraft(
                        "item-1",
                        "10:00",
                        "星海广场",
                        "attraction",
                        "免费",
                        "带外套",
                        placeId = "place-1",
                        transport = "步行去下一站",
                    ),
                ),
                budget = "约 300 元",
            ),
        ),
        budget = "约 3000 元",
        baseArea = "中山区",
        places = listOf(
            PlanPlaceDraft(
                id = "place-1",
                name = "星海广场",
                latitude = 38.8817,
                longitude = 121.5880,
                mapLinks = PlanMapLinks(amap = "https://uri.amap.com/marker?position=121.5880,38.8817"),
            ),
        ),
        sourcePayloadJson = """
            {
              "schemaVersion":1,
              "title":"大连慢旅行",
              "destinations":[{"name":"大连","countryCode":"CN","latitude":38.914,"longitude":121.6147}],
              "days":[{"id":"day-1","label":"9月25日","title":"海边散步","items":[{
                "id":"item-1","time":"10:00","title":"星海广场","category":"attraction","notes":"带外套",
                "duration":"2小时","sourceIds":["source-1"],"warningIds":[]
              }]}],
              "places":[{
                "id":"place-1","name":"星海广场","city":"大连","category":"attraction","confidence":"high",
                "sourceIds":["source-1"],"tags":["海边"],"latitude":38.8817,"longitude":121.588
              }],
              "sources":[{"id":"source-1","platform":"xhs","title":"星海攻略"}],
              "warnings":[]
            }
        """.trimIndent(),
    )
}
