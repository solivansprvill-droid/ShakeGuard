# ShakeGuard 摇一摇克星 🛡

安卓版「Restrict Motion Data」—— 关掉传感器，让「摇一摇跳广告」彻底失效。

## 下载

- **APK（推荐）**：[ShakeGuard-v1.0.0-debug.apk](https://github.com/solivansprvill-droid/ShakeGuard/releases/latest/download/ShakeGuard-v1.0.0-debug.apk)
  —— 由 GitHub Actions 自动构建（见 `.github/workflows/android.yml`），debug 签名包，需 Android 7.0+
- **官网**：https://shakeguard-lp.app.workbuddy.host/
- **自己编译**：克隆仓库用 Android Studio 打开，`Build → Build APK(s)`（零第三方依赖）

## 背景

iOS 27.2 Beta 2 新增了 **Restrict Motion Data** 开关：在 App Store 登录国区账号后，
进入「设置 → 隐私与安全 → 运动与健身」，可以把指定 App 加入黑名单，被加入的 App
将拿不到加速度计/陀螺仪数据，摇一摇跳广告（拼多多、闲鱼等）直接失效。

安卓没有这个按 App 拒绝的机制（读取加速度计不需要任何运行时权限），但系统提供了
一个开发者级总开关 `Settings.Secure"sensors_off"`，置 1 后全机传感器静默。
本 App 通过 `WRITE_SECURE_SETTINGS` 权限读写这个开关，实现同等效果。

## 功能

- **主开关**：App 内一键开关全机传感器
- **快捷磁贴**：下拉通知栏「传感器开关」磁贴，开拼多多前一秒关、退出后一秒开
- **定时自动恢复**：1/5/10 分钟后自动恢复传感器，防止忘开导航/计步
- **零第三方依赖**：纯 Android SDK，单 Activity + 一个 TileService

## 使用

1. 从 [Releases](https://github.com/solivansprvill-droid/ShakeGuard/releases) 下载 APK 安装；
   或自行构建：用 Android Studio 打开项目，`Build → Build APK(s)` 生成 `app-debug.apk`；
2. 手机连电脑（开启 USB 调试），执行一次授权命令（之后永久生效）：

   ```
   adb shell pm grant com.victory.shakeguard android.permission.WRITE_SECURE_SETTINGS
   ```

   （App 内也有一键复制该命令的按钮）
3. 下拉通知栏，长按编辑磁贴，把「传感器开关」加到快捷面板。

## 副作用说明

`sensors_off` 关闭的是**全机**传感器：自动横屏、计步、抬手亮屏、指南针、部分导航
会暂时失效。用完记得开回来，或用定时恢复功能。

## 局限性

- 无法做到 iOS 那样的**按 App** 精确屏蔽（那需要 root + Xposed/LSPosed 模块）；
- `sensors_off` 是全局开关，属于「用小不方便换大清净」的工程折中。

## 构建

| 项 | 值 |
|---|---|
| minSdk | 24 (Android 7.0) |
| targetSdk | 34 |
| 语言 | Kotlin |
| 第三方依赖 | 无 |
