# AndroidTool v1.4.0 — 读分区表

给刷写加一层保险：刷之前先把这台设备**真实的分区**拉出来看。

## 新增：读分区表

- 跑 `fastboot getvar all`，解析 `partition-size` / `partition-type`
- 列表展示：分区名、大小（自动换算 MB/GB）、类型、原始十六进制值
- **复制列表**到剪贴板、**导出 CSV**
- 选中某行点「用于刷入镜像」，直接把分区名带进刷写对话框——不用手打，也就不会打错

![分区表](https://github.com/moqi12386/android-debug-console/raw/main/docs/parts.png)

## 连带改进

- 读过分区表后，「刷入镜像」的分区下拉框会用**真实分区名**替代内置的 40 个通用名
- 没读过也能用，只是候选来自内置列表

## 下载

| 文件 | 说明 |
|---|---|
| `AndroidTool-v1.4.0.zip` | 解压即用 |
| `AndroidTool.exe` | 单文件本体，81 KB |

## 完整功能一览

| 分类 | 功能 |
|---|---|
| 常用 | 检测设备、版本信息、设备属性、重启到 Bootloader / Recovery / EDL、重启系统 |
| 刷写 | 读分区表、刷入镜像、线刷 ROM（小米 fastboot 包） |
| 备份 | 备份基带（modem / EFS），附 SHA256 |
| 控制台 | adb shell / adb / fastboot 三档模式，历史记录，输出分色，存日志 |
| 外观 | 8 套配色 + 自定义取色，MiSans / JetBrains Mono 字体，4 档字号 |

## 许可

代码 MIT。platform-tools、MiSans、JetBrains Mono 遵循各自许可，不随本仓库分发。
