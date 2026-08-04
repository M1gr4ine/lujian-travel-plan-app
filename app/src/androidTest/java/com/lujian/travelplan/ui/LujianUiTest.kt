package com.lujian.travelplan.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
    fun `详情返回阶段仍保留计划库进入时的共享动画`() {
        val previousRoute = mutableStateOf<String?>("library")
        composeRule.setContent {
            val enabled = rememberPlanNoteSharedBoundsEnabled(
                entryKey = "detail-7",
                fromRoute = previousRoute.value,
                reduceMotion = false,
            )
            Text(if (enabled) "共享动画开启" else "共享动画关闭")
        }

        composeRule.onNodeWithText("共享动画开启").assertIsDisplayed()
        composeRule.runOnIdle { previousRoute.value = "home" }
        composeRule.onNodeWithText("共享动画开启").assertIsDisplayed()
    }

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
    fun 计划库使用可整体点击的折角便签() {
        val plan = StoredPlan(
            id = 7,
            parsed = ParsedPlan(
                title = "大连旅行计划",
                capability = PlanCapability.ENHANCED,
                days = listOf(PlanDayDraft("d1", "9月24日", "海边散步", emptyList())),
            ),
            sourceFileName = "dalian.html",
            rawPath = "dalian.html",
            generatedPath = null,
            thumbnailPath = null,
            compatibilityMode = false,
            updatedAt = 1,
        )
        var openedId: Long? = null
        composeRule.setContent {
            LujianTheme {
                PlanLibraryScreen(listOf(plan), onImport = {}, onOpenPlan = { openedId = it })
            }
        }

        composeRule.onNodeWithContentDescription("大连旅行计划折角便签")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { check(openedId == 7L) }
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

        composeRule.onNodeWithText("🗺️ 每日地图").performClick()
        composeRule.onNodeWithText("9月2日").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第二天").assertIsDisplayed()
        composeRule.onNodeWithText("第二天内容").assertIsDisplayed()
        composeRule.onNodeWithText("第二天内容").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第一天内容").assertIsDisplayed()
        composeRule.onNodeWithText("第一天内容").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第二天内容").assertIsDisplayed()

        composeRule.onNodeWithText("🗓️ 行程").performClick()
        composeRule.onNodeWithText("第二天内容").assertIsDisplayed()
        composeRule.onNodeWithText("9月1日").performClick()
        composeRule.onNodeWithText("第一天内容").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("🗺️ 在每日地图中查看").assertIsDisplayed()
        composeRule.onNodeWithText("🏖️ attraction").assertIsDisplayed()

        composeRule.onNodeWithText("第一天内容").assertIsDisplayed().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第二天内容").assertIsDisplayed()
    }
}
