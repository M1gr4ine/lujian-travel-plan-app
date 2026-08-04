# 旅笺板、归档与计划相册 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有旅笺 Android App 中交付可流畅切换的计划板/足迹板、双向归档、自定义预览图、按大头针保存的计划照片，以及计划内和全局两层相册。

**Architecture:** Room v2 保存归档状态、自定义封面元数据和大头针照片记录，`PlanMediaStore` 只负责 App 私有目录中的安全复制与清理，`PlanRepository` 负责事务和 WorkManager 缩略图调度。Compose 页面通过明确回调调用仓库；计划内相册按时间或大头针组织当前计划，全局相册按时间或计划聚合全部计划。

**Tech Stack:** Kotlin 2.3、Jetpack Compose、Room 2.8.4、WorkManager 2.11.2、Activity Result Photo Picker、JUnit 4、Compose UI Test、Android 虚拟机。

## Global Constraints

- 最低系统 Android 8 / API 26，编译与目标 SDK 为 API 37。
- 只复制用户选择的图片到 `filesDir`，不移动原图，不写入 MediaStore，不申请宽泛存储权限。
- 自定义封面和大头针照片读取前必须验证规范化路径仍在 `filesDir` 内。
- 大头针目录键使用 `SHA-256(pinId)` 前 24 位十六进制摘要，禁止把外部活动或地图 ID 直接拼入路径。
- 归档计划仍显示在首页地图，并保留打开、编辑、导出和删除能力。
- 板切换采用约 280ms 水平滑动加淡入淡出；系统减少动态效果开启时退化为短淡入或直接更新。
- `main` 已包含地图、解析器和详情基线提交 `9316847`，不得回退。
- 本轮实现和虚拟机测试完成后保持全部改动未提交，等待用户实机测试确认。
- Kotlin、Java、XML 和 Markdown 均使用 UTF-8 无 BOM。

---

## File Map

- `app/src/main/java/com/lujian/travelplan/data/db/Entities.kt`：Room 计划字段和照片实体。
- `app/src/main/java/com/lujian/travelplan/data/db/PlanDao.kt`：归档、封面和照片 DAO。
- `app/src/main/java/com/lujian/travelplan/data/db/DatabaseMigrations.kt`：v1 → v2 显式迁移。
- `app/src/main/java/com/lujian/travelplan/data/PlanMediaStore.kt`：图片来源读取、私有复制、路径校验和文件清理。
- `app/src/main/java/com/lujian/travelplan/data/PlanRepository.kt`：业务事务、照片/封面方法和缩略图调度。
- `app/src/main/java/com/lujian/travelplan/ui/screens/TravelBoardPolicy.kt`：板筛选、方向和操作语义。
- `app/src/main/java/com/lujian/travelplan/ui/screens/PlanLibraryScreen.kt`：旅笺板切换、动画和管理栏。
- `app/src/main/java/com/lujian/travelplan/ui/components/CoverEditorCard.kt`：编辑页预览图选择状态。
- `app/src/main/java/com/lujian/travelplan/ui/screens/PlanGalleryScreen.kt`：当前计划按时间或大头针浏览、加图、跳转和大图移除。
- `app/src/main/java/com/lujian/travelplan/ui/screens/GlobalGalleryScreen.kt`：跨计划按时间或计划分组浏览。
- `app/src/main/java/com/lujian/travelplan/ui/screens/PlanDetailScreen.kt`：照片选择器、相册页签和大头针定位。
- `app/src/main/java/com/lujian/travelplan/ui/screens/DailyMapPage.kt`：地图卡片跳转相册入口。
- `app/src/main/java/com/lujian/travelplan/ui/LujianRoot.kt`：仓库回调与底部“旅笺板”文案。

---

### Task 0: 验证主线基线

**Files:**
- Read only: `main` 当前提交与工作区状态。

**Interfaces:**
- Consumes: `main` 上的地图控制、地图气泡、解析器字段和阅读器交互。
- Produces: 通过自动化验证的 `9316847` 基线，后续改动全部保持未提交。

