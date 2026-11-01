# AndroidTool v1.1.0

首个可用版本之后的功能更新：**首次启动自动安装 platform-tools**。

## 新增

- **自动下载依赖**：启动时检测不到 adb，就弹窗询问，点「立即下载」后自动下载并解压到 `bin\`，
  校验 zip 有效性、体积、包内是否真有 `adb.exe`，装完直接可用
- **多源自动切换**：腾讯云镜像 → Google 官方 → Google CDN，逐个尝试，全失败才报错
  （腾讯云镜像与官方文件 SHA256 一致，国内速度通常快一个数量级）
- **自定义源与代理**：`config.ini` 支持 `pt_source=` 指定镜像、`proxy=` 指定 HTTP 代理
- **TLS 1.2**：显式启用，避免 .NET Framework 默认协议在镜像站握手失败

## 下载

| 文件 | 说明 |
|---|---|
| `AndroidTool-v1.1.0.zip` | 解压即用，含 exe、启动 bat、说明 |
| `AndroidTool.exe` | 单文件本体 |

## 使用

1. 解压，双击 `AndroidTool.exe`
2. 首次启动点「立即下载」装 platform-tools（约 8 MB）
3. 字体可选：MiSans / JetBrains Mono 放进 `assets\fonts\`，不放会回退微软雅黑

## 命令行

```bat
AndroidTool.exe --version
AndroidTool.exe --selftest
AndroidTool.exe --run "adb devices -l"
```

## 许可

代码 MIT。platform-tools、MiSans、JetBrains Mono 遵循各自许可，**不随本仓库分发**，
详见 [THIRD-PARTY.md](https://github.com/moqi12386/android-debug-console/blob/main/THIRD-PARTY.md)。
