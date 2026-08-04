package com.lujian.travelplan.ui.screens

import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.model.PlanMapLinks
import com.lujian.travelplan.model.PlanMapStopDraft
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class DailyMapStop(
    val itemId: String,
    val title: String,
    val time: String?,
    val category: String?,
    val transport: String?,
    val latitude: Double?,
    val longitude: Double?,
    val mapLinks: PlanMapLinks,
)

data class DailyMapLeg(
    val id: String,
    val fromStop: DailyMapStop,
    val toStop: DailyMapStop,
    val mode: String,
    val summary: String,
)

data class DailyMapRoute(
    val stops: List<DailyMapStop>,
    val legs: List<DailyMapLeg>,
    val distanceEstimate: String,
    val durationEstimate: String,
)

enum class PlanReaderPage(val label: String) {
    ITINERARY("🗓️ 行程"),
    MAP("🗺️ 地图"),
    BUDGET("💰 预算"),
    ALBUM("📷 相册"),
}

data class PlanReaderDayAction(
    val selectedIndex: Int,
    val pagerTarget: Int?,
)

object PlanReaderDayPolicy {
    fun select(requestedIndex: Int, dayCount: Int, activePage: PlanReaderPage): PlanReaderDayAction {
        val selected = requestedIndex.coerceIn(0, (dayCount - 1).coerceAtLeast(0))
        return PlanReaderDayAction(
            selectedIndex = selected,
            pagerTarget = selected.takeIf {
                activePage == PlanReaderPage.ITINERARY || activePage == PlanReaderPage.MAP
            },
        )
    }
}

object PlanReaderPresentation {
    fun categoryLabel(category: String?): String {
        val label = category?.trim().orEmpty().ifBlank { "其他" }
        val normalized = label.lowercase()
        val emoji = when {
            normalized in setOf("hotel", "accommodation") || "住宿" in label || "酒店" in label -> "🏨"
            normalized in setOf("restaurant", "food") || "餐" in label || "美食" in label -> "🍜"
            normalized == "cafe" || "咖啡" in label -> "☕"
            normalized in setOf("attraction", "neighborhood") || "景点" in label || "街区" in label -> "🏖️"
            normalized in setOf("transport", "transit") || "交通" in label -> "🚆"
            normalized in setOf("shop", "shopping") || "购物" in label || "逛店" in label -> "🛍️"
            else -> "✦"
        }
        return "$emoji $label"
    }
}

fun buildDailyMapRoute(plan: ParsedPlan, day: PlanDayDraft): List<DailyMapStop> =
    buildDailyMapPresentation(plan, day).stops

fun buildDailyMapPresentation(plan: ParsedPlan, day: PlanDayDraft): DailyMapRoute {
    val places = plan.places.associateBy { it.id }
    val stops = if (day.mapStops.isNotEmpty()) {
        day.mapStops.map { stop -> explicitStop(stop, day, places) }
    } else {
        day.items.map { item ->
            val place = item.placeId?.let(places::get)
            val itemLinks = item.mapLinks
            DailyMapStop(
                itemId = item.id,
                title = item.title,
                time = item.time,
                category = item.category,
                transport = item.transport,
                latitude = place?.latitude,
                longitude = place?.longitude,
                mapLinks = if (itemLinks.amap != null || itemLinks.baidu != null) {
                    itemLinks
                } else {
                    place?.mapLinks ?: PlanMapLinks()
                },
            )
        }
    }
    val stopsById = if (day.mapStops.isNotEmpty()) {
        day.mapStops.zip(stops).associate { (draft, stop) -> draft.id to stop }
    } else {
        stops.associateBy { it.itemId }
    }
    val legs = if (day.mapLegs.isNotEmpty()) {
        day.mapLegs.mapNotNull { leg ->
            val from = stopsById[leg.fromId] ?: return@mapNotNull null
            val to = stopsById[leg.toId] ?: return@mapNotNull null
            val summary = leg.summary?.takeIf(String::isNotBlank) ?: "交通方式待补"
            DailyMapLeg(
                id = leg.id,
                fromStop = from,
                toStop = to,
                mode = routeMode(leg.mode ?: summary),
                summary = summary,
            )
        }
    } else {
        stops.zipWithNext().mapIndexed { index, (from, to) ->
            val summary = from.transport?.takeIf(String::isNotBlank) ?: "交通方式待补"
            DailyMapLeg(
                id = "leg-${index + 1}",
                fromStop = from,
                toStop = to,
                mode = routeMode(summary),
                summary = summary,
            )
        }
    }
    val estimate = estimateDailyRoute(legs)
    return DailyMapRoute(
        stops = stops,
        legs = legs,
        distanceEstimate = day.distanceEstimate?.takeIf(String::isNotBlank) ?: estimate.first,
        durationEstimate = day.durationEstimate?.takeIf(String::isNotBlank) ?: estimate.second,
    )
}