- [ ] **Step 1: 运行现有 JVM 测试**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`；若失败，先归因并记录基线问题，不回退主线文件。

- [ ] **Step 2: 运行现有 Android 仪器测试编译**

Run:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
```

Expected: 两个 APK 均生成，证明主线可作为本次实现基线。

- [ ] **Step 3: 确认分支与未提交边界**

```powershell
git status --short --branch
git log -1 --oneline
```

Expected: 当前分支为 `main`，HEAD 为 `9316847`；仅设计和实施计划文档尚未提交。

---

### Task 1: Room v2 与归档数据契约

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/lujian/travelplan/data/db/Entities.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/data/db/PlanDao.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/data/db/LujianDatabase.kt`
- Create: `app/src/main/java/com/lujian/travelplan/data/db/DatabaseMigrations.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/LujianApplication.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/data/PlanRepository.kt`
- Create: `app/src/androidTest/java/com/lujian/travelplan/data/db/LujianDatabaseMigrationTest.kt`
- Generated: `app/schemas/com.lujian.travelplan.data.db.LujianDatabase/2.json`

**Interfaces:**
- Consumes: 现有 `PlanEntity`、`PlanWithDetails`、`StoredPlan` 和 `PlanRepository.observePlans()`。
- Produces: `PlanPhotoEntity`、`PlanPhoto`、`StoredPlan.archivedAt`、`StoredPlan.customCoverPath`、`StoredPlan.customCoverAddedAt`、`StoredPlan.photos`、`PlanRepository.setArchived(planIds, archived)`。

- [ ] **Step 1: 增加失败的 v1 → v2 迁移测试**

在 `LujianDatabaseMigrationTest.kt` 创建 v1 数据库，插入一份最小计划，执行迁移后断言原计划仍在、三个新增字段为 `NULL`，并能查询空的 `plan_photos`：

```kotlin
@get:Rule
val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    LujianDatabase::class.java,
)

@Test
fun migration1To2PreservesPlansAndCreatesPhotos() {
    helper.createDatabase(TEST_DB, 1).apply {
        execSQL(
            "INSERT INTO plans (id,title,capability,sourceFileName,sourceMimeType,charsetName,sha256,rawPath,generatedPath,thumbnailPath,compatibilityMode,sectionsJson,createdAt,updatedAt) " +
                "VALUES (1,'大连','ENHANCED','dalian.html',NULL,'UTF-8','hash','plans/1/raw.html',NULL,NULL,0,'{}',10,20)",
        )
        close()
    }

    helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
        db.query("SELECT archivedAt, customCoverPath, customCoverAddedAt FROM plans WHERE id=1").use { cursor ->
            check(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }
        db.query("SELECT COUNT(*) FROM plan_photos").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }
}
```

- [ ] **Step 2: 运行迁移测试并确认失败**

Run:

```powershell
.\gradlew.bat assembleDebugAndroidTest
```

Expected: FAIL，原因是数据库 v2、`MIGRATION_1_2` 或照片表尚不存在。

- [ ] **Step 3: 定义实体与 DAO 接口**

在 `PlanEntity` 末尾增加默认字段，并新增照片实体：

```kotlin
val archivedAt: Long? = null,
val customCoverPath: String? = null,
val customCoverAddedAt: Long? = null,

