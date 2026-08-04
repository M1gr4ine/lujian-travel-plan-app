package com.lujian.travelplan.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LujianParserAndroidTest {
    @Test
    fun 旧版地图坐标正则兼容Android运行时() {
        val html = """
            <html><body>
            <div class="logo-title">大连旅行计划</div>
            <script id="lujian-plan" type="application/json">
            {
              "schemaVersion":1,
              "title":"大连 5天4晚旅行计划",
              "destinations":["大连"],
              "places":[{"id":"place-d1-0","name":"酒店早餐"}],
              "days":[{"id":"day-1","label":"9月25日","title":"第一天","items":[
                {"id":"d1-0","time":"08:30","title":"酒店早餐","category":"hotel","notes":"","placeId":"place-d1-0"}
              ]}]
            }
            </script>
            <script>
            /* LIVE_MAP_DATA_START */
            const LIVE_MAP_DAYS = Object.freeze({
              'day-1': { points:[
                {id:'hotel',name:'亚朵X酒店',coord:[121.5875,38.9150],kind:'hotel'}
              ], routes:[] }
            });
            </script>
            </body></html>
        """.trimIndent()

        val parsed = LujianJsonParser().parse(ParseRequest("大连.html", "text/html", html))

        assertNotNull(parsed)
        assertEquals("大连旅行计划", parsed?.title)
        assertEquals(38.9150, parsed?.places?.single()?.latitude ?: 0.0, 0.0001)
        assertEquals(121.5875, parsed?.places?.single()?.longitude ?: 0.0, 0.0001)
    }
}
