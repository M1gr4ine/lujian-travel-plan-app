package com.lujian.travelplan.ui.screens

import com.lujian.travelplan.data.StoredPlan

data class GlobalGalleryItem(
    val planId: Long,
    val planTitle: String,
    val item: PlanGalleryItem,
) {
    val relativePath: String get() = item.relativePath
    val addedAt: Long get() = item.addedAt
}

data class GlobalPlanGallery(
    val planId: Long,
    val planTitle: String,
    val items: List<GlobalGalleryItem>,
)

object GlobalGalleryPolicy {
    fun recent(plans: List<StoredPlan>): List<GlobalGalleryItem> = plans.flatMap(::itemsOf)
        .sortedByDescending { it.addedAt }

    fun byPlan(plans: List<StoredPlan>): List<GlobalPlanGallery> = plans.mapNotNull { plan ->
        itemsOf(plan).takeIf { it.isNotEmpty() }?.let { items ->
            GlobalPlanGallery(plan.id, plan.parsed.title, items.sortedByDescending { it.addedAt })
        }
    }

    private fun itemsOf(plan: StoredPlan): List<GlobalGalleryItem> =
        PlanGalleryPolicy.recent(plan).map { item ->
            GlobalGalleryItem(plan.id, plan.parsed.title, item)
        }
}
