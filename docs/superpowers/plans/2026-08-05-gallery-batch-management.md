# 相册批量管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为计划内相册和全局相册增加可选择地点照片与自定义预览图的批量管理、批量删除能力，并完成虚拟机验证与 GitHub Release 发布。

**Architecture:** 使用独立的 `GallerySelectionKey` 表示照片和封面，纯策略层负责选择去重、失效项收敛和删除摘要。仓储层在 Room 事务中删除照片记录、清空封面字段并收集私有路径，事务后通过现有 `PlanMediaStore` 删除文件并刷新封面缩略图；两个 Compose 相册页面复用同一管理操作条和选择语义。

**Tech Stack:** Kotlin 2.3、Jetpack Compose Material 3、Room 2.8、Coroutines、JUnit 4、Compose UI Test、Gradle、ADB/Android Emulator、GitHub CLI。

---

## 文件结构

- 新建 `app/src/main/java/com/lujian/travelplan/ui/screens/GallerySelectionPolicy.kt`：稳定选择键、可选项和纯选择策略。
- 新建 `app/src/main/java/com/lujian/travelplan/ui/screens/GalleryManagementBar.kt`：两个相册复用的管理操作条和确认框。
- 新建 `app/src/test/java/com/lujian/travelplan/ui/screens/GallerySelectionPolicyTest.kt`：选择、去重、失效收敛和摘要单元测试。
- 修改 `app/src/main/java/com/lujian/travelplan/data/PlanMediaStore.kt`：私有文件删除改为幂等。
- 修改 `app/src/test/java/com/lujian/travelplan/data/PlanMediaStoreTest.kt`：覆盖已不存在文件和目录外路径。
- 修改 `app/src/main/java/com/lujian/travelplan/data/db/PlanDao.kt`：批量查询照片/计划、删除照片、清空封面。
- 修改 `app/src/main/java/com/lujian/travelplan/data/PlanRepository.kt`：批量删除请求、摘要与事务实现。
- 新建 `app/src/androidTest/java/com/lujian/travelplan/data/GalleryBatchDeletionTest.kt`：真实 Room 数据与私有文件批量删除测试。
- 修改 `app/src/main/java/com/lujian/travelplan/ui/screens/PlanGalleryPolicy.kt`：把计划 ID 映射到封面选择键。
- 修改 `app/src/main/java/com/lujian/travelplan/ui/screens/GlobalGalleryPolicy.kt`：输出全局相册稳定选择项。
- 修改 `app/src/main/java/com/lujian/travelplan/ui/screens/PlanGalleryScreen.kt`：计划相册管理状态、多选和批量删除。
- 修改 `app/src/main/java/com/lujian/travelplan/ui/screens/GlobalGalleryScreen.kt`：全局相册管理状态、多选和批量删除。
- 修改 `app/src/main/java/com/lujian/travelplan/ui/screens/PlanDetailScreen.kt`、`app/src/main/java/com/lujian/travelplan/ui/LujianRoot.kt`：仓储回调接线。
- 修改 `app/src/androidTest/java/com/lujian/travelplan/ui/LujianUiTest.kt`：两个相册的批量管理交互测试。
- 修改 `app/build.gradle.kts`、`README.md`：版本升级到 `1.2.0`/`versionCode 4` 并补充发布功能说明。
- 新建 `docs/releases/v1.2.0.md`：GitHub Release 的固定发布说明。

### Task 1: 选择模型与纯策略

**Files:**
- Create: `app/src/main/java/com/lujian/travelplan/ui/screens/GallerySelectionPolicy.kt`
- Create: `app/src/test/java/com/lujian/travelplan/ui/screens/GallerySelectionPolicyTest.kt`

- [ ] **Step 1: 写失败测试**

测试定义照片键、封面键、全选、失效项收敛和摘要：

