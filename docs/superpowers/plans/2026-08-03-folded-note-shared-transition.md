# Folded Note Shared Transition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将计划库缩略图框改成折角便签，并让便签框平滑展开为详情页、返回时反向收回，同时保留框外灰色元数据。

**Architecture:** 用独立 `FoldedNoteShape` 与绘制修饰符封装便签轮廓；在根 `SharedTransitionLayout` 中用相同 plan id 的 `sharedBounds` 连接计划库与详情页。Navigation 只为详情路由保留轻淡入，减少动画模式关闭共享缩放。

**Tech Stack:** Kotlin 2.3.21、Jetpack Compose BOM 2026.06.00、Navigation Compose 2.9.8、Compose UI Test、adb/gfxinfo。

## Global Constraints

- 仅计划库卡片进入详情使用共享展开；首页地图进入详情维持普通动画。
- 共享边界时长 280ms，详情内容在 55ms 后淡入；返回动画对称。
- 缩略图只在 `remember(plan.thumbnailPath)` 中解码一次，不在动画帧内创建位图。
- 管理模式点击只选择，不导航。
- `reduceMotion=true` 时不用共享缩放，只短淡入淡出。
- 不卸载 App、不清数据，最终用 `adb install -r` 覆盖安装。

---

### Task 1: 折角便签组件

**Files:**
- Create: `app/src/main/java/com/lujian/travelplan/ui/components/FoldedPlanNote.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/PlanLibraryScreen.kt`
- Test: `app/src/androidTest/java/com/lujian/travelplan/ui/LujianUiTest.kt`

