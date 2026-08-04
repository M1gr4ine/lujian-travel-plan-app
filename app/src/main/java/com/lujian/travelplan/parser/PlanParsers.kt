package com.lujian.travelplan.parser

import com.lujian.travelplan.model.DestinationDraft
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.model.PlanItemDraft
import com.lujian.travelplan.model.PlanMapLegDraft
import com.lujian.travelplan.model.PlanMapLinks
import com.lujian.travelplan.model.PlanMapStopDraft
import com.lujian.travelplan.model.PlanPlaceDraft
import com.lujian.travelplan.model.PlanSectionDraft
import org.json.JSONObject
import org.jsoup.Jsoup

data class ParseRequest(
    val fileName: String,
    val mimeType: String?,
    val html: String,
)

fun interface PlanParser {
    fun parse(request: ParseRequest): ParsedPlan?
}

class LujianJsonParser : PlanParser {
    override fun parse(request: ParseRequest): ParsedPlan? {
        val document = Jsoup.parse(request.html)
        val root = when (val detection = LujianHtmlContract.inspect(request.html)) {
            is LujianHtmlDetection.Compatible -> detection.payload
            LujianHtmlDetection.Absent,
            is LujianHtmlDetection.Incompatible,
            -> return null
        }

        val destinations = root.optJSONArray("destinations")?.let { array ->
            List(array.length()) { index -> array.opt(index) }
                .mapNotNull { item ->
                    when (item) {
                        is JSONObject -> DestinationDraft(
                            name = item.optString("name"),
                            countryCode = item.optNullableString("countryCode"),
                            latitude = item.optLatitude("latitude"),
                            longitude = item.optLongitude("longitude"),
                        )
                        is String -> item.takeIf { it.isNotBlank() }?.let {
                            DestinationDraft(it, null, null, null)
                        }
                        else -> null
                    }
                }
        }.orEmpty()

        val days = root.optJSONArray("days")?.let { array ->
            List(array.length()) { dayIndex ->
                val day = array.getJSONObject(dayIndex)
                val items = day.optJSONArray("items")?.let { itemsArray ->
                    List(itemsArray.length()) { itemIndex ->
                        val item = itemsArray.getJSONObject(itemIndex)
                        PlanItemDraft(
                            id = item.optString("id", "item-$dayIndex-$itemIndex"),
                            time = item.optNullableString("time"),
                            title = item.optString("title"),
                            category = item.optNullableString("category"),
                            cost = item.optNullableString("cost"),
                            notes = item.optNullableString("notes"),
                            placeId = item.optNullableString("placeId"),
                            transport = item.optNullableString("transport"),
                            mapLinks = item.optMapLinks(),
                        )
                    }
                }.orEmpty()
                val mapStops = day.optJSONArray("mapStops")?.let { stopsArray ->
                    List(stopsArray.length()) { stopIndex -> stopsArray.optJSONObject(stopIndex) }
                        .filterNotNull()
                        .mapIndexed { stopIndex, stop ->
                            val coordinates = stop.optJSONObject("coordinates")
                            PlanMapStopDraft(
                                id = stop.optString("id", "${day.optString("id", "day-$dayIndex")}-map-$stopIndex"),
                                title = stop.optString("title")
                                    .ifBlank { stop.optString("name") }
                                    .ifBlank { "地点" },
                                time = stop.optNullableString("time") ?: stop.optNullableString("meta"),
                                category = stop.optNullableString("category") ?: stop.optNullableString("kind"),
                                latitude = stop.optLatitude("latitude")
                                    ?: coordinates?.optLatitude("latitude")
                                    ?: coordinates?.optLatitude("lat"),
                                longitude = stop.optLongitude("longitude")
                                    ?: coordinates?.optLongitude("longitude")
                                    ?: coordinates?.optLongitude("lng")
                                    ?: coordinates?.optLongitude("lon"),
                            )
                        }
                }.orEmpty()
                val mapLegs = day.optJSONArray("mapLegs")?.let { legsArray ->
                    List(legsArray.length()) { legIndex -> legsArray.optJSONObject(legIndex) }
                        .mapIndexedNotNull { legIndex, leg ->
                            val fromId = leg?.optString("from").orEmpty()
                            val toId = leg?.optString("to").orEmpty()
                            if (leg == null || fromId.isBlank() || toId.isBlank()) return@mapIndexedNotNull null
                            PlanMapLegDraft(
                                id = leg.optString("id", "leg-${legIndex + 1}"),
                                fromId = fromId,
                                toId = toId,
                                mode = leg.optNullableString("mode"),
                                summary = leg.optNullableString("summary") ?: leg.optNullableString("transport"),
                            )
                        }
                }.orEmpty()
                PlanDayDraft(
                    id = day.optString("id", "day-$dayIndex"),
                    label = day.optString("label"),
                    title = day.optString("title"),
                    items = items,
                    summary = day.optNullableString("summary"),
                    budget = day.optNullableString("budget"),
                    backup = day.optNullableString("backup"),
                    distanceEstimate = day.optNullableString("distanceEstimate"),
                    durationEstimate = day.optNullableString("durationEstimate"),
                    mapStops = mapStops,
                    mapLegs = mapLegs,
                )
            }
        }.orEmpty()

        val legacyCoordinates = LegacyMapCoordinateExtractor.extract(request.html, days)

        val places = root.optJSONArray("places")?.let { array ->
            List(array.length()) { index -> array.optJSONObject(index) }
                .filterNotNull()
                .mapIndexed { index, place ->
                    val coordinates = place.optJSONObject("coordinates")
                    val id = place.optString("id", "place-$index")
                    val legacyCoordinate = legacyCoordinates[id]
                    PlanPlaceDraft(
                        id = id,
                        name = place.optString("name"),
                        address = place.optNullableString("address"),
                        latitude = place.optLatitude("latitude")
                            ?: coordinates?.optLatitude("latitude")
                            ?: coordinates?.optLatitude("lat")
                            ?: legacyCoordinate?.latitude,
                        longitude = place.optLongitude("longitude")
                            ?: coordinates?.optLongitude("longitude")
                            ?: coordinates?.optLongitude("lng")
                            ?: coordinates?.optLongitude("lon")
                            ?: legacyCoordinate?.longitude,
                        mapLinks = place.optMapLinks(),
                    )
                }
        }.orEmpty()

        val trip = root.optJSONObject("trip")

        val sections = buildList {
            root.optJSONArray("sections")?.let { array ->
                repeat(array.length()) { index ->
                    val section = array.getJSONObject(index)
                    add(PlanSectionDraft(section.optString("title"), section.optString("content")))
                }
            }
            listOf(
                "预算" to "budget",
                "交通" to "transport",
                "住宿" to "accommodation",
                "补充说明" to "notes",
            ).forEach { (title, key) ->
                if (root.has(key) && !root.isNull(key)) {
                    val value = root.get(key).toString()
                    if (value.isNotBlank()) add(PlanSectionDraft(title, value))
                }
            }
        }

        return ParsedPlan(
            title = extractPageDisplayTitle(document)
                .ifBlank { root.optString("title") }
                .ifBlank { document.title() }
                .ifBlank { request.fileName },
            capability = PlanCapability.ENHANCED,
            destinations = destinations,
            days = days,
            sections = sections,
            dateRange = root.optNullableString("dateRange"),
            travelers = root.optNullableString("travelers"),
            style = root.optNullableString("style"),
            baseArea = root.optNullableString("baseArea"),
            budget = root.optNullableString("budget"),
            accommodationBudget = trip?.optNullableString("accommodationBudget")
                ?: trip?.optNullableString("hotelBudget"),
            assumptions = root.optJSONArray("assumptions")?.let { array ->
                List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
            }.orEmpty(),
            places = places,
            sourcePayloadJson = root.toString(),
        )
    }
}

