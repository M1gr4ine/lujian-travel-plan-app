# iOS 适配 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为旅笺新增原生 SwiftUI iOS 应用、iPhone 模拟器全流程测试和自动发布的模拟器安装包/未签名 IPA，同时保持 Android 基线不变。

**Architecture:** 在 `ios/` 下建立独立 SwiftUI 工程，使用 `Codable` 快照与应用私有目录保存计划和媒体，复用 Android 已公开的 `lujian-plan` JSON 契约。平台能力全部使用 Apple SDK；GitHub macOS runner 通过 XcodeGen 生成工程，测试后构建模拟器 `.app.zip` 和未签名 `.ipa` 并创建预发布。

**Tech Stack:** Swift 6、SwiftUI、MapKit、WebKit、PhotosUI、UniformTypeIdentifiers、CryptoKit、XCTest、XCUITest、XcodeGen、GitHub Actions、iOS 17+

## Global Constraints

- iOS 首版 `MARKETING_VERSION=1.0.0`、`CURRENT_PROJECT_VERSION=1`、最低 iOS 17。
- Android 源码、版本号、数据库和构建脚本不修改。
- 生产运行时不引入第三方 Swift 包。
- 原始 HTML 和照片只保存在应用私有目录，不删除系统相册或导入源文件。
- HTML 单文件最大 50 MB；解码顺序为 BOM、meta charset、严格 UTF-8、GB18030。
- WKWebView 默认禁用 JavaScript，不注册原生脚本桥；HTTP 和非法 scheme 默认阻止。
- 无 Apple 签名材料时只发布模拟器可安装包和明确标注的未签名 IPA。
- 所有 Swift、YAML、plist、Markdown 文件统一 UTF-8 无 BOM。

---

## 文件结构

- 新建 `ios/project.yml`：XcodeGen 的应用、单元测试和 UI 测试 target/scheme。
- 新建 `ios/Config/Info.plist`：应用信息、HTML 文档类型和照片用途说明。
- 新建 `ios/Lujian/App/LujianApp.swift`：应用入口、依赖装配、外部 URL 导入。
- 新建 `ios/Lujian/App/AppMetadata.swift`：iOS 版本和存储 schema 常量。
- 新建 `ios/Lujian/App/LujianTheme.swift`：纸张色、墨色、金色和复用卡片样式。
- 新建 `ios/Lujian/Resources/Assets.xcassets`：沿用 Android 旅笺纸张/大头针图形的 iOS AppIcon 和强调色。
- 新建 `ios/Lujian/Models/PlanModels.swift`：Codable 计划、日期、地点、照片模型。
- 新建 `ios/Lujian/Data/PlanStore.swift`：原子索引、计划事务、私有路径边界。
- 新建 `ios/Lujian/Importing/HTMLDecoder.swift`：大小、HTML 内容和编码识别。
- 新建 `ios/Lujian/Importing/PlanHTMLParser.swift`：增强契约与普通 HTML 解析。
- 新建 `ios/Lujian/Importing/PlanImportService.swift`：安全作用域读取、哈希、去重和文件复制。
- 新建 `ios/Lujian/Exporting/PlanHTMLExporter.swift`：自包含 UTF-8 HTML 导出。
- 新建 `ios/Lujian/Security/WebNavigationPolicy.swift`：WKWebView 外链与 scheme 策略。
- 新建 `ios/Lujian/Views/RootView.swift`：四栏根导航与全局导入反馈。
- 新建 `ios/Lujian/Views/HomeMapView.swift`：计划大头针与地图视野。
- 新建 `ios/Lujian/Views/PlanBoardView.swift`：计划/足迹、多选归档恢复删除和导入。
- 新建 `ios/Lujian/Views/PlanDetailView.swift`：行程、地图、预算、相册四分区。
- 新建 `ios/Lujian/Views/PlanEditorView.swift`：计划基本信息、日期行程和目的地编辑。
- 新建 `ios/Lujian/Views/GalleryViews.swift`：计划/全局相册、PhotosPicker 和批量删除。
- 新建 `ios/Lujian/Views/SecureWebView.swift`：隔离 WKWebView 包装。
- 新建 `ios/Lujian/Views/ProfileView.swift`：版本、存储统计、隐私和演示数据。
- 新建 `ios/Lujian/Resources/DemoPlan.html`：UI 测试和用户演示导入文件。
- 新建 `ios/LujianTests/*.swift`：模型、存储、解析、导出和安全单元测试。
- 新建 `ios/LujianUITests/LujianUITests.swift`：iPhone 模拟器核心流程。
- 新建 `ios/ci/test-and-package.sh`：模拟器测试、安装启动、两类安装包和校验值生成。
- 新建 `.github/workflows/ios-release.yml`：macOS 测试、安装、启动、打包和预发布。
- 新建 `docs/releases/ios-v1.0.0.md`：产物用途、安装命令和签名边界。
- 修改 `README.md`：增加 iOS 功能、构建、测试与安装说明。

