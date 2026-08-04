package com.lujian.travelplan.ui.screens

import com.lujian.travelplan.data.PlanPhoto
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalGalleryPolicyTest {
    private fun plan(id: Long, title: String, coverAt: Long?, photoAt: Long) = StoredPlan(
        id = id,
        parsed = ParsedPlan(title = title, capability = PlanCapability.ENHANCED),
        sourceFileName = "$id.html",
        rawPath = "plans/$id/raw.html",
        generatedPath = null,
        thumbnailPath = null,
        compatibilityMode = false,
        updatedAt = 1,
        customCoverPath = coverAt?.let { "plans/$id/cover.jpg" },
        customCoverAddedAt = coverAt,
        photos = listOf(PlanPhoto(id, "pin-$id", "地点$id", "plans/$id/photo.jpg", photoAt, null)),
    )

    @Test
    fun `全局相册按时间混排所有计划照片`() {
        val result = GlobalGalleryPolicy.recent(
            listOf(plan(1, "大连", 5, 30), plan(2, "青岛", 20, 10)),
        )

        assertEquals(listOf("大连", "青岛", "青岛", "大连"), result.map { it.planTitle })
    }

    @Test
    fun `全局相册按计划分组且过滤空相册`() {
        val empty = plan(3, "空计划", null, 1).copy(photos = emptyList())
        val groups = GlobalGalleryPolicy.byPlan(listOf(plan(1, "大连", 5, 30), empty))

        assertEquals(listOf("大连"), groups.map { it.planTitle })
        assertEquals(2, groups.single().items.size)
    }
}
