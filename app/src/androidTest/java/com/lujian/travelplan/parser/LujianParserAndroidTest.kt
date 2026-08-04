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

    @Test
    fun 增强计划保留预计统计和返酒店路段() {
        val html = """
            <html><body><script id="lujian-plan" type="application/json">
            {
              "schemaVersion":1,
              "title":"大连旅行计划",
              "destinations":[{"name":"大连","latitude":38.914,"longitude":121.6147}],
              "days":[{
                "id":"day-1","label":"9月25日","title":"第一天",
                "distanceEstimate":"18.6 km","durationEstimate":"1 小时 32 分钟",
                "mapStops":[
                  {"id":"hotel","title":"固定酒店","latitude":38.915,"longitude":121.5875},
                  {"id":"sea","title":"星海广场","latitude":38.881,"longitude":121.588}
                ],
                "mapLegs":[
                  {"id":"leg-1","from":"hotel","to":"sea","mode":"ride","summary":"骑行"},
                  {"id":"leg-2","from":"sea","to":"hotel","mode":"drive","summary":"返回固定酒店"}
                ],
                "items":[]
              }]
            }
            </script></body></html>
        """.trimIndent()

        val parsed = LujianJsonParser().parse(ParseRequest("大连.html", "text/html", html))
        val day = parsed?.days?.single()

        assertEquals("18.6 km", day?.distanceEstimate)
        assertEquals("1 小时 32 分钟", day?.durationEstimate)
        assertEquals(2, day?.mapStops?.size)
        assertEquals("hotel", day?.mapLegs?.last()?.toId)
        assertEquals("返回固定酒店", day?.mapLegs?.last()?.summary)
    }
}