### Task 1: iOS 工程骨架与可测试入口

**Files:**
- Create: `ios/project.yml`
- Create: `ios/Config/Info.plist`
- Create: `ios/Lujian/App/LujianApp.swift`
- Create: `ios/Lujian/App/AppMetadata.swift`
- Create: `ios/Lujian/App/LujianTheme.swift`
- Create: `ios/Lujian/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json`
- Create: `ios/Lujian/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`
- Create: `ios/Lujian/Resources/Assets.xcassets/AccentColor.colorset/Contents.json`
- Create: `ios/Lujian/Views/RootView.swift`
- Create: `ios/LujianTests/ProjectSmokeTests.swift`

**Interfaces:**
- Consumes: 无。
- Produces: `AppMetadata`、最小 `LujianApp`、`RootView()`、应用/测试/UI 测试三个 Xcode target 和 `Lujian` scheme。

- [ ] **Step 1: 写工程烟雾测试**

```swift
import XCTest
@testable import Lujian

final class ProjectSmokeTests: XCTestCase {
    func testVersionContract() {
        XCTAssertEqual(AppMetadata.version, "1.0.0")
        XCTAssertEqual(AppMetadata.storeSchema, 1)
    }
}
```

- [ ] **Step 2: 验证测试因工程和类型不存在而失败**

Run: `cd ios && xcodegen generate && xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/ProjectSmokeTests`

Expected: FAIL，提示 `project.yml`、target 或 `AppMetadata` 不存在。

- [ ] **Step 3: 建立最小工程和元数据**

```swift
enum AppMetadata {
    static let version = "1.0.0"
    static let build = "1"
    static let storeSchema = 1
}

@main
struct LujianApp: App {
    var body: some Scene { WindowGroup { RootView() } }
}
```

`project.yml` 必须定义 iOS 17、Swift 6、`com.lujian.travelplan.ios`、`LujianTests` 和 `LujianUITests` 依赖关系；`Info.plist` 注册 `.html/.htm` 文档类型和 `LSSupportsOpeningDocumentsInPlace=false`。

AppIcon 使用现有 Android `ic_launcher_foreground.xml` 的浅纸色底、墨色折角便签、金色路线和复古红大头针，不另造品牌符号；1024 图无透明通道，资产目录只声明 iOS universal 1024 marketing 图。

- [ ] **Step 4: 生成工程并验证烟雾测试转绿**

Run: `cd ios && xcodegen generate && xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/ProjectSmokeTests`

Expected: PASS。

- [ ] **Step 5: 提交工程骨架**

```bash
git add ios/project.yml ios/Config ios/Lujian/App ios/Lujian/Resources/Assets.xcassets ios/Lujian/Views/RootView.swift ios/LujianTests/ProjectSmokeTests.swift
git commit -m "新增 iOS SwiftUI 工程骨架"
```

### Task 2: 计划模型与原子存储

**Files:**
- Create: `ios/Lujian/Models/PlanModels.swift`
- Create: `ios/Lujian/Data/PlanStore.swift`
- Modify: `ios/Lujian/App/LujianApp.swift`
- Modify: `ios/Lujian/Views/RootView.swift`
- Create: `ios/LujianTests/PlanStoreTests.swift`
- Create: `ios/LujianTests/TestFixtures.swift`

**Interfaces:**
- Consumes: `AppMetadata.storeSchema`。
- Produces: `TravelPlan`、`PlanDay`、`PlanItem`、`PlanDestination`、`PlanPlace`、`PlanPhoto`、`PlanSnapshot`、`GalleryDeleteRequest`；`PlanStore.live() -> PlanStore`、`PlanStore.temporary(root:) -> PlanStore`、`PlanStore.recoveryIssue: String?`、`PlanStore.storageSummary: StorageSummary`、`upsert(_:)`、`archive(ids: Set<UUID>)`、`restore(ids: Set<UUID>)`、`delete(ids: Set<UUID>)`、`savePhoto(data:planID:placeID:)`、`saveCover(data:planID:)`、`deleteGalleryItems(_ request: GalleryDeleteRequest)`；测试复用的 `TravelPlan.fixture`、`PlanDay.fixture`、`temporaryRoot()`、`fixtureHTML(...)`。