@Entity(
    tableName = "plan_photos",
    foreignKeys = [ForeignKey(
        entity = PlanEntity::class,
        parentColumns = ["id"],
        childColumns = ["planId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("planId"), Index(value = ["planId", "pinId"])],
)
data class PlanPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val pinId: String,
    val pinTitle: String,
    val relativePath: String,
    val addedAt: Long,
    val displayName: String?,
)
```

给 `PlanWithDetails` 增加 `@Relation(parentColumn = "id", entityColumn = "planId") val photos: List<PlanPhotoEntity>`，给 DAO 增加：

```kotlin
@Query("UPDATE plans SET archivedAt = :archivedAt WHERE id IN (:planIds)")
suspend fun updateArchivedAt(planIds: Set<Long>, archivedAt: Long?)

@Query("UPDATE plans SET customCoverPath = :path, customCoverAddedAt = :addedAt WHERE id = :planId")
suspend fun updateCustomCover(planId: Long, path: String?, addedAt: Long?)

@Insert
suspend fun insertPhotos(photos: List<PlanPhotoEntity>): List<Long>

@Query("DELETE FROM plan_photos WHERE id = :photoId")
suspend fun deletePhoto(photoId: Long)

@Query("SELECT * FROM plan_photos WHERE planId = :planId AND id = :photoId LIMIT 1")
suspend fun findPhoto(planId: Long, photoId: Long): PlanPhotoEntity?
```

- [ ] **Step 4: 实现显式迁移并接入数据库构建**

`DatabaseMigrations.kt`：

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plans ADD COLUMN archivedAt INTEGER")
        db.execSQL("ALTER TABLE plans ADD COLUMN customCoverPath TEXT")
        db.execSQL("ALTER TABLE plans ADD COLUMN customCoverAddedAt INTEGER")
        db.execSQL("CREATE TABLE IF NOT EXISTS plan_photos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, planId INTEGER NOT NULL, pinId TEXT NOT NULL, pinTitle TEXT NOT NULL, relativePath TEXT NOT NULL, addedAt INTEGER NOT NULL, displayName TEXT, FOREIGN KEY(planId) REFERENCES plans(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_photos_planId ON plan_photos(planId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_photos_planId_pinId ON plan_photos(planId, pinId)")
    }
}
```

将数据库版本改为 2、加入 `PlanPhotoEntity`，在 `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2).build()` 接入；添加 `androidTestImplementation("androidx.room:room-testing:2.8.4")`。

- [ ] **Step 5: 扩展领域模型和归档仓库方法**

在 `PlanRepository.kt` 定义：

```kotlin
data class PlanPhoto(
    val id: Long,
    val pinId: String,
    val pinTitle: String,
    val relativePath: String,
    val addedAt: Long,
    val displayName: String?,
)

// 以下字段加在 StoredPlan.updatedAt 之后，保留默认值以兼容现有调用方。
val archivedAt: Long? = null
val customCoverPath: String? = null
val customCoverAddedAt: Long? = null
val photos: List<PlanPhoto> = emptyList()

suspend fun setArchived(planIds: Set<Long>, archived: Boolean) {
    if (planIds.isEmpty()) return
    dao.updateArchivedAt(planIds, System.currentTimeMillis().takeIf { archived })
}
```

`toStoredPlan()` 必须复制归档、封面和按 `addedAt DESC` 排序的照片字段；首页继续消费未过滤的 `observePlans()`。

- [ ] **Step 6: 运行测试并生成 v2 schema**

Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebugAndroidTest
```

Expected: PASS，并生成 `2.json`；随后在虚拟机执行该迁移测试。

- [ ] **Step 7: 检查数据契约改动**

```powershell
git diff --check
git status --short
```

---

### Task 2: 私有图片复制、封面与清理

**Files:**
- Create: `app/src/main/java/com/lujian/travelplan/data/PlanMediaStore.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/data/PlanRepository.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/importing/ThumbnailWorker.kt`
- Create: `app/src/test/java/com/lujian/travelplan/data/PlanMediaStoreTest.kt`

**Interfaces:**
- Consumes: Task 1 的照片实体、封面字段和 DAO 方法。
- Produces: `PlanMediaStore.copyCover`、`PlanMediaStore.copyPhoto`、`PlanMediaStore.resolvePrivateFile`、`PlanRepository.setCustomCover`、`clearCustomCover`、`addPhotos`、`removePhoto`。

- [ ] **Step 1: 写私有路径和失败回滚测试**

使用可注入图片来源，覆盖非法日期 ID 不进入路径、复制完成内容一致、来源抛错不保留 `.tmp`：

```kotlin
private class FakeImageSource(private val bytes: ByteArray, private val fail: Boolean = false) : ImageSource {
    override fun open(uri: Uri): InputStream = if (fail) error("读取失败") else bytes.inputStream()
    override fun mimeType(uri: Uri): String = "image/jpeg"
    override fun displayName(uri: Uri): String = "海边.jpg"
}

@Test
fun dayIdCannotEscapePrivateRoot() {
    val store = PlanMediaStore(root, FakeImageSource(byteArrayOf(1, 2, 3)))
    val copied = store.copyPhoto(7, "../../危险/pin", Uri.EMPTY)
    assertTrue(copied.file.canonicalPath.startsWith(root.canonicalPath + File.separator))
    assertFalse(copied.relativePath.contains(".."))
}
```

- [ ] **Step 2: 运行媒体存储测试并确认失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lujian.travelplan.data.PlanMediaStoreTest"
```

Expected: FAIL，`PlanMediaStore` 和 `ImageSource` 尚不存在。

- [ ] **Step 3: 实现安全复制边界**

定义接口和返回类型：

```kotlin
interface ImageSource {
    fun open(uri: Uri): InputStream
    fun mimeType(uri: Uri): String?
    fun displayName(uri: Uri): String?
}

data class CopiedPlanImage(
    val file: File,
    val relativePath: String,
    val displayName: String?,
)
```

`PlanMediaStore` 仅接受 `image/*`，使用 UUID 文件名、同目录 `.tmp` 和 `Files.move(..., ATOMIC_MOVE)`；不支持原子移动时回退到同卷 `renameTo`。`pinKey(pinId)` 返回 SHA-256 前 24 位十六进制字符。`resolvePrivateFile(relativePath)` 必须以规范化根路径前缀加分隔符验证边界。

- [ ] **Step 4: 实现仓库媒体方法和事务补偿**

仓库公开接口固定为：

```kotlin
suspend fun setCustomCover(planId: Long, uri: Uri): Result<Unit>
suspend fun clearCustomCover(planId: Long): Result<Unit>
suspend fun addPhotos(planId: Long, pinId: String, pinTitle: String, uris: List<Uri>): Result<List<PlanPhoto>>
suspend fun removePhoto(planId: Long, photoId: Long): Result<Unit>
```

文件先复制、数据库后更新；数据库失败时删除本次复制文件。封面数据库更新后按唯一工作名 `thumbnail-$planId` 以 `ExistingWorkPolicy.REPLACE` 调度 `ThumbnailWorker`。删除计划时额外清理经过边界校验的 `plans/{planId}` 目录。

- [ ] **Step 5: 让缩略图任务从数据库读取封面**

`ThumbnailWorker` 的优先级改为：输入参数 `INPUT_CUSTOM_COVER_PATH` → `stored.customCoverPath` → 自动 HTML 封面。`loadCustomCover(relativePath)` 继续做规范化路径检查；清除封面后即使调用无参数 worker，也会生成自动封面。

- [ ] **Step 6: 跑聚焦测试**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lujian.travelplan.data.PlanMediaStoreTest" --tests "com.lujian.travelplan.ui.screens.PlanThumbnailLoaderTest"
```

Expected: PASS，无 `.tmp` 残留。

- [ ] **Step 7: 检查私有媒体层改动**

```powershell
git diff --check
git status --short
```

---

### Task 3: 可切换旅笺板与归档动画

**Files:**
- Create: `app/src/main/java/com/lujian/travelplan/ui/screens/TravelBoardPolicy.kt`
- Create: `app/src/test/java/com/lujian/travelplan/ui/screens/TravelBoardPolicyTest.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/PlanLibraryScreen.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/LujianRoot.kt`
- Modify: `app/src/androidTest/java/com/lujian/travelplan/ui/LujianUiTest.kt`

**Interfaces:**
- Consumes: `StoredPlan.archivedAt` 和 `PlanRepository.setArchived`。
- Produces: `TravelBoard.PLANS`、`TravelBoard.FOOTPRINTS`、`PlanLibraryScreen(..., reduceMotion, onSetArchived)`。

- [ ] **Step 1: 写板筛选和目标状态的失败测试**

```kotlin
@Test
fun boardsSplitByArchivedAt() {
    val active = storedPlan(id = 1, archivedAt = null)
    val archived = storedPlan(id = 2, archivedAt = 10)
    assertEquals(listOf(active), TravelBoardPolicy.plansFor(TravelBoard.PLANS, listOf(active, archived)))
    assertEquals(listOf(archived), TravelBoardPolicy.plansFor(TravelBoard.FOOTPRINTS, listOf(active, archived)))
    assertTrue(TravelBoardPolicy.archiveValue(TravelBoard.PLANS))
    assertFalse(TravelBoardPolicy.archiveValue(TravelBoard.FOOTPRINTS))
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
```

- [ ] **Step 2: 运行策略测试并确认失败**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lujian.travelplan.ui.screens.TravelBoardPolicyTest"
```

Expected: FAIL，策略类型尚不存在。

- [ ] **Step 3: 实现板策略与页面接口**

```kotlin
enum class TravelBoard(val title: String, val subtitle: String) {
    PLANS("计划板", "把下一次出发，钉在这里"),
    FOOTPRINTS("足迹板", "走过的旅程，都留有一枚脚印"),
}

fun PlanLibraryScreen(
    plans: List<StoredPlan>,
    onImport: (Uri) -> Unit,
    onOpenPlan: (Long) -> Unit,
    onDeletePlans: (Set<Long>) -> Unit = {},
    onSetArchived: (Set<Long>, Boolean) -> Unit = { _, _ -> },
    reduceMotion: Boolean = false,
    transitionScopes: PlanSharedTransitionScopes? = null,
    sharedBoundsEnabled: Boolean = false,
)
```

- [ ] **Step 4: 实现切换、滚动保持和归档补位动画**

标题按钮切换 `TravelBoard`；两个 `LazyGridState` 分别 `rememberSaveable`。非减少动态时使用 `AnimatedContent` 的 280ms `slideInHorizontally + fadeIn` / `slideOutHorizontally + fadeOut`，卡片使用 `Modifier.animateItem()`；减少动态时只用 90ms `fadeIn/fadeOut`。计划板显示添加卡，足迹板显示空态且不显示导入卡。

- [ ] **Step 5: 接线仓库并更新底部文案**

`LujianRoot` 将根入口标签从“计划库”改为“旅笺板”，并接入：

```kotlin
onSetArchived = { ids, archived ->
    scope.launch { graph.repository.setArchived(ids, archived) }
},
reduceMotion = reduceMotion,
```

- [ ] **Step 6: 增加 Compose 行为测试**

在 `LujianUiTest` 验证：“计划板”显示活动计划、点击标题切到“足迹板”、管理模式归档回调收到 `(setOf(1L), true)`、移回收到 `false`，并保留空计划板添加入口。

- [ ] **Step 7: 运行板测试并检查范围**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lujian.travelplan.ui.screens.TravelBoardPolicyTest" assembleDebugAndroidTest
git diff --check
```

---

### Task 4: 编辑页自定义预览图

**Files:**
- Create: `app/src/main/java/com/lujian/travelplan/ui/components/CoverEditorCard.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/EditPlanScreen.kt`
- Modify: `app/src/androidTest/java/com/lujian/travelplan/ui/LujianUiTest.kt`

**Interfaces:**
- Consumes: `StoredPlan.customCoverPath`、`thumbnailPath`、`PlanRepository.setCustomCover` 和 `clearCustomCover`。
- Produces: 单图 Photo Picker 入口及“选择/更换/恢复自动封面”界面。

- [ ] **Step 1: 写封面卡片状态测试**

用独立 `CoverEditorCard` 的回调测试三个状态：无自定义封面显示“选择图片”；有自定义封面显示“更换图片”和“恢复自动封面”；点击按钮只触发对应回调。

- [ ] **Step 2: 运行仪器测试编译并确认失败**

```powershell
.\gradlew.bat assembleDebugAndroidTest
```

Expected: FAIL，`CoverEditorCard` 尚不存在。

- [ ] **Step 3: 实现封面卡片和图片加载**

卡片沿用 `PaperCard`、`PaperDeep`、`Coral` 和 3dp 墨色边框；优先显示 `customCoverPath`，否则显示现有 `thumbnailPath`。图片解码复用 `PlanThumbnailLoader.decode`，按钮文案严格为“选择图片”“更换图片”“恢复自动封面”。

- [ ] **Step 4: 接入系统单图选择器**

在 `EditPlanScreen` 注册：

```kotlin
val coverPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    scope.launch { repository.setCustomCover(plan.id, uri) }
}
```

启动参数使用 `PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)`；恢复操作调用 `clearCustomCover`。失败时在编辑页显示明确错误文案，成功后由 `observePlans()` 更新卡片。

- [ ] **Step 5: 运行测试并检查范围**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebugAndroidTest
git diff --check
```

---

### Task 5: 大头针加图与计划内相册

**Files:**
- Create: `app/src/main/java/com/lujian/travelplan/ui/screens/PlanGalleryScreen.kt`
- Create: `app/src/main/java/com/lujian/travelplan/ui/screens/PlanGalleryPolicy.kt`
- Create: `app/src/test/java/com/lujian/travelplan/ui/screens/PlanGalleryPolicyTest.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/PlanReaderPresentation.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/PlanDetailScreen.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/screens/DailyMapPage.kt`
- Modify: `app/src/androidTest/java/com/lujian/travelplan/ui/LujianUiTest.kt`

**Interfaces:**
- Consumes: `StoredPlan.photos`、自定义封面字段、`DailyMapStop.itemId`、`addPhotos` 和 `removePhoto`。
- Produces: `PlanReaderPage.ALBUM`、`PlanGalleryMode.RECENT`、`PlanGalleryMode.BY_PIN`、`PhotoPin`、行程/地图加图入口及大图查看器。

- [ ] **Step 1: 写计划相册排序与大头针分组失败测试**

```kotlin
@Test
fun recentIncludesCoverAndSortsDescending() {
    val photos = listOf(
        PlanPhoto(1, "item-1", "星海广场", "plans/1/photos/a.jpg", 10, "a.jpg"),
        PlanPhoto(2, "item-2", "东港", "plans/1/photos/b.jpg", 20, "b.jpg"),
    )
    val items = PlanGalleryPolicy.recentItems("plans/1/cover/c.jpg", 15, photos)
    assertEquals(listOf(20L, 15L, 10L), items.map { it.addedAt })
}

@Test
fun pinGroupsKeepFirstAppearanceOrder() {
    val pins = listOf(PhotoPin("item-2", "东港"), PhotoPin("item-1", "星海广场"))
    val photos = listOf(
        PlanPhoto(1, "item-1", "星海广场", "plans/1/photos/a.jpg", 10, "a.jpg"),
        PlanPhoto(2, "item-2", "东港", "plans/1/photos/b.jpg", 20, "b.jpg"),
    )
    assertEquals(listOf("item-2", "item-1"), PlanGalleryPolicy.pinGroups(pins, photos).map { it.pin.id })
}
```

- [ ] **Step 2: 运行策略测试并确认失败**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lujian.travelplan.ui.screens.PlanGalleryPolicyTest"
```

Expected: FAIL，计划相册策略尚不存在。

- [ ] **Step 3: 定义大头针与计划相册模型**

```kotlin
enum class PlanGalleryMode { RECENT, BY_PIN }

data class PhotoPin(val id: String, val title: String)

sealed interface GalleryItem {
    val relativePath: String
    val addedAt: Long
    data class Cover(override val relativePath: String, override val addedAt: Long) : GalleryItem
    data class Photo(val photo: PlanPhoto) : GalleryItem {
        override val relativePath = photo.relativePath
        override val addedAt = photo.addedAt
    }
}

data class PinGalleryGroup(val pin: PhotoPin, val items: List<GalleryItem.Photo>)
```

`PlanGalleryPolicy.pinsFor(plan)` 按日期和活动顺序收集 `PlanItemDraft.id/title`，再补入仅存在于 `buildDailyMapPresentation(...).stops` 的 `itemId/title`，按 ID 去重。给 `PlanReaderPage` 增加 `ALBUM("📷 相册")`；`PlanReaderDayPolicy` 仅在 `ITINERARY` 和 `MAP` 返回 `pagerTarget`。

- [ ] **Step 4: 实现计划内相册页面**

```kotlin
@Composable
fun PlanGalleryScreen(
    plan: StoredPlan,
    pins: List<PhotoPin>,
    requestedPinId: String?,
    onAddPhotos: (PhotoPin) -> Unit,
    onRemovePhoto: (Long) -> Unit,
    modifier: Modifier = Modifier,
)
```

页面顶部切换“按加入时间 / 按大头针”；前者为当前计划双列时间流，后者按大头针分组并可滚动定位 `requestedPinId`。封面加“封面”角标。点击图片打开 `Dialog` 大图；普通照片提供“移除照片”并二次确认，封面提示从编辑页恢复自动封面。

- [ ] **Step 5: 接入一次多选和统一大头针回调**

`NativePlanReader` 保存 `pendingPhotoPin` 并注册：

```kotlin
val photoPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50),
) { uris ->
    val pin = pendingPhotoPin ?: return@rememberLauncherForActivityResult
    if (uris.isNotEmpty()) onAddPhotos(pin, uris)
}

