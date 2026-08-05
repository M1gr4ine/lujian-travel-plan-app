# iOS 适配设计

## 目标

在不改动现有 Android 应用行为的前提下，为旅笺新增原生 iOS 应用和可重复的 macOS CI 构建、测试、打包、发布链路。首个 iOS 版本号为 `1.0.0`，最低支持 iOS 17。

没有真机和 Apple 发布证书时，交付边界明确为：

- 发布可直接安装到 iOS Simulator 的 `.app.zip`。
- 发布未签名 `.ipa`，供 AltStore、Sideloadly 或后续 Apple 证书重签。
- CI 必须在 iPhone 模拟器完成单元测试、应用启动和核心用户流程测试。
- 不宣称未签名 IPA 可以直接安装到普通真机，也不伪造真机验证结果。

## 方案选择

### 采用：原生 SwiftUI 子工程

在仓库的 `ios/` 目录新增独立 SwiftUI 工程。iOS 端复用现有 `lujian-plan` JSON 数据契约和产品信息架构，平台能力使用 MapKit、WKWebView、PhotosUI、UniformTypeIdentifiers、CryptoKit 和系统文件分享。

选择原因：当前 Android 工程的 UI、导航、数据库、后台任务、地图、WebView、文件 URI 和相册链路均直接依赖 Android/AndroidX。独立 iOS 工程可以保持 Android 基线稳定，并按 iOS 原生交互和安全边界落地。

### 未采用：Compose Multiplatform 整体迁移

优点是长期可共享部分 Kotlin 模型与 UI；缺点是需要同时替换或抽象 Room、WorkManager、MapLibre Android、WebViewAssetLoader、Content URI、Android 导航和相册接口。首轮迁移范围远大于 iOS 适配本身，且会扩大 Android 回归面。

### 未采用：PWA 或网页壳

可以快速展示行程，但无法完整覆盖系统文件导入、私有文件复制、离线相册、系统照片选择、原生地图、应用间打开和安全的本地 HTML 阅读，因此不满足旅笺的本地优先核心能力。

## 工程结构

```text
ios/
├─ project.yml                         XcodeGen 工程定义
├─ Config/                             Info.plist、测试配置
├─ Lujian/
│  ├─ App/                             应用入口、根导航、主题
│  ├─ Models/                          Codable 计划、日期、地点、照片模型
│  ├─ Data/                            私有目录与原子 JSON 持久化
│  ├─ Importing/                       HTML 校验、编码、哈希、解析和去重
│  ├─ Exporting/                       自包含移动版 HTML 导出
│  ├─ Security/                        WKWebView 导航与脚本策略
│  ├─ Views/                           首页、旅笺板、详情、相册、我的
│  └─ Resources/                       颜色、图标和演示导入文件
├─ LujianTests/                        纯逻辑与存储集成测试
└─ LujianUITests/                      iPhone 模拟器核心流程测试
```

工程使用 XcodeGen 描述文件，CI 在 macOS runner 生成 `.xcodeproj`，避免在 Windows 手工维护易损的 `project.pbxproj`。生产代码只依赖 Apple SDK，不引入第三方运行时包。

## 数据与文件

### 计划模型

iOS 模型覆盖 Android 当前公开能力：

- 计划标题、日期范围、旅行者、风格、预算、住宿、备注和归档状态。
- 目的地、按天行程、行程项、地点、地图坐标、地图链接和分段内容。
- 原始 HTML 相对路径、自定义封面相对路径、地点照片和创建/更新时间。
- 解析能力区分增强格式与仅查看格式。

持久化使用 `Codable` 快照和应用私有目录：

- `Application Support/Lujian/store.json` 保存结构化索引。
- `Application Support/Lujian/plans/<id>/source.html` 保存原始 HTML。
- `Application Support/Lujian/plans/<id>/media/` 保存封面和地点照片副本。
- 写入先落临时文件，再使用原子替换，避免中途退出破坏索引。
- 删除只允许解析到旅笺根目录内的相对路径，禁止越界删除外部文件。

### HTML 导入

导入入口包括应用内文件选择器和系统“用其他应用打开”。处理顺序保持 Android 契约：

1. 扩展名/UTType、50 MB 大小和真实 HTML 内容校验。
2. BOM、`meta charset`、严格 UTF-8、GB18030 顺序解码。
3. SHA-256 内容哈希去重；同哈希默认更新已有计划，保留用户相册和归档状态。
4. 优先解析唯一的 `script#lujian-plan[type="application/json"]` 且 `schemaVersion=1` 的增强格式。
5. 其他有效 HTML 作为仅查看计划，提取标题和地理书签；无坐标时允许后续编辑目的地。
6. 原始文件复制到私有目录，外部安全作用域访问在读取后立即释放。

