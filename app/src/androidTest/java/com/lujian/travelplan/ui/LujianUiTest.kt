package com.lujian.travelplan.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.model.PlanItemDraft
import com.lujian.travelplan.ui.screens.NativePlanReader
import com.lujian.travelplan.ui.screens.PlanLibraryScreen
import com.lujian.travelplan.ui.theme.LujianTheme
import org.junit.Rule
import org.junit.Test

class LujianUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 空计划库显示同尺寸添加入口() {
        composeRule.setContent {
            LujianTheme {
                PlanLibraryScreen(emptyList(), onImport = {}, onOpenPlan = {})
            }
        }

        composeRule.onNodeWithText("添加计划").assertIsDisplayed()
        composeRule.onNodeWithText("选择 HTML 文件").assertIsDisplayed()
    }

    @Test
    fun 左滑内容会切换顶部日期对应的单日页面() {
        val plan = StoredPlan(
            id = 1,
            parsed = ParsedPlan(
                title = "日期轴测试",
                capability = PlanCapability.ENHANCED,
                days = listOf(
                    PlanDayDraft("d1", "9月1日", "第一天", listOf(PlanItemDraft("i1", "09:00", "第一天内容", null, null, null))),
                    PlanDayDraft("d2", "9月2日", "第二天", listOf(PlanItemDraft("i2", "10:00", "第二天内容", null, null, null))),
                ),
            ),
            sourceFileName = "test.html",
            rawPath = "test.html",
            generatedPath = null,
            thumbnailPath = null,
            compatibilityMode = false,
            updatedAt = 1,
        )
        composeRule.setContent { LujianTheme { NativePlanReader(plan) } }

        composeRule.onNodeWithText("第一天内容").assertIsDisplayed().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第二天内容").assertIsDisplayed()
    }
}