- [ ] **Step 1: 写原子保存和路径边界失败测试**

```swift
@MainActor
func testStorePersistsAndRejectsOutsideDeletion() throws {
    let root = temporaryRoot()
    let store = PlanStore.temporary(root: root)
    let plan = TravelPlan.fixture(title: "大连五日")
    try store.upsert(plan)

    let reloaded = PlanStore.temporary(root: root)
    XCTAssertEqual(reloaded.plans.map(\.title), ["大连五日"])
    XCTAssertThrowsError(try reloaded.deletePrivateFile(relativePath: "../outside.jpg"))
}

@MainActor
func testArchiveRestoreAndBatchDeleteAreStable() throws {
    let store = PlanStore.temporary(root: temporaryRoot())
    let ids = [TravelPlan.fixture(title: "A"), TravelPlan.fixture(title: "B")]
    try ids.forEach(store.upsert)
    try store.archive(ids: Set(ids.map(\.id)))
    XCTAssertTrue(store.plans.allSatisfy(\.isArchived))
    try store.restore(ids: Set([ids[0].id]))
    XCTAssertFalse(store.plan(id: ids[0].id)!.isArchived)
}

@MainActor
func testCorruptIndexIsPreservedAndReported() throws {
    let root = temporaryRoot()
    try Data("{broken".utf8).write(to: root.appendingPathComponent("store.json"))
    let store = PlanStore.temporary(root: root)
    XCTAssertTrue(store.plans.isEmpty)
    XCTAssertNotNil(store.recoveryIssue)
    XCTAssertTrue(FileManager.default.fileExists(atPath: root.appendingPathComponent("store.corrupt.json").path))
}
```

- [ ] **Step 2: 验证测试因模型和存储不存在而失败**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/PlanStoreTests`

Expected: FAIL，提示 `TravelPlan` 或 `PlanStore` 未解析。

- [ ] **Step 3: 实现 Codable 模型和原子快照**

```swift
struct PlanSnapshot: Codable, Equatable {
    var schemaVersion: Int = AppMetadata.storeSchema
    var plans: [TravelPlan]
}

@MainActor
final class PlanStore: ObservableObject {
    @Published private(set) var plans: [TravelPlan]
    private let root: URL

    func upsert(_ plan: TravelPlan) throws {
        if let index = plans.firstIndex(where: { $0.id == plan.id }) { plans[index] = plan }
        else { plans.append(plan) }
        try persistAtomically()
    }
}
```

`LujianApp` 在本任务改为 `@StateObject private var store = PlanStore.live()` 并注入 `RootView(store:)`。路径解析必须先标准化根目录和目标 URL，要求目标路径前缀为 `root.appendingPathComponent("plans", isDirectory: true)`；索引使用 `Data.write(options: .atomic)`。测试辅助文件提供固定 UUID，避免快照断言随运行变化。

- [ ] **Step 4: 验证存储测试转绿**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/PlanStoreTests`

Expected: PASS。

- [ ] **Step 5: 提交模型与存储**

```bash
git add ios/Lujian/Models ios/Lujian/Data ios/Lujian/App/LujianApp.swift ios/Lujian/Views/RootView.swift ios/LujianTests/PlanStoreTests.swift ios/LujianTests/TestFixtures.swift
git commit -m "实现 iOS 计划模型与本地存储"
```

### Task 3: HTML 解码、解析与导入事务

**Files:**
- Create: `ios/Lujian/Importing/HTMLDecoder.swift`
- Create: `ios/Lujian/Importing/PlanHTMLParser.swift`
- Create: `ios/Lujian/Importing/PlanImportService.swift`
- Create: `ios/LujianTests/HTMLImportTests.swift`

**Interfaces:**
- Consumes: `TravelPlan`、`PlanStore.upsert(_:)`、私有 `plans/<id>` 目录。
- Produces: `HTMLDecoder.decode(data:fileName:) -> DecodedHTML`、`PlanHTMLParser.parse(_:) -> ParsedImport`、`PlanImportService.importURL(_:) async throws -> ImportOutcome`、`importData(_:fileName:) throws -> ImportOutcome`。

- [ ] **Step 1: 写编码、契约和去重失败测试**

