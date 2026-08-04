package com.lujian.travelplan.parser

import com.lujian.travelplan.model.DestinationDraft
import org.jsoup.nodes.Document

object DestinationHintExtractor {
    fun extract(document: Document): List<DestinationDraft> {
        explicitDestination(document)?.let { return listOf(it) }
        return inferKnownCity(document)?.let(::listOf).orEmpty()
    }

    private fun explicitDestination(document: Document): DestinationDraft? {
        val taggedElement = document.selectFirst("[data-lujian-destination], [data-destination]")
        val name = meta(document, "lujian:destination")
            ?: meta(document, "travel:destination")
            ?: meta(document, "geo.placename")
            ?: taggedElement?.attr("data-lujian-destination")?.ifBlank {
                taggedElement.attr("data-destination")
            }
            ?: return null

        val geoPosition = meta(document, "geo.position")
            ?.split(';', ',')
            ?.mapNotNull { it.trim().toDoubleOrNull() }
        val icbm = meta(document, "ICBM")
            ?.split(';', ',')
            ?.mapNotNull { it.trim().toDoubleOrNull() }
        val latitude = meta(document, "lujian:latitude")?.toDoubleOrNull()
            ?: taggedElement?.attr("data-latitude")?.toDoubleOrNull()
            ?: geoPosition?.getOrNull(0)
            ?: icbm?.getOrNull(0)
        val longitude = meta(document, "lujian:longitude")?.toDoubleOrNull()
            ?: taggedElement?.attr("data-longitude")?.toDoubleOrNull()
            ?: geoPosition?.getOrNull(1)
            ?: icbm?.getOrNull(1)
        val known = CityCatalog.findExact(name)

        return DestinationDraft(
            name = name.trim(),
            countryCode = meta(document, "lujian:country-code") ?: known?.countryCode,
            latitude = latitude ?: known?.latitude,
            longitude = longitude ?: known?.longitude,
        )
    }

    private fun inferKnownCity(document: Document): DestinationDraft? {
        val candidates = listOf(
            document.title(),
            document.select("h1").joinToString(" ") { it.text() },
            document.select("h2, .title, .logo-title, [class*=destination]")
                .joinToString(" ") { it.text() },
            document.body()?.text()?.take(3_000).orEmpty(),
        )
        return candidates.firstNotNullOfOrNull(CityCatalog::findFirst)
    }

    private fun meta(document: Document, name: String): String? = document
        .selectFirst("meta[name=\"$name\" i]")
        ?.attr("content")
        ?.trim()
        ?.ifBlank { null }
}

private object CityCatalog {
    private val cities = listOf(
        city("北京", "CN", 39.9042, 116.4074), city("上海", "CN", 31.2304, 121.4737),
        city("天津", "CN", 39.0842, 117.2009), city("重庆", "CN", 29.5630, 106.5516),
        city("大连", "CN", 38.9140, 121.6147), city("青岛", "CN", 36.0671, 120.3826),
        city("杭州", "CN", 30.2741, 120.1551), city("苏州", "CN", 31.2989, 120.5853),
        city("南京", "CN", 32.0603, 118.7969), city("厦门", "CN", 24.4798, 118.0894),
        city("福州", "CN", 26.0745, 119.2965), city("泉州", "CN", 24.8741, 118.6757),
        city("广州", "CN", 23.1291, 113.2644), city("深圳", "CN", 22.5431, 114.0579),
        city("香港", "HK", 22.3193, 114.1694), city("澳门", "MO", 22.1987, 113.5439),
        city("成都", "CN", 30.5728, 104.0668), city("西安", "CN", 34.3416, 108.9398),
        city("武汉", "CN", 30.5928, 114.3055), city("长沙", "CN", 28.2282, 112.9388),
        city("张家界", "CN", 29.1171, 110.4792), city("桂林", "CN", 25.2736, 110.2900),
        city("三亚", "CN", 18.2528, 109.5119), city("海口", "CN", 20.0440, 110.1999),
        city("昆明", "CN", 25.0389, 102.7183), city("丽江", "CN", 26.8550, 100.2278),
        city("大理", "CN", 25.6065, 100.2676), city("香格里拉", "CN", 27.8297, 99.7008),
        city("拉萨", "CN", 29.6520, 91.1721), city("乌鲁木齐", "CN", 43.8256, 87.6168),
        city("喀什", "CN", 39.4704, 75.9898), city("哈尔滨", "CN", 45.8038, 126.5349),
        city("长春", "CN", 43.8171, 125.3235), city("沈阳", "CN", 41.8057, 123.4315),
        city("呼和浩特", "CN", 40.8426, 111.7492), city("银川", "CN", 38.4872, 106.2309),
        city("兰州", "CN", 36.0611, 103.8343), city("西宁", "CN", 36.6171, 101.7782),
        city("洛阳", "CN", 34.6197, 112.4540), city("开封", "CN", 34.7973, 114.3076),
        city("济南", "CN", 36.6512, 117.1201), city("烟台", "CN", 37.4638, 121.4479),
        city("威海", "CN", 37.5131, 122.1204), city("北海", "CN", 21.4811, 109.1202),
        city("珠海", "CN", 22.2707, 113.5767), city("潮州", "CN", 23.6567, 116.6226),
        city("汕头", "CN", 23.3541, 116.6819), city("扬州", "CN", 32.3942, 119.4129),
        city("无锡", "CN", 31.4912, 120.3119), city("宁波", "CN", 29.8683, 121.5440),
        city("东京", "JP", 35.6762, 139.6503), city("大阪", "JP", 34.6937, 135.5023),
        city("京都", "JP", 35.0116, 135.7681), city("首尔", "KR", 37.5665, 126.9780),
        city("曼谷", "TH", 13.7563, 100.5018), city("新加坡", "SG", 1.3521, 103.8198),
        city("吉隆坡", "MY", 3.1390, 101.6869), city("巴黎", "FR", 48.8566, 2.3522),
        city("伦敦", "GB", 51.5072, -0.1276), city("罗马", "IT", 41.9028, 12.4964),
        city("纽约", "US", 40.7128, -74.0060), city("洛杉矶", "US", 34.0522, -118.2437),
        city("悉尼", "AU", -33.8688, 151.2093), city("墨尔本", "AU", -37.8136, 144.9631),
    )

    fun findExact(name: String): DestinationDraft? {
        val normalized = name.trim().removeSuffix("市")
        return cities.firstOrNull { it.name == normalized }
    }

    fun findFirst(text: String): DestinationDraft? = cities
        .mapNotNull { city -> text.indexOf(city.name).takeIf { it >= 0 }?.let { city to it } }
        .minWithOrNull(compareBy<Pair<DestinationDraft, Int>> { it.second }.thenByDescending { it.first.name.length })
        ?.first

    private fun city(name: String, countryCode: String, latitude: Double, longitude: Double) =
        DestinationDraft(name, countryCode, latitude, longitude)
}