internal fun NativePlanReader(
    plan: StoredPlan,
    modifier: Modifier = Modifier,
    onAddPhotos: (PhotoPin, List<Uri>) -> Unit = { _, _ -> },
    onRemovePhoto: (Long) -> Unit = {},
)
```

`PlanDetailScreen` 将回调接到 `repository.addPhotos(plan.id, pin.id, pin.title, uris)` 和 `repository.removePhoto(plan.id, photoId)`。

- [ ] **Step 6: 在行程卡片增加加图和跳转**

`ItineraryCard` 展开后增加“📷 添加照片”和“查看照片”；前者启动当前 `PhotoPin(item.id, item.title)` 的选择器，后者设置 `requestedAlbumPinId = item.id` 并切换 `PlanReaderPage.ALBUM`。没有照片时“查看照片”仍进入该大头针分组并显示添加入口。

- [ ] **Step 7: 在地图地点卡片增加相同操作**

给 `DailyMapPage` 增加：

```kotlin
onAddPhotos: (DailyMapStop) -> Unit = {},
onOpenPhotos: (DailyMapStop) -> Unit = {},
```

每张 `MapStopCard` 增加“📷 添加照片”和“查看照片”，用 `DailyMapStop.itemId/title` 构造与行程页一致的 `PhotoPin`。跳转时设置 `requestedAlbumPinId` 并切到计划内相册。

- [ ] **Step 8: 增加 Compose 行为测试**

在 `LujianUiTest` 验证：相册页签及两种模式可见；行程“星海广场”和地图大头针触发相同 `itemId`；两处“查看照片”均定位“星海广场”分组；封面有“封面”标记；大图移除回调传递正确照片 ID。

- [ ] **Step 9: 运行测试并检查范围**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lujian.travelplan.ui.screens.PlanGalleryPolicyTest" assembleDebugAndroidTest
git diff --check
```

