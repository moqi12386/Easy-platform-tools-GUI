// Easy-platform-tools-GUI — 语言表
// 中文是源码里的原始文案，英文在这里按「精确匹配」和「正则模板」两种方式映射。

using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;
using System.Windows.Forms;

namespace EasyPlatformTools
{
    internal static class Lang
    {
        public const string ZH = "zh";
        public const string EN = "en";

        public static string Current = ZH;

        public static bool IsEn { get { return Current == EN; } }

        // ---------------- 精确匹配 ----------------
        private static readonly Dictionary<string, string> Exact = new Dictionary<string, string>(StringComparer.Ordinal)
        {
            // 侧栏
            { "快捷命令", "Quick actions" },
            { "刷写与备份", "Flash & backup" },
            { "检测设备", "Check device" },
            { "版本信息", "adb version" },
            { "设备属性", "Device model" },
            { "重启到 Bootloader", "Reboot → Bootloader" },
            { "重启到 Recovery", "Reboot → Recovery" },
            { "重启到 EDL", "Reboot → EDL" },
            { "重启系统", "Reboot system" },
            { "fastboot 设备", "fastboot devices" },
            { "fastboot 重启", "fastboot reboot" },
            { "读分区表…", "Partitions…" },
            { "刷入镜像…", "Flash image…" },
            { "线刷 ROM…", "Flash ROM…" },
            { "备份基带…", "Backup modem…" },
            { "⚙  设置", "⚙  Settings" },

            // 主界面
            { "正在检测设备…", "Detecting device…" },
            { "○ 未检测到设备（请检查数据线与 USB 调试）", "○ No device (check cable and USB debugging)" },
            { "x 找不到 adb / fastboot，请检查 bin 目录", "x adb / fastboot not found, check the bin folder" },
            { "↑↓ 翻历史　·　以 adb / fastboot 开头则按原样执行，否则套用左侧模式", "↑↓ history · commands starting with adb / fastboot run as-is, others use the mode on the left" },
            { "执行", "Run" },
            { "停止", "Stop" },
            { "清空", "Clear" },
            { "存日志", "Save log" },
            { "左下角「设置」可换背景色和字体。", "Use “Settings” at the bottom-left to change colors and font." },
            { "现在可以直接输入命令了。", "You can start typing commands now." },
            { "文本文件 (*.txt)|*.txt", "Text files (*.txt)|*.txt" },
            { "镜像文件 (*.img)|*.img|所有文件 (*.*)|*.*", "Image files (*.img)|*.img|All files (*.*)|*.*" },
            { "线刷包 (*.tgz;*.tar;*.gz;*.zip)|*.tgz;*.tar;*.gz;*.zip|所有文件 (*.*)|*.*", "ROM packages (*.tgz;*.tar;*.gz;*.zip)|*.tgz;*.tar;*.gz;*.zip|All files (*.*)|*.*" },

            // 设置
            { "设置", "Settings" },
            { "背景配色", "Background" },
            { "字体", "Font" },
            { "语言", "Language" },
            { "背景图", "Wallpaper" },
            { "选择图片…", "Choose image…" },
            { "移除", "Remove" },
            { "（无）", "(none)" },
            { "中文", "Chinese" },
            { "小 8.5pt", "Small 8.5pt" },
            { "标准 9.5pt", "Normal 9.5pt" },
            { "大 10.5pt", "Large 10.5pt" },
            { "特大 11.5pt", "XL 11.5pt" },
            { "自定义颜色…", "Custom color…" },
            { "示例　Easy-platform-tools-GUI AaBb 0123　adb devices -l", "Preview  Easy-platform-tools-GUI AaBb 0123  adb devices -l" },
            { "内置字体在 assets\\fonts\\，丢新的 .ttf 进去重启即可出现在列表里。", "Bundled fonts live in assets\\fonts\\. Drop a new .ttf there and restart." },
            { "关闭", "Close" },

            // 首次运行
            { "初始化 — 需要 Android platform-tools", "Setup — Android platform-tools required" },
            { "缺少 adb / fastboot", "adb / fastboot missing" },
            { "本程序依赖 Android platform-tools 才能和设备通信。\n点「立即下载」会拉取约 8 MB 的压缩包并自动解压到 bin\\ 目录。\n下载源先在腾讯云镜像和 Google 官方之间自动切换，失败会自动换下一个。\n\n内容归 Google 所有，遵循 Android SDK 许可协议。",
              "This tool needs Android platform-tools to talk to your device.\n“Download” fetches an ~8 MB package and unpacks it into bin\\.\nMirrors are tried in order (Tencent Cloud → Google → Google CDN).\n\nContent belongs to Google and is governed by the Android SDK license." },
            { "立即下载", "Download" },
            { "手动下载", "Manual download" },
            { "退出", "Quit" },
            { "重试", "Retry" },
            { "重新检测", "Re-check" },
            { "下载失败", "Download failed" },
            { "正在连接 Google…", "Connecting to Google…" },

            // 刷入镜像
            { "刷入镜像", "Flash image" },
            { "分区", "Partition" },
            { "镜像", "Image" },
            { "浏览…", "Browse…" },
            { "刷完后重启到系统", "Reboot to system after flashing" },
            { "刷入", "Flash" },
            { "提示：分区写错会导致无法开机。线刷包请用「线刷 ROM…」，不用逐个刷。", "Tip: a wrong partition can brick the device. For full ROMs use “Flash ROM…”." },
            { "请填写分区名", "Enter a partition name" },
            { "找不到镜像文件", "Image file not found" },
            { "正在刷入…", "Flashing…" },
            { "完成", "Done" },
            { "失败", "Failed" },
            { "确认刷入", "Confirm flash" },

            // 线刷 ROM
            { "线刷 ROM", "Flash ROM" },
            { "刷机包", "ROM package" },
            { "选择…", "Choose…" },
            { "选目录", "Folder" },
            { "选一个线刷包（.tgz / .zip）或已解压的目录", "Pick a ROM package (.tgz / .zip) or an unpacked folder" },
            { "正在解压…", "Extracting…" },
            { "擦除 userdata（清空全部数据）", "Erase userdata (wipes all data)" },
            { "刷完自动重启", "Reboot when done" },
            { "开始线刷", "Start flashing" },
            { "确认线刷", "Confirm flashing" },
            { "选择已解压的线刷包目录（含 images 子目录）", "Select the unpacked ROM folder (with an images subfolder)" },
            { "没有可执行的刷写操作，请先选择题机包", "Nothing to flash, pick a ROM package first" },

            // 备份基带
            { "备份基带", "Backup modem" },
            { "输出目录", "Output folder" },
            { "备份内容", "What to back up" },
            { "开始备份", "Start backup" },
            { "打开目录", "Open folder" },
            { "确认备份", "Confirm backup" },
            { "备份中…", "Backing up…" },
            { "请选择输出目录", "Choose an output folder" },
            { "至少选一个分区", "Select at least one partition" },
            { "需要手机已 root（adb shell su 可用），备份过程请勿拔线。", "Requires root (adb shell su). Do not unplug during backup." },
            { "没有 root 权限，无法读取分区（需要 Magisk / 内核 root）", "No root access, cannot read partitions (needs Magisk / kernel root)" },
            { "root 可用", "root available" },
            { "选择备份保存目录", "Choose a backup folder" },
            { "基带固件（主体）", "modem firmware (main)" },
            { "EFS 分区 1（NV）", "EFS partition 1 (NV)" },
            { "EFS 分区 2（NV）", "EFS partition 2 (NV)" },
            { "FSG（调制解调器配置）", "FSG (modem config)" },
            { "FSC（网络锁计数）", "FSC (network lock count)" },
            { "persist（传感器/校准）", "persist (sensors / calibration)" },
            { "蓝牙固件", "bluetooth firmware" },
            { "DSP 固件", "DSP firmware" },

            // 分区表
            { "分区表", "Partitions" },
            { "读取 / 刷新", "Read / refresh" },
            { "用于刷入镜像", "Use for flashing" },
            { "复制列表", "Copy list" },
            { "导出 CSV", "Export CSV" },
            { "大小", "Size" },
            { "类型", "Type" },
            { "原始值", "Raw value" },
            { "正在连接…", "Connecting…" },
            { "正在读取 fastboot getvar all …", "Reading fastboot getvar all …" },
            { "没有检测到 fastboot 设备，请先进入 Bootloader", "No fastboot device, enter Bootloader first" },
            { "getvar all 里没有解析到分区信息", "No partitions found in getvar all" },

            // 下载相关
            { "正在连接 ", "Connecting to " },
            { "所有镜像源都失败了", "All mirrors failed" },
            { "不是有效的 zip（可能被网络劫持或返回了网页）", "Not a valid zip (hijacked or an HTML page was returned)" },
            { "压缩包里没有 adb.exe", "adb.exe not found in the package" },
            { "压缩包里没有找到 adb.exe", "adb.exe not found in the package" },
            { "压缩包里缺 fastboot.exe", "fastboot.exe missing in the package" },
            { "解压中…", "Extracting…" },
            { "系统没有 tar.exe，无法解压 .tgz，请先手动解压", "tar.exe is missing, unpack the .tgz manually first" },
            { "需要走代理的话，在 config.ini 里加一行：proxy=http://127.0.0.1:7897", "If you need a proxy, add to config.ini: proxy=http://127.0.0.1:7897" },
            { "内容归 Google 所有，遵循 Android SDK 许可协议。", "Content belongs to Google, under the Android SDK license." },
            { "AdbWinApi.dll、AdbWinUsbApi.dll 放进：\n", "AdbWinApi.dll, AdbWinUsbApi.dll into:\n" },
            { "\n然后点左边的「重新检测」。", "\nthen click “Re-check” on the left." },
            { "已打开官方下载页。解压后把 adb.exe、fastboot.exe、\n", "Official download page opened. After unpacking, put adb.exe, fastboot.exe,\n" },
            { "未下载", "not downloaded" },
            { "腾讯云", "Tencent Cloud" },
            { "自定义", "Custom" },
            { "adb 通道不可用，改用 fastboot oem edl", "adb channel unavailable, trying fastboot oem edl" },
            { "已请求进入 EDL：手机可能黑屏无反应，设备管理器里应出现 Qualcomm HS-USB QDLoader 9008", "EDL requested: the screen may stay black; look for Qualcomm HS-USB QDLoader 9008 in Device Manager" },
            { "进入 EDL 失败：多数机型需要已解锁 Bootloader，或厂商授权账号", "Failed to enter EDL: most devices need an unlocked Bootloader or a vendor-authorized account" },
            { "> 重启到 EDL（9008 深刷模式）", "> Rebooting to EDL (9008)" },
            { "> 检查 root 权限…", "> Checking root…" },
            { "（已发送终止）", "(terminated)" },
            { "（已有命令在运行，先停止）", "(a command is running, stop it first)" },
            { "按 images 目录推断", "inferred from images/" },
            { "备份说明.txt", "README.txt" }
        };

