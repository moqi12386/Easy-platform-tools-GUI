# 安卓调试台 · AndroidTool

Windows 上的 adb / fastboot 图形化命令台。深色界面、MiSans 字体、可换背景配色，
编译出来就是一个 74 KB 的单文件 exe，不需要装 PowerShell 模块、不需要运行库。

![主界面](docs/main.png)

## 特性

- **左侧快捷命令**：检测设备、版本信息、设备属性、重启到 Bootloader / Recovery / EDL、重启系统、fastboot 设备、fastboot 重启
- **刷入镜像**：任选分区 + 任意 `.img`，二次确认后刷入，可选刷完重启
- **线刷 ROM**：直接吃小米 fastboot 线刷包（`.tgz` / `.zip` / 已解压目录），自动解析 `flash_all.bat` 的刷写顺序
- **备份基带**：把 modem / modemst1 / modemst2 / fsg / fsc 等分区导出成本地镜像，附 SHA256 与设备信息
- **首次启动自动装依赖**：检测到没有 adb 时，自动从镜像/官方源下载 platform-tools 并解压到 `bin\`
- **命令控制台**：底部按模式输入（`adb shell` / `adb` / `fastboot`），以 adb / fastboot 开头的命令按原样执行，↑↓ 翻历史
- **输出分色**：stdout 正文色、stderr 红色、结束时打印退出码（0 绿 / 非 0 红），可清空、可存日志
- **主题**：8 个预设背景色 + 自定义取色器，按背景亮度自动推导整套配色，浅色底不会出现浅色字
- **字体**：内置 MiSans / MiSans Medium / JetBrains Mono，另可选择系统里的微软雅黑、等线、宋体、黑体、Consolas，字号 4 档
- **编码处理**：设备返回的是 UTF-8，adb 自身的提示是 ANSI(GBK)，逐行判定解码，中文路径不乱码
- **零依赖**：`csc.exe`（.NET Framework 4.x 自带）直接编译，无第三方库、无 NuGet、无运行时安装

## 快速开始

1. 到 [Releases](../../releases) 下载 `AndroidTool-v1.1.0.zip` 并解压
2. 双击 `AndroidTool.exe`
3. 首次启动会提示缺少 **Android platform-tools**，点「立即下载」即可，程序自动下载并解压到 `bin\`

![首次启动](docs/setup.png)

不想让程序联网就点「手动下载」跳转官方页面，把 `adb.exe`、`fastboot.exe`、
`AdbWinApi.dll`、`AdbWinUsbApi.dll` 放进 `bin\`，再点「重新检测」。

组件查找顺序：`exe 同级 bin\` → `上级 bin\` → `exe 所在目录` → `C:\platform-tools` → `PATH`。
整个文件夹可以整体挪走，只要 `bin\` 跟着走。

### 下载源

程序不打包 platform-tools（分发受 Android SDK 许可限制），改为首次运行时按顺序尝试：

| 顺序 | 源 | 说明 |
|---|---|---|
| 1 | 腾讯云镜像 `mirrors.cloud.tencent.com/AndroidSDK` | 与官方文件 SHA256 一致，国内通常最快 |
| 2 | Google 官方 `dl.google.com/android/repository` | 原始来源 |
| 3 | Google CDN `redirector.gvt1.com` | 官方备用线路 |

任一源失败自动换下一个。下载后会校验：必须是有效 zip、体积合理、压缩包里确实有 `adb.exe`。
想指定自己的镜像或走代理，在 `config.ini` 里加：

```ini
pt_source=https://your-mirror/platform-tools-latest-windows.zip
proxy=http://127.0.0.1:7897
```

## 刷写与备份

左侧「刷写与备份」一栏有三件工具，都是**破坏性操作，执行前会二次确认**。

### 刷入镜像

任选分区（内置 boot / recovery / dtbo / vbmeta / super / modem 等 40 个常用分区名，也可手填），
挑一个 `.img` 刷入，可选刷完自动重启。

![刷入镜像](docs/flashimg.png)

### 线刷 ROM（小米 fastboot 包）

直接选 `.tgz` / `.zip` 线刷包，或已解压的目录。程序会：

1. 自动解压（用系统自带 `tar.exe`）
2. 找到 `flash_all.bat`，**按它里面的顺序**解析出 `fastboot flash` / `erase` / `set_active` / `reboot`
3. 列出全部操作供核对，再逐条执行

没有 `flash_all.bat` 时退化为「把 `images\` 下每个 `.img` 刷到同名分区」。
可勾选是否擦除 userdata、刷完是否重启。

![线刷 ROM](docs/flashrom.png)

### 备份基带

勾选要备份的分区（默认 modem / modemst1 / modemst2 / fsg / fsc），导出到
`backups\基带备份-时间戳\`，同时写一份 `备份说明.txt`，含设备型号、序列号、每个文件的大小与 SHA256。

需要手机已 root（`adb shell su` 可用）。读取走
`adb exec-out su -c "dd if=/dev/block/by-name/xxx bs=1M"`，不落临时文件，二进制安全。

![备份基带](docs/backup.png)

> 备份里含设备 NV / IMEI 等身份数据，能用来恢复**本机**，但不要外传。

## 关于 EDL

「重启到 EDL」先试 `adb reboot edl`，失败再试 `fastboot oem edl`。进入后手机黑屏无反应，
设备管理器里出现 `Qualcomm HS-USB QDLoader 9008` 即为成功。
多数机型进 EDL 需要已解锁 Bootloader 或厂商授权账号，失败时会如实报出来。

## 字体

仓库**不包含**字体文件（授权原因）。想要截图里的效果，把字体放进 `assets\fonts\` 即可，
程序启动时用 `PrivateFontCollection` 私有加载，不会装进系统字体目录：

| 字体 | 用途 | 获取 |
|---|---|---|
| MiSans | 默认界面字体 | <https://hyperos.mi.com/font/zh/download/> |
| JetBrains Mono | 等宽西文，控制台对齐用 | <https://www.jetbrains.com/lp/mono/> |

一个字体都没放时，会自动回退到「微软雅黑 / 等线 / 宋体」中存在的第一个，不会掉到西文系统字体上。

## 主题与设置

点左下角「⚙ 设置」：

- **背景配色**：8 个预设（墨黑 / 纯黑 / 石墨 / 深海 / 墨绿 / 酒红 / 灰白 / 米黄）+ 自定义取色
- **字体**：字体下拉 + 字号下拉，下方有实时预览

![设置](docs/settings.png)

选择结果写入程序目录下的 `config.ini`：

```ini
bg=#0E1117
font=MiSans
size=9.5
```

换成等宽字体后，控制台输出是这样：

![等宽](docs/mono.png)

## 构建

```bat
powershell -ExecutionPolicy Bypass -File src\build.ps1
```

- 只用 .NET Framework 4.x 自带的 `C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe`
- 产物是同目录的 `AndroidTool.exe`
- 图标由 `src\make-icon.ps1` 生成 `src\app.ico`
- `src\shot.ps1` 是开发期截图脚本（启动 exe → 输入命令 → PrintWindow 存 PNG）

## 命令行参数

```bat
AndroidTool.exe --version                  :: 版本与仓库地址
AndroidTool.exe --selftest                 :: 打印组件解析、字体、主题、控件数
AndroidTool.exe --run "adb devices -l"     :: 不开窗口跑一条命令并输出
```

`--selftest` / `--run` 可加 `--out <文件>` 把结果写进文件（在无控制台的宿主里很有用）。

## 附带脚本：HakiOS 一键刷入

`scripts\hackios-boot.ps1` 是把 XNU 内核 + iOS 用户态纯内存启动到 raphael 的刷入脚本，
配套 `一键启动HakiOS.bat` 使用。**仓库不包含镜像文件**（UEFI 与 APFS 镜像各 2 GB 量级），
需要自备后用参数指定：

```bat
powershell -ExecutionPolicy Bypass -File scripts\hackios-boot.ps1 -Uefi <uefi.img> -Apfs <system.apfs> -Seconds 600
```

注意：该脚本依赖 WSL 与 `C:\hakios\hakios-run.sh` 辅助脚本，且与具体机型绑定，不是通用工具。

## 第三方组件

代码本身只用到 .NET Framework，不含任何第三方二进制。platform-tools 由用户在首次启动时
从 Google 官方（或与官方同哈希的镜像）下载到本机，本仓库不包含、不分发。详见 [THIRD-PARTY.md](THIRD-PARTY.md)。

## 许可

本项目代码采用 [MIT 许可](LICENSE)。
随项目使用的字体与 Android platform-tools 遵循各自的许可，不随本仓库分发。

## 免责

仅用于调试**你自己拥有**的设备。刷机有风险，操作前请确认能接受变砖的可能。