```swift
func testEnhancedPlanDecodesAndParses() throws {
    let html = fixtureHTML(title: "大连", schemaVersion: 1)
    let decoded = try HTMLDecoder.decode(data: Data(html.utf8), fileName: "dalian.html")
    let parsed = try PlanHTMLParser.parse(decoded)
    XCTAssertEqual(parsed.plan.title, "大连")
    XCTAssertEqual(parsed.plan.days.first?.items.first?.title, "星海广场")
}

@MainActor
func testSameHashUpdatesWithoutLosingArchiveState() throws {
    let store = PlanStore.temporary(root: temporaryRoot())
    let service = PlanImportService(store: store)
    let first = try service.importData(Data(fixtureHTML(title: "A").utf8), fileName: "a.html")
    try store.archive(ids: Set([first.planID]))
    let second = try service.importData(Data(fixtureHTML(title: "A").utf8), fileName: "copy.html")
    XCTAssertEqual(first.planID, second.planID)
    XCTAssertTrue(store.plan(id: second.planID)!.isArchived)
}
```

另写用例覆盖伪 HTML、重复元数据块、`schemaVersion=2`、50 MB 超限、UTF-8 BOM、GB18030 和普通 HTML 标题/地理 meta。

- [ ] **Step 2: 验证测试因导入类型不存在而失败**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/HTMLImportTests`

Expected: FAIL，提示 `HTMLDecoder`、`PlanHTMLParser` 或 `PlanImportService` 未解析。

- [ ] **Step 3: 实现严格解码和增强契约解析**

```swift
struct DecodedHTML { let text: String; let data: Data; let encodingName: String }

enum HTMLDecoder {
    static let maximumBytes = 50 * 1024 * 1024
    static func decode(data: Data, fileName: String) throws -> DecodedHTML {
        guard data.count <= maximumBytes else { throw HTMLImportError.fileTooLarge }
        guard ["html", "htm"].contains((fileName as NSString).pathExtension.lowercased()) else {
            throw HTMLImportError.unsupportedType
        }
        let candidates = EncodingCandidate.orderedCandidates(for: data)
        guard let decoded = candidates.lazy.compactMap({ $0.decode(data) }).first,
              HTMLContentValidator.looksLikeHTML(decoded.text) else {
            throw HTMLImportError.invalidHTML
        }
        return DecodedHTML(text: decoded.text, data: data, encodingName: decoded.name)
    }
}

struct ImportOutcome: Equatable { let planID: UUID; let replacedExisting: Bool }
```

解析器使用不区分大小写正则定位 `<script>`，要求增强脚本恰好一个，再以 `JSONDecoder` 解码严格 DTO 并验证非空字段。普通 HTML 提取 `<title>`、`lujian:*`/`geo.*` meta；SHA-256 使用 `CryptoKit.SHA256`。

- [ ] **Step 4: 验证导入测试转绿**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/HTMLImportTests`

Expected: PASS。

- [ ] **Step 5: 提交导入链路**

```bash
git add ios/Lujian/Importing ios/LujianTests/HTMLImportTests.swift
git commit -m "实现 iOS HTML 导入与解析"
```

### Task 4: 导出与 WebView 安全策略

**Files:**
- Create: `ios/Lujian/Exporting/PlanHTMLExporter.swift`
- Create: `ios/Lujian/Security/WebNavigationPolicy.swift`
- Create: `ios/Lujian/Views/SecureWebView.swift`
- Create: `ios/LujianTests/ExportAndSecurityTests.swift`

**Interfaces:**
- Consumes: `TravelPlan` 和其原始 HTML 私有 URL。
- Produces: `PlanHTMLExporter.data(for:) throws -> Data`、`WebNavigationPolicy.decision(for:isMainFrame:) -> WebDecision`、`SecureWebView(fileURL:allowsJavaScript:)`。

- [ ] **Step 1: 写导出再导入和安全策略失败测试**

```swift
func testExportRoundTripsCoreContract() throws {
    let source = TravelPlan.fixture(title: "大连五日")
    let data = try PlanHTMLExporter.data(for: source)
    XCTAssertFalse(data.starts(with: [0xEF, 0xBB, 0xBF]))
    let parsed = try PlanHTMLParser.parse(try HTMLDecoder.decode(data: data, fileName: "export.html"))
    XCTAssertEqual(parsed.plan.title, source.title)
    XCTAssertEqual(parsed.plan.days, source.days)
}

func testNavigationPolicyRejectsUnsafeSchemes() {
    XCTAssertEqual(WebNavigationPolicy.decision(for: URL(string: "http://example.com")!, isMainFrame: true), .cancel)
    XCTAssertEqual(WebNavigationPolicy.decision(for: URL(string: "javascript:alert(1)")!, isMainFrame: true), .cancel)
    XCTAssertEqual(WebNavigationPolicy.decision(for: URL(string: "https://example.com")!, isMainFrame: true), .openExternally)
}
```