private data class LegacyMapCoordinate(
    val latitude: Double,
    val longitude: Double,
)

private data class LegacyMapPoint(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val kind: String?,
)

private data class LinkedPlanPlace(
    val dayId: String,
    val title: String,
    val category: String?,
)

private object LegacyMapCoordinateExtractor {
    private const val MARKER = "LIVE_MAP_DATA_START"
    private val dayPattern = Regex(
        """['\"](day-\d+)['\"]\s*:\s*\{[\s\S]*?points\s*:\s*\[([\s\S]*?)]\s*,\s*routes\s*:""",
    )
    private val pointPattern = Regex("""\{([^{}]*?coord\s*:\s*\[[^\]]+\][^{}]*?)\}""")
    private val namePattern = Regex("""name\s*:\s*(['\"])(.*?)\1""")
    private val kindPattern = Regex("""kind\s*:\s*(['\"])(.*?)\1""")
    private val coordinatePattern = Regex(
        """coord\s*:\s*\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\]""",
    )

    fun extract(html: String, days: List<PlanDayDraft>): Map<String, LegacyMapCoordinate> {
        val markedContent = html.substringAfter(MARKER, missingDelimiterValue = "")
        if (markedContent.isBlank()) return emptyMap()
        val pointsByDay = dayPattern.findAll(markedContent).associate { dayMatch ->
            dayMatch.groupValues[1] to pointPattern.findAll(dayMatch.groupValues[2]).mapNotNull(::parsePoint).toList()
        }
        if (pointsByDay.isEmpty()) return emptyMap()

        val linkedPlaces = days.flatMap { day ->
            day.items.mapNotNull { item ->
                item.placeId?.takeIf(String::isNotBlank)?.let { placeId ->
                    placeId to LinkedPlanPlace(day.id, item.title, item.category)
                }
            }
        }.toMap()

        return linkedPlaces.mapNotNull { (placeId, linked) ->
            val point = selectPoint(linked, pointsByDay[linked.dayId].orEmpty()) ?: return@mapNotNull null
            placeId to LegacyMapCoordinate(point.latitude, point.longitude)
        }.toMap()
    }