```kotlin
@Test fun `全选去重并在数据刷新后移除失效项`() {
    val photo = GallerySelectionKey.Photo(planId = 1, photoId = 10)
    val cover = GallerySelectionKey.Cover(planId = 1)
    val selected = GallerySelectionPolicy.selectAll(listOf(photo, photo, cover))
    assertEquals(setOf(photo, cover), selected)
    assertEquals(setOf(cover), GallerySelectionPolicy.retainAvailable(selected, setOf(cover)))
}

@Test fun `删除摘要分别统计照片和封面`() {
    val summary = GallerySelectionPolicy.summary(
        setOf(GallerySelectionKey.Photo(1, 10), GallerySelectionKey.Cover(1)),
    )
    assertEquals("删除 1 张照片和 1 张自定义预览图？", summary.confirmation)
}
```

- [ ] **Step 2: 验证测试因类型不存在而失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*GallerySelectionPolicyTest" --no-configuration-cache`

Expected: FAIL，提示 `GallerySelectionKey` 或 `GallerySelectionPolicy` 未解析。

- [ ] **Step 3: 实现最小纯策略**

```kotlin
sealed interface GallerySelectionKey {
    val planId: Long
    data class Photo(override val planId: Long, val photoId: Long) : GallerySelectionKey
    data class Cover(override val planId: Long) : GallerySelectionKey
}

data class GallerySelectionSummary(val photos: Int, val covers: Int) {
    val confirmation: String = buildString {
        append("删除")
        if (photos > 0) append(" $photos 张照片")
        if (photos > 0 && covers > 0) append("和")
        if (covers > 0) append(" $covers 张自定义预览图")
        append("？")
    }
}

object GallerySelectionPolicy {
    fun selectAll(keys: Collection<GallerySelectionKey>) = keys.toSet()
    fun retainAvailable(selected: Set<GallerySelectionKey>, available: Set<GallerySelectionKey>) = selected intersect available
    fun summary(selected: Set<GallerySelectionKey>) = GallerySelectionSummary(
        photos = selected.count { it is GallerySelectionKey.Photo },
        covers = selected.count { it is GallerySelectionKey.Cover },
    )
}
```

- [ ] **Step 4: 验证单元测试转绿**

Run: `./gradlew :app:testDebugUnitTest --tests "*GallerySelectionPolicyTest" --no-configuration-cache`

Expected: PASS。

### Task 2: 私有文件幂等删除

**Files:**
- Modify: `app/src/main/java/com/lujian/travelplan/data/PlanMediaStore.kt`
- Modify: `app/src/test/java/com/lujian/travelplan/data/PlanMediaStoreTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
@Test fun 已不存在的私有文件视为删除成功() {
    val root = temporaryFolder.newFolder("idempotent")
    val store = PlanMediaStore(root, FakeImageSource(byteArrayOf()))
    assertTrue(store.deletePrivateFile("plans/1/photos/missing.jpg"))
}

@Test fun 目录外路径不能被删除() {
    val root = temporaryFolder.newFolder("outside-root")
    val outside = temporaryFolder.newFile("outside.jpg")
    val store = PlanMediaStore(root, FakeImageSource(byteArrayOf()))
    assertFalse(store.deletePrivateFile(outside.canonicalPath))
    assertTrue(outside.exists())
}
```

- [ ] **Step 2: 验证缺失文件用例失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlanMediaStoreTest" --no-configuration-cache`

Expected: FAIL，缺失文件当前返回 `false`。

- [ ] **Step 3: 最小修改删除语义**

```kotlin
fun deletePrivateFile(relativePath: String?): Boolean {
    if (relativePath.isNullOrBlank()) return true
    val file = resolvePrivateFile(relativePath) ?: return false
    return !file.exists() || file.delete()
}
```

- [ ] **Step 4: 验证私有文件测试转绿**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlanMediaStoreTest" --no-configuration-cache`

Expected: PASS。

### Task 3: DAO 与仓储批量删除

**Files:**
- Modify: `app/src/main/java/com/lujian/travelplan/data/db/PlanDao.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/data/PlanRepository.kt`
- Create: `app/src/androidTest/java/com/lujian/travelplan/data/GalleryBatchDeletionTest.kt`

