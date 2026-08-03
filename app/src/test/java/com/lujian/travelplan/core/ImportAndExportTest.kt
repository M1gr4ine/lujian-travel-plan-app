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
import com.lujian.travelplan.parser.LujianJsonParser
import com.lujian.travelplan.parser.ParseRequest
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
        val source = ParsedPlan(
            title = "大连慢旅行",
            capability = PlanCapability.ENHANCED,
            destinations = listOf(DestinationDraft("大连", "CN", 38.914, 121.6147)),
            days = listOf(
                PlanDayDraft(
                    id = "day-1",
                    label = "9月25日",
                    title = "海边散步",
                    items = listOf(PlanItemDraft("item-1", "10:00", "星海广场", "景点", "免费", "带外套")),
                ),
            ),
        )

        val html = MobileHtmlGenerator.generate(source)
        val parsed = LujianJsonParser().parse(ParseRequest("export.html", "text/html", html))

        assertFalse(html.startsWith("\uFEFF"))
        assertTrue(html.contains("charset=\"utf-8\""))
        assertEquals("星海广场", parsed!!.days.single().items.single().title)
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
}
