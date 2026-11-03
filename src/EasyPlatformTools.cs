// Easy-platform-tools-GUI — adb / fastboot 图形化命令台
// 编译：powershell -ExecutionPolicy Bypass -File build.ps1
// 依赖：.NET Framework 4.x（Windows 自带），无需 PowerShell 运行时。
// 字体：assets\fonts\*.ttf（MiSans、JetBrains Mono），缺失时回退系统字体。

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Text;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace EasyPlatformTools
{
    internal static class Program
    {
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool AttachConsole(int dwProcessId);

        private const int ATTACH_PARENT_PROCESS = -1;

        private const string AppVersion = "2.1.0";
        private const string AppRepo = "https://github.com/moqi12386/Easy-platform-tools-GUI";

        // 首次运行时自动下载的镜像源：按顺序尝试，全部为官方或与官方逐字节一致的镜像
        private static readonly string[][] PT_SOURCES = new string[][]
        {
            new string[] { "腾讯云",   "https://mirrors.cloud.tencent.com/AndroidSDK/platform-tools-latest-windows.zip" },
            new string[] { "Google",   "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" },
            new string[] { "Google CDN", "https://redirector.gvt1.com/edgedl/android/repository/platform-tools-latest-windows.zip" }
        };
        private const string PT_PAGE = "https://developer.android.com/tools/releases/platform-tools";

        // ================================================================
        //  主题
        // ================================================================
        /// <summary>按窗体坐标贴同一张壁纸的面板，解决 WinForms 不支持真透明的问题</summary>
        /// <summary>支持半透明底的平面按钮</summary>
        private sealed class AlphaButton : Button
        {
            public AlphaButton()
            {
                SetStyle(ControlStyles.SupportsTransparentBackColor, true);
                FlatStyle = FlatStyle.Flat;
                FlatAppearance.BorderSize = 1;
            }
        }

        private sealed class WallPanel : Panel
        {
            public static Bitmap Wall;
            public static Color Veil = Color.FromArgb(160, 10, 12, 16);

            public WallPanel()
            {
                SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint, true);
            }

            protected override void OnPaintBackground(PaintEventArgs e)
            {
                if (Wall == null) { base.OnPaintBackground(e); return; }
                Form f = FindForm();
                if (f == null) { base.OnPaintBackground(e); return; }
                Point p = PointToScreen(Point.Empty);
                Point inForm = f.PointToClient(p);
                using (SolidBrush baseBrush = new SolidBrush(BackColor)) { e.Graphics.FillRectangle(baseBrush, ClientRectangle); }
                try { e.Graphics.DrawImageUnscaled(Wall, -inForm.X, -inForm.Y); } catch { }
                using (SolidBrush veilBrush = new SolidBrush(Veil)) { e.Graphics.FillRectangle(veilBrush, ClientRectangle); }
            }
        }

        private sealed class Theme
        {
            public Color Bg, Panel, Output, EditBg, Text, Dim, Accent, Ok, Err, Warn, Btn, Border, RunBtn, StopBtn;
            public bool Dark;
        }

        private static Color Mix(Color a, Color b, double f)
        {
            return Color.FromArgb(
                Clamp((int)Math.Round(a.R + (b.R - a.R) * f)),
                Clamp((int)Math.Round(a.G + (b.G - a.G) * f)),
                Clamp((int)Math.Round(a.B + (b.B - a.B) * f)));
        }

        private static int Clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

        private static Color Hex(string s)
        {
            s = s.Trim().TrimStart('#');
            int v = int.Parse(s, System.Globalization.NumberStyles.HexNumber);
            return Color.FromArgb((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF);
        }

        private static string ToHex(Color c)
        {
            return "#" + c.R.ToString("X2") + c.G.ToString("X2") + c.B.ToString("X2");
        }

        private static Theme BuildTheme(Color bg)
        {
            double lum = 0.299 * bg.R + 0.587 * bg.G + 0.114 * bg.B;
            bool dark = lum < 128;
            Theme t = new Theme();
            t.Dark = dark;
            t.Bg = bg;
            t.Panel = dark ? Mix(bg, Color.White, 0.07) : Mix(bg, Color.Black, 0.05);
            t.Output = dark ? Mix(bg, Color.Black, 0.45) : Mix(bg, Color.White, 0.55);
            t.EditBg = dark ? Mix(bg, Color.Black, 0.25) : Mix(bg, Color.White, 0.80);
            t.Btn = dark ? Mix(bg, Color.White, 0.13) : Mix(bg, Color.Black, 0.09);
            t.Border = dark ? Mix(bg, Color.White, 0.24) : Mix(bg, Color.Black, 0.18);
            t.Text = dark ? Color.FromArgb(214, 219, 228) : Color.FromArgb(28, 32, 40);
            t.Dim = dark ? Color.FromArgb(132, 140, 154) : Color.FromArgb(108, 116, 130);
            t.Accent = dark ? Color.FromArgb(122, 178, 255) : Color.FromArgb(26, 88, 190);
            t.Ok = dark ? Color.FromArgb(126, 214, 160) : Color.FromArgb(18, 118, 70);
            t.Err = dark ? Color.FromArgb(240, 140, 140) : Color.FromArgb(176, 40, 40);
            t.Warn = dark ? Color.FromArgb(240, 200, 120) : Color.FromArgb(150, 100, 10);
            t.RunBtn = dark ? Color.FromArgb(46, 92, 150) : Color.FromArgb(38, 99, 198);
            t.StopBtn = dark ? Color.FromArgb(70, 40, 40) : Color.FromArgb(200, 120, 120);
            return t;
        }

        private static Theme theme = BuildTheme(Color.FromArgb(14, 17, 23));

        private static readonly Color[] PRESETS = new Color[]
        {
            Color.FromArgb(14, 17, 23),    // 墨黑
            Color.FromArgb(0, 0, 0),       // 纯黑
            Color.FromArgb(31, 32, 36),    // 石墨
            Color.FromArgb(11, 30, 45),    // 深海
            Color.FromArgb(15, 34, 26),    // 墨绿
            Color.FromArgb(37, 19, 26),    // 酒红
            Color.FromArgb(243, 244, 246), // 灰白
            Color.FromArgb(246, 241, 228)  // 米黄
        };

        private static readonly string[] PRESET_BG_NAMES = new string[]
        { "墨黑", "纯黑", "石墨", "深海", "墨绿", "酒红", "灰白", "米黄" };

        // ================================================================
        //  字体
        // ================================================================
        private static PrivateFontCollection fontPool;
        private static List<string> bundledFamilies = new List<string>();
        private static string fontName = "MiSans";
        private static float fontSize = 9.5F;

        private static Font fUi, fUiBold, fSmall, fOut, fInput;

        private static void LoadFonts()
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string[] dirs = { Path.Combine(baseDir, @"assets\fonts"), Path.Combine(baseDir, "fonts") };

            PrivateFontCollection pool = new PrivateFontCollection();
            foreach (string d in dirs)
            {
                if (!Directory.Exists(d)) { continue; }
                string[] files = Directory.GetFiles(d, "*.ttf");
                Array.Sort(files);
                foreach (string f in files)
                {
                    try { pool.AddFontFile(f); } catch { }
                }
                if (pool.Families.Length > 0) { break; }
            }

            if (pool.Families.Length > 0)
            {
                fontPool = pool;
                foreach (FontFamily fam in pool.Families) { bundledFamilies.Add(fam.Name); }
            }
            BuildFonts();
        }

        private static FontFamily ResolveFamily(string name)
        {
            if (fontPool != null)
            {
                foreach (FontFamily f in fontPool.Families)
                {
                    if (string.Equals(f.Name, name, StringComparison.OrdinalIgnoreCase)) { return f; }
                }
            }
            foreach (FontFamily f in FontFamily.Families)
            {
                if (string.Equals(f.Name, name, StringComparison.OrdinalIgnoreCase)) { return f; }
            }
            return null;
        }

        // 没装内置字体时，优先挑一个像样的中文字体，而不是西文系统字体
        private static FontFamily FallbackFamily()
        {
            string[] prefer = new string[] { "微软雅黑", "Microsoft YaHei UI", "Microsoft YaHei", "等线", "宋体" };
            foreach (string n in prefer)
            {
                foreach (FontFamily f in FontFamily.Families)
                {
                    if (string.Equals(f.Name, n, StringComparison.OrdinalIgnoreCase)) { return f; }
                }
            }
            return SystemFonts.MessageBoxFont.FontFamily;
        }

        private static string ResolvedFontName()
        {
            FontFamily f = ResolveFamily(fontName);
            if (f != null) { return f.Name; }
            return FallbackFamily().Name + "（回退）";
        }

        private static void BuildFonts()
        {
            FontFamily fam = ResolveFamily(fontName);
            if (fam == null) { fam = FallbackFamily(); }
            bool hasBold = fam.IsStyleAvailable(FontStyle.Bold);
            float baseSize = fontSize;

            fUi = new Font(fam, baseSize, FontStyle.Regular);
            fUiBold = new Font(fam, baseSize + 0.5F, hasBold ? FontStyle.Bold : FontStyle.Regular);
            fSmall = new Font(fam, Math.Max(7F, baseSize - 1.5F), FontStyle.Regular);
            fOut = new Font(fam, baseSize + 0.5F, FontStyle.Regular);
            fInput = new Font(fam, baseSize + 0.5F, FontStyle.Regular);
        }

        private static void ApplyFonts()
        {
            if (form == null) { return; }
            BuildFonts();

            form.Font = fUi;
            if (lblMenu != null) { lblMenu.Font = fSmall; }
            if (lblStatus != null) { lblStatus.Font = fUiBold; }
            if (lblPath != null) { lblPath.Font = fSmall; }
            if (lblHint != null) { lblHint.Font = fSmall; }
            if (btnSettings != null) { btnSettings.Font = fUi; }
            if (cbMode != null) { cbMode.Font = fUi; }
            if (txtInput != null) { txtInput.Font = fInput; }
            if (rt != null) { rt.Font = fOut; }
            if (btnRun != null) { btnRun.Font = fUi; }
            if (btnStop != null) { btnStop.Font = fUi; }
            if (btnClear != null) { btnClear.Font = fSmall; }
            if (btnSave != null) { btnSave.Font = fSmall; }
            foreach (Button b in menuButtons) { b.Font = fUi; }

            if (dlgSettings != null && !dlgSettings.IsDisposed)
            {
                dlgSettings.Font = fUi;
                foreach (Label l in dlgLabels) { l.Font = fUi; }
                foreach (Button b in dlgButtons) { b.Font = fUi; }
                if (cbFont != null) { cbFont.Font = fUi; }
                if (cbSize != null) { cbSize.Font = fUi; }
                if (lblFontTip != null) { lblFontTip.Font = fSmall; }
                if (lblPreview != null)
                {
                    lblPreview.Font = new Font(ResolveFamily(fontName), fontSize, FontStyle.Regular);
                }
            }

            LayoutInputRow();
        }

        // ================================================================
        //  运行状态
        // ================================================================
        private static string toolDir = "";
        private static string adbPath = "adb.exe";
        private static string fastbootPath = "fastboot.exe";

        private static Form form;
        private static TableLayoutPanel tbl;
        private static Panel panelLeft, panelMain, panelTop, panelBottom, rtHost;
        private static Label lblStatus, lblPath, lblMenu, lblHint;
        private static RichTextBox rt;
        private static ComboBox cbMode;
        private static TextBox txtInput;
        private static Button btnRun, btnStop, btnClear, btnSave, btnSettings;
        private static readonly List<Button> menuButtons = new List<Button>();
        private static readonly List<Label> sectionLabels = new List<Label>();
        private static Process proc;
        private static volatile bool running;
        private static readonly List<string> history = new List<string>();
        private static int histIdx;

        private static string statusText = "正在检测设备…";
        private static int statusState;

        // 设置窗口
        private static Form dlgSettings;
        private static ComboBox cbFont, cbSize;
        private static Label lblFontTip, lblPreview;
        private static readonly List<Label> dlgLabels = new List<Label>();
        private static readonly List<Button> dlgButtons = new List<Button>();
        private static FlowLayoutPanel dlgSwatches;
        private static bool dlgLoading;

        // 首次运行：下载 platform-tools
        private static Form dlgSetup;
        private static Label lblSetupTitle, lblSetupBody, lblSetupPct;
        private static ProgressBar pbSetup;
        private static Button btnSetupGo, btnSetupManual, btnSetupExit;
        private static volatile bool setupBusy;
        private static string proxyUrl = "";
        private static string customPtSource = "";
        private static string remoteToken = "";
        private static string wallpaperPath = "";
        private static int btnAlpha = 100;
        private static string lastPtSource = "";

        private enum Role { Text, Cmd, Err, Ok, Dim, Warn }

        private static readonly List<KeyValuePair<Role, string>> lines = new List<KeyValuePair<Role, string>>();

        // ================================================================
        //  入口
        // ================================================================
        [STAThread]
        private static int Main(string[] args)
        {
            bool selftest = false;
            bool showVersion = false;
            string runOnce = null;
            string outFile = null;

            for (int i = 0; i < args.Length; i++)
            {
                string a = args[i].ToLowerInvariant();
                if (a == "--selftest") { selftest = true; }
                else if (a == "--version" || a == "-v") { showVersion = true; }
                else if (a == "--run" && i + 1 < args.Length) { runOnce = args[++i]; }
                else if (a == "--out" && i + 1 < args.Length) { outFile = args[++i]; }
            }

            if (showVersion)
            {
                AttachConsole(ATTACH_PARENT_PROCESS);
                Emit("Easy-platform-tools-GUI " + AppVersion + "\r\n" + AppRepo + "\r\n", outFile);
                return 0;
            }

            ResolveTools();
            LoadFonts();
            LoadConfig();
            if (string.IsNullOrEmpty(remoteToken)) { remoteToken = new Random().Next(100000, 999999).ToString(); SaveConfig(); }

            // .NET Framework 默认可能只协商 TLS 1.0，镜像站普遍要求 1.2
            try { ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12; } catch { }

            if (runOnce != null) { AttachConsole(ATTACH_PARENT_PROCESS); return RunHeadless(runOnce, outFile); }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            if (selftest) { AttachConsole(ATTACH_PARENT_PROCESS); return RunSelfTest(outFile); }

            BuildUi();
            Application.Run(form);
            return 0;
        }

        // ================================================================
        //  组件定位
        // ================================================================
        private static void ResolveTools()
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            List<string> candidates = new List<string>();
            candidates.Add(Path.Combine(baseDir, "bin"));
            candidates.Add(Path.GetFullPath(Path.Combine(baseDir, @"..\bin")));
            candidates.Add(baseDir);
            candidates.Add(@"C:\platform-tools");

            foreach (string c in candidates)
            {
                string full;
                try { full = Path.GetFullPath(c); } catch { continue; }
                if (File.Exists(Path.Combine(full, "adb.exe")))
                {
                    toolDir = full;
                    adbPath = Path.Combine(full, "adb.exe");
                    string fb = Path.Combine(full, "fastboot.exe");
                    fastbootPath = File.Exists(fb) ? fb : "fastboot.exe";
                    return;
                }
            }

            string[] dirs = (Environment.GetEnvironmentVariable("PATH") ?? "").Split(';');
            foreach (string d in dirs)
            {
                if (d.Trim().Length == 0) { continue; }
                try
                {
                    string p = Path.Combine(d.Trim(), "adb.exe");
                    if (File.Exists(p))
                    {
                        toolDir = d.Trim();
                        adbPath = p;
                        string fb = Path.Combine(toolDir, "fastboot.exe");
                        fastbootPath = File.Exists(fb) ? fb : "fastboot.exe";
                        return;
                    }
                }
                catch { }
            }

            toolDir = "(未找到 adb，请把 platform-tools 放到 bin 目录)";
        }

        // ================================================================
        //  配置：bg=#RRGGBB  font=MiSans  size=9.5
        // ================================================================
        private static string ConfigPath
        {
            get { return Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "config.ini"); }
        }

        private static void LoadConfig()
        {
            try
            {
                if (!File.Exists(ConfigPath)) { return; }
                foreach (string raw in File.ReadAllLines(ConfigPath))
                {
                    string line = raw.Trim();
                    if (line.Length == 0 || line.StartsWith(";")) { continue; }
                    int eq = line.IndexOf('=');
                    if (eq <= 0) { continue; }
                    string key = line.Substring(0, eq).Trim().ToLowerInvariant();
                    string val = line.Substring(eq + 1).Trim();

                    if (key == "bg")
                    {
                        try { theme = BuildTheme(Hex(val)); } catch { }
                    }
                    else if (key == "font")
                    {
                        if (val.Length > 0) { fontName = val; }
                    }
                    else if (key == "size")
                    {
                        float s;
                        if (float.TryParse(val, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out s)
                            && s >= 7F && s <= 16F)
                        {
                            fontSize = s;
                        }
                    }
                    else if (key == "proxy")
                    {
                        proxyUrl = val;
                    }
                    else if (key == "lang")
                    {
                        Lang.Current = (val == "en") ? Lang.EN : Lang.ZH;
                    }
                    else if (key == "alpha")
                    {
                        int a;
                        if (int.TryParse(val, out a)) { btnAlpha = Math.Max(0, Math.Min(100, a)); }
                    }
                    else if (key == "wall")
                    {
                        wallpaperPath = val;
                    }
                    else if (key == "token")
                    {
                        remoteToken = val;
                    }
                    else if (key == "pt_source")
                    {
                        customPtSource = val;
                    }
                }
            }
            catch { }
        }

        private static void SaveConfig()
        {
            try
            {
                StringBuilder sb = new StringBuilder();
                sb.AppendLine("; Easy-platform-tools-GUI 配置");
                sb.AppendLine("bg=" + ToHex(theme.Bg));
                sb.AppendLine("font=" + fontName);
                sb.AppendLine("size=" + fontSize.ToString("0.#", System.Globalization.CultureInfo.InvariantCulture));
                sb.AppendLine("lang=" + Lang.Current);
                sb.AppendLine("token=" + remoteToken);
                sb.AppendLine("wall=" + wallpaperPath);
                sb.AppendLine("alpha=" + btnAlpha);
                File.WriteAllText(ConfigPath, sb.ToString(), new UTF8Encoding(false));
            }
            catch { }
        }

        // ================================================================
        //  主界面
        // ================================================================
        private static void BuildUi()
        {
            form = new Form();
            form.Text = "Easy-platform-tools-GUI — adb / fastboot";
            form.Size = new Size(1060, 720);
            form.MinimumSize = new Size(880, 560);
            form.StartPosition = FormStartPosition.CenterScreen;
            form.AutoScaleMode = AutoScaleMode.Dpi;
            form.AutoScaleDimensions = new SizeF(96F, 96F);
            form.Font = fUi;
            try { form.Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); }
            catch { form.Icon = SystemIcons.Application; }

            tbl = new TableLayoutPanel();
            tbl.Dock = DockStyle.Fill;
            tbl.ColumnCount = 2;
            tbl.RowCount = 1;
            tbl.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 200F));
            tbl.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100F));
            tbl.RowStyles.Add(new RowStyle(SizeType.Percent, 100F));
            form.Controls.Add(tbl);

            BuildSidebar();
            BuildMain();

            form.Shown += delegate
            {
                AddOut("Easy-platform-tools-GUI v" + AppVersion + "　·　adb / fastboot 命令控制台", Role.Cmd);
                AddOut("组件目录：" + toolDir, Role.Dim);
                AddOut(Lang.IsEn
                    ? "Font: " + ResolvedFontName() + " " + fontSize.ToString("0.#") + "pt  ·  theme: " + ToHex(theme.Bg)
                    : "字体：" + ResolvedFontName() + " " + fontSize.ToString("0.#") + "pt　·　主题：" + ToHex(theme.Bg), Role.Dim, false);
                AddOut("左下角「设置」可换背景色和字体。", Role.Dim);
                AddOut("", Role.Dim);
                txtInput.Focus();
                RefreshStatusAsync();
                if (!File.Exists(adbPath)) { ShowSetupDialog(); }
            };

            ApplyTheme(theme);
            ApplyFonts();
            Lang.ApplyTo(form);
        }

        private static void BuildSidebar()
        {
            panelLeft = new WallPanel();
            panelLeft.Dock = DockStyle.Fill;
            panelLeft.Padding = new Padding(12, 10, 8, 10);
            tbl.Controls.Add(panelLeft, 0, 0);

            TableLayoutPanel side = new TableLayoutPanel();
            side.Dock = DockStyle.Fill;
            side.ColumnCount = 1;
            side.RowCount = 3;
            side.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100F));
            side.RowStyles.Add(new RowStyle(SizeType.Absolute, 30F));
            side.RowStyles.Add(new RowStyle(SizeType.Percent, 100F));
            side.RowStyles.Add(new RowStyle(SizeType.Absolute, 48F));
            panelLeft.Controls.Add(side);

            lblMenu = new Label();
            lblMenu.Text = "快捷命令";
            lblMenu.Dock = DockStyle.Fill;
            lblMenu.Font = fSmall;
            lblMenu.TextAlign = ContentAlignment.MiddleLeft;
            side.Controls.Add(lblMenu, 0, 0);

            FlowLayoutPanel flowMenu = new FlowLayoutPanel();
            flowMenu.Dock = DockStyle.Fill;
            flowMenu.FlowDirection = FlowDirection.TopDown;
            flowMenu.WrapContents = false;
            flowMenu.AutoScroll = true;
            side.Controls.Add(flowMenu, 0, 1);

            btnSettings = new AlphaButton();
            btnSettings.Text = "⚙  设置";
            btnSettings.Dock = DockStyle.Fill;
            btnSettings.Margin = new Padding(0, 8, 4, 0);
            btnSettings.FlatStyle = FlatStyle.Flat;
            btnSettings.FlatAppearance.BorderSize = 1;
            btnSettings.Font = fUi;
            btnSettings.TextAlign = ContentAlignment.MiddleLeft;
            btnSettings.Padding = new Padding(8, 0, 0, 0);
            btnSettings.Click += delegate { OpenSettings(); };
            side.Controls.Add(btnSettings, 0, 2);

            NewMenuButton(flowMenu, "检测设备", "adb devices -l");
            NewMenuButton(flowMenu, "版本信息", "adb version");
            NewMenuButton(flowMenu, "设备属性", "adb shell getprop ro.product.device");
            NewMenuButton(flowMenu, "重启到 Bootloader", "adb reboot bootloader");
            NewMenuButton(flowMenu, "重启到 Recovery", "adb reboot recovery");
            NewMenuButton(flowMenu, "重启到 EDL", "@edl");
            NewMenuButton(flowMenu, "重启系统", "adb reboot");
            NewMenuButton(flowMenu, "fastboot 设备", "fastboot devices");
            NewMenuButton(flowMenu, "fastboot 重启", "fastboot reboot");

            NewSectionLabel(flowMenu, "刷写与备份");
            NewMenuButton(flowMenu, "读分区表…", "@parts");
            NewMenuButton(flowMenu, "刷入镜像…", "@flashimg");
            NewMenuButton(flowMenu, "线刷 ROM…", "@rom");
            NewMenuButton(flowMenu, "备份基带…", "@backup");
            NewSectionLabel(flowMenu, "手机遥控");
            NewMenuButton(flowMenu, "遥控服务…", "@remote");
        }

        private static void NewSectionLabel(FlowLayoutPanel host, string text)
        {
            Label l = new Label();
            l.Text = text;
            l.Size = new Size(160, 24);
            l.Margin = new Padding(2, 10, 0, 2);
            l.Font = fSmall;
            l.TextAlign = ContentAlignment.BottomLeft;
            host.Controls.Add(l);
            sectionLabels.Add(l);
        }

        private static void BuildMain()
        {
            panelMain = new WallPanel();
            panelMain.Dock = DockStyle.Fill;
            tbl.Controls.Add(panelMain, 1, 0);

            panelTop = new WallPanel();
            panelTop.Dock = DockStyle.Top;
            panelTop.Height = 60;
            panelMain.Controls.Add(panelTop);

            lblStatus = new Label();
            SetLbl(lblStatus, "正在检测设备…");
            lblStatus.AutoSize = true;
            lblStatus.Location = new Point(14, 10);
            lblStatus.Font = fUiBold;
            panelTop.Controls.Add(lblStatus);

            lblPath = new Label();
            SetLbl(lblPath, "组件目录：" + toolDir);
            lblPath.AutoSize = true;
            lblPath.Location = new Point(14, 32);
            lblPath.Font = fSmall;
            panelTop.Controls.Add(lblPath);

            panelBottom = new WallPanel();
            panelBottom.Dock = DockStyle.Bottom;
            panelBottom.Height = 92;
            panelMain.Controls.Add(panelBottom);

            cbMode = new ComboBox();
            cbMode.DropDownStyle = ComboBoxStyle.DropDownList;
            cbMode.Items.AddRange(new object[] { "adb shell", "adb", "fastboot" });
            cbMode.SelectedIndex = 0;
            cbMode.Location = new Point(14, 14);
            cbMode.Size = new Size(104, 26);
            cbMode.FlatStyle = FlatStyle.Flat;
            cbMode.Font = fUi;
            panelBottom.Controls.Add(cbMode);

            txtInput = new TextBox();
            txtInput.Location = new Point(126, 14);
            txtInput.Size = new Size(600, 26);
            txtInput.Font = fInput;
            txtInput.BorderStyle = BorderStyle.FixedSingle;
            panelBottom.Controls.Add(txtInput);

            btnRun = new AlphaButton();
            btnRun.Text = "执行";
            btnRun.Size = new Size(84, 28);
            btnRun.Location = new Point(600, 14);
            btnRun.FlatStyle = FlatStyle.Flat;
            btnRun.ForeColor = Color.White;
            btnRun.FlatAppearance.BorderSize = 0;
            btnRun.Font = fUi;
            panelBottom.Controls.Add(btnRun);

            btnStop = new AlphaButton();
            btnStop.Text = "停止";
            btnStop.Size = new Size(58, 28);
            btnStop.Location = new Point(690, 14);
            btnStop.FlatStyle = FlatStyle.Flat;
            btnStop.ForeColor = Color.White;
            btnStop.Enabled = false;
            btnStop.FlatAppearance.BorderSize = 0;
            btnStop.Font = fUi;
            panelBottom.Controls.Add(btnStop);

            btnClear = new AlphaButton();
            btnClear.Text = "清空";
            btnClear.Size = new Size(58, 24);
            btnClear.Location = new Point(600, 52);
            btnClear.FlatStyle = FlatStyle.Flat;
            btnClear.FlatAppearance.BorderSize = 0;
            btnClear.Font = fSmall;
            panelBottom.Controls.Add(btnClear);

            btnSave = new AlphaButton();
            btnSave.Text = "存日志";
            btnSave.Size = new Size(64, 24);
            btnSave.Location = new Point(668, 52);
            btnSave.FlatStyle = FlatStyle.Flat;
            btnSave.FlatAppearance.BorderSize = 0;
            btnSave.Font = fSmall;
            panelBottom.Controls.Add(btnSave);

            lblHint = new Label();
            lblHint.Text = "↑↓ 翻历史　·　以 adb / fastboot 开头则按原样执行，否则套用左侧模式";
            lblHint.AutoSize = true;
            lblHint.Location = new Point(126, 50);
            lblHint.Font = fSmall;
            panelBottom.Controls.Add(lblHint);

            rtHost = new Panel();
            rtHost.Dock = DockStyle.Fill;
            rtHost.Padding = new Padding(16, 6, 0, 0);
            panelMain.Controls.Add(rtHost);

            rt = new RichTextBox();
            rt.Dock = DockStyle.Fill;
            rt.BorderStyle = BorderStyle.None;
            rt.ReadOnly = true;
            rt.WordWrap = false;
            rt.HideSelection = false;
            rt.DetectUrls = false;
            rt.Font = fOut;
            rtHost.Controls.Add(rt);
            rtHost.BringToFront();

            panelBottom.Resize += delegate { LayoutInputRow(); };
            LayoutInputRow();

            btnRun.Click += delegate { Execute(txtInput.Text); };

            btnStop.Click += delegate
            {
                Process p = proc;
                if (p == null) { return; }
                try
                {
                    if (!p.HasExited)
                    {
                        p.Kill();
                        AddOut("（已发送终止）", Role.Dim);
                    }
                }
                catch { }
            };

            btnClear.Click += delegate { lines.Clear(); rt.Clear(); };

            btnSave.Click += delegate
            {
                SaveFileDialog dlg = new SaveFileDialog();
                dlg.Filter = "文本文件 (*.txt)|*.txt";
                dlg.FileName = "adb-log-" + DateTime.Now.ToString("yyyyMMdd-HHmmss") + ".txt";
                string logDir = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "logs");
                if (Directory.Exists(logDir)) { dlg.InitialDirectory = logDir; }
                if (dlg.ShowDialog() == DialogResult.OK)
                {
                    File.WriteAllText(dlg.FileName, rt.Text, new UTF8Encoding(true));
                    AddOut("已保存：" + dlg.FileName, Role.Dim);
                }
            };

            txtInput.KeyDown += delegate(object s, KeyEventArgs e)
            {
                if (e.KeyCode == Keys.Enter)
                {
                    e.SuppressKeyPress = true;
                    string cmd = txtInput.Text;
                    if (!string.IsNullOrEmpty(cmd.Trim()))
                    {
                        if (history.Count == 0 || history[history.Count - 1] != cmd) { history.Add(cmd); }
                        histIdx = history.Count;
                        Execute(cmd);
                        txtInput.Clear();
                    }
                }
                else if (e.KeyCode == Keys.Up)
                {
                    if (history.Count > 0)
                    {
                        if (histIdx > 0) { histIdx--; }
                        txtInput.Text = history[histIdx];
                        txtInput.SelectionStart = txtInput.TextLength;
                        e.SuppressKeyPress = true;
                    }
                }
                else if (e.KeyCode == Keys.Down)
                {
                    if (history.Count > 0)
                    {
                        if (histIdx < history.Count - 1) { histIdx++; txtInput.Text = history[histIdx]; }
                        else { histIdx = history.Count; txtInput.Text = ""; }
                        txtInput.SelectionStart = txtInput.TextLength;
                        e.SuppressKeyPress = true;
                    }
                }
            };
        }

        private static void LayoutInputRow()
        {
            if (panelBottom == null || btnStop == null) { return; }
            int w = panelBottom.ClientSize.Width;
            btnStop.Left = w - 14 - btnStop.Width;
            btnRun.Left = btnStop.Left - 8 - btnRun.Width;
            txtInput.Width = Math.Max(120, btnRun.Left - 10 - txtInput.Left);
            btnSave.Left = w - 14 - btnSave.Width;
            btnClear.Left = btnSave.Left - 8 - btnClear.Width;
        }

        private static void NewMenuButton(FlowLayoutPanel host, string text, string cmd)
        {
            Button b = new AlphaButton();
            b.Text = text;
            b.Size = new Size(160, 30);
            b.AutoEllipsis = true;
            b.Margin = new Padding(0, 0, 0, 6);
            b.FlatStyle = FlatStyle.Flat;
            b.FlatAppearance.BorderSize = 1;
            b.Font = fUi;
            b.TextAlign = ContentAlignment.MiddleLeft;
            b.Padding = new Padding(8, 0, 0, 0);
            b.Tag = cmd;
            b.Click += delegate(object s, EventArgs e)
            {
                string tag = ((Button)s).Tag.ToString();
                if (tag == "@edl") { ExecuteEdl(); }
                else if (tag == "@flashimg") { OpenFlashImage(); }
                else if (tag == "@rom") { OpenFlashRom(); }
                else if (tag == "@backup") { OpenBackup(); }
                else if (tag == "@parts") { OpenPartitions(); }
                else if (tag == "@remote") { OpenRemote(); }
                else { Execute(tag); }
            };
            host.Controls.Add(b);
            menuButtons.Add(b);
        }

        // ================================================================
        //  设置窗口
        // ================================================================
        private static void OpenSettings()
        {
            if (dlgSettings != null && !dlgSettings.IsDisposed)
            {
                dlgSettings.Activate();
                return;
            }

            dlgLoading = true;
            dlgLabels.Clear();
            dlgButtons.Clear();

            dlgSettings = new Form();
            dlgSettings.Text = "设置";
            dlgSettings.FormBorderStyle = FormBorderStyle.FixedDialog;
            dlgSettings.MaximizeBox = false;
            dlgSettings.MinimizeBox = false;
            dlgSettings.ShowInTaskbar = false;
            dlgSettings.StartPosition = FormStartPosition.Manual;
            dlgSettings.AutoScaleMode = AutoScaleMode.None;
            dlgSettings.ClientSize = new Size(452, 540);
            dlgSettings.Font = fUi;
            try { dlgSettings.Icon = form.Icon; } catch { }

            // ---- 背景 ----
            AddDlgLabel("背景配色", 16, 12, true);

            dlgSwatches = new FlowLayoutPanel();
            dlgSwatches.Location = new Point(13, 38);
            dlgSwatches.Size = new Size(300, 64);
            dlgSwatches.WrapContents = true;
            dlgSettings.Controls.Add(dlgSwatches);

            for (int i = 0; i < PRESETS.Length; i++)
            {
                Button sw = new Button();
                sw.Size = new Size(30, 26);
                sw.Margin = new Padding(3);
                sw.FlatStyle = FlatStyle.Flat;
                sw.FlatAppearance.BorderSize = 1;
                sw.FlatAppearance.BorderColor = Color.FromArgb(90, 96, 110);
                sw.BackColor = PRESETS[i];
                sw.TabStop = false;
                sw.Cursor = Cursors.Hand;
                sw.Tag = i;
                Color captured = PRESETS[i];
                sw.Click += delegate { SetTheme(captured); };
                ToolTip tip = new ToolTip();
                tip.SetToolTip(sw, PRESET_BG_NAMES[i] + "  " + ToHex(PRESETS[i]));
                dlgSwatches.Controls.Add(sw);
            }

            Button btnPick = AddDlgButton("自定义颜色…", 13, 112, 130, 30);
            btnPick.Click += delegate
            {
                ColorDialog cd = new ColorDialog();
                cd.FullOpen = true;
                cd.Color = theme.Bg;
                if (cd.ShowDialog() == DialogResult.OK) { SetTheme(cd.Color); }
            };

            // ---- 字体 ----
            AddDlgLabel("字体", 16, 156, true);

            cbFont = new ComboBox();
            cbFont.DropDownStyle = ComboBoxStyle.DropDownList;
            cbFont.Location = new Point(16, 182);
            cbFont.Size = new Size(286, 26);
            cbFont.FlatStyle = FlatStyle.Flat;
            cbFont.Font = fUi;
            dlgSettings.Controls.Add(cbFont);

            cbSize = new ComboBox();
            cbSize.DropDownStyle = ComboBoxStyle.DropDownList;
            cbSize.Location = new Point(312, 182);
            cbSize.Size = new Size(124, 26);
            cbSize.FlatStyle = FlatStyle.Flat;
            cbSize.Font = fUi;
            dlgSettings.Controls.Add(cbSize);

            cbSize.Items.AddRange(new object[] { "小 8.5pt", "标准 9.5pt", "大 10.5pt", "特大 11.5pt" });
            float[] sizes = new float[] { 8.5F, 9.5F, 10.5F, 11.5F };
            int sizeIdx = 1;
            for (int i = 0; i < sizes.Length; i++) { if (Math.Abs(sizes[i] - fontSize) < 0.01F) { sizeIdx = i; } }
            cbSize.SelectedIndex = sizeIdx;

            List<string> names = new List<string>();
            foreach (string n in bundledFamilies) { names.Add(n); }
            string[] sys = new string[] { "微软雅黑", "等线", "宋体", "黑体", "Consolas" };
            foreach (string n in sys) { if (!names.Contains(n)) { names.Add(n); } }
            if (!names.Contains(fontName)) { names.Add(fontName); }
            cbFont.Items.Clear();
            foreach (string n in names) { cbFont.Items.Add(n); }
            cbFont.SelectedItem = fontName;

            lblFontTip = new Label();
            lblFontTip.Text = "内置字体在 assets\\fonts\\，丢新的 .ttf 进去重启即可出现在列表里。";
            lblFontTip.Location = new Point(16, 216);
            lblFontTip.AutoSize = true;
            lblFontTip.Font = fSmall;
            dlgSettings.Controls.Add(lblFontTip);

            lblPreview = new Label();
            lblPreview.Text = "示例　Easy-platform-tools-GUI AaBb 0123　adb devices -l";
            lblPreview.Location = new Point(16, 244);
            lblPreview.Size = new Size(420, 46);
            lblPreview.TextAlign = ContentAlignment.MiddleLeft;
            lblPreview.Padding = new Padding(10, 0, 0, 0);
            lblPreview.BorderStyle = BorderStyle.FixedSingle;
            dlgSettings.Controls.Add(lblPreview);

            Button btnClose = AddDlgButton("关闭", 348, 486, 88, 32);
            btnClose.Click += delegate { dlgSettings.Close(); };

            cbFont.SelectedIndexChanged += delegate
            {
                if (dlgLoading) { return; }
                fontName = cbFont.SelectedItem.ToString();
                ApplyFonts();
                SaveConfig();
                AddOut("字体已切换：" + fontName, Role.Dim);
            };

            cbSize.SelectedIndexChanged += delegate
            {
                if (dlgLoading) { return; }
                float[] sz = new float[] { 8.5F, 9.5F, 10.5F, 11.5F };
                fontSize = sz[cbSize.SelectedIndex];
                ApplyFonts();
                SaveConfig();
            };

            dlgLoading = false;

            ApplySettingsTheme();
            dlgSettings.StartPosition = FormStartPosition.Manual;
            dlgSettings.Location = new Point(
                form.Left + 30,
                form.Bottom - dlgSettings.Height - 46);
            if (dlgSettings.Top < 0) { dlgSettings.Top = 40; }
            AddDlgLabel("语言", 16, 300, true);

            ComboBox cbLang = new ComboBox();
            cbLang.DropDownStyle = ComboBoxStyle.DropDownList;
            cbLang.Location = new Point(16, 326);
            cbLang.Size = new Size(180, 26);
            cbLang.FlatStyle = FlatStyle.Flat;
            cbLang.Font = fUi;
            cbLang.Items.AddRange(new object[] { "中文", "English" });
            cbLang.SelectedIndex = Lang.IsEn ? 1 : 0;
            cbLang.SelectedIndexChanged += delegate
            {
                if (dlgLoading) { return; }
                string want = cbLang.SelectedIndex == 1 ? Lang.EN : Lang.ZH;
                if (want == Lang.Current) { return; }
                Lang.Current = want;
                SaveConfig();
                string ask = Lang.IsEn
                    ? "Language changed. Restart to apply?"
                    : "语言已切换，需要重启程序生效。现在重启？";
                if (MessageBox.Show(dlgSettings, ask, "Language / 语言", MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes)
                {
                    Application.Restart();
                }
            };
            dlgSettings.Controls.Add(cbLang);

            AddDlgLabel("背景图", 16, 356, true);

            Label lblWall = new Label();
            lblWall.Text = string.IsNullOrEmpty(wallpaperPath) ? "（无）" : Path.GetFileName(wallpaperPath);
            lblWall.Location = new Point(262, 390);
            lblWall.AutoSize = true;
            lblWall.ForeColor = theme.Dim;
            lblWall.Font = fSmall;
            dlgSettings.Controls.Add(lblWall);

            Button btnWall = AddDlgButton("选择图片…", 16, 382, 130, 32);
            btnWall.Click += delegate
            {
                OpenFileDialog ofd = new OpenFileDialog();
                ofd.Filter = "图片 (*.jpg;*.jpeg;*.png;*.bmp)|*.jpg;*.jpeg;*.png;*.bmp|所有文件 (*.*)|*.*";
                if (ofd.ShowDialog() == DialogResult.OK)
                {
                    wallpaperPath = ofd.FileName;
                    SaveConfig();
                    ApplyTheme(theme);
                    lblWall.Text = Path.GetFileName(wallpaperPath);
                }
            };

            Button btnWallOff = AddDlgButton("移除", 156, 382, 90, 32);
            btnWallOff.Click += delegate
            {
                wallpaperPath = "";
                SaveConfig();
                ApplyTheme(theme);
                lblWall.Text = "（无）";
            };

            AddDlgLabel("按键透明度", 16, 420, true);
            TrackBar tbAlpha = new TrackBar();
            tbAlpha.Minimum = 0;
            tbAlpha.Maximum = 100;
            tbAlpha.TickFrequency = 10;
            tbAlpha.SmallChange = 5;
            tbAlpha.LargeChange = 10;
            tbAlpha.Value = btnAlpha;
            tbAlpha.Location = new Point(120, 414);
            tbAlpha.Width = 220;
            dlgSettings.Controls.Add(tbAlpha);

            Label lblAlphaVal = new Label();
            lblAlphaVal.Text = btnAlpha + "%";
            lblAlphaVal.Location = new Point(350, 422);
            lblAlphaVal.AutoSize = true;
            lblAlphaVal.ForeColor = theme.Dim;
            lblAlphaVal.Font = fSmall;
            dlgSettings.Controls.Add(lblAlphaVal);

            tbAlpha.Scroll += delegate
            {
                btnAlpha = tbAlpha.Value;
                lblAlphaVal.Text = btnAlpha + "%";
                ApplyTheme(theme);
            };
            tbAlpha.MouseCaptureChanged += delegate
            {
                btnAlpha = tbAlpha.Value;
                SaveConfig();
            };
            Lang.ApplyTo(dlgSettings);
            dlgSettings.Show(form);
        }

        private static Label AddDlgLabel(string text, int x, int y, bool strong)
        {
            Label l = new Label();
            l.Text = text;
            l.Location = new Point(x, y);
            l.AutoSize = true;
            l.Font = strong ? fUiBold : fSmall;
            dlgSettings.Controls.Add(l);
            dlgLabels.Add(l);
            return l;
        }

        private static Button AddDlgButton(string text, int x, int y, int w, int h)
        {
            Button b = new Button();
            b.Text = text;
            b.Location = new Point(x, y);
            b.Size = new Size(w, h);
            b.FlatStyle = FlatStyle.Flat;
            b.FlatAppearance.BorderSize = 1;
            b.Font = fUi;
            dlgSettings.Controls.Add(b);
            dlgButtons.Add(b);
            return b;
        }

        private static void ApplySettingsTheme()
        {
            if (dlgSettings == null || dlgSettings.IsDisposed) { return; }
            Theme t = theme;
            dlgSettings.BackColor = t.Panel;
            foreach (Label l in dlgLabels) { l.ForeColor = t.Text; }
            foreach (Button b in dlgButtons) { b.BackColor = t.Btn; b.ForeColor = t.Text; b.FlatAppearance.BorderColor = t.Border; }
            if (cbFont != null) { cbFont.BackColor = t.EditBg; cbFont.ForeColor = t.Text; }
            if (cbSize != null) { cbSize.BackColor = t.EditBg; cbSize.ForeColor = t.Text; }
            if (lblFontTip != null) { lblFontTip.ForeColor = t.Dim; }
            if (lblPreview != null) { lblPreview.BackColor = t.EditBg; lblPreview.ForeColor = t.Text; }
            if (dlgSwatches != null) { dlgSwatches.BackColor = t.Panel; }
        }

        // ================================================================
        //  首次运行：联网下载 platform-tools
        // ================================================================
        private static void ShowSetupDialog()
        {
            if (dlgSetup != null && !dlgSetup.IsDisposed) { dlgSetup.Activate(); return; }

            dlgSetup = new Form();
            dlgSetup.Text = "初始化 — 需要 Android platform-tools";
            dlgSetup.FormBorderStyle = FormBorderStyle.FixedDialog;
            dlgSetup.MaximizeBox = false;
            dlgSetup.MinimizeBox = false;
            dlgSetup.ShowInTaskbar = false;
            dlgSetup.StartPosition = FormStartPosition.CenterParent;
            dlgSetup.AutoScaleMode = AutoScaleMode.None;
            dlgSetup.ClientSize = new Size(520, 272);
            dlgSetup.Font = fUi;
            dlgSetup.BackColor = theme.Panel;

            lblSetupTitle = new Label();
            lblSetupTitle.Text = "缺少 adb / fastboot";
            lblSetupTitle.Location = new Point(20, 18);
            lblSetupTitle.AutoSize = true;
            lblSetupTitle.Font = fUiBold;
            lblSetupTitle.ForeColor = theme.Text;
            dlgSetup.Controls.Add(lblSetupTitle);

            lblSetupBody = new Label();
            lblSetupBody.Text = "本程序依赖 Android platform-tools 才能和设备通信。\n"
                + "点「立即下载」会拉取约 8 MB 的压缩包并自动解压到 bin\\ 目录。\n"
                + "下载源先在腾讯云镜像和 Google 官方之间自动切换，失败会自动换下一个。\n\n"
                + "内容归 Google 所有，遵循 Android SDK 许可协议。";
            lblSetupBody.Location = new Point(20, 46);
            lblSetupBody.Size = new Size(480, 100);
            lblSetupBody.ForeColor = theme.Text;
            dlgSetup.Controls.Add(lblSetupBody);

            pbSetup = new ProgressBar();
            pbSetup.Location = new Point(20, 156);
            pbSetup.Size = new Size(480, 14);
            pbSetup.Visible = false;
            dlgSetup.Controls.Add(pbSetup);

            lblSetupPct = new Label();
            lblSetupPct.Location = new Point(20, 174);
            lblSetupPct.Size = new Size(480, 20);
            lblSetupPct.AutoSize = false;
            lblSetupPct.Font = fSmall;
            lblSetupPct.ForeColor = theme.Dim;
            lblSetupPct.Text = "";
            dlgSetup.Controls.Add(lblSetupPct);

            btnSetupGo = new Button();
            btnSetupGo.Text = "立即下载";
            btnSetupGo.Location = new Point(20, 216);
            btnSetupGo.Size = new Size(150, 36);
            btnSetupGo.FlatStyle = FlatStyle.Flat;
            btnSetupGo.FlatAppearance.BorderSize = 0;
            btnSetupGo.BackColor = theme.RunBtn;
            btnSetupGo.ForeColor = Color.White;
            btnSetupGo.Font = fUi;
            dlgSetup.Controls.Add(btnSetupGo);

            btnSetupManual = new Button();
            btnSetupManual.Text = "手动下载";
            btnSetupManual.Location = new Point(182, 216);
            btnSetupManual.Size = new Size(120, 36);
            btnSetupManual.FlatStyle = FlatStyle.Flat;
            btnSetupManual.FlatAppearance.BorderSize = 1;
            btnSetupManual.FlatAppearance.BorderColor = theme.Border;
            btnSetupManual.BackColor = theme.Btn;
            btnSetupManual.ForeColor = theme.Text;
            btnSetupManual.Font = fUi;
            dlgSetup.Controls.Add(btnSetupManual);

            btnSetupExit = new Button();
            btnSetupExit.Text = "退出";
            btnSetupExit.Location = new Point(314, 216);
            btnSetupExit.Size = new Size(100, 36);
            btnSetupExit.FlatStyle = FlatStyle.Flat;
            btnSetupExit.FlatAppearance.BorderSize = 1;
            btnSetupExit.FlatAppearance.BorderColor = theme.Border;
            btnSetupExit.BackColor = theme.Btn;
            btnSetupExit.ForeColor = theme.Text;
            btnSetupExit.Font = fUi;
            dlgSetup.Controls.Add(btnSetupExit);

            btnSetupGo.Click += delegate { RunSetup(); };
            btnSetupManual.Click += delegate
            {
                try { Process.Start(PT_PAGE); } catch { }
                lblSetupBody.Text = "已打开官方下载页。解压后把 adb.exe、fastboot.exe、\n"
                    + "AdbWinApi.dll、AdbWinUsbApi.dll 放进：\n"
                    + Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "bin")
                    + "\n然后点左边的「重新检测」。";
                btnSetupGo.Text = "重新检测";
                btnSetupGo.Enabled = true;
            };
            btnSetupExit.Click += delegate { dlgSetup.Close(); };

            Lang.ApplyTo(dlgSetup);
            dlgSetup.Show(form);
        }

        private static void RunSetup()
        {
            if (setupBusy) { return; }
            ResolveTools();
            if (!toolDir.StartsWith("("))
            {
                FinishSetup("已找到 adb：" + toolDir);
                return;
            }
            setupBusy = true;
            btnSetupGo.Enabled = false;
            btnSetupManual.Enabled = false;
            pbSetup.Visible = true;
            pbSetup.Style = ProgressBarStyle.Continuous;
            pbSetup.Value = 0;
            SetLbl(lblSetupPct, "正在连接 Google…");
            Thread t = new Thread(new ThreadStart(delegate { DownloadPlatformTools(); }));
            t.IsBackground = true;
            t.Start();
        }

        private static void DownloadPlatformTools()
        {
            string zip = Path.Combine(Path.GetTempPath(), "androidtool-pt.zip");
            string dir = Path.Combine(Path.GetTempPath(), "androidtool-pt");
            List<string> tried = new List<string>();
            string lastError = null;

            try
            {
                bool ok = false;
                List<string[]> sources = new List<string[]>();
                if (customPtSource != null && customPtSource.Trim().Length > 0)
                {
                    sources.Add(new string[] { "自定义", customPtSource.Trim() });
                }
                foreach (string[] s in PT_SOURCES) { sources.Add(s); }

                foreach (string[] src in sources)
                {
                    string name = src[0], url = src[1];
                    tried.Add(name);
                    try
                    {
                        try { if (File.Exists(zip)) { File.Delete(zip); } } catch { }
                        SetupProgress(0, "正在连接 " + name + " …");
                        DownloadTo(url, zip, name);
                        ValidateZip(zip);
                        lastPtSource = name;
                        ok = true;
                        break;
                    }
                    catch (Exception ex)
                    {
                        lastError = name + "：" + ex.Message;
                    }
                }
                if (!ok) { throw new Exception(lastError ?? "所有镜像源都失败了"); }

                SetupProgress(100, "解压中…");
                if (Directory.Exists(dir)) { Directory.Delete(dir, true); }
                ZipFile.ExtractToDirectory(zip, dir);

                string inner = FindAdbFolder(dir);
                if (inner == null) { throw new Exception("压缩包里没有找到 adb.exe"); }
                if (!File.Exists(Path.Combine(inner, "fastboot.exe"))) { throw new Exception("压缩包里缺 fastboot.exe"); }

                string binDir = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "bin");
                Directory.CreateDirectory(binDir);
                foreach (string f in Directory.GetFiles(inner))
                {
                    File.Copy(f, Path.Combine(binDir, Path.GetFileName(f)), true);
                }

                try { File.Delete(zip); } catch { }
                try { Directory.Delete(dir, true); } catch { }
                SetupFinished(true, null);
            }
            catch (Exception ex)
            {
                SetupFinished(false, ex.Message + "\n（已尝试：" + string.Join(" / ", tried.ToArray()) + "）");
            }
        }

        // 校验：必须是真的 zip、体积合理、里面确实有 adb.exe
        private static void ValidateZip(string path)
        {
            FileInfo fi = new FileInfo(path);
            if (!fi.Exists || fi.Length < 1048576) { throw new Exception("文件大小异常（" + (fi.Exists ? (fi.Length / 1024) + " KB" : "未下载") + "）"); }
            byte[] head = new byte[2];
            using (FileStream fs = File.OpenRead(path)) { fs.Read(head, 0, 2); }
            if (head[0] != 0x50 || head[1] != 0x4B) { throw new Exception("不是有效的 zip（可能被网络劫持或返回了网页）"); }
            using (ZipArchive za = ZipFile.OpenRead(path))
            {
                bool hasAdb = false;
                foreach (ZipArchiveEntry e in za.Entries)
                {
                    if (e.FullName.EndsWith("adb.exe", StringComparison.OrdinalIgnoreCase)) { hasAdb = true; break; }
                }
                if (!hasAdb) { throw new Exception("压缩包里没有 adb.exe"); }
            }
        }

        private static string FindAdbFolder(string root)
        {
            if (File.Exists(Path.Combine(root, "adb.exe"))) { return root; }
            try
            {
                foreach (string d in Directory.GetDirectories(root))
                {
                    if (File.Exists(Path.Combine(d, "adb.exe"))) { return d; }
                }
            }
            catch { }
            return null;
        }

        private static void DownloadTo(string url, string dest, string sourceName)
        {
            WebRequest req = WebRequest.Create(url);
            req.Timeout = 60000;
            try { ((HttpWebRequest)req).ReadWriteTimeout = 180000; } catch { }
            if (proxyUrl != null && proxyUrl.Trim().Length > 0) { req.Proxy = new WebProxy(proxyUrl.Trim()); }
            else { req.Proxy = null; }   // 显式直连，避免自动代理探测拖慢速度
            using (WebResponse resp = req.GetResponse())
            {
                long total = resp.ContentLength;
                using (Stream s = resp.GetResponseStream())
                using (FileStream fs = new FileStream(dest, FileMode.Create, FileAccess.Write))
                {
                    byte[] buf = new byte[65536];
                    long got = 0;
                    int n;
                    int lastPct = -1;
                    while ((n = s.Read(buf, 0, buf.Length)) > 0)
                    {
                        fs.Write(buf, 0, n);
                        got += n;
                        int pct = total > 0 ? (int)(got * 100 / total) : -1;
                        if (pct != lastPct)
                        {
                            lastPct = pct;
                            string label = pct < 0
                                ? string.Format("下载中  {0:N1} MB   ·  {1}", got / 1048576.0, sourceName)
                                : string.Format("下载中 {0}%  {1:N1} / {2:N1} MB   ·  {3}", pct, got / 1048576.0, total / 1048576.0, sourceName);
                            SetupProgress(pct, label);
                        }
                    }
                }
            }
        }

        private static void SetupProgress(int pct, string text)
        {
            if (dlgSetup == null || dlgSetup.IsDisposed) { return; }
            try
            {
                dlgSetup.BeginInvoke(new Action(delegate
                {
                    if (pbSetup == null) { return; }
                    if (pct >= 0) { pbSetup.Value = Math.Max(0, Math.Min(100, pct)); }
                    lblSetupPct.Text = text;
                }));
            }
            catch { }
        }

        private static void SetupFinished(bool ok, string error)
        {
            if (dlgSetup == null || dlgSetup.IsDisposed) { return; }
            try
            {
                dlgSetup.BeginInvoke(new Action(delegate
                {
                    setupBusy = false;
                    if (ok)
                    {
                        ResolveTools();
                        FinishSetup("platform-tools 已就绪（下载源：" + lastPtSource + "）：" + toolDir);
                        return;
                    }
                    pbSetup.Visible = false;
                    lblSetupPct.Text = "";
                    lblSetupBody.Text = "下载失败：" + error + "\n\n"
                        + "可以点「重试」，或者点「手动下载」自己装到 bin\\ 里。\n"
                        + "需要走代理的话，在 config.ini 里加一行：proxy=http://127.0.0.1:7897";
                    lblSetupTitle.Text = "下载失败";
                    lblSetupTitle.ForeColor = theme.Err;
                    btnSetupGo.Text = "重试";
                    btnSetupGo.Enabled = true;
                    btnSetupManual.Enabled = true;
                }));
            }
            catch { }
        }

        private static void FinishSetup(string message)
        {
            setupBusy = false;
            AddOut(message, Role.Ok);
            AddOut("现在可以直接输入命令了。", Role.Dim);
            if (lblPath != null) { SetLbl(lblPath, "组件目录：" + toolDir); }
            if (dlgSetup != null && !dlgSetup.IsDisposed) { dlgSetup.Close(); }
            RefreshStatusAsync();
        }

        // ================================================================
        //  主题应用
        // ================================================================
        private static void SetTheme(Color bg)
        {
            theme = BuildTheme(bg);
            ApplyTheme(theme);
            SaveConfig();
            RenderAll();
            ApplySettingsTheme();
        }

        /// <summary>壁纸：把原图压暗后铺满窗体，面板设为透明让壁纸透出来</summary>
        private static Color WithAlpha(Color c)
        {
            if (btnAlpha >= 100) { return c; }
            return Color.FromArgb(Math.Max(0, Math.Min(100, btnAlpha)) * 255 / 100, c.R, c.G, c.B);
        }

        private static void ApplyWallpaper(Theme t)
        {
            bool on = !string.IsNullOrEmpty(wallpaperPath) && File.Exists(wallpaperPath);
            if (!on)
            {
                if (form.BackgroundImage != null) { form.BackgroundImage.Dispose(); form.BackgroundImage = null; }
                WallPanel.Wall = null;
                if (panelLeft != null) { panelLeft.Invalidate(); }
                if (panelTop != null) { panelTop.Invalidate(); }
                if (panelBottom != null) { panelBottom.Invalidate(); }
                if (panelMain != null) { panelMain.Invalidate(); }
                return;
            }

            try
            {
                int w = Math.Max(320, form.ClientSize.Width);
                int h = Math.Max(240, form.ClientSize.Height);
                using (Image src = Image.FromFile(wallpaperPath))
                {
                    Bitmap bmp = new Bitmap(w, h);
                    using (Graphics g = Graphics.FromImage(bmp))
                    {
                        g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
                        double sc = Math.Max((double)w / src.Width, (double)h / src.Height);
                        int dw = (int)Math.Ceiling(src.Width * sc);
                        int dh = (int)Math.Ceiling(src.Height * sc);
                        g.DrawImage(src, new Rectangle((w - dw) / 2, (h - dh) / 2, dw, dh));
                        // 压暗，保证文字可读
                        using (SolidBrush veil = new SolidBrush(Color.FromArgb(0, t.Output)))
                        {
                            g.FillRectangle(veil, 0, 0, w, h);
                        }
                    }
                    if (form.BackgroundImage != null) { form.BackgroundImage.Dispose(); }
                    form.BackgroundImage = bmp;
                    form.BackgroundImageLayout = ImageLayout.Stretch;
                }
                WallPanel.Wall = (Bitmap)form.BackgroundImage;
                WallPanel.Veil = Color.FromArgb(t.Dark ? 120 : 110, t.Output);
                panelLeft.Invalidate();
                panelTop.Invalidate();
                panelBottom.Invalidate();
                panelMain.Invalidate();
                tbl.Invalidate();
            }
            catch
            {
                wallpaperPath = "";
            }
        }
        private static void ApplyTheme(Theme t)
        {
            if (form == null) { return; }

            form.BackColor = t.Output;
            if (tbl != null) { tbl.BackColor = t.Panel; }
            panelLeft.BackColor = t.Panel;
            panelTop.BackColor = t.Panel;
            panelBottom.BackColor = t.Panel;
            panelMain.BackColor = t.Output;
            if (rtHost != null) { rtHost.BackColor = t.Output; }

            lblPath.ForeColor = t.Dim;
            lblHint.ForeColor = t.Dim;
            lblMenu.ForeColor = t.Dim;
            PaintStatus();

            rt.BackColor = t.Output;
            rt.ForeColor = t.Text;
            txtInput.BackColor = t.EditBg;
            txtInput.ForeColor = t.Text;
            cbMode.BackColor = t.Btn;
            cbMode.ForeColor = t.Text;

            btnRun.BackColor = t.RunBtn;
            btnStop.BackColor = t.StopBtn;
            btnClear.BackColor = WithAlpha(t.Btn);
            btnClear.ForeColor = t.Text;
            btnSave.BackColor = WithAlpha(t.Btn);
            btnSave.ForeColor = t.Text;

            if (btnSettings != null)
            {
                btnSettings.BackColor = WithAlpha(t.Btn);
                btnSettings.ForeColor = t.Text;
                btnSettings.FlatAppearance.BorderColor = t.Border;
            }

            foreach (Button b in menuButtons)
            {
                b.BackColor = WithAlpha(t.Btn);
                b.ForeColor = t.Text;
                b.FlatAppearance.BorderColor = t.Border;
            }

            foreach (Label l in sectionLabels) { l.ForeColor = t.Dim; }
            ApplyWallpaper(t);
        }

        private static Color RoleColor(Role r)
        {
            switch (r)
            {
                case Role.Cmd: return theme.Accent;
                case Role.Err: return theme.Err;
                case Role.Ok: return theme.Ok;
                case Role.Dim: return theme.Dim;
                case Role.Warn: return theme.Warn;
                default: return theme.Text;
            }
        }

        // ================================================================
        //  输出
        // ================================================================
        private static void SetLbl(Label l, string s) { if (l != null) { l.Text = Lang.Tr(s); } }

        private static void AddOut(string text, Role role, bool translate = true)
        {
            if (rt == null) { return; }
            if (translate) { text = Lang.Tr(text); }
            if (rt.InvokeRequired)
            {
                try { rt.BeginInvoke(new Action<string, Role, bool>(AddOut), new object[] { text, role, false }); }
                catch { }
                return;
            }
            lines.Add(new KeyValuePair<Role, string>(role, text));
            AppendLine(text, RoleColor(role));
        }

        private static void AppendLine(string text, Color color)
        {
            rt.SelectionStart = rt.TextLength;
            rt.SelectionLength = 0;
            rt.SelectionColor = color;
            rt.AppendText(text + "\r\n");
            rt.SelectionStart = rt.TextLength;
            rt.ScrollToCaret();
        }

        private static void RenderAll()
        {
            if (rt == null) { return; }
            rt.Clear();
            foreach (KeyValuePair<Role, string> kv in lines)
            {
                AppendLine(kv.Value, RoleColor(kv.Key));
            }
        }

        private static void SetIdle()
        {
            if (form == null || form.IsDisposed) { return; }
            try
            {
                form.BeginInvoke(new Action(delegate
                {
                    running = false;
                    if (btnRun != null) { btnRun.Enabled = true; btnRun.BackColor = theme.RunBtn; }
                    if (btnStop != null) { btnStop.Enabled = false; }
                    RefreshStatusAsync();
                }));
            }
            catch { }
        }

        // ================================================================
        //  设备状态
        // ================================================================
        private static void RefreshStatusAsync()
        {
            ThreadPool.QueueUserWorkItem(delegate
            {
                string adbOut = SafeCapture(adbPath, "devices");
                string fbOut = SafeCapture(fastbootPath, "devices");
                if (form == null || form.IsDisposed || !form.IsHandleCreated) { return; }
                try
                {
                    form.BeginInvoke(new Action(delegate { ApplyStatus(adbOut, fbOut); }));
                }
                catch { }
            });
        }

        private static string SafeCapture(string exe, string args)
        {
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo(exe);
                psi.Arguments = args;
                psi.UseShellExecute = false;
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError = true;
                psi.CreateNoWindow = true;
                psi.StandardOutputEncoding = Encoding.UTF8;
                psi.StandardErrorEncoding = Encoding.UTF8;
                using (Process p = Process.Start(psi))
                {
                    Task<string> err = p.StandardError.ReadToEndAsync();
                    string o = p.StandardOutput.ReadToEnd();
                    p.WaitForExit(8000);
                    return o + err.Result;
                }
            }
            catch { return ""; }
        }

        private static void ApplyStatus(string adbOut, string fbOut)
        {
            if (lblStatus == null) { return; }

            string adbDev = FirstDevice(adbOut, "device");
            string fbDev = FirstDevice(fbOut, "fastboot");

            if (adbDev != null) { statusText = "● Android 已连接　" + adbDev; statusState = 1; }
            else if (fbDev != null) { statusText = "● Fastboot 已连接　" + fbDev; statusState = 2; }
            else if (toolDir.StartsWith("(")) { statusText = "x 找不到 adb / fastboot，请检查 bin 目录"; statusState = 4; }
            else { statusText = "○ 未检测到设备（请检查数据线与 USB 调试）"; statusState = 3; }
            PaintStatus();
        }

        private static void PaintStatus()
        {
            if (lblStatus == null) { return; }
            SetLbl(lblStatus, statusText);
            switch (statusState)
            {
                case 1: lblStatus.ForeColor = theme.Ok; break;
                case 2: lblStatus.ForeColor = theme.Warn; break;
                case 4: lblStatus.ForeColor = theme.Err; break;
                case 3: lblStatus.ForeColor = theme.Dim; break;
                default: lblStatus.ForeColor = theme.Text; break;
            }
        }

        private static string FirstDevice(string text, string state)
        {
            if (string.IsNullOrEmpty(text)) { return null; }
            foreach (string raw in text.Split('\n'))
            {
                string line = raw.TrimEnd('\r');
                if (line.EndsWith("\t" + state, StringComparison.Ordinal))
                {
                    int tab = line.IndexOf('\t');
                    return tab > 0 ? line.Substring(0, tab) : line;
                }
            }
            return null;
        }

        // ================================================================
        //  命令解析与执行
        // ================================================================
        private static string[] SplitArgs(string text)
        {
            List<string> list = new List<string>();
            StringBuilder cur = new StringBuilder();
            bool inQuote = false;
            foreach (char ch in text)
            {
                if (ch == '"') { inQuote = !inQuote; continue; }
                if (ch == ' ' && !inQuote)
                {
                    if (cur.Length > 0) { list.Add(cur.ToString()); cur.Length = 0; }
                }
                else { cur.Append(ch); }
            }
            if (cur.Length > 0) { list.Add(cur.ToString()); }
            return list.ToArray();
        }

        private static string QuoteArg(string a)
        {
            if (a.IndexOfAny(new char[] { ' ', '"', '\t' }) >= 0)
            {
                return "\"" + a.Replace("\"", "\\\"") + "\"";
            }
            return a;
        }

        private static string JoinQuoted(string[] argv)
        {
            string[] parts = new string[argv.Length];
            for (int i = 0; i < argv.Length; i++) { parts[i] = QuoteArg(argv[i]); }
            return string.Join(" ", parts);
        }

        private static bool BuildInvocation(string[] tokens, string mode, out string exe, out string[] argv)
        {
            exe = adbPath;
            argv = new string[0];
            if (tokens.Length == 0) { return false; }

            string first = tokens[0].ToLowerInvariant();
            int skip = 1;

            if (first == "adb" || first == "fastboot")
            {
                exe = first == "adb" ? adbPath : fastbootPath;
            }
            else if (first == "shell")
            {
                exe = adbPath;
                skip = 0;
            }
            else
            {
                skip = 0;
                if (mode == "adb shell") { exe = adbPath; argv = new string[] { "shell" }; }
                else if (mode == "adb") { exe = adbPath; }
                else { exe = fastbootPath; }
            }

            List<string> rest = new List<string>(argv);
            for (int i = skip; i < tokens.Length; i++) { rest.Add(tokens[i]); }
            argv = rest.ToArray();
            return true;
        }

        private static void Execute(string text)
        {
            if (text == null) { return; }
            text = text.Trim();
            if (text.Length == 0) { return; }
            if (running) { AddOut("（已有命令在运行，先停止）", Role.Dim); return; }

            string[] tokens = SplitArgs(text);
            if (tokens.Length == 0) { return; }

            string mode = cbMode.SelectedItem == null ? "adb shell" : cbMode.SelectedItem.ToString();
            string exe;
            string[] argv;
            if (!BuildInvocation(tokens, mode, out exe, out argv)) { return; }

            ProcessStartInfo psi = new ProcessStartInfo(exe);
            psi.Arguments = JoinQuoted(argv);
            psi.UseShellExecute = false;
            psi.RedirectStandardOutput = true;
            psi.RedirectStandardError = true;
            psi.CreateNoWindow = true;

            AddOut("> " + text, Role.Cmd);
            running = true;
            btnRun.Enabled = false;
            btnStop.Enabled = true;
            btnRun.BackColor = theme.Btn;

            try
            {
                Process p = new Process();
                p.StartInfo = psi;
                proc = p;
                p.Start();

                Stream so = p.StandardOutput.BaseStream;
                Stream se = p.StandardError.BaseStream;

                Task t1 = Task.Factory.StartNew(delegate { Pump(so, delegate(string line) { AddOut(line, Role.Text); }); });
                Task t2 = Task.Factory.StartNew(delegate { Pump(se, delegate(string line) { AddOut(line, Role.Err); }); });
                Task.Factory.StartNew(delegate
                {
                    int code = -1;
                    try
                    {
                        p.WaitForExit();
                        t1.Wait();
                        t2.Wait();
                        code = p.ExitCode;
                    }
                    catch { }
                    AddOut("── 退出码 " + code + " ──", code == 0 ? Role.Ok : Role.Err);
                    SetIdle();
                });
            }
            catch (Exception ex)
            {
                AddOut("启动失败：" + ex.Message, Role.Err);
                running = false;
                btnRun.Enabled = true;
                btnRun.BackColor = theme.RunBtn;
                btnStop.Enabled = false;
            }
        }

        // ================================================================
        //  输出解码：设备数据是 UTF-8，adb 自身的提示是 ANSI(GBK)，逐行判定
        // ================================================================
        private static readonly UTF8Encoding StrictUtf8 = new UTF8Encoding(false, true);
        private static readonly Encoding AnsiGbk = Encoding.GetEncoding(936);

        private static string DecodeLine(List<byte> bytes)
        {
            byte[] a = bytes.ToArray();
            try { return StrictUtf8.GetString(a); }
            catch
            {
                try { return AnsiGbk.GetString(a); }
                catch { return Encoding.UTF8.GetString(a); }
            }
        }

        private static void Pump(Stream stream, Action<string> emit)
        {
            byte[] buf = new byte[8192];
            List<byte> cur = new List<byte>(512);
            int n;
            while ((n = stream.Read(buf, 0, buf.Length)) > 0)
            {
                for (int i = 0; i < n; i++)
                {
                    byte b = buf[i];
                    if (b == 10)
                    {
                        if (cur.Count > 0 && cur[cur.Count - 1] == 13) { cur.RemoveAt(cur.Count - 1); }
                        emit(DecodeLine(cur));
                        cur.Clear();
                    }
                    else { cur.Add(b); }
                }
            }
            if (cur.Count > 0) { emit(DecodeLine(cur)); }
        }

        // ================================================================
        //  工具窗体辅助
        // ================================================================
        private static Form NewToolDialog(string title, Size client)
        {
            Form f = new Form();
            f.Text = title;
            f.FormBorderStyle = FormBorderStyle.FixedDialog;
            f.MaximizeBox = false;
            f.MinimizeBox = false;
            f.ShowInTaskbar = false;
            f.StartPosition = FormStartPosition.CenterParent;
            f.AutoScaleMode = AutoScaleMode.None;
            f.ClientSize = client;
            f.Font = fUi;
            f.BackColor = theme.Panel;
            try { f.Icon = form.Icon; } catch { }
            return f;
        }

        private static Label DialogLabel(Form f, string text, int x, int y, int w)
        {
            Label l = new Label();
            l.Text = text;
            l.Location = new Point(x, y);
            l.Size = new Size(w, 22);
            l.ForeColor = theme.Text;
            f.Controls.Add(l);
            return l;
        }

        private static Button DialogButton(Form f, string text, int x, int y, int w, int h, bool primary)
        {
            Button b = new Button();
            b.Text = text;
            b.Location = new Point(x, y);
            b.Size = new Size(w, h);
            b.FlatStyle = FlatStyle.Flat;
            b.FlatAppearance.BorderSize = primary ? 0 : 1;
            b.FlatAppearance.BorderColor = theme.Border;
            b.BackColor = primary ? theme.RunBtn : theme.Btn;
            b.ForeColor = primary ? Color.White : theme.Text;
            b.Font = fUi;
            f.Controls.Add(b);
            return b;
        }

        private static TextBox DialogText(Form f, int x, int y, int w, string text)
        {
            TextBox t = new TextBox();
            t.Location = new Point(x, y);
            t.Size = new Size(w, 26);
            t.Text = text;
            t.Font = fUi;
            t.BackColor = theme.EditBg;
            t.ForeColor = theme.Text;
            t.BorderStyle = BorderStyle.FixedSingle;
            f.Controls.Add(t);
            return t;
        }

        private static CheckBox DialogCheck(Form f, string text, int x, int y, bool value)
        {
            CheckBox c = new CheckBox();
            c.Text = text;
            c.Location = new Point(x, y);
            c.AutoSize = true;
            c.Checked = value;
            c.ForeColor = theme.Text;
            c.Font = fUi;
            f.Controls.Add(c);
            return c;
        }

        // 二进制输出落盘（adb exec-out dd）
        private static bool RunBinary(string exe, string[] argv, string dest, out string message)
        {
            message = "";
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo(exe);
                psi.Arguments = JoinQuoted(argv);
                psi.UseShellExecute = false;
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError = true;
                psi.CreateNoWindow = true;

                using (Process p = new Process())
                {
                    p.StartInfo = psi;
                    p.Start();
                    Task<string> err = p.StandardError.ReadToEndAsync();
                    using (Stream s = p.StandardOutput.BaseStream)
                    using (FileStream fs = new FileStream(dest, FileMode.Create, FileAccess.Write))
                    {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = s.Read(buf, 0, buf.Length)) > 0) { fs.Write(buf, 0, n); }
                    }
                    if (!p.WaitForExit(600000)) { try { p.Kill(); } catch { } }
                    string e = "";
                    try { e = err.Result; } catch { }
                    message = (e ?? "").Trim();
                    return p.ExitCode == 0 && File.Exists(dest) && new FileInfo(dest).Length > 0;
                }
            }
            catch (Exception ex)
            {
                message = ex.Message;
                return false;
            }
        }

        // 同步跑一条命令，返回是否成功
        private static bool RunTask(string exe, string[] argv, out string output)
        {
            output = "";
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo(exe);
                psi.Arguments = JoinQuoted(argv);
                psi.UseShellExecute = false;
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError = true;
                psi.CreateNoWindow = true;
                psi.StandardOutputEncoding = Encoding.UTF8;
                psi.StandardErrorEncoding = Encoding.UTF8;

                using (Process p = new Process())
                {
                    p.StartInfo = psi;
                    p.Start();
                    Task<string> err = p.StandardError.ReadToEndAsync();
                    string o = p.StandardOutput.ReadToEnd();
                    if (!p.WaitForExit(180000)) { try { p.Kill(); } catch { } }
                    string e = "";
                    try { e = err.Result; } catch { }
                    output = (o + e).TrimEnd();
                    return p.ExitCode == 0;
                }
            }
            catch (Exception ex)
            {
                output = ex.Message;
                return false;
            }
        }

        private static bool HasFastbootDevice()
        {
            string o;
            if (!RunTask(fastbootPath, new string[] { "devices" }, out o)) { return false; }
            return o.IndexOf("\tfastboot", StringComparison.OrdinalIgnoreCase) >= 0
                || o.IndexOf(" fastboot", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        // ================================================================
        //  EDL
        // ================================================================
        private static void ExecuteEdl()
        {
            if (running) { AddOut("（已有命令在运行，先停止）", Role.Dim); return; }
            AddOut("> 重启到 EDL（9008 深刷模式）", Role.Cmd);

            string o;
            bool ok = RunTask(adbPath, new string[] { "reboot", "edl" }, out o);
            string text = o ?? "";
            string low = text.ToLowerInvariant();
            if (!ok || low.Contains("error") || low.Contains("not found") || low.Contains("unknown"))
            {
                AddOut("adb 通道不可用，改用 fastboot oem edl", Role.Dim);
                ok = RunTask(fastbootPath, new string[] { "oem", "edl" }, out o);
                text = o ?? "";
            }
            if (text.Trim().Length > 0) { AddOut(text.TrimEnd(), ok ? Role.Text : Role.Err); }
            AddOut(ok
                ? "已请求进入 EDL：手机可能黑屏无反应，设备管理器里应出现 Qualcomm HS-USB QDLoader 9008"
                : "进入 EDL 失败：多数机型需要已解锁 Bootloader，或厂商授权账号",
                ok ? Role.Ok : Role.Err);
            RefreshStatusAsync();
        }

        // ================================================================
        //  刷入镜像
        // ================================================================
        private static readonly string[] PARTITIONS = new string[]
        {
            "boot", "init_boot", "recovery", "dtbo", "vbmeta", "vbmeta_system", "vbmeta_vendor",
            "super", "system", "vendor", "product", "odm", "system_ext", "cust",
            "modem", "dsp", "bluetooth", "persist", "misc", "logo", "cache", "userdata", "metadata",
            "xbl", "xbl_config", "abl", "aop", "hyp", "tz", "keymaster", "devcfg", "featenabler",
            "qupfw", "uefisecapp", "cmnlib", "cmnlib64", "imagefv", "multiimgoem", "shrm", "cpucp"
        };

        private static Form dlgFlashImage;
        private static ComboBox cbPartition;
        private static TextBox txtImgPath;
        private static Label lblFlashStatus;
        private static CheckBox chkFlashReboot;
        private static Button btnFlashGo;

        private static void OpenFlashImage()
        {
            if (dlgFlashImage != null && !dlgFlashImage.IsDisposed) { dlgFlashImage.Activate(); return; }

            dlgFlashImage = NewToolDialog("刷入镜像", new Size(540, 276));

            DialogLabel(dlgFlashImage, "分区", 20, 20, 60);
            cbPartition = new ComboBox();
            cbPartition.DropDownStyle = ComboBoxStyle.DropDown;
            cbPartition.Location = new Point(90, 16);
            cbPartition.Size = new Size(430, 26);
            cbPartition.FlatStyle = FlatStyle.Flat;
            cbPartition.BackColor = theme.EditBg;
            cbPartition.ForeColor = theme.Text;
            cbPartition.Font = fUi;
            if (deviceParts.Count > 0)
            {
                foreach (string[] p in deviceParts) { cbPartition.Items.Add(p[0]); }
                cbPartition.Text = "boot";
                for (int i = 0; i < deviceParts.Count; i++)
                {
                    if (deviceParts[i][0] == "boot") { cbPartition.Text = "boot"; break; }
                }
                if (cbPartition.Items.IndexOf(cbPartition.Text) < 0) { cbPartition.Text = deviceParts[0][0]; }
            }
            else
            {
                foreach (string p in PARTITIONS) { cbPartition.Items.Add(p); }
                cbPartition.Text = "boot";
            }
            dlgFlashImage.Controls.Add(cbPartition);

            DialogLabel(dlgFlashImage, "镜像", 20, 60, 60);
            txtImgPath = DialogText(dlgFlashImage, 90, 56, 290, "");
            Button btnBrowse = DialogButton(dlgFlashImage, "浏览…", 390, 54, 130, 28, false);
            btnBrowse.Click += delegate
            {
                OpenFileDialog ofd = new OpenFileDialog();
                ofd.Filter = "镜像文件 (*.img)|*.img|所有文件 (*.*)|*.*";
                if (ofd.ShowDialog() == DialogResult.OK)
                {
                    txtImgPath.Text = ofd.FileName;
                    lblFlashStatus.Text = string.Format("{0}　{1:N1} MB", Path.GetFileName(ofd.FileName), new FileInfo(ofd.FileName).Length / 1048576.0);
                    lblFlashStatus.ForeColor = theme.Dim;
                }
            };
            txtImgPath.TextChanged += delegate
            {
                string p = txtImgPath.Text.Trim();
                if (File.Exists(p))
                {
                    lblFlashStatus.Text = string.Format("{0}　{1:N1} MB", Path.GetFileName(p), new FileInfo(p).Length / 1048576.0);
                    lblFlashStatus.ForeColor = theme.Dim;
                }
            };

            lblFlashStatus = new Label();
            lblFlashStatus.Location = new Point(20, 96);
            lblFlashStatus.Size = new Size(500, 22);
            lblFlashStatus.ForeColor = theme.Dim;
            lblFlashStatus.Font = fSmall;
            dlgFlashImage.Controls.Add(lblFlashStatus);

            chkFlashReboot = DialogCheck(dlgFlashImage, "刷完后重启到系统", 20, 130, true);

            Label tip = new Label();
            tip.Text = "提示：分区写错会导致无法开机。线刷包请用「线刷 ROM…」，不用逐个刷。";
            tip.Location = new Point(20, 166);
            tip.Size = new Size(500, 22);
            tip.ForeColor = theme.Dim;
            tip.Font = fSmall;
            dlgFlashImage.Controls.Add(tip);

            btnFlashGo = DialogButton(dlgFlashImage, "刷入", 20, 206, 140, 38, true);
            btnFlashGo.Click += delegate { DoFlashImage(); };
            Button btnClose = DialogButton(dlgFlashImage, "关闭", 172, 206, 100, 38, false);
            btnClose.Click += delegate { dlgFlashImage.Close(); };

            Lang.ApplyTo(dlgFlashImage);
            dlgFlashImage.Show(form);
        }

        private static void DoFlashImage()
        {
            string part = cbPartition.Text.Trim();
            string img = txtImgPath.Text.Trim();

            if (part.Length == 0) { lblFlashStatus.Text = "请填写分区名"; lblFlashStatus.ForeColor = theme.Err; return; }
            if (!File.Exists(img)) { lblFlashStatus.Text = "找不到镜像文件"; lblFlashStatus.ForeColor = theme.Err; return; }
            if (!HasFastbootDevice())
            {
                SetLbl(lblFlashStatus, "没有检测到 fastboot 设备，请先进入 Bootloader");
                lblFlashStatus.ForeColor = theme.Err;
                return;
            }

            long size = new FileInfo(img).Length;
            string msg = Lang.IsEn
                ? "About to run:\n\nfastboot flash " + part + " " + Path.GetFileName(img)
                  + "\n\nSize: " + string.Format("{0:N1} MB", size / 1048576.0)
                  + "\n\nFlashing the wrong partition can brick the device. Continue?"
                : "即将执行：\n\nfastboot flash " + part + " " + Path.GetFileName(img)
                  + "\n\n大小：" + string.Format("{0:N1} MB", size / 1048576.0)
                  + "\n\n写错分区可能导致无法开机，确定继续？";
            if (MessageBox.Show(dlgFlashImage, msg, Lang.IsEn ? "Confirm flash" : "确认刷入", MessageBoxButtons.OKCancel, MessageBoxIcon.Warning) != DialogResult.OK)
            {
                return;
            }

            btnFlashGo.Enabled = false;
            SetLbl(lblFlashStatus, "正在刷入…");
            lblFlashStatus.ForeColor = theme.Text;
            AddOut("> fastboot flash " + part + " " + img, Role.Cmd);

            Thread t = new Thread(new ThreadStart(delegate
            {
                string outp;
                bool ok = RunTask(fastbootPath, new string[] { "flash", part, img }, out outp);
                if (ok && chkFlashReboot.Checked)
                {
                    string o2;
                    RunTask(fastbootPath, new string[] { "reboot" }, out o2);
                }
                string result = outp;
                try
                {
                    dlgFlashImage.BeginInvoke(new Action(delegate
                    {
                        if (result.Length > 0) { AddOut(result, ok ? Role.Text : Role.Err); }
                        AddOut(ok ? ("刷入完成：" + part) : ("刷入失败：" + part), ok ? Role.Ok : Role.Err);
                        if (lblFlashStatus != null)
                        {
                            SetLbl(lblFlashStatus, ok ? "完成" : "失败");
                            lblFlashStatus.ForeColor = ok ? theme.Ok : theme.Err;
                        }
                        if (btnFlashGo != null) { btnFlashGo.Enabled = true; }
                        RefreshStatusAsync();
                    }));
                }
                catch { }
            }));
            t.IsBackground = true;
            t.Start();
        }

        // ================================================================
        //  线刷 ROM（小米 fastboot 包）
        // ================================================================
        private static Form dlgFlashRom;
        private static TextBox txtRomPath;
        private static ListBox lstRomTasks;
        private static Label lblRomInfo, lblRomStatus;
        private static CheckBox chkEraseData, chkRomReboot;
        private static Button btnRomGo;
        private static List<string[]> romTasks = new List<string[]>();

        private static void OpenFlashRom()
        {
            if (dlgFlashRom != null && !dlgFlashRom.IsDisposed) { dlgFlashRom.Activate(); return; }

            dlgFlashRom = NewToolDialog("线刷 ROM", new Size(620, 470));

            DialogLabel(dlgFlashRom, "刷机包", 20, 20, 70);
            txtRomPath = DialogText(dlgFlashRom, 90, 16, 360, "");
            Button btnBrowse = DialogButton(dlgFlashRom, "选择…", 458, 14, 140, 28, false);
            btnBrowse.Click += delegate { BrowseRom(); };
            Button btnFolder = DialogButton(dlgFlashRom, "选目录", 458, 48, 140, 28, false);
            btnFolder.Click += delegate
            {
                FolderBrowserDialog fbd = new FolderBrowserDialog();
                fbd.Description = "选择已解压的线刷包目录（含 images 子目录）";
                if (fbd.ShowDialog() == DialogResult.OK) { txtRomPath.Text = fbd.SelectedPath; ParseRom(); }
            };
            txtRomPath.TextChanged += delegate { ParseRom(); };

            lblRomInfo = new Label();
            lblRomInfo.Location = new Point(20, 82);
            lblRomInfo.Size = new Size(578, 22);
            lblRomInfo.ForeColor = theme.Dim;
            lblRomInfo.Font = fSmall;
            dlgFlashRom.Controls.Add(lblRomInfo);

            lstRomTasks = new ListBox();
            lstRomTasks.Location = new Point(20, 108);
            lstRomTasks.Size = new Size(578, 200);
            lstRomTasks.Font = fSmall;
            lstRomTasks.BackColor = theme.Output;
            lstRomTasks.ForeColor = theme.Text;
            lstRomTasks.BorderStyle = BorderStyle.FixedSingle;
            lstRomTasks.HorizontalScrollbar = true;
            dlgFlashRom.Controls.Add(lstRomTasks);

            chkEraseData = DialogCheck(dlgFlashRom, "擦除 userdata（清空全部数据）", 20, 318, true);
            chkRomReboot = DialogCheck(dlgFlashRom, "刷完自动重启", 300, 318, true);

            lblRomStatus = new Label();
            lblRomStatus.Location = new Point(20, 348);
            lblRomStatus.Size = new Size(578, 22);
            lblRomStatus.ForeColor = theme.Dim;
            lblRomStatus.Font = fSmall;
            dlgFlashRom.Controls.Add(lblRomStatus);

            btnRomGo = DialogButton(dlgFlashRom, "开始线刷", 20, 388, 160, 40, true);
            btnRomGo.Click += delegate { DoFlashRom(); };
            Button btnClose = DialogButton(dlgFlashRom, "关闭", 192, 388, 100, 40, false);
            btnClose.Click += delegate { dlgFlashRom.Close(); };

            Lang.ApplyTo(dlgFlashRom);
            dlgFlashRom.Show(form);
        }

        private static void BrowseRom()
        {
            OpenFileDialog ofd = new OpenFileDialog();
            ofd.Filter = "线刷包 (*.tgz;*.tar;*.gz;*.zip)|*.tgz;*.tar;*.gz;*.zip|所有文件 (*.*)|*.*";
            if (ofd.ShowDialog() == DialogResult.OK)
            {
                txtRomPath.Text = ofd.FileName;
                ParseRom();
            }
        }

        private static void ParseRom()
        {
            romTasks = new List<string[]>();
            if (lstRomTasks == null) { return; }
            lstRomTasks.Items.Clear();
            string path = txtRomPath.Text.Trim();

            if (path.Length == 0 || (!Directory.Exists(path) && !File.Exists(path)))
            {
                lblRomInfo.Text = "选一个线刷包（.tgz / .zip）或已解压的目录";
                lblRomInfo.ForeColor = theme.Dim;
                return;
            }

            try
            {
                string dir = path;
                if (!Directory.Exists(path))
                {
                    lblRomInfo.Text = "正在解压…";
                    Application.DoEvents();
                    dir = ExtractRom(path);
                }

                string imagesDir = Path.Combine(dir, "images");
                if (!Directory.Exists(imagesDir)) { imagesDir = dir; }

                string bat = null;
                foreach (string f in Directory.GetFiles(dir, "*.bat")) { if (Path.GetFileName(f).StartsWith("flash_all")) { bat = f; break; } }

                if (bat != null) { romTasks = ParseFlashScript(bat, dir); }
                else
                {
                    foreach (string img in Directory.GetFiles(imagesDir, "*.img"))
                    {
                        romTasks.Add(new string[] { "flash", Path.GetFileNameWithoutExtension(img), img });
                    }
                }

                foreach (string[] t in romTasks)
                {
                    string name = t[0] == "flash" ? ("flash  " + t[1]) : (t[0] + "  " + t[1]);
                    string file = t[0] == "flash" ? Path.GetFileName(t[2]) : "";
                    lstRomTasks.Items.Add(name.PadRight(28) + file);
                }

                lblRomInfo.Text = string.Format("{0}　·　共 {1} 个操作　·　脚本：{2}",
                    Path.GetFileName(dir.TrimEnd('\\')) == "" ? dir : Path.GetFileName(dir.TrimEnd('\\')),
                    romTasks.Count,
                    bat == null ? "按 images 目录推断" : Path.GetFileName(bat));
                lblRomInfo.ForeColor = theme.Ok;
            }
            catch (Exception ex)
            {
                romTasks = new List<string[]>();
                lblRomInfo.Text = "解析失败：" + ex.Message;
                lblRomInfo.ForeColor = theme.Err;
            }
        }

        private static string ExtractRom(string archive)
        {
            string dir = Path.Combine(Path.GetTempPath(), "androidtool-rom");
            if (Directory.Exists(dir)) { Directory.Delete(dir, true); }
            Directory.CreateDirectory(dir);

            if (archive.EndsWith(".zip", StringComparison.OrdinalIgnoreCase))
            {
                ZipFile.ExtractToDirectory(archive, dir);
            }
            else
            {
                string tar = Path.Combine(Environment.SystemDirectory, "tar.exe");
                string o;
                if (!File.Exists(tar)) { throw new Exception("系统没有 tar.exe，无法解压 .tgz，请先手动解压"); }
                bool ok = RunTask(tar, new string[] { "-xf", archive, "-C", dir }, out o);
                if (!ok) { throw new Exception("解压失败：" + o); }
            }

            // 包里通常还有一层目录
            string[] subs = Directory.GetDirectories(dir);
            if (subs.Length == 1 && !Directory.Exists(Path.Combine(dir, "images")))
            {
                return subs[0];
            }
            return dir;
        }

        private static List<string[]> ParseFlashScript(string bat, string root)
        {
            List<string[]> tasks = new List<string[]>();
            foreach (string raw in File.ReadAllLines(bat))
            {
                string line = raw.Trim();
                if (line.Length == 0 || line.StartsWith("::") || line.StartsWith("rem ", StringComparison.OrdinalIgnoreCase)) { continue; }
                if (!line.StartsWith("fastboot", StringComparison.OrdinalIgnoreCase)) { continue; }

                string rest = Regex.Replace(line, @"^fastboot\s+(%\*|\$\*)?\s*", "", RegexOptions.IgnoreCase).Trim();
                string[] parts = rest.Split(new char[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
                if (parts.Length < 1) { continue; }

                string cmd = parts[0].ToLowerInvariant();
                if (cmd == "flash" && parts.Length >= 3)
                {
                    string file = parts[2].Trim('"');
                    string full = Path.IsPathRooted(file) ? file : Path.Combine(root, file);
                    if (!File.Exists(full)) { continue; }
                    tasks.Add(new string[] { "flash", parts[1], full });
                }
                else if (cmd == "erase" && parts.Length >= 2)
                {
                    tasks.Add(new string[] { "erase", parts[1], "" });
                }
                else if (cmd == "set_active" && parts.Length >= 2)
                {
                    tasks.Add(new string[] { "set_active", parts[1], "" });
                }
                else if (cmd == "reboot")
                {
                    tasks.Add(new string[] { "reboot", "", "" });
                }
            }
            return tasks;
        }

        private static void DoFlashRom()
        {
            if (romTasks == null || romTasks.Count == 0)
            {
                SetLbl(lblRomStatus, "没有可执行的刷写操作，请先选择题机包");
                lblRomStatus.ForeColor = theme.Err;
                return;
            }
            if (!HasFastbootDevice())
            {
                SetLbl(lblRomStatus, "没有检测到 fastboot 设备，请先进入 Bootloader");
                lblRomStatus.ForeColor = theme.Err;
                return;
            }

            int count = romTasks.Count;
            string msg = Lang.IsEn
                ? "About to flash " + count + " steps" + (chkEraseData.Checked ? ", erasing userdata (all data lost)" : ", keeping userdata")
                  + ".\n\nUnplugging or a power loss can brick the device.\n\nStart?"
                : "即将线刷 " + count + " 个操作" + (chkEraseData.Checked ? "，并擦除 userdata（数据全部丢失）" : "，保留 userdata")
                  + "。\n\n过程中断电或拔线可能变砖，请确保电量充足、全程别动数据线。\n\n确定开始？";
            if (MessageBox.Show(dlgFlashRom, msg, Lang.IsEn ? "Confirm flashing" : "确认线刷", MessageBoxButtons.OKCancel, MessageBoxIcon.Warning) != DialogResult.OK)
            {
                return;
            }

            btnRomGo.Enabled = false;
            AddOut("> 线刷开始，共 " + count + " 个操作", Role.Cmd);

            bool erase = chkEraseData.Checked;
            bool reboot = chkRomReboot.Checked;
            List<string[]> tasks = new List<string[]>(romTasks);

            Thread t = new Thread(new ThreadStart(delegate
            {
                int done = 0; int failed = 0; string lastError = "";
                foreach (string[] task in tasks)
                {
                    string kind = task[0];
                    string arg1 = task[1];
                    string arg2 = task[2];

                    if (kind == "erase" && arg1 == "userdata" && !erase) { done++; continue; }

                    done++;
                    int index = done;
                    string label = kind == "flash" ? ("flash " + arg1) : (kind + " " + arg1);
                    try
                    {
                        dlgFlashRom.BeginInvoke(new Action(delegate
                        {
                            SetLbl(lblRomStatus, string.Format("正在执行 {0}/{1}：{2}", index, count, label));
                            lblRomStatus.ForeColor = theme.Text;
                        }));
                    }
                    catch { }

                    string outp;
                    bool ok;
                    if (kind == "flash") { ok = RunTask(fastbootPath, new string[] { "flash", arg1, arg2 }, out outp); }
                    else if (kind == "erase") { ok = RunTask(fastbootPath, new string[] { "erase", arg1 }, out outp); }
                    else if (kind == "set_active") { ok = RunTask(fastbootPath, new string[] { "set_active", arg1 }, out outp); }
                    else if (kind == "reboot") { RunTask(fastbootPath, new string[] { "reboot" }, out outp); ok = true; continue; }
                    else { ok = true; outp = ""; }

                    if (!ok)
                    {
                        failed++;
                        lastError = label + "：" + outp;
                        break;
                    }
                }

                if (failed == 0 && reboot)
                {
                    string o2;
                    RunTask(fastbootPath, new string[] { "reboot" }, out o2);
                }

                int okCount = done - failed;
                bool success = failed == 0;
                try
                {
                    dlgFlashRom.BeginInvoke(new Action(delegate
                    {
                        AddOut(success
                            ? string.Format("线刷完成：{0}/{1} 个操作", okCount, count)
                            : ("线刷中断，已完成 " + okCount + " 个，失败：" + lastError),
                            success ? Role.Ok : Role.Err);
                        if (lblRomStatus != null)
                        {
                            SetLbl(lblRomStatus, success ? "完成" : "失败");
                            lblRomStatus.ForeColor = success ? theme.Ok : theme.Err;
                        }
                        if (btnRomGo != null) { btnRomGo.Enabled = true; }
                        RefreshStatusAsync();
                    }));
                }
                catch { }
            }));
            t.IsBackground = true;
            t.Start();
        }

        // ================================================================
        //  读分区表（fastboot getvar all）
        // ================================================================
        private static readonly List<string[]> deviceParts = new List<string[]>();
        private static Form dlgParts;
        private static ListView lvParts;
        private static Label lblPartsInfo;
        private static Button btnPartsLoad;

        private static void OpenPartitions()
        {
            if (dlgParts != null && !dlgParts.IsDisposed) { dlgParts.Activate(); return; }

            dlgParts = NewToolDialog("分区表", new Size(680, 520));

            lblPartsInfo = new Label();
            lblPartsInfo.Location = new Point(20, 16);
            lblPartsInfo.Size = new Size(640, 22);
            lblPartsInfo.ForeColor = theme.Dim;
            lblPartsInfo.Font = fSmall;
            SetLbl(lblPartsInfo, "正在连接…");
            dlgParts.Controls.Add(lblPartsInfo);

            lvParts = new ListView();
            lvParts.Location = new Point(20, 44);
            lvParts.Size = new Size(640, 386);
            lvParts.View = View.Details;
            lvParts.FullRowSelect = true;
            lvParts.MultiSelect = false;
            lvParts.HideSelection = false;
            lvParts.BackColor = theme.Output;
            lvParts.ForeColor = theme.Text;
            lvParts.Font = fSmall;
            lvParts.Columns.Add("分区", 250);
            lvParts.Columns.Add("大小", 120, HorizontalAlignment.Right);
            lvParts.Columns.Add("类型", 170);
            lvParts.Columns.Add("原始值", 90);
            dlgParts.Controls.Add(lvParts);

            btnPartsLoad = DialogButton(dlgParts, "读取 / 刷新", 20, 446, 140, 40, true);
            btnPartsLoad.Click += delegate { StartReadPartitions(); };

            Button btnUse = DialogButton(dlgParts, "用于刷入镜像", 172, 446, 150, 40, false);
            btnUse.Click += delegate
            {
                if (lvParts.SelectedItems.Count == 0) { return; }
                string name = lvParts.SelectedItems[0].Text;
                if (dlgFlashImage == null || dlgFlashImage.IsDisposed) { OpenFlashImage(); }
                if (cbPartition != null) { cbPartition.Text = name; }
                dlgParts.Close();
            };

            Button btnCopy = DialogButton(dlgParts, "复制列表", 334, 446, 120, 40, false);
            btnCopy.Click += delegate
            {
                StringBuilder sb = new StringBuilder();
                foreach (string[] p in deviceParts) { sb.AppendLine(p[0] + "\t" + p[1] + "\t" + p[2]); }
                try { Clipboard.SetText(sb.ToString()); lblPartsInfo.Text = "已复制 " + deviceParts.Count + " 行到剪贴板"; }
                catch { }
            };

            Button btnCsv = DialogButton(dlgParts, "导出 CSV", 466, 446, 120, 40, false);
            btnCsv.Click += delegate { ExportPartitionsCsv(); };

            Button btnClose = DialogButton(dlgParts, "关闭", 598, 446, 62, 40, false);
            btnClose.Click += delegate { dlgParts.Close(); };

            Lang.ApplyTo(dlgParts);
            dlgParts.Show(form);
            if (deviceParts.Count > 0) { FillPartitions(); }
            StartReadPartitions();
        }

        private static void StartReadPartitions()
        {
            if (btnPartsLoad == null) { return; }
            btnPartsLoad.Enabled = false;
            SetLbl(lblPartsInfo, "正在读取 fastboot getvar all …");
            lblPartsInfo.ForeColor = theme.Text;

            Thread t = new Thread(new ThreadStart(delegate
            {
                string error = "";
                bool ok = ReadPartitions(out error);
                try
                {
                    dlgParts.BeginInvoke(new Action(delegate
                    {
                        if (btnPartsLoad != null) { btnPartsLoad.Enabled = true; }
                        if (ok) { FillPartitions(); }
                        else if (lblPartsInfo != null)
                        {
                            SetLbl(lblPartsInfo, "读取失败：" + error);
                            lblPartsInfo.ForeColor = theme.Err;
                        }
                    }));
                }
                catch { }
            }));
            t.IsBackground = true;
            t.Start();
        }

        private static bool ReadPartitions(out string error)
        {
            error = "";
            if (!HasFastbootDevice()) { error = "没有检测到 fastboot 设备，请先进入 Bootloader"; return false; }

            string o;
            RunTask(fastbootPath, new string[] { "getvar", "all" }, out o);
            List<string[]> parts = ParseGetvar(o);
            if (parts.Count == 0) { error = "getvar all 里没有解析到分区信息"; return false; }

            deviceParts.Clear();
            deviceParts.AddRange(parts);
            return true;
        }

        private static List<string[]> ParseGetvar(string text)
        {
            Dictionary<string, string> sizes = new Dictionary<string, string>();
            Dictionary<string, string> types = new Dictionary<string, string>();

            if (text != null)
            {
                foreach (string raw in text.Split('\n'))
                {
                    Match m = Regex.Match(raw.Trim(), @"partition-(size|type):\s*([^:\s]+):\s*(\S+)");
                    if (!m.Success) { continue; }
                    string kind = m.Groups[1].Value;
                    string name = m.Groups[2].Value;
                    string val = m.Groups[3].Value;
                    if (kind == "size") { sizes[name] = val; }
                    else { types[name] = val; }
                }
            }

            List<string> names = new List<string>();
            foreach (string k in sizes.Keys) { if (!names.Contains(k)) { names.Add(k); } }
            foreach (string k in types.Keys) { if (!names.Contains(k)) { names.Add(k); } }
            names.Sort(StringComparer.OrdinalIgnoreCase);

            List<string[]> list = new List<string[]>();
            foreach (string n in names)
            {
                string s = sizes.ContainsKey(n) ? sizes[n] : "";
                string t = types.ContainsKey(n) ? types[n] : "";
                list.Add(new string[] { n, s, t });
            }
            return list;
        }

        private static long ParseHexSize(string v)
        {
            if (string.IsNullOrEmpty(v)) { return -1; }
            v = v.Trim();
            try
            {
                if (v.StartsWith("0x", StringComparison.OrdinalIgnoreCase))
                {
                    return Convert.ToInt64(v.Substring(2), 16);
                }
                long n;
                if (long.TryParse(v, out n)) { return n; }
            }
            catch { }
            return -1;
        }

        private static string HumanSize(long bytes)
        {
            if (bytes < 0) { return "—"; }
            if (bytes >= 1073741824L) { return (bytes / 1073741824.0).ToString("N2") + " GB"; }
            if (bytes >= 1048576L) { return (bytes / 1048576.0).ToString("N1") + " MB"; }
            if (bytes >= 1024L) { return (bytes / 1024.0).ToString("N0") + " KB"; }
            return bytes + " B";
        }

        private static void FillPartitions()
        {
            if (lvParts == null) { return; }
            lvParts.BeginUpdate();
            lvParts.Items.Clear();
            foreach (string[] p in deviceParts)
            {
                ListViewItem it = new ListViewItem(p[0]);
                it.SubItems.Add(HumanSize(ParseHexSize(p[1])));
                it.SubItems.Add(p[2]);
                it.SubItems.Add(p[1]);
                lvParts.Items.Add(it);
            }
            lvParts.EndUpdate();
            if (lblPartsInfo != null)
            {
                SetLbl(lblPartsInfo, "共 " + deviceParts.Count + " 个分区（fastboot getvar all）");
                lblPartsInfo.ForeColor = theme.Ok;
            }
        }

        private static void ExportPartitionsCsv()
        {
            if (deviceParts.Count == 0) { return; }
            SaveFileDialog dlg = new SaveFileDialog();
            dlg.Filter = "CSV (*.csv)|*.csv";
            dlg.FileName = "partitions-" + DateTime.Now.ToString("yyyyMMdd-HHmmss") + ".csv";
            if (dlg.ShowDialog() != DialogResult.OK) { return; }
            StringBuilder sb = new StringBuilder();
            sb.AppendLine("partition,size_bytes,size_human,type");
            foreach (string[] p in deviceParts)
            {
                long b = ParseHexSize(p[1]);
                sb.AppendLine(p[0] + "," + (b < 0 ? "" : b.ToString()) + "," + HumanSize(b) + "," + p[2]);
            }
            try
            {
                File.WriteAllText(dlg.FileName, sb.ToString(), new UTF8Encoding(true));
                if (lblPartsInfo != null) { lblPartsInfo.Text = "已导出：" + dlg.FileName; }
            }
            catch (Exception ex)
            {
                if (lblPartsInfo != null) { lblPartsInfo.Text = "导出失败：" + ex.Message; }
            }
        }

        // ================================================================
        //  备份基带（modem / EFS）
        // ================================================================
        private static readonly string[][] BAK_PARTS = new string[][]
        {
            new string[] { "modem",     "基带固件（主体）",        "1" },
            new string[] { "modemst1",  "EFS 分区 1（NV）",        "1" },
            new string[] { "modemst2",  "EFS 分区 2（NV）",        "1" },
            new string[] { "fsg",       "FSG（调制解调器配置）",   "1" },
            new string[] { "fsc",       "FSC（网络锁计数）",       "1" },
            new string[] { "persist",   "persist（传感器/校准）",  "0" },
            new string[] { "bluetooth", "蓝牙固件",                "0" },
            new string[] { "dsp",       "DSP 固件",                "0" }
        };

        private static Form dlgBak;
        private static TextBox txtBakDir;
        private static ListBox lstBakLog;
        private static Label lblBakStatus;
        private static Button btnBakGo, btnBakOpen;
        private static CheckBox[] bakChecks;

        private static void OpenBackup()
        {
            if (dlgBak != null && !dlgBak.IsDisposed) { dlgBak.Activate(); return; }

            dlgBak = NewToolDialog("备份基带", new Size(640, 500));

            DialogLabel(dlgBak, "输出目录", 20, 20, 80);
            string defDir = Path.Combine(AppDomain.CurrentDomain.BaseDirectory,
                "backups", "基带备份-" + DateTime.Now.ToString("yyyyMMdd-HHmmss"));
            txtBakDir = DialogText(dlgBak, 100, 16, 390, defDir);
            Button btnPick = DialogButton(dlgBak, "选择…", 500, 14, 118, 28, false);
            btnPick.Click += delegate
            {
                FolderBrowserDialog fbd = new FolderBrowserDialog();
                fbd.Description = "选择备份保存目录";
                if (fbd.ShowDialog() == DialogResult.OK)
                {
                    txtBakDir.Text = Path.Combine(fbd.SelectedPath, "基带备份-" + DateTime.Now.ToString("yyyyMMdd-HHmmss"));
                }
            };

            Label lblSel = new Label();
            lblSel.Text = "备份内容";
            lblSel.Location = new Point(20, 54);
            lblSel.ForeColor = theme.Text;
            dlgBak.Controls.Add(lblSel);

            bakChecks = new CheckBox[BAK_PARTS.Length];
            for (int i = 0; i < BAK_PARTS.Length; i++)
            {
                string[] info = BAK_PARTS[i];
                CheckBox c = new CheckBox();
                c.Text = info[0] + "　" + info[1];
                c.Location = new Point(20 + (i % 2) * 300, 78 + (i / 2) * 28);
                c.AutoSize = true;
                c.Checked = info[2] == "1";
                c.ForeColor = theme.Text;
                c.Font = fUi;
                dlgBak.Controls.Add(c);
                bakChecks[i] = c;
            }

            lstBakLog = new ListBox();
            lstBakLog.Location = new Point(20, 200);
            lstBakLog.Size = new Size(598, 180);
            lstBakLog.Font = fSmall;
            lstBakLog.BackColor = theme.Output;
            lstBakLog.ForeColor = theme.Text;
            lstBakLog.BorderStyle = BorderStyle.FixedSingle;
            lstBakLog.HorizontalScrollbar = true;
            dlgBak.Controls.Add(lstBakLog);

            lblBakStatus = new Label();
            lblBakStatus.Location = new Point(20, 388);
            lblBakStatus.Size = new Size(598, 22);
            lblBakStatus.ForeColor = theme.Dim;
            lblBakStatus.Font = fSmall;
            SetLbl(lblBakStatus, "需要手机已 root（adb shell su 可用），备份过程请勿拔线。");
            dlgBak.Controls.Add(lblBakStatus);

            btnBakGo = DialogButton(dlgBak, "开始备份", 20, 424, 150, 40, true);
            btnBakGo.Click += delegate { DoBackup(); };
            btnBakOpen = DialogButton(dlgBak, "打开目录", 182, 424, 120, 40, false);
            btnBakOpen.Enabled = false;
            btnBakOpen.Click += delegate
            {
                try { Process.Start("explorer.exe", "\"" + txtBakDir.Text.Trim() + "\""); } catch { }
            };
            Button btnClose = DialogButton(dlgBak, "关闭", 314, 424, 100, 40, false);
            btnClose.Click += delegate { dlgBak.Close(); };

            Lang.ApplyTo(dlgBak);
            dlgBak.Show(form);
        }

        private static bool HasRoot(out string info)
        {
            string o;
            RunTask(adbPath, new string[] { "shell", "su", "-c", "id" }, out o);
            info = o;
            return o != null && o.IndexOf("uid=0", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        private static void DoBackup()
        {
            string dir = txtBakDir.Text.Trim();
            if (dir.Length == 0) { lblBakStatus.Text = "请选择输出目录"; lblBakStatus.ForeColor = theme.Err; return; }

            List<string[]> picked = new List<string[]>();
            for (int i = 0; i < BAK_PARTS.Length; i++)
            {
                if (bakChecks[i].Checked) { picked.Add(BAK_PARTS[i]); }
            }
            if (picked.Count == 0) { lblBakStatus.Text = "至少选一个分区"; lblBakStatus.ForeColor = theme.Err; return; }

            string rootInfo;
            AddOut("> 检查 root 权限…", Role.Cmd);
            if (!HasRoot(out rootInfo))
            {
                SetLbl(lblBakStatus, "没有 root 权限，无法读取分区（需要 Magisk / 内核 root）");
                lblBakStatus.ForeColor = theme.Err;
                if (rootInfo.Trim().Length > 0) { AddOut(rootInfo.TrimEnd(), Role.Err); }
                return;
            }
            AddOut("root 可用", Role.Ok);

            if (MessageBox.Show(dlgBak,
                Lang.IsEn
                    ? "Back up " + picked.Count + " partitions to:\n" + dir + "\n\nThese files contain device identity and calibration data. Do not share.\n\nStart?"
                    : "即将备份 " + picked.Count + " 个分区到：\n" + dir + "\n\n备份文件包含设备身份与校准数据，请勿外传。\n\n开始？",
                Lang.IsEn ? "Confirm backup" : "确认备份", MessageBoxButtons.OKCancel, MessageBoxIcon.Information) != DialogResult.OK)
            {
                return;
            }

            btnBakGo.Enabled = false;
            lstBakLog.Items.Clear();
            SetLbl(lblBakStatus, "备份中…");
            lblBakStatus.ForeColor = theme.Text;
            AddOut("> 开始备份基带，共 " + picked.Count + " 个分区", Role.Cmd);

            Thread t = new Thread(new ThreadStart(delegate
            {
                int okCount = 0;
                StringBuilder report = new StringBuilder();
                try { Directory.CreateDirectory(dir); }
                catch (Exception ex)
                {
                    ReportBak("无法创建目录：" + ex.Message, false, 0);
                    return;
                }

                report.AppendLine("Easy-platform-tools-GUI 基带备份");
                report.AppendLine("时间：" + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));
                report.AppendLine("设备：" + DeviceProp("ro.product.model") + " / " + DeviceProp("ro.serialno"));
                report.AppendLine();

                foreach (string[] part in picked)
                {
                    string name = part[0];
                    string file = Path.Combine(dir, name + ".img");
                    ReportBak("正在读取 " + name + " …", true, -1);
                    string msg;
                    bool ok = RunBinary(adbPath, new string[] { "exec-out", "su", "-c", "dd if=/dev/block/by-name/" + name + " bs=1M" }, file, out msg);
                    long size = File.Exists(file) ? new FileInfo(file).Length : 0;
                    if (ok && size > 0)
                    {
                        okCount++;
                        string hash = Sha256File(file);
                        ReportBak(string.Format("OK  {0,-10} {1,8:N2} MB  {2}", name, size / 1048576.0, hash.Substring(0, 16) + "…"), true, size);
                        report.AppendLine(string.Format("{0,-10} {1,10:N0} 字节  sha256={2}", name, size, hash));
                    }
                    else
                    {
                        ReportBak("失败 " + name + (msg.Length > 0 ? ("：" + msg) : ""), false, 0);
                        try { if (File.Exists(file) && size == 0) { File.Delete(file); } } catch { }
                    }
                }

                try
                {
                    File.WriteAllText(Path.Combine(dir, "备份说明.txt"),
                        report.ToString() + "\r\n注意：本目录含设备 NV / 基带数据，可用于恢复本机，但不要外传。\r\n",
                        new UTF8Encoding(false));
                }
                catch { }

                BakFinished(okCount, picked.Count, dir);
            }));
            t.IsBackground = true;
            t.Start();
        }

        private static string DeviceProp(string key)
        {
            string o;
            RunTask(adbPath, new string[] { "shell", "getprop", key }, out o);
            return (o ?? "").Trim();
        }

        private static string Sha256File(string path)
        {
            try
            {
                using (SHA256 sha = SHA256.Create())
                using (FileStream fs = File.OpenRead(path))
                {
                    byte[] h = sha.ComputeHash(fs);
                    StringBuilder sb = new StringBuilder();
                    foreach (byte b in h) { sb.Append(b.ToString("x2")); }
                    return sb.ToString();
                }
            }
            catch { return ""; }
        }

        private static void ReportBak(string line, bool ok, long size)
        {
            if (dlgBak == null || dlgBak.IsDisposed) { return; }
            try
            {
                dlgBak.BeginInvoke(new Action(delegate
                {
                    if (lstBakLog != null)
                    {
                        lstBakLog.Items.Add(line);
                        lstBakLog.TopIndex = Math.Max(0, lstBakLog.Items.Count - 1);
                    }
                    if (lblBakStatus != null) { lblBakStatus.Text = line; }
                }));
            }
            catch { }
        }

        private static void BakFinished(int okCount, int total, string dir)
        {
            if (dlgBak == null || dlgBak.IsDisposed) { return; }
            try
            {
                dlgBak.BeginInvoke(new Action(delegate
                {
                    bool all = okCount == total;
                    if (lblBakStatus != null)
                    {
                        SetLbl(lblBakStatus, string.Format("完成 {0}/{1} 个分区", okCount, total));
                        lblBakStatus.ForeColor = all ? theme.Ok : theme.Err;
                    }
                    if (btnBakGo != null) { btnBakGo.Enabled = true; }
                    if (btnBakOpen != null) { btnBakOpen.Enabled = okCount > 0; }
                    AddOut(string.Format("基带备份完成：{0}/{1} 个分区 → {2}", okCount, total, dir),
                        all ? Role.Ok : Role.Err);
                }));
            }
            catch { }
        }

        // ================================================================
        //  手机遥控服务
        // ================================================================
        public static string AdbPathPublic { get { return adbPath; } }
        public static string FastbootPathPublic { get { return fastbootPath; } }
        public static string VersionPublic { get { return AppVersion; } }
        public static List<string[]> ParseGetvarPublic(string text) { return ParseGetvar(text); }

        public static bool RunConsoleCommand(string text, out string output)
        {
            output = "";
            if (string.IsNullOrEmpty(text)) { return false; }
            string[] tokens = SplitArgs(text);
            if (tokens.Length == 0) { return false; }
            string exe;
            string[] argv;
            if (!BuildInvocation(tokens, "adb shell", out exe, out argv)) { return false; }
            return RunTask(exe, argv, out output);
        }

        private static Form dlgRemote;
        private static Label lblRemoteInfo, lblRemoteStatus;

        private static void OpenRemote()
        {
            if (dlgRemote != null && !dlgRemote.IsDisposed) { dlgRemote.Activate(); return; }

            dlgRemote = NewToolDialog("手机遥控服务", new Size(520, 300));

            lblRemoteInfo = new Label();
            lblRemoteInfo.Location = new Point(20, 18);
            lblRemoteInfo.Size = new Size(480, 90);
            lblRemoteInfo.Font = fSmall;
            lblRemoteInfo.ForeColor = theme.Text;
            dlgRemote.Controls.Add(lblRemoteInfo);

            lblRemoteStatus = new Label();
            lblRemoteStatus.Location = new Point(20, 116);
            lblRemoteStatus.Size = new Size(480, 22);
            lblRemoteStatus.Font = fSmall;
            lblRemoteStatus.ForeColor = theme.Dim;
            dlgRemote.Controls.Add(lblRemoteStatus);

            Button btnToggle = DialogButton(dlgRemote, "启动服务", 20, 156, 140, 40, true);
            btnToggle.Click += delegate
            {
                if (RemoteServer.Running)
                {
                    RemoteServer.Stop();
                    btnToggle.Text = "启动服务";
                    SetLbl(lblRemoteStatus, "已停止");
                    lblRemoteStatus.ForeColor = theme.Dim;
                }
                else
                {
                    if (RemoteServer.Start(8765, remoteToken))
                    {
                        btnToggle.Text = "停止服务";
                        string ip = RemoteServer.LocalIp();
                        SetLbl(lblRemoteStatus, "运行中：http://" + ip + ":8765");
                        lblRemoteStatus.ForeColor = theme.Ok;
                    }
                    else
                    {
                        SetLbl(lblRemoteStatus, "启动失败：" + RemoteServer.LastError);
                        lblRemoteStatus.ForeColor = theme.Err;
                    }
                }
                RefreshRemoteInfo();
            };

            Button btnCopy = DialogButton(dlgRemote, "复制配对码", 172, 156, 130, 40, false);
            btnCopy.Click += delegate
            {
                try { Clipboard.SetText(remoteToken); SetLbl(lblRemoteStatus, "配对码已复制"); } catch { }
            };

            Button btnClose = DialogButton(dlgRemote, "关闭", 314, 156, 90, 40, false);
            btnClose.Click += delegate { dlgRemote.Close(); };

            Button btnReload = DialogButton(dlgRemote, "换一个配对码", 20, 206, 160, 34, false);
            btnReload.Click += delegate
            {
                remoteToken = new Random().Next(100000, 999999).ToString();
                SaveConfig();
                RefreshRemoteInfo();
                SetLbl(lblRemoteStatus, "配对码已更换，需要重启服务才生效");
            };

            RefreshRemoteInfo();
            dlgRemote.FormClosed += delegate { if (RemoteServer.Running) { } };
            dlgRemote.Show(form);
        }

        private static void RefreshRemoteInfo()
        {
            if (lblRemoteInfo == null) { return; }
            string ip = RemoteServer.LocalIp();
            lblRemoteInfo.Text = "手机和电脑连同一个 Wi-Fi，在手机 App 的「遥控 PC」模式里填：\n\n"
                + "    地址：  " + ip + ":8765\n"
                + "    配对码：" + remoteToken + "\n\n"
                + "服务只响应这台电脑上的 adb / fastboot，命令不会离开本机。";
        }
        // ================================================================
        //  无界面模式（自检 / 单条命令）
        // ================================================================
        private static int RunHeadless(string command, string outFile)
        {
            StringBuilder sb = new StringBuilder();
            sb.AppendLine("版 本：" + AppVersion);
            sb.AppendLine("组件目录：" + toolDir);
            sb.AppendLine("字体：" + ResolvedFontName() + " " + fontSize.ToString("0.#") + "pt");
            sb.AppendLine("背景：" + ToHex(theme.Bg));
            sb.AppendLine("> " + command);

            string[] tokens = SplitArgs(command);
            string exe;
            string[] argv;
            if (!BuildInvocation(tokens, "adb shell", out exe, out argv))
            {
                sb.AppendLine("无法解析命令");
                Emit(sb.ToString(), outFile);
                return 2;
            }

            sb.AppendLine("调用：" + exe + " " + JoinQuoted(argv));
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo(exe);
                psi.Arguments = JoinQuoted(argv);
                psi.UseShellExecute = false;
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError = true;
                psi.CreateNoWindow = true;
                psi.StandardOutputEncoding = Encoding.UTF8;
                psi.StandardErrorEncoding = Encoding.UTF8;
                using (Process p = Process.Start(psi))
                {
                    StringBuilder outBuf = new StringBuilder();
                    StringBuilder errBuf = new StringBuilder();
                    Stream so = p.StandardOutput.BaseStream;
                    Stream se = p.StandardError.BaseStream;
                    Task t1 = Task.Factory.StartNew(delegate { Pump(so, delegate(string line) { lock (outBuf) { outBuf.AppendLine(line); } }); });
                    Task t2 = Task.Factory.StartNew(delegate { Pump(se, delegate(string line) { lock (errBuf) { errBuf.AppendLine(line); } }); });

                    if (!p.WaitForExit(60000)) { try { p.Kill(); } catch { } }
                    t1.Wait(5000);
                    t2.Wait(5000);

                    sb.Append(outBuf.ToString());
                    if (errBuf.Length > 0) { sb.AppendLine("[stderr]"); sb.Append(errBuf.ToString()); }
                    sb.AppendLine("── 退出码 " + p.ExitCode + " ──");
                }
            }
            catch (Exception ex)
            {
                sb.AppendLine("启动失败：" + ex.Message);
                Emit(sb.ToString(), outFile);
                return 1;
            }

            Emit(sb.ToString(), outFile);
            return 0;
        }

        private static int RunSelfTest(string outFile)
        {
            StringBuilder sb = new StringBuilder();
            sb.AppendLine("SELFTEST OK");
            sb.AppendLine("版本=" + AppVersion);
            sb.AppendLine("组件目录=" + toolDir);
            sb.AppendLine("adb=" + adbPath + " exists=" + File.Exists(adbPath));
            sb.AppendLine("fastboot=" + fastbootPath + " exists=" + File.Exists(fastbootPath));
            sb.AppendLine("内置字体=" + string.Join(", ", bundledFamilies.ToArray()));
            sb.AppendLine("当前字体=" + ResolvedFontName() + " " + fontSize.ToString("0.#") + "pt");
            sb.AppendLine("背景=" + ToHex(theme.Bg) + " 深色=" + theme.Dark);

            BuildUi();
            sb.AppendLine("控件数=" + form.Controls.Count);
            sb.AppendLine("左侧按钮=" + menuButtons.Count);
            try
            {
                OpenSettings();
                List<string> fontItems = new List<string>();
                foreach (object o in cbFont.Items) { fontItems.Add(o.ToString()); }
                sb.AppendLine("设置窗口字体项=" + fontItems.Count + "（" + string.Join(" / ", fontItems.ToArray()) + "）");
                sb.AppendLine("设置窗口打开=" + (dlgSettings != null && dlgSettings.Visible));
                dlgSettings.Close();
            }
            catch (Exception ex)
            {
                sb.AppendLine("设置窗口异常：" + ex.Message);
            }
            form.Dispose();
            Emit(sb.ToString(), outFile);
            return 0;
        }

        private static void Emit(string text, string outFile)
        {
            if (outFile != null)
            {
                File.WriteAllText(outFile, text, new UTF8Encoding(true));
                return;
            }
            try
            {
                using (StreamWriter w = new StreamWriter(Console.OpenStandardOutput(), new UTF8Encoding(false)))
                {
                    w.AutoFlush = true;
                    w.Write(text);
                }
            }
            catch { }
        }
    }
}