---

### Task 6: 底部全局相册

**Files:**
- Create: `app/src/main/java/com/lujian/travelplan/ui/screens/GlobalGalleryScreen.kt`
- Create: `app/src/main/java/com/lujian/travelplan/ui/screens/GlobalGalleryPolicy.kt`
- Create: `app/src/test/java/com/lujian/travelplan/ui/screens/GlobalGalleryPolicyTest.kt`
- Modify: `app/src/main/java/com/lujian/travelplan/ui/LujianRoot.kt`
- Modify: `app/src/test/java/com/lujian/travelplan/core/PolicyTest.kt`
- Modify: `app/src/androidTest/java/com/lujian/travelplan/ui/LujianUiTest.kt`

**Interfaces:**
- Consumes: 所有 `StoredPlan` 的照片与自定义封面。
- Produces: `RootDestination.GALLERY`、`GlobalGalleryMode.RECENT`、`GlobalGalleryMode.BY_PLAN` 和全局相册页面。

- [ ] **Step 1: 写跨计划排序和分组失败测试**

构造两份 `StoredPlan`，分别包含加入时间 10、20 的照片和时间 15 的封面；断言 `GlobalGalleryPolicy.recentItems(plans)` 时间为 `20,15,10`，`planGroups(plans)` 按计划 `updatedAt DESC` 分组且每组只包含自身图片。

