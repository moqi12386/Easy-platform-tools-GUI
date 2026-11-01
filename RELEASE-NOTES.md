# AndroidTool v1.0.0

Windows 上的 adb / fastboot 图形化命令台，首个公开版本。

## 包含什么

- **左侧快捷命令**：检测设备 / 版本信息 / 设备属性 / 重启到 Bootloader / 重启系统 / fastboot 设备 / fastboot 重启
- **命令控制台**：`adb shell` / `adb` / `fastboot` 三档模式，支持 ↑↓ 历史、清空、存日志
- **输出分色**：stdout 正文色、stderr 红字、结束打印退出码
- **主题系统**：8 个预设背景色 + 自定义取色，按背景亮度自动推导整套配色
- **字体**：内置 MiSans / MiSans Medium / JetBrains Mono 预设，可选系统字体，4 档字号，带实时预览
- **编码修复**：设备输出 UTF-8、adb 自身提示 GBK，逐行判定解码，中文路径不乱码
- **零依赖**：.NET Framework 4.x 自带 `csc.exe` 编译，44 KB 单文件

## 下载

| 文件 | 说明 |
|---|---|
| `AndroidTool-v1.0.0.zip` | 解压即用，含 exe、启动 bat、说明 |
| `AndroidTool.exe` | 单文件本体 |

## 使用前需要自备

1. **platform-tools**（必须）：把 `adb.exe`、`fastboot.exe`、`AdbWinApi.dll`、`AdbWinUsbApi.dll` 放进 `bin\`
2. **字体**（可选）：MiSans / JetBrains Mono 放进 `assets\fonts\`，不放会回退到微软雅黑

## 命令行

```bat
AndroidTool.exe --version
AndroidTool.exe --selftest
AndroidTool.exe --run "adb devices -l"
```

## 第三方组件

代码本身不依赖任何第三方二进制。platform-tools、MiSans、JetBrains Mono 分别遵循各自许可，
**不随本仓库分发**，详见 [THIRD-PARTY.md](https://github.com/moqi12386/android-debug-console/blob/main/THIRD-PARTY.md)。

## 许可

MIT
