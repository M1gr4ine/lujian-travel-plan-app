package com.lujian.travelplan.ui.screens

import com.lujian.travelplan.data.PlanPhoto
import com.lujian.travelplan.data.StoredPlan

data class PhotoPin(val id: String, val title: String)

sealed interface PlanGalleryItem {
    val relativePath: String
    val addedAt: Long

    data class Cover(
        override val relativePath: String,
        override val addedAt: Long,
    ) : PlanGalleryItem

    data class Photo(val value: PlanPhoto) : PlanGalleryItem {
        override val relativePath: String = value.relativePath
        override val addedAt: Long = value.addedAt
    }
}

data class PlanGalleryGroup(
    val id: String,
    val title: String,
    val items: List<PlanGalleryItem>,
)

object PlanGalleryPolicy {
    fun recent(plan: StoredPlan): List<PlanGalleryItem> = buildList {
        plan.customCoverPath?.let { path ->
            add(PlanGalleryItem.Cover(path, plan.customCoverAddedAt ?: 0L))
        }
        addAll(plan.photos.map(PlanGalleryItem::Photo))
    }.sortedByDescending { it.addedAt }

    fun pins(plan: StoredPlan): List<PhotoPin> {
        val result = linkedMapOf<String, PhotoPin>()
        plan.parsed.days.forEach { day ->
            day.items.forEach { item ->
                if (item.id.isNotBlank()) result.putIfAbsent(item.id, PhotoPin(item.id, item.title))
            }
            buildDailyMapPresentation(plan.parsed, day).stops.forEach { stop ->
                if (stop.itemId.isNotBlank()) {
                    result.putIfAbsent(stop.itemId, PhotoPin(stop.itemId, stop.title))
                }
            }
        }
        return result.values.toList()
    }

    fun groups(plan: StoredPlan): List<PlanGalleryGroup> = buildList {
        plan.customCoverPath?.let { path ->
            add(
                PlanGalleryGroup(
                    id = "cover",
                    title = "计划封面",
                    items = listOf(PlanGalleryItem.Cover(path, plan.customCoverAddedAt ?: 0L)),
                ),
            )
        }
        val photosByPin = plan.photos.groupBy { it.pinId }
        val plannedPins = pins(plan)
        plannedPins.forEach { pin ->
            photosByPin[pin.id]?.let { photos ->
                add(
                    PlanGalleryGroup(
                        id = pin.id,
                        title = pin.title,
                        items = photos.sortedByDescending { it.addedAt }.map(PlanGalleryItem::Photo),
                    ),
                )
            }
        }
        val plannedIds = plannedPins.mapTo(mutableSetOf()) { it.id }
        plan.photos.filterNot { it.pinId in plannedIds }
            .groupBy { it.pinId }
            .forEach { (pinId, photos) ->
                add(
                    PlanGalleryGroup(
                        id = pinId,
                        title = photos.first().pinTitle,
                        items = photos.sortedByDescending { it.addedAt }.map(PlanGalleryItem::Photo),
                    ),
                )
            }
    }
}
