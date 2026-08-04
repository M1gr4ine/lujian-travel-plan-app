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
    val placeId: String? = null,
    val transport: String? = null,
    val mapLinks: PlanMapLinks = PlanMapLinks(),
)

data class PlanDayDraft(
    val id: String,
    val label: String,
    val title: String,
    val items: List<PlanItemDraft>,
    val summary: String? = null,
    val budget: String? = null,
    val backup: String? = null,
    val distanceEstimate: String? = null,
    val durationEstimate: String? = null,
    val mapStops: List<PlanMapStopDraft> = emptyList(),
    val mapLegs: List<PlanMapLegDraft> = emptyList(),
)

data class PlanMapStopDraft(
    val id: String,
    val title: String,
    val time: String? = null,
    val category: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class PlanMapLegDraft(
    val id: String,
    val fromId: String,
    val toId: String,
    val mode: String? = null,
    val summary: String? = null,
)

data class PlanMapLinks(
    val amap: String? = null,
    val baidu: String? = null,
)

data class PlanPlaceDraft(
    val id: String,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mapLinks: PlanMapLinks = PlanMapLinks(),
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
    val dateRange: String? = null,
    val travelers: String? = null,
    val style: String? = null,
    val baseArea: String? = null,
    val budget: String? = null,
    val accommodationBudget: String? = null,
    val assumptions: List<String> = emptyList(),
    val places: List<PlanPlaceDraft> = emptyList(),
    /** 原始增强契约，用于导出时透传技能端的来源、警告和地点证据字段。 */
    val sourcePayloadJson: String? = null,
)