- [ ] **Step 2: 验证测试因导出器和策略不存在而失败**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/ExportAndSecurityTests`

Expected: FAIL，提示 `PlanHTMLExporter` 或 `WebNavigationPolicy` 未解析。

- [ ] **Step 3: 实现自包含 HTML 和隔离 WKWebView**

```swift
enum WebDecision: Equatable { case allow, cancel, openExternally }

struct WebNavigationPolicy {
    static func decision(for url: URL, isMainFrame: Bool) -> WebDecision {
        guard isMainFrame else { return url.scheme == "https" ? .allow : .cancel }
        if url.isFileURL { return .allow }
        if url.scheme == "https" { return .openExternally }
        return .cancel
    }
}
```

`SecureWebView` 使用 `WKWebViewConfiguration.defaultWebpagePreferences.allowsContentJavaScript`，不注册 `WKScriptMessageHandler`，只用 `loadFileURL(_:allowingReadAccessTo:)` 读取单个计划目录。

- [ ] **Step 4: 验证导出与安全测试转绿**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/ExportAndSecurityTests`

Expected: PASS。

- [ ] **Step 5: 提交导出和安全边界**

```bash
git add ios/Lujian/Exporting ios/Lujian/Security ios/Lujian/Views/SecureWebView.swift ios/LujianTests/ExportAndSecurityTests.swift
git commit -m "实现 iOS 导出与 WebView 安全策略"
```

### Task 5: 根导航、首页地图与旅笺板

**Files:**
- Modify: `ios/Lujian/App/LujianApp.swift`
- Modify: `ios/Lujian/Views/RootView.swift`
- Create: `ios/Lujian/Views/HomeMapView.swift`
- Create: `ios/Lujian/Views/PlanBoardView.swift`
- Create: `ios/LujianTests/BoardPolicyTests.swift`

**Interfaces:**
- Consumes: `PlanStore`、`PlanImportService`、`TravelPlan.isArchived` 和目的地坐标。
- Produces: `RootTab`、`BoardMode`、`BoardSelectionPolicy`、`HomeMapView`、`PlanBoardView`；应用内 `fileImporter` 和 `onOpenURL` 导入接线。

- [ ] **Step 1: 写旅笺板选择策略失败测试**

```swift
func testSelectionRetainsVisibleIDsAndSelectAllUsesCurrentBoard() {
    let active = [TravelPlan.fixture(title: "A"), TravelPlan.fixture(title: "B")]
    let archived = TravelPlan.fixture(title: "C", archived: true)
    XCTAssertEqual(BoardSelectionPolicy.selectAll(plans: active + [archived], mode: .active), Set(active.map(\.id)))
    XCTAssertEqual(BoardSelectionPolicy.retain(Set([active[0].id, archived.id]), in: active), Set([active[0].id]))
}
```

- [ ] **Step 2: 验证测试因板策略不存在而失败**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/BoardPolicyTests`

Expected: FAIL，提示 `BoardSelectionPolicy` 未解析。

- [ ] **Step 3: 实现四栏和计划管理交互**

```swift
enum RootTab: Hashable { case home, board, gallery, profile }
enum BoardMode: Hashable { case active, archived }

enum BoardSelectionPolicy {
    static func selectAll(plans: [TravelPlan], mode: BoardMode) -> Set<UUID> {
        Set(plans.filter { $0.isArchived == (mode == .archived) }.map(\.id))
    }
}
```

首页使用 `Map(position:)` 和 `Marker`；旅笺板使用 `NavigationStack`、系统 `fileImporter`、`confirmationDialog` 和 44 点以上管理控件。导入结果在根页面用可访问 alert 显示。

- [ ] **Step 4: 验证板策略和工程编译转绿**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/BoardPolicyTests`

Expected: PASS。

- [ ] **Step 5: 提交根导航与旅笺板**

```bash
git add ios/Lujian/App ios/Lujian/Views/RootView.swift ios/Lujian/Views/HomeMapView.swift ios/Lujian/Views/PlanBoardView.swift ios/LujianTests/BoardPolicyTests.swift
git commit -m "实现 iOS 根导航与旅笺板"
```

### Task 6: 计划详情、每日地图与编辑

**Files:**
- Create: `ios/Lujian/Views/PlanDetailView.swift`
- Create: `ios/Lujian/Views/PlanEditorView.swift`
- Create: `ios/LujianTests/DetailPolicyTests.swift`

