# 第三方组件与许可

本仓库**不包含**任何第三方二进制文件。下表列出本项目在运行、构建、发布过程中使用到的
开源 / 免费组件，以及各自的许可与获取方式。

## 运行时

| 组件 | 用途 | 许可 | 是否随仓库分发 |
|---|---|---|---|
| [.NET Framework 4.x](https://dotnet.microsoft.com/download/dotnet-framework)（WinForms / System.Drawing） | 界面与进程控制 | Microsoft .NET Framework 许可（随 Windows 提供） | 否，系统自带 |

## 可选：与设备通信

| 组件 | 用途 | 许可 | 是否随仓库分发 |
|---|---|---|---|
| [Android platform-tools](https://developer.android.com/tools/releases/platform-tools)（adb / fastboot） | 与 Android 设备通信 | Android SDK 许可协议 / Apache-2.0 | 否，需自行下载 |

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

## 本项目

- 代码：MIT（见 [LICENSE](LICENSE)）
- 图标 `src/app.ico`：由 `src/make-icon.ps1` 用 GDI+ 现画，原创