        // ---------------- 正则模板（带变量） ----------------
        private sealed class Rule
        {
            public Regex Pattern;
            public string Replacement;
        }

        private static readonly Rule[] Rules = new Rule[]
        {
            R(@"^── 退出码 (-?\d+) ──$", "── exit code $1 ──"),
            R(@"^● Android 已连接　(\S+)$", "● Android connected  $1"),
            R(@"^● Fastboot 已连接　(\S+)$", "● Fastboot connected  $1"),
            R(@"^组件目录：(.*)$", "Tools folder: $1"),
            R(@"^组件目录=(.*)$", "tools=$1"),
            R(@"^字体：(.*)$", "Font: $1"),
            R(@"^字体：(\S+) (\S+)　·　主题：(\S+)$", "Font: $1 $2  ·  theme: $3"),
            R(@"^Easy-platform-tools-GUI v(\S+)　·　adb / fastboot 命令控制台$", "Easy-platform-tools-GUI v$1  ·  adb / fastboot console"),
            R(@"^> (.*)$", "> $1"),
            R(@"^已保存：(.*)$", "Saved: $1"),
            R(@"^已导出：(.*)$", "Exported: $1"),
            R(@"^字体已切换：(.*)$", "Font switched to: $1"),
            R(@"^读取失败：(.*)$", "Read failed: $1"),
            R(@"^解析失败：(.*)$", "Parse failed: $1"),
            R(@"^解压失败：(.*)$", "Extract failed: $1"),
            R(@"^导出失败：(.*)$", "Export failed: $1"),
            R(@"^启动失败：(.*)$", "Failed to start: $1"),
            R(@"^无法创建目录：(.*)$", "Cannot create folder: $1"),
            R(@"^无法解析命令$", "Cannot parse command"),
            R(@"^调用：(.*)$", "Invoke: $1"),
            R(@"^版 本：(.*)$", "Version: $1"),
            R(@"^背景：(.*)$", "Background: $1"),
            R(@"^正在读取 (\S+) …$", "Reading $1 …"),
            R(@"^正在连接 (.*) …$", "Connecting to $1 …"),
            R(@"^正在执行 (\d+)/(\d+)：(.*)$", "Running $1/$2: $3"),
            R(@"^完成 (\d+)/(\d+) 个分区$", "Done $1/$2 partitions"),
            R(@"^基带备份完成：(\d+)/(\d+) 个分区 → (.*)$", "Modem backup done: $1/$2 partitions → $3"),
            R(@"^线刷完成：(\d+)/(\d+) 个操作$", "Flashing done: $1/$2 steps"),
            R(@"^线刷中断，已完成 (\d+) 个，失败：(.*)$", "Flashing aborted after $1 steps, failed: $2"),
            R(@"^已复制 (\d+) 行到剪贴板$", "Copied $1 rows to clipboard"),
            R(@"^共 (\d+) 个分区（fastboot getvar all）$", "$1 partitions (fastboot getvar all)"),
            R(@"^刷入完成：(\S+)$", "Flashed: $1"),
            R(@"^刷入失败：(\S+)$", "Flash failed: $1"),
            R(@"^失败 (\S+)(:.*)?$", "Failed $1$2"),
            R(@"^OK  (\S+)\s+(\S+) MB\s+(\S+)$", "OK  $1  $2 MB  $3"),
            R(@"^下载失败：(.*)$", "Download failed: $1"),
            R(@"^platform-tools 已就绪（下载源：(.*)）：(.*)$", "platform-tools ready (from $1): $2"),
            R(@"^已找到 adb：(.*)$", "adb found: $1"),
            R(@"^> 开始备份基带，共 (\d+) 个分区$", "> Backing up $1 partitions"),
            R(@"^> 线刷开始，共 (\d+) 个操作$", "> Flashing $1 steps"),
            R(@"^> (.*)$", "> $1"),
            R(@"^文件大小异常（(.*)）$", "Unexpected file size ($1)"),
            R(@"^正在下载.*$", "Downloading…"),
            R(@"^下载中 (.*)$", "Downloading $1"),
            R(@"^\s*$", ""),
            R(@"^(\d+) 个操作$", "$1 steps")
        };

