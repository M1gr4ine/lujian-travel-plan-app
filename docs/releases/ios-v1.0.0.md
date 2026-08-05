# 旅笺 iOS 1.0.0 预发布

这是旅笺的首个原生 SwiftUI iOS 版本，最低支持 iOS 17。核心能力包括 HTML 导入与去重、计划/足迹批量管理、首页及每日路线地图、结构化编辑、UTF-8 HTML 导出、私有相册和隔离原页查看。

## 安装包

- `Lujian-iOS-Simulator-1.0.0.app.zip`：已由流水线安装并启动验证的 iPhone 模拟器应用。
- `Lujian-iOS-Unsigned-1.0.0.ipa`：未签名真机包，只用于后续重签或侧载。
- `SHA256SUMS.txt`：两个安装包的 SHA-256 校验值。

## 模拟器安装

需要 macOS、Xcode 16 或更高版本，并先启动一个 iOS 17+ iPhone 模拟器：

```bash
unzip Lujian-iOS-Simulator-1.0.0.app.zip
xcrun simctl install booted Lujian.app
xcrun simctl launch booted com.lujian.travelplan.ios
```

## 真机侧载边界

`Lujian-iOS-Unsigned-1.0.0.ipa` 没有 Apple 证书、Provisioning Profile 或 App Store 签名，不能直接安装到普通 iPhone。需要使用自己的 Apple 开发者身份重签，或通过 AltStore/Sideloadly 完成侧载；证书有效期和设备信任由所用签名方式决定。

本次没有 iPhone 实机，真机触控、相册权限和侧载签名未验证。流水线已在 iPhone Simulator 上执行单元测试、两条核心 UI 流程、应用安装和启动验证。

## 隐私

计划原始 HTML、编辑后的结构化索引和所选照片只写入应用私有目录。删除旅笺私有照片不会删除系统相册原图；默认禁用 HTML JavaScript，不注册原生脚本桥，HTTP 与非法 scheme 会被阻止。
