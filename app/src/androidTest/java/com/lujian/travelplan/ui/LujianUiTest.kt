package com.lujian.travelplan.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun 原生阅读器支持日期切换预算页和卡片跳转地图() {
        val plan = StoredPlan(
            id = 1,
            parsed = ParsedPlan(
                title = "日期轴测试",
                capability = PlanCapability.ENHANCED,
                budget = "¥3,000",
                days = listOf(
                    PlanDayDraft("d1", "9月1日", "第一天", listOf(PlanItemDraft("i1", "09:00", "第一天内容", "attraction", null, "第一天说明")), budget = "¥1,500"),
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

        composeRule.onNodeWithText("🗓️ 行程").assertIsDisplayed()
        composeRule.onNodeWithText("🗺️ 每日地图").assertIsDisplayed()
        composeRule.onNodeWithText("💰 预算").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("行程总预算").assertIsDisplayed()
        composeRule.onNodeWithText("¥3,000").assertIsDisplayed()

        composeRule.onNodeWithText("🗓️ 行程").performClick()
        composeRule.onNodeWithText("第一天内容").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("🗺️ 在每日地图中查看").assertIsDisplayed()
        composeRule.onNodeWithText("🏖️ attraction").assertIsDisplayed()

        composeRule.onNodeWithText("第一天内容").assertIsDisplayed().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第二天内容").assertIsDisplayed()
    }
}