- [ ] **Step 2: 运行策略测试并确认失败**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lujian.travelplan.ui.screens.GlobalGalleryPolicyTest"
```

Expected: FAIL，全局相册策略尚不存在。

- [ ] **Step 3: 定义全局相册接口**

```kotlin
enum class GlobalGalleryMode { RECENT, BY_PLAN }

data class GlobalGalleryItem(
    val planId: Long,
    val planTitle: String,
    val pinTitle: String?,
    val item: GalleryItem,
)

data class PlanGalleryGroup(
    val planId: Long,
    val planTitle: String,
    val items: List<GlobalGalleryItem>,
)
```

- [ ] **Step 4: 实现底部全局相册页面**

```kotlin
@Composable
fun GlobalGalleryScreen(
    plans: List<StoredPlan>,
    onOpenPlan: (Long) -> Unit,
    modifier: Modifier = Modifier,
)
```

顶部切换“按加入时间 / 按计划”；卡片标注计划名和大头针标题，点击大图后提供“打开所属计划”，不提供计划外加图入口。无图片时提示“先在行程或地图的大头针下添加照片”。

- [ ] **Step 5: 接入第四个底部导航页签**

`RootDestination` 增加 `GALLERY("gallery", "相册", Icons.Outlined.PhotoLibrary, Icons.Rounded.PhotoLibrary)`，位置为首页与旅笺板之后；对应 composable 传入全部 `plans`。更新主页面相邻滑动测试的 `count` 从 3 为 4，并验证边界索引 3。

- [ ] **Step 6: 增加 Compose 测试并检查范围**

在 `LujianUiTest` 验证全局相册显示两份计划图片、可切换“按计划”、点击“打开所属计划”回调正确 ID。

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lujian.travelplan.ui.screens.GlobalGalleryPolicyTest" assembleDebugAndroidTest
git diff --check
```