private fun explicitStop(
    stop: PlanMapStopDraft,
    day: PlanDayDraft,
    places: Map<String, com.lujian.travelplan.model.PlanPlaceDraft>,
): DailyMapStop {
    val normalizedStopTitle = normalizeMapTitle(stop.title)
    val linkedItem = day.items.firstOrNull { item ->
        item.id == stop.id || item.placeId == stop.id || titlesOverlap(normalizedStopTitle, normalizeMapTitle(item.title))
    } ?: day.items.firstOrNull { item ->
        isHotelCategory(stop.category) && isHotelCategory(item.category)
    }
    val linkedPlace = linkedItem?.placeId?.let(places::get)
    val itemLinks = linkedItem?.mapLinks ?: PlanMapLinks()
    return DailyMapStop(
        itemId = linkedItem?.id ?: stop.id,
        title = stop.title,
        time = stop.time ?: linkedItem?.time,
        category = linkedItem?.category?.takeIf(String::isNotBlank) ?: stop.category,
        transport = linkedItem?.transport,
        latitude = stop.latitude ?: linkedPlace?.latitude,
        longitude = stop.longitude ?: linkedPlace?.longitude,
        mapLinks = if (itemLinks.amap != null || itemLinks.baidu != null) {
            itemLinks
        } else {
            linkedPlace?.mapLinks ?: PlanMapLinks()
        },
    )
}

private fun normalizeMapTitle(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

private fun titlesOverlap(first: String, second: String): Boolean =
    first.length >= 3 && second.length >= 3 && (first in second || second in first)

private fun isHotelCategory(value: String?): Boolean =
    value.equals("hotel", ignoreCase = true) || value?.contains("酒店") == true || value?.contains("住宿") == true

private fun routeMode(value: String): String = when {
    value.equals("walk", ignoreCase = true) || "步行" in value || "徒步" in value -> "walk"
    value.equals("drive", ignoreCase = true) || listOf("打车", "出租", "网约", "驾车", "接送").any(value::contains) -> "drive"
    else -> "ride"
}

private fun estimateDailyRoute(legs: List<DailyMapLeg>): Pair<String, String> {
    val usable = legs.filter {
        it.fromStop.latitude != null && it.fromStop.longitude != null &&
            it.toStop.latitude != null && it.toStop.longitude != null
    }
    if (usable.isEmpty()) return "待补" to "待补"

    var distance = 0.0
    var minutes = 0.0
    usable.forEach { leg ->
        val segment = haversineKm(leg.fromStop, leg.toStop) * 1.22
        val speed = when (leg.mode) {
            "walk" -> 4.5
            "drive" -> 28.0
            else -> 22.0
        }
        distance += segment
        minutes += segment / speed * 60 + if (leg.mode == "walk") 0 else 3
    }
    val roundedMinutes = ((minutes / 5).roundToInt() * 5).coerceAtLeast(1)
    val duration = if (roundedMinutes >= 60) {
        val remainder = roundedMinutes % 60
        "约 ${roundedMinutes / 60} 小时${if (remainder == 0) "" else " $remainder 分钟"}"
    } else {
        "约 $roundedMinutes 分钟"
    }
    return String.format(Locale.US, "约 %.1f km", distance) to duration
}

private fun haversineKm(from: DailyMapStop, to: DailyMapStop): Double {
    val earthRadius = 6371.0
    val fromLatitude = Math.toRadians(requireNotNull(from.latitude))
    val toLatitude = Math.toRadians(requireNotNull(to.latitude))
    val deltaLatitude = toLatitude - fromLatitude
    val deltaLongitude = Math.toRadians(requireNotNull(to.longitude) - requireNotNull(from.longitude))
    val value = sin(deltaLatitude / 2) * sin(deltaLatitude / 2) +
        cos(fromLatitude) * cos(toLatitude) * sin(deltaLongitude / 2) * sin(deltaLongitude / 2)
    return earthRadius * 2 * atan2(sqrt(value), sqrt(1 - value))
}