        private static Rule R(string pattern, string replacement)
        {
            Rule r = new Rule();
            r.Pattern = new Regex(pattern, RegexOptions.Compiled);
            r.Replacement = replacement;
            return r;
        }

        public static string Tr(string zh)
        {
            if (!IsEn || string.IsNullOrEmpty(zh)) { return zh; }
            string hit;
            if (Exact.TryGetValue(zh, out hit)) { return hit; }
            foreach (Rule r in Rules)
            {
                if (r.Pattern.IsMatch(zh)) { return r.Pattern.Replace(zh, r.Replacement); }
            }
            return zh;
        }

        // 把已构建好的控件文案换掉（只换精确匹配项，未收录的保持原样）
        public static void ApplyTo(Control root)
        {
            if (!IsEn || root == null) { return; }
            ApplyOne(root);
            foreach (Control c in root.Controls) { ApplyTo(c); }
        }

        private static void ApplyOne(Control c)
        {
            if (c is ComboBox)
            {
                ComboBox cb = (ComboBox)c;
                for (int i = 0; i < cb.Items.Count; i++)
                {
                    string s = cb.Items[i] as string;
                    if (s == null) { continue; }
                    string en;
                    if (Exact.TryGetValue(s, out en)) { cb.Items[i] = en; }
                }
            }
            if (c is ListView)
            {
                foreach (ColumnHeader h in ((ListView)c).Columns)
                {
                    string en;
                    if (Exact.TryGetValue(h.Text, out en)) { h.Text = en; }
                }
            }
            string t = c.Text;
            if (string.IsNullOrEmpty(t)) { return; }
            string exact;
            if (Exact.TryGetValue(t, out exact)) { c.Text = exact; return; }
        }
    }
}