    private fun parsePoint(match: MatchResult): LegacyMapPoint? {
        val body = match.groupValues[1]
        val name = namePattern.find(body)?.groupValues?.get(2)?.takeIf(String::isNotBlank) ?: return null
        val coordinate = coordinatePattern.find(body) ?: return null
        val longitude = coordinate.groupValues[1].toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: return null
        val latitude = coordinate.groupValues[2].toDoubleOrNull()?.takeIf { it in -90.0..90.0 } ?: return null
        return LegacyMapPoint(
            name = name,
            latitude = latitude,
            longitude = longitude,
            kind = kindPattern.find(body)?.groupValues?.get(2),
        )
    }

    private fun selectPoint(linked: LinkedPlanPlace, points: List<LegacyMapPoint>): LegacyMapPoint? {
        if (points.isEmpty()) return null
        val isHotel = linked.category.equals("hotel", ignoreCase = true) || "酒店" in linked.title
        if (isHotel) points.firstOrNull { it.kind.equals("hotel", ignoreCase = true) }?.let { return it }

        val target = normalize(linked.title)
        return points.mapIndexedNotNull { index, point ->
            val match = matchPosition(target, normalize(point.name)) ?: return@mapIndexedNotNull null
            Triple(point, match, index)
        }.minWithOrNull(
            compareBy<Triple<LegacyMapPoint, Pair<Int, Int>, Int>> { it.second.first }
                .thenByDescending { it.second.second }
                .thenBy { it.third },
        )?.first
    }

    private fun matchPosition(target: String, candidate: String): Pair<Int, Int>? {
        if (target.isBlank() || candidate.isBlank()) return null
        target.indexOf(candidate).takeIf { it >= 0 }?.let { return it to candidate.length }
        candidate.indexOf(target).takeIf { it >= 0 }?.let { return 0 to target.length }
        for (length in minOf(target.length, candidate.length) downTo 3) {
            val matches = (0..candidate.length - length).mapNotNull { start ->
                val fragment = candidate.substring(start, start + length)
                target.indexOf(fragment).takeIf { it >= 0 }
            }
            if (matches.isNotEmpty()) return matches.min() to length
        }
        return null
    }

    private fun normalize(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)
}