**Interfaces:**
- Consumes: `TravelPlan`、`PlanStore.upsert(_:)`、`SecureWebView`。
- Produces: `PlanDetailSection`、`DailyRoutePolicy.stops(for:)`、`PlanDetailView(planID:store:)`、`PlanEditorView(plan:onSave:)`。

- [ ] **Step 1: 写日期选择和路线失败测试**

```swift
func testDailyRouteKeepsOnlyCoordinateStopsInItemOrder() {
    let day = PlanDay.fixture(stops: [.coordinate("A", 38, 121), .missing("B"), .coordinate("C", 39, 122)])
    XCTAssertEqual(DailyRoutePolicy.stops(for: day).map(\.title), ["A", "C"])
}

func testSelectedDayFallsBackAfterEdit() {
    let days = [PlanDay.fixture(id: "day-2")]
    XCTAssertEqual(DetailSelectionPolicy.validDayID("removed", in: days), "day-2")
}
```

- [ ] **Step 2: 验证测试因详情策略不存在而失败**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/DetailPolicyTests`

Expected: FAIL，提示 `DailyRoutePolicy` 或 `DetailSelectionPolicy` 未解析。

- [ ] **Step 3: 实现四分区详情与编辑提交**

```swift
enum PlanDetailSection: Hashable, CaseIterable { case itinerary, map, budget, gallery }

enum DetailSelectionPolicy {
    static func validDayID(_ selected: String?, in days: [PlanDay]) -> String? {
        days.contains(where: { $0.id == selected }) ? selected : days.first?.id
    }
}
```

地图用 `MapPolyline` 按有效坐标顺序连接；外部地图按钮优先打开计划提供的高德/百度 HTTPS 链接，并始终提供 Apple Maps。编辑页在本地副本上修改，点击保存时一次调用 `store.upsert`，取消不写入。

- [ ] **Step 4: 验证详情策略和工程编译转绿**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/DetailPolicyTests`

Expected: PASS。

- [ ] **Step 5: 提交详情与编辑**

```bash
git add ios/Lujian/Views/PlanDetailView.swift ios/Lujian/Views/PlanEditorView.swift ios/LujianTests/DetailPolicyTests.swift
git commit -m "实现 iOS 计划详情与编辑"
```

### Task 7: 私有相册与批量管理

**Files:**
- Create: `ios/Lujian/Views/GalleryViews.swift`
- Create: `ios/LujianTests/GalleryPolicyTests.swift`

**Interfaces:**
- Consumes: `PlanStore.savePhoto`、`PlanStore.deleteGalleryItems`、`TravelPlan.photos`、PhotosUI。
- Produces: `GallerySelectionKey`、`GallerySelectionPolicy`、`PlanGalleryView`、`GlobalGalleryView`。

- [ ] **Step 1: 写选择去重与删除摘要失败测试**

```swift
func testGallerySelectAllDeduplicatesAcrossGroups() {
    let photo = GallerySelectionKey.photo(planID: UUID(), photoID: UUID())
    XCTAssertEqual(GallerySelectionPolicy.selectAll([photo, photo]), [photo])
}

func testSummarySeparatesPhotosAndCovers() {
    let planID = UUID()
    let keys: Set<GallerySelectionKey> = [.photo(planID: planID, photoID: UUID()), .cover(planID: planID)]
    XCTAssertEqual(GallerySelectionPolicy.summary(keys), "删除 1 张照片和 1 张自定义预览图？")
}
```

- [ ] **Step 2: 验证测试因相册策略不存在而失败**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/GalleryPolicyTests`

Expected: FAIL，提示 `GallerySelectionKey` 未解析。

- [ ] **Step 3: 实现 PhotosPicker 私有复制和统一管理状态**

```swift
enum GallerySelectionKey: Hashable {
    case photo(planID: UUID, photoID: UUID)
    case cover(planID: UUID)
}

enum GallerySelectionPolicy {
    static func selectAll(_ keys: [GallerySelectionKey]) -> Set<GallerySelectionKey> { Set(keys) }
    static func retain(_ selection: Set<GallerySelectionKey>, available: Set<GallerySelectionKey>) -> Set<GallerySelectionKey> { selection.intersection(available) }
}
```

`PhotosPicker` 使用多选，逐项 `loadTransferable(type: Data.self)`；写入前识别 JPEG/PNG/HEIC 后缀，复制到计划私有 `media/`。编辑页的单选 PhotosPicker 调用 `store.saveCover`，旧封面仅在新文件和索引提交成功后幂等删除。管理模式切换分组时保留稳定选择键，数据刷新后收敛失效项。

- [ ] **Step 4: 验证相册测试转绿**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/GalleryPolicyTests`

