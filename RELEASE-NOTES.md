# Easy-platform-tools-GUI v1.5.0 — 换名字

项目从 `android-debug-console` 改名为 **Easy-platform-tools-GUI**。

旧仓库地址 <https://github.com/moqi12386/android-debug-console> 会被 GitHub 自动重定向，
老链接仍然可用，但建议更新到新地址。

## 变化

| 项目 | 旧 | 新 |
|---|---|---|
| 仓库 | `android-debug-console` | `Easy-platform-tools-GUI` |
| 程序 | `AndroidTool.exe` | `Easy-platform-tools-GUI.exe` |
| 启动器 | `AndroidTool.bat` | `Easy-platform-tools-GUI.bat` |
| 窗口标题 | 安卓调试台 — adb / fastboot | Easy-platform-tools-GUI — adb / fastboot |
| 源码 | `src/AndroidTool.cs` | `src/EasyPlatformTools.cs` |

功能没有变化，配置 `config.ini` 也继续沿用（`bg` / `font` / `size` / `proxy` / `pt_source`）。

## 下载

| 文件 | 说明 |
|---|---|
| `Easy-platform-tools-GUI-v1.5.0.zip` | 解压即用 |
| `Easy-platform-tools-GUI.exe` | 单文件本体，81 KB |

## 功能一览

| 分类 | 功能 |
|---|---|
| 常用 | 检测设备、版本信息、设备属性、重启到 Bootloader / Recovery / EDL、重启系统 |
| 刷写 | 读分区表、刷入镜像、线刷 ROM（小米 fastboot 包） |
| 备份 | 备份基带（modem / EFS），附 SHA256 |
| 控制台 | adb shell / adb / fastboot 三档模式，历史记录，输出分色，存日志 |
| 外观 | 8 套配色 + 自定义取色，MiSans / JetBrains Mono 字体，4 档字号 |

## 许可

代码 MIT。platform-tools、MiSans、JetBrains Mono 遵循各自许可，不随本仓库分发。
