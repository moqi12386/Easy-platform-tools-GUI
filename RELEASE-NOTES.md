# Easy-platform-tools-GUI v1.7.0 — 增加 Android 版

桌面版之外，现在有同名的 Android App：`EasyPT.apk`。三种模式在 App 内自由切换。

## Android App

| 模式 | 干什么 | 前提 |
|---|---|---|
| **OTG 主机** | 手机 A 用 OTG 线刷手机 B：读分区表、刷镜像、擦除、重启 | 目标机进 fastboot |
| **本机 root** | 不用电脑，直接备份基带、刷自己分区、进 Recovery / EDL | 本机需 root |
| **遥控 PC** | 手机当遥控器，指挥电脑上的桌面版 | 同一 Wi-Fi，电脑开服务 |

- **OTG 模式是自己实现的 fastboot 协议**：USB bulk 传输 + 64 字节命令包（`FASTBOOT` + 命令），
  处理 `OKAY` / `FAIL` / `INFO` / `DATA` 响应，下载镜像走 `download:` 分包传输。不依赖 adb，不需要 root。
- **本机模式**用 `su` + `dd` 直接读写 `/dev/block/by-name/*`。
- **遥控模式**走 HTTP，配对码 6 位，靠 `X-Token` 头校验。
- 界面与桌面版同一套配色，全部用系统控件，无第三方依赖，APK 仅 28 KB。

## 桌面版新增

- 侧栏新增「遥控服务…」：启动一个内置 HTTP 服务（TcpListener 手写，非管理员也能跑），
  显示本机 IP 和 6 位配对码，手机 App 填进去即可用
- 接口：`/api/status`、`/api/run`、`/api/partitions`、`/api/flash`、`/api/reboot`，
  全部要求 `X-Token` 匹配
- 配对码存进 `config.ini` 的 `token=`，可一键更换

## 下载

| 文件 | 说明 |
|---|---|
| `EasyPT.apk` | Android 版，28 KB，minSdk 26 |
| `Easy-platform-tools-GUI-v1.7.0.zip` | Windows 桌面版，解压即用 |
| `Easy-platform-tools-GUI.exe` | 桌面版单文件，107 KB |

## 自己编译 APK

```bat
powershell -ExecutionPolicy Bypass -File android\build-apk.ps1
```

不依赖 Gradle：`aapt2` 链接清单 → `javac` 编译 → `d8` 转 dex → `zipalign` → `apksigner` 签名。

## 说明

Android 版**没有在真机上跑过**（开发机没连手机，也没有 OTG 测试条件），
OTG 协议和 root 路径是按规范实现并做了静态校验的，第一次用时请注意：
先用「读分区表」确认能正常通信，再尝试写入操作。

## 许可

代码 MIT。platform-tools、MiSans、JetBrains Mono 遵循各自许可，不随本仓库分发。
