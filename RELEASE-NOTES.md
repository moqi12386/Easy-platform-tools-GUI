# Easy-platform-tools-GUI v1.8.0 — Android 版功能对齐桌面版

这次把 Android App 从「能刷」补到「和电脑版几乎一样」。

## Android 版新增

| 功能 | 说明 |
|---|---|
| **线刷 ROM** | 选 `.zip` / `.tgz` / 已解压目录，解析 `flash_all.bat` 按原顺序执行；没有脚本就按 `images/*.img` 推断 |
| **流式刷写** | 大镜像（如 `super.img`）不再整体读进内存，按 64 KB 分块走 `download:` 协议 |
| **分区表增强** | 名称 / 大小自动换算（MB/GB）/ 类型，支持复制到剪贴板和导出 CSV |
| **备份基带多选** | 8 个分区可勾选（modem / modemst1 / modemst2 / fsg / fsc / persist / bluetooth / dsp），逐个算 SHA256 |
| **主题配色** | 8 套预设背景，深浅两套色板自动推导文字/按钮/输入框颜色 |
| **字号** | 小 / 标准 / 大 三档，全界面生效 |
| **中英文** | 设置里切换，界面文案全部双语 |
| **日志分色 + 存日志** | 命令蓝、成功绿、失败红；一键存到 `Android/data/com.easypt.gui/files/logs/` |
| **命令历史** | 最近 50 条，点一下回填 |

## 桌面版

- 版本号对齐 1.8.0，功能不变（遥控服务、线刷、备份、读分区表等在前面几个版本已就位）

## 功能对照

| 功能 | 桌面版 | Android 版 |
|---|---|---|
| 读分区表 / 复制 / CSV | ✅ | ✅ |
| 刷入镜像 | ✅ | ✅ |
| 线刷 ROM | ✅ | ✅ |
| 备份基带 | ✅ | ✅ |
| 重启 Recovery / BL / EDL | ✅ | ✅ |
| 控制台 + 历史 | ✅ | ✅ |
| 日志分色 + 保存 | ✅ | ✅ |
| 配色 / 字号 / 双语 | ✅ | ✅ |

## 下载

| 文件 | 说明 |
|---|---|
| `EasyPT.apk` | Android 1.8.0，40 KB，minSdk 26 |
| `Easy-platform-tools-GUI-v1.8.0.zip` | Windows 桌面版 |
| `Easy-platform-tools-GUI.exe` | 桌面版单文件，107 KB |

## 说明

Android 版仍未在真机验证（开发机没有可测试的设备与 OTG 环境）。
协议实现、打包、签名均已静态校验，首次使用建议先跑「读分区表」确认通信正常。

## 许可

代码 MIT。platform-tools、MiSans、JetBrains Mono 遵循各自许可，不随本仓库分发。
