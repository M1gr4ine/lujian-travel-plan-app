package com.lujian.travelplan.ui.screens

import com.lujian.travelplan.data.PlanPhoto
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.model.PlanItemDraft
import com.lujian.travelplan.model.PlanMapStopDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanGalleryPolicyTest {
    private val plan = StoredPlan(
        id = 7,
        parsed = ParsedPlan(
            title = "大连",
            capability = PlanCapability.ENHANCED,
            days = listOf(
                PlanDayDraft(
                    id = "d1",
                    label = "第一天",
                    title = "海边",
                    items = listOf(
                        PlanItemDraft("pin-a", "09:00", "星海广场", null, null, null),
                        PlanItemDraft("pin-b", "11:00", "海边咖啡", null, null, null),
                    ),
                    mapStops = listOf(
                        PlanMapStopDraft("pin-a", "星海广场"),
                        PlanMapStopDraft("pin-c", "跨海大桥"),
                    ),
                ),
            ),
        ),
        sourceFileName = "dalian.html",
        rawPath = "plans/7/raw.html",
        generatedPath = null,
        thumbnailPath = null,
        compatibilityMode = false,
        updatedAt = 1,
        customCoverPath = "plans/7/cover/cover.jpg",
        customCoverAddedAt = 20,
        photos = listOf(
            PlanPhoto(1, "pin-a", "星海广场", "plans/7/photos/a.jpg", 10, null),
            PlanPhoto(2, "pin-c", "跨海大桥", "plans/7/photos/c.jpg", 30, null),
        ),
    )

    @Test
    fun `按加入时间包含封面并倒序`() {
        assertEquals(
            listOf("plans/7/photos/c.jpg", "plans/7/cover/cover.jpg", "plans/7/photos/a.jpg"),
            PlanGalleryPolicy.recent(plan).map { it.relativePath },
        )
    }

    @Test
    fun `按大头针依照行程细分并保留地图额外地点`() {
        assertEquals(
            listOf("计划封面", "星海广场", "跨海大桥"),
            PlanGalleryPolicy.groups(plan).map { it.title },
        )
        assertEquals(
            listOf("pin-a", "pin-b", "pin-c"),
            PlanGalleryPolicy.pins(plan).map { it.id },
        )
    }
}