Expected: PASS。

- [ ] **Step 5: 提交相册能力**

```bash
git add ios/Lujian/Views/GalleryViews.swift ios/LujianTests/GalleryPolicyTests.swift
git commit -m "实现 iOS 私有相册批量管理"
```

### Task 8: 我的页、演示计划和外部打开恢复

**Files:**
- Create: `ios/Lujian/Views/ProfileView.swift`
- Create: `ios/Lujian/Resources/DemoPlan.html`
- Modify: `ios/Lujian/App/LujianApp.swift`
- Modify: `ios/Lujian/Views/RootView.swift`
- Create: `ios/LujianTests/AppFlowTests.swift`

**Interfaces:**
- Consumes: `PlanStore.storageSummary`、`PlanImportService.importData`、应用 `onOpenURL`。
- Produces: `ProfileView`、`DemoPlanLoader.importIfNeeded(store:)`、排队外部 URL 的恢复逻辑。

- [ ] **Step 1: 写演示导入幂等测试**

```swift
@MainActor
func testDemoImportIsIdempotent() throws {
    let store = PlanStore.temporary(root: temporaryRoot())
    try DemoPlanLoader.importIfNeeded(store: store)
    try DemoPlanLoader.importIfNeeded(store: store)
    XCTAssertEqual(store.plans.filter { $0.title == "大连五日旅行计划" }.count, 1)
}
```

- [ ] **Step 2: 验证测试因演示加载器不存在而失败**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/AppFlowTests`

Expected: FAIL，提示 `DemoPlanLoader` 未解析。

- [ ] **Step 3: 实现资料页和启动参数**

```swift
enum DemoPlanLoader {
    @MainActor static func importIfNeeded(store: PlanStore) throws {
        guard !store.plans.contains(where: { $0.sourceName == "DemoPlan.html" }) else { return }
        try PlanImportService(store: store).importBundledResource(name: "DemoPlan", extension: "html")
    }
}
```

当启动参数包含 `-ui-testing` 时使用临时存储；包含 `-seed-demo` 时自动导入演示计划。普通启动的 `onOpenURL` 在 store 就绪后排队处理 URL，并释放安全作用域。资料页读取 `storageSummary` 展示计划数、照片数和私有文件体积；`recoveryIssue` 非空时显示“索引已隔离，原文件仍保留”的恢复提示。

- [ ] **Step 4: 验证应用流程测试转绿**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianTests/AppFlowTests`

Expected: PASS。

- [ ] **Step 5: 提交资料页和恢复路径**

```bash
git add ios/Lujian/Views/ProfileView.swift ios/Lujian/Resources/DemoPlan.html ios/Lujian/App/LujianApp.swift ios/Lujian/Views/RootView.swift ios/LujianTests/AppFlowTests.swift
git commit -m "完善 iOS 演示导入与资料页"
```

### Task 9: iPhone 模拟器 UI 全流程

**Files:**
- Create: `ios/LujianUITests/LujianUITests.swift`
- Modify: `ios/Lujian/Views/*.swift`（仅补稳定 accessibility identifier/label）

**Interfaces:**
- Consumes: `-ui-testing`、`-seed-demo` 启动参数和所有核心页面。
- Produces: `testCoreJourney()`、`testArchiveRestoreAndGalleryManagement()` 两条可重复 UI 流程。

- [ ] **Step 1: 写失败的端到端 UI 测试**

```swift
func testCoreJourney() {
    app.launchArguments = ["-ui-testing", "-seed-demo"]
    app.launch()
    XCTAssertTrue(app.tabBars.buttons["旅笺板"].waitForExistence(timeout: 8))
    app.tabBars.buttons["旅笺板"].tap()
    app.buttons["计划卡片-大连五日旅行计划"].tap()
    XCTAssertTrue(app.segmentedControls.buttons["行程"].exists)
    app.segmentedControls.buttons["地图"].tap()
    XCTAssertTrue(app.otherElements["每日路线地图"].exists)
}
```

第二条测试进入管理、选中计划、归档、切换足迹板并恢复；再进入相册验证管理入口、空相册提示和退出管理。

