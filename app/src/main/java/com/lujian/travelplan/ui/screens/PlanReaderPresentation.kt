package com.lujian.travelplan.ui.screens

import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.model.PlanMapLinks

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

enum class PlanReaderPage(val label: String) {
    ITINERARY("🗓️ 行程"),
    MAP("🗺️ 每日地图"),
    BUDGET("💰 预算"),
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
            pagerTarget = selected.takeIf { activePage != PlanReaderPage.BUDGET },
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

fun buildDailyMapRoute(plan: ParsedPlan, day: PlanDayDraft): List<DailyMapStop> {
    val places = plan.places.associateBy { it.id }
    return day.items.map { item ->
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
            mapLinks = if (itemLinks.amap != null || itemLinks.baidu != null) itemLinks else place?.mapLinks ?: PlanMapLinks(),
        )
    }
}