### 导出

增强计划导出 UTF-8、无 BOM、自包含 HTML，并嵌入同一 `lujian-plan` JSON。通过系统文件导出器保存或分享；导出后必须能够被 iOS 解析器重新导入。

## iOS 交互

### 根导航

使用四栏 `TabView`：

- 首页：MapKit 展示全部已确认坐标的计划大头针，点击进入计划。
- 旅笺板：计划板/足迹板切换，导入、管理、多选、全选、归档、恢复和删除。
- 相册：跨计划按加入时间或计划分组，支持管理、多选、全选和批量删除。
- 我的：版本、存储统计、隐私说明、演示计划导入和测试信息。

根导航尊重 iPhone 安全区，不自行覆盖 Home Indicator。主要触控目标不小于 44 点，正文支持动态字体和 VoiceOver 标签，危险操作使用系统确认对话框。

### 计划详情

详情使用原生导航和四个分区：

- 行程：日期横向选择、按天卡片、行程项展开和备注。
- 地图：MapKit 当日地点、顺序折线、距离/时间摘要和 Apple/高德/百度地图链接。
- 预算：预算、住宿、假设和补充段落。
- 相册：PhotosPicker 多选导入，照片复制到私有目录，按时间或地点浏览与批量删除。

编辑页允许修改计划文本、日期、行程项、目的地坐标和自定义封面。普通 HTML 仅允许修改标题、目的地和封面，正文通过隔离 WKWebView 查看。

### HTML 安全策略

- 使用 `WKWebView` 加载私有文件，默认禁用 JavaScript。
- 不注册脚本消息桥，不开放任意本地文件目录。
- HTTPS 顶层链接交给系统浏览器；HTTP、非 Web scheme 和子资源跳转默认阻止。
- 只允许当前计划私有目录内的读取范围。
- 兼容模式只对单个计划显式开启 JavaScript，不改变外链和本地目录限制。

## 错误与恢复

- 导入、解析、存储、图片复制和导出错误统一显示可读提示，不静默丢失数据。
- 无定位结果不阻止导入；计划保留在旅笺板，等待手动补坐标。
- 相册批量删除先提交索引，再幂等删除私有文件；不存在文件视为成功，越界路径视为失败。
- 存储索引损坏时保留原文件并启动空索引，同时在“我的”页显示恢复提示；不自动删除孤立文件。
- CI 发布只在全部测试、模拟器安装和启动烟雾测试通过后执行。

## 测试与发布

### 自动化测试

- 解析：增强契约、普通 HTML、伪 HTML、重复脚本、版本错误、UTF-8/GB18030、50 MB 边界。
- 数据：原子保存/重载、哈希去重更新、归档/恢复、编辑、越界路径保护、照片批量删除。
- 导出：增强计划导出再导入保持标题、日期、地点和预算。
- 安全：JavaScript 默认关闭、HTTP 和非法 scheme 被拒绝、HTTPS 外链外跳。
- UI：启动、导入内置演示计划、四栏导航、计划详情、归档/恢复和相册管理入口。

### CI 和产物

GitHub Actions 使用 macOS runner：

1. 安装 XcodeGen 并生成工程。
2. 选择可用的 iPhone Simulator，执行 `xcodebuild test`。
3. 无签名构建 `iphonesimulator` 应用，使用 `simctl install` 和 `simctl launch` 验证可安装、可启动。
4. 压缩为 `Lujian-iOS-Simulator-1.0.0.app.zip`。
5. 无签名构建 `iphoneos` 应用并打包为 `Lujian-iOS-Unsigned-1.0.0.ipa`。
6. 生成 SHA-256 校验文件和发布说明。
7. 在分支发布流水线通过后创建或更新 `ios-v1.0.0` 预发布，并上传全部产物。

如果仓库未来提供 Apple Distribution 证书和 provisioning profile，流水线可增加签名与真机 IPA 阶段；本次不依赖也不假定这些秘密存在。

## 非目标

- 不修改 Android 现有版本、数据库或发布包。
- 不增加账号、云同步、推送、应用商店元数据或付费能力。
- 不声称完成 App Store 审核、真机兼容性或正式签名验证。
- 不实现与本次 iOS 适配无关的暗色主题和全离线地图。