- [ ] **Step 1: 写 Room 仓储失败测试**

在内存数据库插入两个计划、两张照片和两个真实私有文件，然后调用期望 API：

```kotlin
val result = repository.removeGalleryItems(
    GalleryDeleteRequest(
        photoIds = setOf(photoOneId, photoTwoId),
        coverPlanIds = setOf(planOneId),
    ),
).getOrThrow()

assertEquals(2, result.deletedPhotos)
assertEquals(1, result.deletedCovers)
assertTrue(database.planDao().findPhotosByIds(setOf(photoOneId, photoTwoId)).isEmpty())
assertNull(database.planDao().findPlanEntitiesByIds(setOf(planOneId)).single().customCoverPath)
assertFalse(photoOneFile.exists())
assertFalse(coverFile.exists())
```

- [ ] **Step 2: 验证 AndroidTest 编译因 API 不存在而失败**

Run: `./gradlew :app:compileDebugAndroidTestKotlin --no-configuration-cache`

Expected: FAIL，提示批量 DAO/仓储 API 未解析。

- [ ] **Step 3: 增加 DAO 批量 API**

```kotlin
@Query("SELECT * FROM plan_photos WHERE id IN (:photoIds)")
suspend fun findPhotosByIds(photoIds: Set<Long>): List<PlanPhotoEntity>

@Query("SELECT * FROM plans WHERE id IN (:planIds)")
suspend fun findPlanEntitiesByIds(planIds: Set<Long>): List<PlanEntity>

@Query("DELETE FROM plan_photos WHERE id IN (:photoIds)")
suspend fun deletePhotos(photoIds: Set<Long>)

@Query("UPDATE plans SET customCoverPath = NULL, customCoverAddedAt = NULL WHERE id IN (:planIds)")
suspend fun clearCustomCovers(planIds: Set<Long>)
```

- [ ] **Step 4: 实现事务与文件清理**

```kotlin
data class GalleryDeleteRequest(val photoIds: Set<Long>, val coverPlanIds: Set<Long>)
data class GalleryDeleteResult(val deletedPhotos: Int, val deletedCovers: Int)
private data class GalleryDeleteTargets(
    val photos: List<PlanPhotoEntity>,
    val covers: List<PlanEntity>,
)

suspend fun removeGalleryItems(request: GalleryDeleteRequest): Result<GalleryDeleteResult> = runCatching {
    val targets = database.withTransaction {
        val photos = request.photoIds.takeIf { it.isNotEmpty() }?.let { dao.findPhotosByIds(it) }.orEmpty()
        val covers = request.coverPlanIds.takeIf { it.isNotEmpty() }
            ?.let { dao.findPlanEntitiesByIds(it) }.orEmpty().filter { it.customCoverPath != null }
        if (photos.isNotEmpty()) dao.deletePhotos(photos.mapTo(mutableSetOf()) { it.id })
        if (covers.isNotEmpty()) dao.clearCustomCovers(covers.mapTo(mutableSetOf()) { it.id })
        GalleryDeleteTargets(photos, covers)
    }
    val failed = withContext(Dispatchers.IO) {
        (targets.photos.map { it.relativePath } + targets.covers.mapNotNull { it.customCoverPath })
            .distinct().count { !mediaStore.deletePrivateFile(it) }
    }
    targets.covers.forEach { enqueueThumbnail(it.id, it.title) }
    check(failed == 0) { "$failed 个私有文件未能删除" }
    GalleryDeleteResult(targets.photos.size, targets.covers.size)
}
```

- [ ] **Step 5: 验证 AndroidTest 编译和仓储测试通过**

Run: `./gradlew :app:compileDebugAndroidTestKotlin --no-configuration-cache`

Expected: PASS；仓储测试在 Task 7 的虚拟机阶段执行。

### Task 4: 复用管理操作条