- [ ] **Step 2: 验证 UI 测试因标识或页面不完整而失败**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianUITests`

Expected: FAIL，指出第一个缺失的 accessibility identifier 或交互。

- [ ] **Step 3: 添加稳定可访问标识和系统交互**

所有标识只附着到最终可点击元素，避免一个标识重复出现在容器和子元素；按钮同时保留中文 VoiceOver label。计划卡片使用：

```swift
Button { open(plan.id) } label: { PlanCard(plan: plan) }
    .accessibilityIdentifier("计划卡片-\(plan.title)")
    .accessibilityLabel("打开计划，\(plan.title)")
```

详情分区 `Picker` 使用 `.accessibilityIdentifier("计划详情分区")`，地图容器使用 `.accessibilityIdentifier("每日路线地图")`，管理按钮分别使用“管理计划”和“管理相册”。

- [ ] **Step 4: 验证 UI 全流程转绿**

Run: `xcodebuild test -scheme Lujian -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LujianUITests`

Expected: PASS，两条流程均通过且测试间使用独立临时存储。

- [ ] **Step 5: 提交 UI 测试与可访问性**

```bash
git add ios/LujianUITests ios/Lujian/Views
git commit -m "补充 iPhone 模拟器全流程测试"
```

### Task 10: CI 打包、发布和文档

**Files:**
- Create: `.github/workflows/ios-release.yml`
- Create: `docs/releases/ios-v1.0.0.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: `Lujian` scheme、macOS runner、GitHub `GITHUB_TOKEN`。
- Produces: `Lujian-iOS-Simulator-1.0.0.app.zip`、`Lujian-iOS-Unsigned-1.0.0.ipa`、`SHA256SUMS.txt` 和 `ios-v1.0.0-ci.<run_number>` 预发布。

- [ ] **Step 1: 写 CI 契约检查并验证缺失**

Run: `Test-Path .github/workflows/ios-release.yml; Test-Path docs/releases/ios-v1.0.0.md`

Expected: 两项均为 `False`。

- [ ] **Step 2: 实现 macOS 构建与发布流水线**

```yaml
name: iOS 全流程测试与预发布
on:
  push:
    branches: [codex/ios-adaptation]
  workflow_dispatch:
permissions:
  contents: write
jobs:
  test-build-release:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - run: brew install xcodegen
      - run: cd ios && xcodegen generate
      - run: ./ios/ci/test-and-package.sh
      - uses: actions/upload-artifact@v4
        with:
          name: Lujian-iOS-1.0.0
          path: ios/build/release/*
      - run: gh release create "ios-v1.0.0-ci.${GITHUB_RUN_NUMBER}" ios/build/release/* --prerelease --target "$GITHUB_SHA" --title "旅笺 iOS 1.0.0 CI ${GITHUB_RUN_NUMBER}" --notes-file docs/releases/ios-v1.0.0.md
        env:
          GH_TOKEN: ${{ github.token }}
```

同时新建 `ios/ci/test-and-package.sh`：动态选择已安装的 iPhone simulator；执行全部单元/UI 测试；构建 `iphonesimulator`、`simctl install`、`simctl launch`；构建 `iphoneos CODE_SIGNING_ALLOWED=NO`；生成 `.app.zip`、未签名 `.ipa` 和 SHA-256。

- [ ] **Step 3: 更新发布说明和 README**

文档必须给出模拟器安装命令：

```bash
unzip Lujian-iOS-Simulator-1.0.0.app.zip
xcrun simctl install booted Lujian.app
xcrun simctl launch booted com.lujian.travelplan.ios
```

未签名 IPA 段必须写明需要 AltStore/Sideloadly 或 Apple 证书重签，且本次没有真机验证。README 同时保留 Android 1.2.0 安装说明。

- [ ] **Step 4: 执行本地可做的静态和 Android 基线检查**

Run: `git diff --check`

Expected: PASS，无空白错误。

Run: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug`

Expected: PASS，确认新增 iOS 目录未破坏 Android 基线。

- [ ] **Step 5: 提交并推送触发 macOS 流水线**

```bash
git add .github/workflows/ios-release.yml ios/ci docs/releases/ios-v1.0.0.md README.md
git commit -m "新增 iOS 自动测试与预发布流水线"
git push -u origin codex/ios-adaptation
```

- [ ] **Step 6: 等待 GitHub Actions 并验证发布资产**

Run: `gh run watch <run-id> --exit-status`

Expected: PASS，测试、模拟器安装和启动、两类包生成、release 上传全部成功。

Run: `gh release view ios-v1.0.0-ci.<run_number> --json url,assets,isPrerelease`

Expected: `isPrerelease=true`，三个产物均存在，返回可访问发布 URL。