**Interfaces:**
- Produces: `FoldedNoteShape(cornerRadius: Dp, foldSize: Dp) : Shape`
- Produces: `Modifier.foldedNoteDecoration(shape: FoldedNoteShape, foldSize: Dp, lineWidth: Dp)`
- Produces: plan card semantics `"<title>折角便签"`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun 计划库使用折角便签且普通模式只打开一次() {
    var opened = 0
    composeRule.setContent {
        LujianTheme { PlanLibraryScreen(listOf(testPlan()), {}, { opened++ }) }
    }
    composeRule.onNodeWithContentDescription("日期轴测试折角便签")
        .assertIsDisplayed()
        .performClick()
    assertEquals(1, opened)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :app:assembleDebugAndroidTest` and the single instrumentation class.
Expected: FAIL because the folded-note semantic node does not exist.

- [ ] **Step 3: Implement the folded note**

```kotlin
class FoldedNoteShape(
    private val cornerRadius: Dp = 18.dp,
    private val foldSize: Dp = 34.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline.Generic {
        val radius = with(density) { cornerRadius.toPx() }.coerceAtMost(size.minDimension / 4f)
        val fold = with(density) { foldSize.toPx() }.coerceAtMost(size.minDimension / 3f)
        return Outline.Generic(Path().apply {
            moveTo(radius, 0f)
            lineTo(size.width - fold, 0f)
            lineTo(size.width, fold)
            lineTo(size.width, size.height - radius)
            quadraticTo(size.width, size.height, size.width - radius, size.height)
            lineTo(radius, size.height)
            quadraticTo(0f, size.height, 0f, size.height - radius)
            lineTo(0f, radius)
            quadraticTo(0f, 0f, radius, 0f)
            close()
        })
    }
}

fun Modifier.foldedNoteDecoration(...): Modifier = clip(shape)
    .background(PaperDeep)
    .drawWithContent {
        drawContent()
        val fold = foldSize.toPx()
        val foldPath = Path().apply {
            moveTo(size.width - fold, 0f)
            lineTo(size.width - fold, fold)
            lineTo(size.width, fold)
            close()
        }
        drawPath(foldPath, Paper)
        drawLine(Ink, Offset(size.width - fold, 0f), Offset(size.width - fold, fold), lineWidth.toPx())
        drawLine(Ink, Offset(size.width - fold, fold), Offset(size.width, fold), lineWidth.toPx())
    }
    .border(lineWidth, Ink, shape)
```

`PlanPreviewCard` keeps the gray metadata outside the note. Only the square cover gets the 3dp folded-note frame and shared-bounds source; move the management check to `TopStart` so it never overlaps the fold.

- [ ] **Step 4: Run the focused test**

Expected: PASS and the click callback count is exactly 1.

### Task 2: 共享边界导航

**Files:**
- Create: `app/src/main/java/com/lujian/travelplan/ui/PlanSharedTransition.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/LujianRoot.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/PlanLibraryScreen.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/PlanDetailScreen.kt`
- Test: `app/src/test/java/com/lujian/travelplan/core/PolicyTest.kt`

**Interfaces:**
- Produces: `PlanSharedTransitionScopes(shared: SharedTransitionScope, visibility: AnimatedVisibilityScope)`
- Produces: `Modifier.planSharedBounds(planId: Long, scopes: PlanSharedTransitionScopes?, enabled: Boolean)`
- Produces: `PlanNoteTransitionPolicy.useSharedBounds(fromRoute: String?, reduceMotion: Boolean): Boolean`

- [ ] **Step 1: Write the failing policy test**

```kotlin
assertTrue(PlanNoteTransitionPolicy.useSharedBounds("library", false))
assertFalse(PlanNoteTransitionPolicy.useSharedBounds("home", false))
assertFalse(PlanNoteTransitionPolicy.useSharedBounds("library", true))
```

- [ ] **Step 2: Run the focused unit test and verify RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests com.lujian.travelplan.core.PolicyTest`
Expected: compilation failure because the policy is missing.

- [ ] **Step 3: Wire the scopes and bounds**

```kotlin
SharedTransitionLayout {
    NavHost(...) {
        composable("library") {
            PlanLibraryScreen(..., transitionScopes = PlanSharedTransitionScopes(this@SharedTransitionLayout, this))
        }
        composable("detail/{planId}", enterTransition = { EnterTransition.None }) {
            PlanDetailScreen(..., transitionScopes = PlanSharedTransitionScopes(this@SharedTransitionLayout, this))
        }
    }
}
```

The source thumbnail note and detail root apply:

```kotlin
sharedBounds(
    sharedContentState = rememberSharedContentState("plan-note-$planId"),
    animatedVisibilityScope = scopes.visibility,
    boundsTransform = { _, _ -> tween(280, easing = SmoothPageEasing) },
    enter = fadeIn(tween(180, delayMillis = 55)),
    exit = fadeOut(tween(120)),
    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
)
```

All library notes expose their keyed source when motion is enabled. The detail target enables its matching key only when `navController.previousBackStackEntry?.destination?.route == "library"`; home navigation therefore has no target pair to reuse.

- [ ] **Step 4: Run unit and instrumentation tests**

Expected: policy GREEN; plan-library tests remain GREEN.

### Task 3: 发布前验证与真机动画检查

**Files:**
- Modify: `README.md`
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: folded note and shared transition from Tasks 1–2.
- Produces: installable v1.1.0 debug APK and recorded device evidence.

- [ ] **Step 1: Update README**

Add folded-note shared expansion to the plan-library and version sections; keep the existing 1.1.0/versionCode 3 values.

- [ ] **Step 2: Run static and automated gates**

Run: `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
Expected: exit 0, all tests pass, lint has 0 errors.

- [ ] **Step 3: Install to an Android virtual device**

Run: `adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk`
Expected: `Success`; do not overwrite the physical device in this round.

- [ ] **Step 4: Exercise enter and return animations**

Reset frame stats, open the library note, wait for detail, press Back, then repeat with another note or the fallback cover. Capture screenshots and `dumpsys gfxinfo com.lujian.travelplan framestats`.

- [ ] **Step 5: Commit**

Commit the folded-note implementation, regression tests, plan, and README with a Chinese conventional commit message. Keep it on the feature branch; merge, push, physical-device install, tag and GitHub Release wait for the next real-device acceptance round.
