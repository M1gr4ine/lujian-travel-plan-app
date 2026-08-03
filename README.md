# 旅笺 Android

用于在手机上导入、阅读和编辑 HTML 旅行计划的原生 Android 应用。

## 当前能力

- Kotlin + Jetpack Compose，最低 Android 8（API 26），目标 API 37。
- 系统开屏接续“航线绘制—大头针—旅笺”品牌动画。
- 首页使用 MapLibre + OpenFreeMap Liberty；中国/全球视野自动切换，相近目的地聚合。
- 计划库使用双列方卡，支持系统文件选择器以及微信 `ACTION_SEND` / `ACTION_VIEW`。
- 支持 UTF-8、GB18030、BOM 和 `meta charset`；文件上限 50 MB，使用 SHA-256 检测重复。
- 解析顺序：旅笺 JSON 元数据、大连模板、普通 HTML。
- 增强计划使用顶部单轴日期切换；日期点选和左右滑动保持同步。
- 编辑计划名、目的地、日期、每日标题以及行程项；生成版不会覆盖原始 HTML。
- 普通 HTML 通过 `WebViewAssetLoader` 隔离加载，默认禁用 JavaScript、文件访问、明文 HTTP、下载和原生桥。
- 支持导出 UTF-8 无 BOM 的独立 HTML。

## 构建

需要 JDK 17 与 Android SDK 37：

```powershell
$env:JAVA_HOME='C:\path\to\jdk-17'
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## HTML 增强格式

自定义计划可在 HTML 中嵌入：

```html
<script id="lujian-plan" type="application/json">
{
  "schemaVersion": 1,
  "title": "旅行计划",
  "destinations": [
    {"name": "大连", "countryCode": "CN", "latitude": 38.914, "longitude": 121.6147}
  ],
  "days": [
    {
      "id": "day-1",
      "label": "9月25日",
      "title": "抵达",
      "items": [
        {"id": "item-1", "time": "10:00", "title": "星海广场", "category": "景点", "cost": "免费", "notes": ""}
      ]
    }
  ]
}
</script>
```

