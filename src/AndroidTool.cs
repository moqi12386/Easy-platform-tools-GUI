// 安卓调试台 — adb / fastboot 图形化命令台
// 编译：powershell -ExecutionPolicy Bypass -File build.ps1
// 依赖：.NET Framework 4.x（Windows 自带），无需 PowerShell 运行时。
// 字体：assets\fonts\*.ttf（MiSans、JetBrains Mono），缺失时回退系统字体。

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Text;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace AndroidTool
{
    internal static class Program
    {
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool AttachConsole(int dwProcessId);

        private const int ATTACH_PARENT_PROCESS = -1;

        private const string AppVersion = "1.0.0";
        private const string AppRepo = "https://github.com/moqi12386/android-debug-console";

        // ================================================================
        //  主题
        // ================================================================
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
                Emit("AndroidTool " + AppVersion + "\r\n" + AppRepo + "\r\n", outFile);
                return 0;
            }

            ResolveTools();
            LoadFonts();
            LoadConfig();

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
                }
            }
            catch { }
        }

        private static void SaveConfig()
        {
            try
            {
                StringBuilder sb = new StringBuilder();
                sb.AppendLine("; 安卓调试台配置");
                sb.AppendLine("bg=" + ToHex(theme.Bg));
                sb.AppendLine("font=" + fontName);
                sb.AppendLine("size=" + fontSize.ToString("0.#", System.Globalization.CultureInfo.InvariantCulture));
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
            form.Text = "安卓调试台 — adb / fastboot";
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
                AddOut("安卓调试台 v" + AppVersion + "　·　adb / fastboot 命令控制台", Role.Cmd);
                AddOut("组件目录：" + toolDir, Role.Dim);
                AddOut("字体：" + ResolvedFontName() + " " + fontSize.ToString("0.#") + "pt　·　主题：" + ToHex(theme.Bg), Role.Dim);
                AddOut("左下角「设置」可换背景色和字体。", Role.Dim);
                AddOut("", Role.Dim);
                txtInput.Focus();
                RefreshStatusAsync();
            };

            ApplyTheme(theme);
            ApplyFonts();
        }

        private static void BuildSidebar()
        {
            panelLeft = new Panel();
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

            btnSettings = new Button();
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
            NewMenuButton(flowMenu, "重启系统", "adb reboot");
            NewMenuButton(flowMenu, "fastboot 设备", "fastboot devices");
            NewMenuButton(flowMenu, "fastboot 重启", "fastboot reboot");
        }

        private static void BuildMain()
        {
            panelMain = new Panel();
            panelMain.Dock = DockStyle.Fill;
            tbl.Controls.Add(panelMain, 1, 0);

            panelTop = new Panel();
            panelTop.Dock = DockStyle.Top;
            panelTop.Height = 60;
            panelMain.Controls.Add(panelTop);

            lblStatus = new Label();
            lblStatus.Text = "正在检测设备…";
            lblStatus.AutoSize = true;
            lblStatus.Location = new Point(14, 10);
            lblStatus.Font = fUiBold;
            panelTop.Controls.Add(lblStatus);

            lblPath = new Label();
            lblPath.Text = "组件目录：" + toolDir;
            lblPath.AutoSize = true;
            lblPath.Location = new Point(14, 32);
            lblPath.Font = fSmall;
            panelTop.Controls.Add(lblPath);

            panelBottom = new Panel();
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

            btnRun = new Button();
            btnRun.Text = "执行";
            btnRun.Size = new Size(84, 28);
            btnRun.Location = new Point(600, 14);
            btnRun.FlatStyle = FlatStyle.Flat;
            btnRun.ForeColor = Color.White;
            btnRun.FlatAppearance.BorderSize = 0;
            btnRun.Font = fUi;
            panelBottom.Controls.Add(btnRun);

            btnStop = new Button();
            btnStop.Text = "停止";
            btnStop.Size = new Size(58, 28);
            btnStop.Location = new Point(690, 14);
            btnStop.FlatStyle = FlatStyle.Flat;
            btnStop.ForeColor = Color.White;
            btnStop.Enabled = false;
            btnStop.FlatAppearance.BorderSize = 0;
            btnStop.Font = fUi;
            panelBottom.Controls.Add(btnStop);

            btnClear = new Button();
            btnClear.Text = "清空";
            btnClear.Size = new Size(58, 24);
            btnClear.Location = new Point(600, 52);
            btnClear.FlatStyle = FlatStyle.Flat;
            btnClear.FlatAppearance.BorderSize = 0;
            btnClear.Font = fSmall;
            panelBottom.Controls.Add(btnClear);

            btnSave = new Button();
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
            Button b = new Button();
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
            b.Click += delegate(object s, EventArgs e) { Execute(((Button)s).Tag.ToString()); };
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
            dlgSettings.ClientSize = new Size(452, 372);
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
            lblPreview.Text = "示例　安卓调试台 AaBb 0123　adb devices -l";
            lblPreview.Location = new Point(16, 244);
            lblPreview.Size = new Size(420, 46);
            lblPreview.TextAlign = ContentAlignment.MiddleLeft;
            lblPreview.Padding = new Padding(10, 0, 0, 0);
            lblPreview.BorderStyle = BorderStyle.FixedSingle;
            dlgSettings.Controls.Add(lblPreview);

            Button btnClose = AddDlgButton("关闭", 348, 320, 88, 32);
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
            btnClear.BackColor = t.Btn;
            btnClear.ForeColor = t.Text;
            btnSave.BackColor = t.Btn;
            btnSave.ForeColor = t.Text;

            if (btnSettings != null)
            {
                btnSettings.BackColor = t.Btn;
                btnSettings.ForeColor = t.Text;
                btnSettings.FlatAppearance.BorderColor = t.Border;
            }

            foreach (Button b in menuButtons)
            {
                b.BackColor = t.Btn;
                b.ForeColor = t.Text;
                b.FlatAppearance.BorderColor = t.Border;
            }
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
        private static void AddOut(string text, Role role)
        {
            if (rt == null) { return; }
            if (rt.InvokeRequired)
            {
                try { rt.BeginInvoke(new Action<string, Role>(AddOut), new object[] { text, role }); }
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
            lblStatus.Text = statusText;
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
