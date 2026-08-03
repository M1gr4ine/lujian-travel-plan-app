package com.lujian.travelplan.parser

import com.lujian.travelplan.model.DestinationDraft
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.model.PlanItemDraft
import com.lujian.travelplan.model.PlanMapLinks
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
                PlanDayDraft(
                    id = day.optString("id", "day-$dayIndex"),
                    label = day.optString("label"),
                    title = day.optString("title"),
                    items = items,
                    summary = day.optNullableString("summary"),
                    budget = day.optNullableString("budget"),
                    backup = day.optNullableString("backup"),
                )
            }
        }.orEmpty()

        val places = root.optJSONArray("places")?.let { array ->
            List(array.length()) { index -> array.optJSONObject(index) }
                .filterNotNull()
                .mapIndexed { index, place ->
                    val coordinates = place.optJSONObject("coordinates")
                    PlanPlaceDraft(
                        id = place.optString("id", "place-$index"),
                        name = place.optString("name"),
                        address = place.optNullableString("address"),
                        latitude = place.optLatitude("latitude")
                            ?: coordinates?.optLatitude("latitude")
                            ?: coordinates?.optLatitude("lat"),
                        longitude = place.optLongitude("longitude")
                            ?: coordinates?.optLongitude("longitude")
                            ?: coordinates?.optLongitude("lng")
                            ?: coordinates?.optLongitude("lon"),
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
            title = root.optString("title").ifBlank { document.title().ifBlank { request.fileName } },
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
