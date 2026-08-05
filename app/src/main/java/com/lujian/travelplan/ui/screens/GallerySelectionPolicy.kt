package com.lujian.travelplan.ui.screens

import com.lujian.travelplan.data.GalleryDeleteRequest

sealed interface GallerySelectionKey {
    val planId: Long

    data class Photo(
        override val planId: Long,
        val photoId: Long,
    ) : GallerySelectionKey

    data class Cover(
        override val planId: Long,
    ) : GallerySelectionKey
}

data class GallerySelectionSummary(
    val photos: Int,
    val covers: Int,
) {
    val confirmation: String = when {
        photos == 0 && covers == 0 -> "没有选择可删除项目"
        else -> buildString {
            append("删除")
            if (photos > 0) append(" $photos 张照片")
            if (photos > 0 && covers > 0) append("和")
            if (covers > 0) append(" $covers 张自定义预览图")
            append("？")
        }
    }
}

object GallerySelectionPolicy {
    fun selectAll(keys: Collection<GallerySelectionKey>): Set<GallerySelectionKey> = keys.toSet()

    fun retainAvailable(
        selected: Set<GallerySelectionKey>,
        available: Set<GallerySelectionKey>,
    ): Set<GallerySelectionKey> = selected intersect available

    fun summary(selected: Set<GallerySelectionKey>): GallerySelectionSummary = GallerySelectionSummary(
        photos = selected.count { it is GallerySelectionKey.Photo },
        covers = selected.count { it is GallerySelectionKey.Cover },
    )
}

internal fun Set<GallerySelectionKey>.toDeleteRequest(): GalleryDeleteRequest = GalleryDeleteRequest(
    photoIds = filterIsInstance<GallerySelectionKey.Photo>().mapTo(mutableSetOf()) { it.photoId },
    coverPlanIds = filterIsInstance<GallerySelectionKey.Cover>().mapTo(mutableSetOf()) { it.planId },
)

internal fun PlanGalleryItem.selectionKey(planId: Long): GallerySelectionKey = when (this) {
    is PlanGalleryItem.Cover -> GallerySelectionKey.Cover(planId)
    is PlanGalleryItem.Photo -> GallerySelectionKey.Photo(planId, value.id)
}
