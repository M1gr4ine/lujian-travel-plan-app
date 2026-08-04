package com.lujian.travelplan.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyMapInteractionPolicyTest {
    @Test
    fun `切换地点时先关闭旧气泡再在居中完成后显示新气泡`() {
        val oldMarker = FakeMarker("旧地点", visible = true)
        val newMarker = FakeMarker("新地点", visible = false)
        val events = mutableListOf<String>()
        var finishCentering: () -> Unit = {}

        focusSingleMapInfoWindow(
            markers = listOf(oldMarker, newMarker),
            isShown = FakeMarker::visible,
            hide = { marker ->
                marker.visible = false
                events += "关闭-${marker.name}"
            },
            center = { onFinished ->
                events += "开始居中"
                finishCentering = onFinished
            },
            show = {
                newMarker.visible = true
                events += "显示-${newMarker.name}"
            },
        )

        assertEquals(listOf("关闭-旧地点", "开始居中"), events)
        assertEquals(emptyList<String>(), listOf(oldMarker, newMarker).filter(FakeMarker::visible).map(FakeMarker::name))

        finishCentering()

        assertEquals(listOf("关闭-旧地点", "开始居中", "显示-新地点"), events)
        assertEquals(listOf("新地点"), listOf(oldMarker, newMarker).filter(FakeMarker::visible).map(FakeMarker::name))
    }

    @Test
    fun `开启拖动时取消相机过渡并关闭拖动惯性`() {
        val events = mutableListOf<String>()

        applyDailyMapDragMode(
            dragEnabled = true,
            cancelTransitions = { events += "取消相机过渡" },
            configureGestures = { scrollEnabled, flingEnabled ->
                events += "拖动=$scrollEnabled,惯性=$flingEnabled"
            },
        )

        assertEquals(listOf("取消相机过渡", "拖动=true,惯性=false"), events)
    }

    @Test
    fun `只有用户手势移动地图时关闭当前气泡`() {
        assertTrue(shouldDismissMapInfoWindowOnCameraMove(reason = 1, gestureReason = 1))
        assertFalse(shouldDismissMapInfoWindowOnCameraMove(reason = 2, gestureReason = 1))
        assertFalse(shouldDismissMapInfoWindowOnCameraMove(reason = 3, gestureReason = 1))
    }

    @Test
    fun `地图拖动开启时关闭外层日期分页手势`() {
        assertFalse(shouldEnableDayPaging(mapDragEnabled = true))
        assertTrue(shouldEnableDayPaging(mapDragEnabled = false))
    }

    @Test
    fun `地图拖动开启时关闭外层纵向列表手势`() {
        assertFalse(shouldEnableDailyMapListScroll(mapDragEnabled = true))
        assertTrue(shouldEnableDailyMapListScroll(mapDragEnabled = false))
    }

    private data class FakeMarker(
        val name: String,
        var visible: Boolean,
    )
}
