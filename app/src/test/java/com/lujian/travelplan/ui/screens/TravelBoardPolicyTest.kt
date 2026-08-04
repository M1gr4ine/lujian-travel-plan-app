package com.lujian.travelplan.ui.screens

import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelBoardPolicyTest {
    @Test
    fun 计划板和足迹板按归档时间分流() {
        val active = storedPlan(1, null)
        val archived = storedPlan(2, 10)

        assertEquals(
            listOf(active),
            TravelBoardPolicy.plansFor(TravelBoard.PLANS, listOf(active, archived)),
        )
        assertEquals(
            listOf(archived),
            TravelBoardPolicy.plansFor(TravelBoard.FOOTPRINTS, listOf(active, archived)),
        )
    }

    @Test
    fun 计划板操作归档而足迹板操作移回() {
        assertTrue(TravelBoardPolicy.archiveValue(TravelBoard.PLANS))
        assertFalse(TravelBoardPolicy.archiveValue(TravelBoard.FOOTPRINTS))
    }
}

private fun storedPlan(id: Long, archivedAt: Long?) = StoredPlan(
    id = id,
    parsed = ParsedPlan(title = "测试计划", capability = PlanCapability.ENHANCED),
    sourceFileName = "test.html",
    rawPath = "plans/$id/raw.html",
    generatedPath = null,
    thumbnailPath = null,
    compatibilityMode = false,
    updatedAt = 1,
    archivedAt = archivedAt,
)
