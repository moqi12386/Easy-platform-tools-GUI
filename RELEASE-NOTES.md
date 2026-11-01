# AndroidTool v1.3.0 — 刷机功能补齐

从「命令台」升级成能干活的小工具：镜像刷写、线刷包、基带备份全部内置。

## 新增

### 刷入镜像
- 任选分区（内置 40 个常用分区名，也可手填）+ 任意 `.img`
- 二次确认对话框列出分区、文件名、大小
- 可选刷完自动重启

### 线刷 ROM（小米 fastboot 包）
- 直接选 `.tgz` / `.zip`，或已解压目录，自动用系统 `tar.exe` 解压
- **按 `flash_all.bat` 里的原始顺序**解析 `fastboot flash` / `erase` / `set_active` / `reboot`
- 执行前列出全部操作供核对；没有脚本时退化为按 `images\*.img` 同名分区刷
- 可勾选擦除 userdata、刷完重启
- 逐条执行并实时显示 `正在执行 5/42：flash xbl`，失败即停并报出具体命令

### 备份基带
- 默认备份 modem / modemst1 / modemst2 / fsg / fsc，另可勾 persist / bluetooth / dsp
- 走 `adb exec-out su -c "dd ..."`，不落设备临时文件，二进制安全
- 每个分区导出为 `.img`，附 SHA256 与设备型号/序列号，写入 `备份说明.txt`
- 需要 root，没 root 会直接提示而不是静默失败

### 重启到 Recovery / EDL
- Recovery：`adb reboot recovery`
- EDL：先 `adb reboot edl`，失败自动改试 `fastboot oem edl`，并说明 9008 端口特征

## 下载

| 文件 | 说明 |
|---|---|
| `AndroidTool-v1.3.0.zip` | 解压即用 |
| `AndroidTool.exe` | 单文件本体，74 KB |

## 安全说明

刷写类操作全部带二次确认，且要先检测到 fastboot 设备才允许开始。
线刷过程中断电/拔线有变砖风险，README 里有说明。

备份文件含设备身份数据（NV/IMEI），只用于恢复本机，不要外传。

## 许可

代码 MIT。platform-tools、MiSans、JetBrains Mono 遵循各自许可，不随本仓库分发。