**Files:**
- Create: `app/src/main/java/com/lujian/travelplan/ui/screens/GalleryManagementBar.kt`
- Modify: `app/src/androidTest/java/com/lujian/travelplan/ui/LujianUiTest.kt`

- [ ] **Step 1: 写管理操作条失败测试**

Compose 测试断言“管理→全选→删除→确认”会返回全部选择键，取消确认保留选择。

- [ ] **Step 2: 验证 AndroidTest 编译因组件不存在而失败**

Run: `./gradlew :app:compileDebugAndroidTestKotlin --no-configuration-cache`

Expected: FAIL，提示 `GalleryManagementBar` 未解析。

- [ ] **Step 3: 实现操作条和确认框**

操作条接收 `availableKeys`、`selectedKeys`、`onSelectionChange`、`onConfirmDelete`；空选择禁用删除按钮，确认框显示 `GallerySelectionPolicy.summary(selectedKeys).confirmation`，辅助文案固定为“只删除旅笺中的私有副本，不影响系统相册原图。”。

```kotlin
@Composable
internal fun GalleryManagementBar(
    managing: Boolean,
    availableKeys: Set<GallerySelectionKey>,
    selectedKeys: Set<GallerySelectionKey>,
    onManagingChange: (Boolean) -> Unit,
    onSelectionChange: (Set<GallerySelectionKey>) -> Unit,
    onConfirmDelete: (Set<GallerySelectionKey>) -> Unit,
)
```

- [ ] **Step 4: 验证 AndroidTest 编译通过**

Run: `./gradlew :app:compileDebugAndroidTestKotlin --no-configuration-cache`

Expected: PASS。

### Task 5: 计划内相册批量管理

**Files:**
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/PlanGalleryPolicy.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/PlanGalleryScreen.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/PlanDetailScreen.kt`
- Modify: `app/src/androidTest/java/com/lujian/travelplan/ui/LujianUiTest.kt`

- [ ] **Step 1: 写计划相册失败测试**

构造含封面和两张照片的计划，进入相册后点击“管理”，选择封面和照片，切换“按大头针”，确认仍显示“已选 2 项”，删除确认后断言回调收到一个照片 ID 和一个封面计划 ID。

- [ ] **Step 2: 运行目标测试并确认失败**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lujian.travelplan.ui.LujianUiTest --no-configuration-cache`

Expected: FAIL，页面尚无相册管理入口。

- [ ] **Step 3: 接入计划相册管理状态**

`PlanGalleryScreen` 为封面生成 `GallerySelectionKey.Cover(plan.id)`，为照片生成 `GallerySelectionKey.Photo(plan.id, photo.id)`；管理状态点击图片切换选择，正常状态保留大图和单张移除。`LaunchedEffect(availableKeys)` 使用 `retainAvailable` 收敛选择。

```kotlin
private fun PlanGalleryItem.selectionKey(planId: Long): GallerySelectionKey = when (this) {
    is PlanGalleryItem.Cover -> GallerySelectionKey.Cover(planId)
    is PlanGalleryItem.Photo -> GallerySelectionKey.Photo(planId, value.id)
}
```

- [ ] **Step 4: 接入仓储批量删除回调**

`PlanDetailScreen` 将选择键转换为 `GalleryDeleteRequest` 调用 `repository.removeGalleryItems`，错误继续显示在现有 `photoError` 通道。

```kotlin
val request = GalleryDeleteRequest(
    photoIds = selected.filterIsInstance<GallerySelectionKey.Photo>().mapTo(mutableSetOf()) { it.photoId },
    coverPlanIds = selected.filterIsInstance<GallerySelectionKey.Cover>().mapTo(mutableSetOf()) { it.planId },
)
photoError = repository.removeGalleryItems(request).exceptionOrNull()?.message
```

- [ ] **Step 5: 运行目标测试并确认通过**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lujian.travelplan.ui.LujianUiTest --no-configuration-cache`

Expected: PASS。

### Task 6: 全局相册批量管理

**Files:**
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/GlobalGalleryPolicy.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/GlobalGalleryScreen.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/LujianRoot.kt`
- Modify: `app/src/androidTest/java/com/lujian/travelplan/ui/LujianUiTest.kt`

