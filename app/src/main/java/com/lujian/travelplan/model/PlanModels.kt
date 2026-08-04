package com.lujian.travelplan.model

enum class PlanCapability {
    ENHANCED,
    VIEW_ONLY,
}

data class DestinationDraft(
    val name: String,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
)

data class PlanItemDraft(
    val id: String,
    val time: String?,
    val title: String,
    val category: String?,
    val cost: String?,
    val notes: String?,
)

data class PlanDayDraft(
    val id: String,
    val label: String,
    val title: String,
    val items: List<PlanItemDraft>,
)

data class PlanSectionDraft(
    val title: String,
    val content: String,
)

data class ParsedPlan(
    val title: String,
    val capability: PlanCapability,
    val destinations: List<DestinationDraft> = emptyList(),
    val days: List<PlanDayDraft> = emptyList(),
    val sections: List<PlanSectionDraft> = emptyList(),
)
