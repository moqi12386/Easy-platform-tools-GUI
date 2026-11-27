# 第三方组件与许可

本仓库**不包含**任何第三方二进制文件。下表列出本项目在运行、构建、发布过程中使用到的
开源 / 免费组件，以及各自的许可与获取方式。

除「运行时」和「可选」两类外，仓库里还有一部分**直接拷贝进来的第三方源码**（液态玻璃组件），
见文末 [拷贝的第三方源码](#拷贝的第三方源码)。

## 运行时

| 组件 | 用途 | 许可 | 是否随仓库分发 |
|---|---|---|---|
| [.NET Framework 4.x](https://dotnet.microsoft.com/download/dotnet-framework)（WinForms / System.Drawing） | Windows 端界面与进程控制 | Microsoft .NET Framework 许可（随 Windows 提供） | 否，系统自带 |
| [AndroidX / Jetpack Compose](https://developer.android.com/jetpack/androidx) | Android 端界面 | Apache-2.0 | 否，Gradle 从 Maven 拉取 |

## Android 端依赖（Gradle）

`android-compose/app/build.gradle.kts` 里声明的依赖，全部从 Maven 中央 / Google Maven 拉取，仓库不存二进制：

| 组件 | 用途 | 许可 |
|---|---|---|
| androidx.core:core-ktx | 基础扩展 | Apache-2.0 |
| androidx.activity:activity-compose | Compose 入口 | Apache-2.0 |
| androidx.compose.ui / foundation | Compose 基础 | Apache-2.0 |
| androidx.compose.material3 | Material 3 组件 | Apache-2.0 |
| androidx.compose.material:material-icons-core / -extended | 图标 | Apache-2.0 |
| androidx.documentfile | SAF 目录读取（线刷选目录） | Apache-2.0 |
| androidx.palette:palette-ktx | 图片主色（**已不再用于莫奈**，莫奈改用 MCU，见文末） | Apache-2.0 |
| [io.github.kyant0:backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | 液态玻璃的实时模糊 / 折射 / 高光 | Apache-2.0 |
| [io.github.kyant0:shapes](https://github.com/Kyant0/AndroidLiquidGlass) | Capsule 等形状 | Apache-2.0 |
| [top.yukonga.miuix.kmp:miuix-ui-android](https://github.com/compose-miuix-ui/miuix) | MIUIX 风格组件 | Apache-2.0 |
| [io.coil-kt:coil-compose](https://github.com/coil-kt/coil) | 图片加载 | Apache-2.0 |


## 可选：与设备通信

| 组件 | 用途 | 许可 | 是否随仓库分发 |
|---|---|---|---|
| [Android platform-tools](https://developer.android.com/tools/releases/platform-tools)（adb / fastboot） | 与 Android 设备通信 | Android SDK 许可协议 / Apache-2.0 | 否。仅在用户点击「立即下载」后，由用户机器从 Google 官方地址（或与其 SHA256 一致的镜像）拉取到本地 `bin\`，本仓库不存储、不分发 |

首次启动的下载顺序：腾讯云镜像 → Google 官方 → Google CDN。
腾讯云镜像与官方文件的 SHA256 一致，仅作加速，二进制内容不变。

## 可选：字体

| 组件 | 用途 | 许可 | 是否随仓库分发 |
|---|---|---|---|
| [MiSans](https://hyperos.mi.com/font/zh/) | 默认界面字体 | MiSans 字体知识产权许可协议（免费商用，需在软件中注明，且不得单独再分发字体文件） | 否 |
| [JetBrains Mono](https://www.jetbrains.com/lp/mono/) | 等宽字体预设 | SIL Open Font License 1.1 | 否 |
| 微软雅黑 / 等线 / 宋体 / Consolas | 系统字体回退与备选 | 随 Windows 提供 | 否，系统自带 |

启动时程序会在界面上打印「字体：xxx」，作为 MiSans 许可协议中「在软件中注明使用了 MiSans 字体」的声明。
字体缺失时自动回退系统字体，功能不受影响。

## 开发 / 发布工具链

| 工具 | 用途 | 许可 | 是否随仓库分发 |
|---|---|---|---|
| [csc.exe](https://learn.microsoft.com/dotnet/framework/)（.NET Framework 编译器） | 编译 C# 源码 | 随 .NET Framework 提供 | 否，系统自带 |
| [Git for Windows](https://gitforwindows.org/) | 版本管理 | GPL-2.0 | 否 |
| [GitHub CLI](https://cli.github.com/) | 创建仓库与发布 Release | MIT | 否 |

## 拷贝的第三方源码

**液态玻璃相关的代码是从 demo 直接拷过来的，不是自己重写的。**

| 来源 | 许可 | 拷进本仓库的位置 |
|---|---|---|
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | Apache-2.0 | `android-compose/app/src/main/java/com/easypt/gui/glass/LiquidButton.kt`（几乎原样）、`Glass.kt` 的 `glassSurface` / `glassOrSolid`、`MainActivity.kt` 的 `GlassDialogPanel` / `glassInsetSurface`、`Wallpaper.kt` 的提饱和度取样 |

按 Apache-2.0 第 4 条，这里保留出处声明：原项目 Copyright Kyant0，完整许可文本见
<https://github.com/Kyant0/AndroidLiquidGlass/blob/main/LICENSE>。

### Material Color Utilities（莫奈取色）

**莫奈取色整条链路也换成了官方实现**，源码是按文件拷贝进仓库的，不是 Gradle 依赖：

| 来源 | 许可 | 拷进本仓库的位置 |
|---|---|---|
| [material-foundation/material-color-utilities](https://github.com/material-foundation/material-color-utilities) | Apache-2.0 | `android-compose/app/src/main/java/{blend,contrast,dislike,dynamiccolor,hct,palettes,quantize,scheme,score,temperature,utils}`（47 个 `.java` 文件） |

用法：`QuantizerCelebi` + `Score` 从壁纸取种子 → `Hct.fromInt` → `SchemeTonalSpot` →
`MaterialDynamicColors` 逐角色取 ARGB，封装在 `com/easypt/gui/Monet.kt`。

这个包依赖的两个编译期注解库（`com.google.errorprone:error_prone_annotations`、
`com.google.code.findbugs:jsr305`）用 `compileOnly` 引入，**不打进 APK**。

按 Apache-2.0：这些文件均保留了 Google 的原始版权头；出处与许可见仓库根目录 [NOTICE](NOTICE)。
如需替换成自己的实现，删掉 `glass/` 目录并把 `glassSurface` 换成纯色兜底即可，功能不受影响。

## 本项目

- 代码：MIT（见 [LICENSE](LICENSE)）
- 图标 `src/app.ico`：由 `src/make-icon.ps1` 用 GDI+ 现画，原创