- [ ] **Step 1: 写全局相册失败测试**

构造两个计划的照片和封面，进入管理后跨计划选择，切换“按计划”并全选；确认批量删除后断言请求去重且包含两个计划的封面与照片。

- [ ] **Step 2: 运行目标测试并确认失败**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lujian.travelplan.ui.LujianUiTest --no-configuration-cache`

Expected: FAIL，全局相册尚无管理入口。

- [ ] **Step 3: 接入全局相册管理状态**

标题行右侧增加“管理/完成”；管理状态下图片点击只切换选择，正常状态仍打开计划。选择键跨“按加入时间/按计划”稳定保留，空相册自动退出管理。

- [ ] **Step 4: 在根导航接入仓储删除**

`LujianRoot` 把 `graph.repository.removeGalleryItems` 作为挂起回调传入；页面显示失败文案并根据 `plans` 流自动刷新。

```kotlin
GlobalGalleryScreen(
    plans = plans,
    onOpenPlan = { planId -> navController.navigate("detail/$planId") },
    onDeleteItems = graph.repository::removeGalleryItems,
)
```

- [ ] **Step 5: 运行目标测试并确认通过**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lujian.travelplan.ui.LujianUiTest --no-configuration-cache`

Expected: PASS。

### Task 7: 版本、完整验证、推送与发版

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Create: `docs/releases/v1.2.0.md`

- [ ] **Step 1: 升级版本与说明**

将 `versionCode` 从 `3` 升到 `4`，`versionName` 从 `1.1.0` 升到 `1.2.0`；README 补充相册批量管理、批量删除和不影响系统原图。

`docs/releases/v1.2.0.md` 固定包含以下发布点：

```markdown
- 旅笺板支持计划板/足迹板切换与计划归档。
- 计划支持自定义预览图，以及按加入时间或大头针浏览私有照片。
- 全局相册支持按加入时间或计划浏览。
- 计划相册与全局相册新增批量管理，可混合删除地点照片和自定义预览图。
- 删除只作用于旅笺私有副本，不影响系统相册原图。
```

- [ ] **Step 2: 运行完整静态与构建门禁**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-configuration-cache`

Expected: BUILD SUCCESSFUL，单元测试零失败，Lint 零 error，生成 Debug APK 与 AndroidTest APK。

- [ ] **Step 3: 启动虚拟机并运行完整设备测试**

Run: `adb devices`，确认 `emulator-5554 device`；随后运行 `./gradlew :app:connectedDebugAndroidTest --no-configuration-cache`。

Expected: BUILD SUCCESSFUL，全部 AndroidTest 零失败。

- [ ] **Step 4: 虚拟机交互回归**

安装 `app/build/outputs/apk/debug/app-debug.apk`，验证计划相册和全局相册的管理、跨分类保留选择、全选、取消确认、照片与封面混合删除、默认预览图恢复、应用重启持久化，并对比删除前后 MediaStore 图片数量不变。

- [ ] **Step 5: 完成前检查并提交**

Run: `git diff --check`、BOM/冲突标记扫描、`git status --short`；提交信息使用 `新增相册批量管理与批量删除`。

- [ ] **Step 6: 推送 main**

Run: `git push origin main`

Expected: 本地 `main` 与 `origin/main` 指向相同提交。

- [ ] **Step 7: 创建 GitHub Release**

Run: `gh release create v1.2.0 app/build/outputs/apk/debug/app-debug.apk#旅笺-v1.2.0.apk --title "旅笺 v1.2.0" --notes-file docs/releases/v1.2.0.md`

发布说明列出旅笺板/足迹板、私有计划相册、自定义预览图、相册批量删除及“不影响系统相册原图”。创建后用 `gh release view v1.2.0 --json url,assets` 验证发布页和 APK 资产。