---

### Task 7: 全量验证、文档与虚拟机回归

**Files:**
- Modify: `README.md`
- Modify only if defects are found: 本计划实际改动文件。

**Interfaces:**
- Consumes: Tasks 0–6 的完整功能。
- Produces: 可安装 APK、自动化验证记录和虚拟机交互证据。

- [ ] **Step 1: 更新用户文档**

README 将“计划库”能力改为“旅笺板”，补充计划板/足迹板、自定义预览图、大头针照片、计划内相册、全局相册和“私有副本不会出现在系统相册”的说明；不改版本号。

- [ ] **Step 2: 检查编码与补丁红线**

Run:

```powershell
git diff --check
rg -n -e "<<<<<<<|=======|>>>>>>>|TODO|TBD" app/src README.md
```

Expected: 无冲突标记、占位符、尾随空格或 BOM；`=======` 若命中正常 Markdown 表格分隔符需人工判定。

- [ ] **Step 3: 运行 JVM 与构建验证**

```powershell
.\gradlew.bat clean testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL`，debug APK 与 test APK 均生成。

- [ ] **Step 4: 在虚拟机运行仪器测试**

```powershell
adb devices
.\gradlew.bat connectedDebugAndroidTest
```

Expected: 迁移测试和 Compose UI 测试全部 PASS；只对明确的目标虚拟机执行。

- [ ] **Step 5: 在虚拟机完成手工回归**

按顺序验证：

1. 使用 `adb install -r app\build\outputs\apk\debug\app-debug.apk` 升级安装，旧计划仍存在。
2. 计划板与足迹板往返切换流畅，两个板滚动位置分别保留。
3. 管理模式批量归档并移回，首页地图始终能打开该计划。
4. 编辑页选择、更换并恢复自定义预览图。
5. 从行程卡片和地图大头针分别为至少两个地点选择多张照片，计划相册“按加入时间”和“按大头针”结果正确。
6. 从行程与地图卡片跳到相册并定位正确大头针。
7. 在底部全局相册切换“按加入时间”和“按计划”，并能打开所属计划。
8. 移除一张私有照片，重启 App 后状态保持。
9. 打开系统相册，确认没有出现旅笺生成的重复媒体项。

- [ ] **Step 6: 检查最终范围并保持未提交**

```powershell
git status --short
git diff --stat
```

Expected: 没有构建产物被 Git 跟踪或暂存；全部实现、测试和文档改动保持未提交，等待用户实机测试。