private fun extractPageDisplayTitle(document: org.jsoup.nodes.Document): String =
    sequenceOf(
        "[data-lujian-cover] .brand-title",
        "[data-lujian-cover] .logo-title",
        ".logo-title",
        "#mobile-brand-title",
    ).mapNotNull { selector -> document.selectFirst(selector)?.text()?.trim() }
        .firstOrNull(String::isNotBlank)
        .orEmpty()

class DalianTemplateParser : PlanParser {
    override fun parse(request: ParseRequest): ParsedPlan? {
        val document = Jsoup.parse(request.html)
        val dayColumns = document.select(".day-col")
        val logoTitle = document.selectFirst(".logo-title")?.text().orEmpty()
        if (dayColumns.isEmpty() || logoTitle.isBlank()) return null

        val days = dayColumns.mapIndexed { dayIndex, column ->
            val dayLabel = column.selectFirst(".day-date")?.text().orEmpty()
            val dayTitle = column.selectFirst(".day-name")?.text().orEmpty()
            val items = column.select(".stop-card").mapIndexed { itemIndex, card ->
                PlanItemDraft(
                    id = "dalian-$dayIndex-$itemIndex",
                    time = card.selectFirst(".card-time")?.text()?.ifBlank { null },
                    title = card.selectFirst(".card-title")?.text().orEmpty(),
                    category = card.attr("data-cat").ifBlank {
                        card.selectFirst(".cat-badge")?.text().orEmpty()
                    }.ifBlank { null },
                    cost = card.selectFirst(".card-cost")?.text()?.ifBlank { null },
                    notes = card.selectFirst(".detail-note, .card-note, .card-desc")?.text()?.ifBlank { null },
                )
            }
            PlanDayDraft(
                id = "dalian-day-$dayIndex",
                label = dayLabel,
                title = dayTitle,
                items = items,
            )
        }

        val sections = buildList {
            document.selectFirst(".budget-panel")?.let { panel ->
                val content = panel.select(".budget-sub, .budget-section-title, .budget-row, .day-budget-row, .budget-total-row, .budget-note")
                    .joinToString("\n") { it.text() }
                if (content.isNotBlank()) add(PlanSectionDraft("行程预算", content))
            }
            document.select(".info-card").forEach { card ->
                val title = card.selectFirst("summary")?.text().orEmpty().removeSuffix("▼").trim()
                val content = card.selectFirst(".info-card-body")?.text().orEmpty()
                if (title.isNotBlank() && content.isNotBlank()) add(PlanSectionDraft(title, content))
            }
        }

        return ParsedPlan(
            title = logoTitle,
            capability = PlanCapability.ENHANCED,
            destinations = listOf(
                DestinationDraft("大连", "CN", 38.9140, 121.6147),
            ),
            days = days,
            sections = sections,
        )
    }
}

class GenericHtmlParser : PlanParser {
    override fun parse(request: ParseRequest): ParsedPlan {
        val document = Jsoup.parse(request.html)
        return ParsedPlan(
            title = document.title().ifBlank { request.fileName.substringBeforeLast('.') },
            capability = PlanCapability.VIEW_ONLY,
            destinations = DestinationHintExtractor.extract(document),
        )
    }
}

class CompositePlanParser(
    private val parsers: List<PlanParser> = listOf(
        LujianJsonParser(),
        DalianTemplateParser(),
        GenericHtmlParser(),
    ),
) : PlanParser {
    override fun parse(request: ParseRequest): ParsedPlan? = parsers.firstNotNullOfOrNull { it.parse(request) }
}

private fun JSONObject.optNullableString(name: String): String? =
    optString(name).ifBlank { null }

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name).takeIf(Double::isFinite) else null

private fun JSONObject.optLatitude(name: String): Double? =
    optNullableDouble(name)?.takeIf { it in -90.0..90.0 }

private fun JSONObject.optLongitude(name: String): Double? =
    optNullableDouble(name)?.takeIf { it in -180.0..180.0 }

private fun JSONObject.optMapLinks(): PlanMapLinks {
    val links = optJSONObject("mapLinks") ?: return PlanMapLinks()
    return PlanMapLinks(
        amap = links.optNullableString("amap"),
        baidu = links.optNullableString("baidu"),
    )
}
