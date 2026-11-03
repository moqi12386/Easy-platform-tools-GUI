package com.easypt.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Easy-platform-tools-GUI · Android 版。
 * 三种模式：OTG 主机（刷别人）/ 本机 root（管自己）/ 遥控 PC，功能对齐桌面版。
 */
public class MainActivity extends Activity {

    private static final int MODE_OTG = 0, MODE_LOCAL = 1, MODE_REMOTE = 2;
    private static final int REQ_IMAGE = 1001, REQ_ROM = 1002, REQ_ROM_DIR = 1003, REQ_WALL = 1004;
    private static final String ACTION_USB_PERM = "com.easypt.gui.USB_PERMISSION";
    private static final String VER = "2.5.0";

    /** 边框预设色（默认第 0 个之外，默认用彩虹） */
    private static final int[] BD_PRESETS = {
            0xFFFFFFFF, 0xFF11151C, 0xFF4C9AFF, 0xFF3DDC97, 0xFFFFC53D, 0xFFA66CFF, 0xFFFF4D6D
    };

    /** 8 套背景色，和桌面版一致 */
    private static final int[] BG = {
            0xFF0E1117, 0xFF000000, 0xFF1F2024, 0xFF0B1E2D,
            0xFF0F221A, 0xFF25131A, 0xFFF3F4F6, 0xFFF6F1E4
    };

    private int mode = MODE_OTG;
    private int themeIdx = 0;
    private float scale = 1.0f;
    private String lang = "zh";
    private int btnAlpha = 90;
    private boolean borderOn = true, borderRain = true;
    private int borderColor = 0xFFFFFFFF, borderDp = 2, borderAlphaPct = 100;
    private int backdrop = 0;    // 壁纸平均色（无壁纸时 = 主题底色）
    private android.graphics.Bitmap wallBmp = null;
    private final java.util.HashMap<Button, Integer> themed = new java.util.HashMap<Button, Integer>();

    private int cBg, cPanel, cText, cDim, cAccent, cOk, cErr, cBtn, cRun, cInput, cOut;

    private SharedPreferences store;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private FastbootUsb fb;
    private UsbDevice pendingDevice;
    private String pendingPart = "boot";

    private TextView logView, statusView;
    private Spinner modeSpinner;
    private GridLayout grid;
    private EditText input;
    private SpannableStringBuilder logBuf = new SpannableStringBuilder();

    private final List<String[]> lastParts = new ArrayList<String[]>();
    private final List<String> history = new ArrayList<String>();
    private final boolean[] bakPick = { true, true, true, true, true, false, false, false };
    private static final String[] BAK_PARTS = { "modem", "modemst1", "modemst2", "fsg", "fsc", "persist", "bluetooth", "dsp" };

    private String T(String zh, String en) { return "en".equals(lang) ? en : zh; }

    // ---------------- 生命周期 ----------------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = getSharedPreferences("easypT", MODE_PRIVATE);
        mode = store.getInt("mode", -1);
        themeIdx = store.getInt("theme", 0);
        scale = store.getFloat("scale", 1.0f);
        lang = store.getString("lang", "zh");
        btnAlpha = store.getInt("alpha", 90);
        borderOn = store.getBoolean("bOn", true);
        borderRain = store.getBoolean("bRain", true);
        borderColor = store.getInt("bCol", 0xFFFFFFFF);
        borderDp = store.getInt("bDp", 2);
        borderAlphaPct = store.getInt("bA", 100);
        pendingPart = store.getString("part", "boot");
        applyColors();

        enableImmersive();
        buildUi();
        registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERM));
        fb = new FastbootUsb(new FastbootUsb.Logger() {
            public void log(String line) { append(line, guessColor(line)); }
        });

        if (mode < 0) { chooseMode(true); } else { applyMode(mode); }
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(usbReceiver); } catch (Exception ignored) { }
        if (fb != null) { fb.close(); }
        super.onDestroy();
    }

    // ---------------- 配色 ----------------

    private void applyColors() {
        int bg = BG[Math.max(0, Math.min(BG.length - 1, themeIdx))];
        double lum = 0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg);
        boolean dark = lum < 128;
        cBg = bg;
        cPanel = blend(bg, Color.WHITE, 0.07);
        cOut = blend(bg, Color.BLACK, 0.35);
        cInput = blend(bg, Color.BLACK, 0.25);
        cBtn = dark ? blend(bg, Color.WHITE, 0.13) : blend(bg, Color.BLACK, 0.09);
        if (!dark) { cPanel = blend(bg, Color.BLACK, 0.05); cInput = blend(bg, Color.WHITE, 0.8); cOut = blend(bg, Color.WHITE, 0.6); }
        cText = dark ? 0xFFD6DBE4 : 0xFF1C2028;
        cDim = dark ? 0xFF848C9A : 0xFF6C7482;
        cAccent = dark ? 0xFF7AB2FF : 0xFF1A58BE;
        cOk = dark ? 0xFF7ED6A0 : 0xFF12764A;
        cErr = dark ? 0xFFF08C8C : 0xFFAF2828;
        cRun = dark ? 0xFF2E5C96 : 0xFF2663C6;
    }

    private static int blend(int a, int b, double f) {
        int r = (int) Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f);
        int g = (int) Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f);
        int bl = (int) Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f);
        return Color.rgb(r, g, bl);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    /** 让内容画到状态栏/导航栏下面，并按 insets 留出安全边距 */
    private void enableImmersive() {
        android.view.Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    /** 深色状态栏图标与否：亮背景要黑图标 */
    private void applyStatusBarIcons(boolean darkIcons) {
        android.view.View dv = getWindow().getDecorView();
        int flags = dv.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.setSystemBarsAppearance(darkIcons ? android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
            return;
        }
        if (darkIcons) { flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; }
        else { flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; }
        dv.setSystemUiVisibility(flags);
    }

    private int blendOver(int fg, int alphaPct, int bg) {
        float a = Math.max(0, Math.min(100, alphaPct)) / 100f;
        return Color.rgb(
                (int) (Color.red(fg) * a + Color.red(bg) * (1 - a)),
                (int) (Color.green(fg) * a + Color.green(bg) * (1 - a)),
                (int) (Color.blue(fg) * a + Color.blue(bg) * (1 - a)));
    }

    /** 按背景亮度选黑字或白字 */
    private boolean isLight(int color) {
        return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) > 145;
    }

    private int textOn(int bgColor) {
        double l = relLum(bgColor);
        double withWhite = 1.05 / (l + 0.05);
        double withBlack = (l + 0.05) / 0.05;
        return withBlack >= withWhite ? 0xFF10131A : 0xFFF4F7FB;
    }

    private double relLum(int c) {
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c));
    }

    private double ch(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** 取壁纸上某个矩形区域的平均色（按钮背后真实的底色） */
    private int averageRegion(android.graphics.Bitmap b, int x, int y, int w, int h) {
        if (b == null) { return cOut; }
        int x0 = Math.max(0, Math.min(b.getWidth() - 1, x));
        int y0 = Math.max(0, Math.min(b.getHeight() - 1, y));
        int x1 = Math.max(x0 + 1, Math.min(b.getWidth(), x + w));
        int y1 = Math.max(y0 + 1, Math.min(b.getHeight(), y + h));
        long r = 0, g = 0, bl = 0;
        int n = 0;
        int stepX = Math.max(1, (x1 - x0) / 12), stepY = Math.max(1, (y1 - y0) / 12);
        for (int yy = y0; yy < y1; yy += stepY) {
            for (int xx = x0; xx < x1; xx += stepX) {
                int p = b.getPixel(xx, yy);
                r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++;
            }
        }
        if (n == 0) { return cOut; }
        return Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
    }

    /** 按每个按钮实际所处位置的底色，重算黑白字（拖动透明度时实时调用） */
    private void refreshButtonColors() {
        if (themed.isEmpty()) { return; }
        java.util.Iterator<Button> it = themed.keySet().iterator();
        while (it.hasNext()) { Button b = it.next(); if (b.getParent() == null) { it.remove(); } }
        for (Button b : themed.keySet()) {
            int baseColor = themed.get(b);
            int local = backdrop;
            if (wallBmp != null) {
                int[] loc = new int[2];
                b.getLocationOnScreen(loc);
                local = averageRegion(wallBmp, loc[0], loc[1], b.getWidth(), b.getHeight());
            }
            b.setBackground(pill(baseColor, 10));
            b.setTextColor(textOn(blendOver(baseColor, btnAlpha, local)));
        }
    }

    private View findRoot() {
        return getWindow().getDecorView();
    }
    private int averageColor(android.graphics.Bitmap b) {
        if (b == null) { return cOut; }
        long r = 0, g = 0, bl = 0;
        int n = 0;
        int sx = Math.max(1, b.getWidth() / 40), sy = Math.max(1, b.getHeight() / 40);
        for (int y = 0; y < b.getHeight(); y += sy) {
            for (int x = 0; x < b.getWidth(); x += sx) {
                int p = b.getPixel(x, y);
                r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++;
            }
        }
        if (n == 0) { return cOut; }
        return Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
    }

    private boolean hasWall() {
        String wp = store.getString("wall", "");
        return wp.length() > 0 && new File(wp).exists();
    }

    // ---------------- UI ----------------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        String wp = store.getString("wall", "");
        wallBmp = wp.length() > 0 ? fitToScreen(android.graphics.BitmapFactory.decodeFile(wp)) : null;
        if (wallBmp != null) {
            root.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), wallBmp));
            backdrop = averageColor(wallBmp);
        } else {
            root.setBackgroundColor(cBg);
            backdrop = cOut;
        }
        applyStatusBarIcons(isLight(backdrop));
        root.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView title = new TextView(this);
        title.setText("Easy-platform-tools");
        title.setTextColor(cText);
        title.setTextSize(18 * scale);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(6));

        modeSpinner = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{
                T("OTG 主机：刷别人", "OTG host: flash another"),
                T("本机 root：管自己", "Local root: manage itself"),
                T("遥控 PC", "Remote PC")
        });
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(ad);
        modeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (mode != pos) { applyMode(pos); }
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        row.addView(modeSpinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button settings = smallBtn(T("设置", "Settings"));
        settings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSettings(); }
        });
        row.addView(settings, new LinearLayout.LayoutParams(dp(96), dp(44)));
        root.addView(row);

        statusView = new TextView(this);
        statusView.setTextColor(cDim);
        statusView.setTextSize(12 * scale);
        root.addView(statusView);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp(8);
        slp.bottomMargin = dp(4);
        scroll.setLayoutParams(slp);
        scroll.setBackgroundColor(Color.argb(hasWall() ? Math.min(235, Math.max(150, btnAlpha)) : 255, Color.red(cOut), Color.green(cOut), Color.blue(cOut)));

        logView = new TextView(this);
        logView.setTextColor(cText);
        logView.setTextSize(11 * scale);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(dp(8), dp(6), dp(8), dp(6));
        logView.setTextIsSelectable(true);
        scroll.addView(logView);
        root.addView(scroll);

        ScrollView gridScroll = new ScrollView(this);
        int gridH = (int) (getResources().getDisplayMetrics().heightPixels * 0.40);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, gridH);
        glp.topMargin = dp(6);
        gridScroll.setLayoutParams(glp);

        grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        gridScroll.addView(grid);
        root.addView(gridScroll);

        LinearLayout tools = new LinearLayout(this);
        tools.setPadding(0, dp(8), 0, 0);
        final LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        tlp.setMargins(dp(4), 0, dp(4), 0);
        tools.addView(smallBtn(T("清空", "Clear"), new View.OnClickListener() {
            public void onClick(View v) { logBuf = new SpannableStringBuilder(); logView.setText(""); }
        }), tlp);
        tools.addView(smallBtn(T("存日志", "Save log"), new View.OnClickListener() {
            public void onClick(View v) { saveLog(); }
        }), tlp);
        tools.addView(smallBtn(T("历史", "History"), new View.OnClickListener() {
            public void onClick(View v) { showHistory(); }
        }), tlp);
        tools.addView(smallBtn(T("关于", "About"), new View.OnClickListener() {
            public void onClick(View v) { showAbout(); }
        }), tlp);
        root.addView(tools);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setPadding(0, dp(6), 0, 0);

        input = new EditText(this);
        input.setHint(T("输入命令", "Type a command"));
        input.setHintTextColor(cDim);
        input.setTextColor(cText);
        input.setTextSize(14 * scale);
        input.setSingleLine(true);
        input.setBackgroundColor(cInput);
        input.setMinHeight(dp(46));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        ilp.setMargins(dp(4), 0, dp(6), 0);
        inputRow.addView(input, ilp);

        Button runBtn = smallBtn(T("执行", "Run"));
        themed.put(runBtn, cRun);
        runBtn.setBackground(pill(cRun, 10));
        runBtn.setTextColor(textOn(blendOver(cRun, btnAlpha, backdrop)));
        runBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(84), dp(48)));
        runBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { runTyped(); }
        });
        inputRow.addView(runBtn);
        root.addView(inputRow);

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                int top, bottom, left, right;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets i = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                    top = i.top; bottom = i.bottom; left = i.left; right = i.right;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                    left = insets.getSystemWindowInsetLeft();
                    right = insets.getSystemWindowInsetRight();
                }
                v.setPadding(dp(12) + left, dp(10) + top, dp(12) + right, dp(10) + bottom);
                return insets;
            }
        });
        setContentView(root);
        root.requestApplyInsets();
        final View rootRef = root;
        rootRef.post(new Runnable() { public void run() { refreshButtonColors(); } });
    }

    private Button smallBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13 * scale);
        themed.put(b, cBtn);
        b.setTextColor(textOn(blendOver(cBtn, btnAlpha, backdrop)));
        b.setBackground(pill(cBtn, 10));
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        b.setMinHeight(dp(46));
        b.setMinimumHeight(dp(46));
        b.setGravity(Gravity.CENTER);
        return b;
    }

    /** 圆角 + 可调不透明的按钮底，外挂一圈自定义边框 */
    private PillDrawable pill(int baseColor, int radiusDp) {
        return pill(baseColor, radiusDp, false);
    }

    /** plain=true：给设置面板里的小控件用，不带边框 */
    private PillDrawable pill(int baseColor, int radiusDp, boolean plain) {
        int a = Math.max(0, Math.min(100, btnAlpha)) * 255 / 100;
        PillDrawable g = new PillDrawable(
                Color.argb(a, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),
                dp(radiusDp));
        if (plain) {
            g.border(false, false, 0, 0, 0);
        } else {
            g.border(borderOn, borderRain, borderColor, dp(borderDp), borderAlphaPct);
        }
        return g;
    }

    /** 真实屏幕分辨率（含状态栏/导航栏） */
    private int[] screenSize() {
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowMetrics wm = getWindowManager().getMaximumWindowMetrics();
            android.graphics.Rect r = wm.getBounds();
            return new int[]{ r.width(), r.height() };
        }
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        return new int[]{ dm.widthPixels, dm.heightPixels };
    }

    private Button smallBtn(String text, View.OnClickListener l) {
        Button b = smallBtn(text);
        b.setOnClickListener(l);
        return b;
    }

    private void act(String label, final Runnable r) {
        Button b = smallBtn(label);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { r.run(); }
        });
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(48);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        b.setLayoutParams(lp);
        grid.addView(b);
    }

    private void applyMode(int m) {
        mode = m;
        store.edit().putInt("mode", m).apply();
        if (modeSpinner.getSelectedItemPosition() != m) { modeSpinner.setSelection(m); }
        grid.removeAllViews();
        lastParts.clear();

        if (m == MODE_OTG) {
            statusView.setText(T("OTG：用 OTG 线连接另一台手机，让它进入 fastboot", "OTG: connect another phone in fastboot"));
            act(T("连接设备", "Connect"), new Runnable() { public void run() { connectUsb(); } });
            act(T("设备信息", "Device info"), new Runnable() { public void run() { otgInfo(); } });
            act(T("读分区表", "Partitions"), new Runnable() { public void run() { otgPartitions(); } });
            act(T("刷入镜像", "Flash image"), new Runnable() { public void run() { pickImage(); } });
            act(T("线刷 ROM", "Flash ROM"), new Runnable() { public void run() { pickRom(); } });
            act(T("擦除分区", "Erase"), new Runnable() { public void run() { otgErase(); } });
            act(T("重启系统", "Reboot"), new Runnable() { public void run() { otgReboot(""); } });
            act(T("重启 Recovery", "Reboot Recovery"), new Runnable() { public void run() { otgReboot("recovery"); } });
            act(T("重启 Bootloader", "Reboot Bootloader"), new Runnable() { public void run() { otgReboot("bootloader"); } });
            act(T("进入 EDL", "Enter EDL"), new Runnable() { public void run() { otgEdl(); } });
        } else if (m == MODE_LOCAL) {
            statusView.setText(T("本机：需要 root（Magisk / 内核 root）", "Local: requires root"));
            act(T("检测 root", "Check root"), new Runnable() { public void run() { checkRoot(); } });
            act(T("读分区表", "Partitions"), new Runnable() { public void run() { localPartitions(); } });
            act(T("备份基带", "Backup modem"), new Runnable() { public void run() { backupDialog(); } });
            act(T("刷入镜像", "Flash image"), new Runnable() { public void run() { pickImage(); } });
            act(T("线刷 ROM", "Flash ROM"), new Runnable() { public void run() { pickRom(); } });
            act(T("重启系统", "Reboot"), new Runnable() { public void run() { localReboot(""); } });
            act(T("重启 Recovery", "Reboot Recovery"), new Runnable() { public void run() { localReboot("recovery"); } });
            act(T("重启 EDL", "Reboot EDL"), new Runnable() { public void run() { localReboot("edl"); } });
        } else {
            statusView.setText(T("遥控：手机和电脑同一局域网，电脑上启动「遥控服务」", "Remote: same Wi-Fi, start the service on the PC"));
            act(T("测试连接", "Test link"), new Runnable() { public void run() { remoteStatus(); } });
            act(T("读分区表", "Partitions"), new Runnable() { public void run() { remotePartitions(); } });
            act(T("刷入镜像", "Flash image"), new Runnable() { public void run() { pickImage(); } });
            act(T("重启系统", "Reboot"), new Runnable() { public void run() { remoteReboot(""); } });
            act(T("重启 Recovery", "Reboot Recovery"), new Runnable() { public void run() { remoteReboot("recovery"); } });
            act(T("重启 Bootloader", "Reboot Bootloader"), new Runnable() { public void run() { remoteReboot("bootloader"); } });
        }
        if (grid != null) { grid.post(new Runnable() { public void run() { refreshButtonColors(); } }); }
    }

    // ---------------- 日志 ----------------

    private int guessColor(String line) {
        if (line == null) { return cText; }
        String l = line.toLowerCase(Locale.US);
        if (l.contains("fail") || l.contains("失败") || l.contains("错误")) { return cErr; }
        if (l.startsWith("ok") || l.contains("成功") || l.contains("完成")) { return cOk; }
        if (line.startsWith(">")) { return cAccent; }
        return cText;
    }

    private void append(final String line, final int color) {
        ui.post(new Runnable() {
            public void run() {
                String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                int start = logBuf.length();
                logBuf.append("[" + ts + "] " + line + "\n");
                logBuf.setSpan(new ForegroundColorSpan(color), start, logBuf.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                logView.setText(logBuf);
                if (logBuf.length() > 100000) {
                    logBuf.delete(0, logBuf.length() - 60000);
                    logView.setText(logBuf);
                }
            }
        });
    }

    private void saveLog() {
        try {
            File dir = getExternalFilesDir("logs");
            if (dir == null) { dir = getFilesDir(); }
            dir.mkdirs();
            File f = new File(dir, "log-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt");
            FileOutputStream out = new FileOutputStream(f);
            out.write(logBuf.toString().getBytes("UTF-8"));
            out.close();
            Toast.makeText(this, f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            append(T("已保存：", "Saved: ") + f.getAbsolutePath(), cDim);
        } catch (Exception e) {
            append(T("保存失败：", "Save failed: ") + e.getMessage(), cErr);
        }
    }

    private void showHistory() {
        if (history.isEmpty()) { append(T("没有历史命令", "No history"), cDim); return; }
        final String[] arr = history.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(T("历史命令", "History"))
                .setItems(arr, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { input.setText(arr[w]); }
                }).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle(T("关于", "About"))
                .setMessage("Easy-platform-tools-GUI\nAndroid " + VER + "\n\n"
                        + T("三种模式：OTG 主机 / 本机 root / 遥控 PC\n开源许可 MIT",
                        "Three modes: OTG host / local root / remote PC\nLicense: MIT"))
                .setPositiveButton("OK", null).show();
    }

    // ---------------- 设置 ----------------

    private void openSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        box.addView(label(T("按键透明度", "Button opacity")));
        LinearLayout alphaRow = new LinearLayout(this);
        alphaRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView tvAlpha = new TextView(this);
        tvAlpha.setText(btnAlpha + "%");
        tvAlpha.setTextColor(cText);
        tvAlpha.setTextSize(13 * scale);
        tvAlpha.setWidth(dp(56));
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(100);
        sb.setProgress(btnAlpha);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean fromUser) {
                tvAlpha.setText(p + "%");
                if (!fromUser) { return; }
                btnAlpha = p;
                refreshButtonColors();
            }
            public void onStartTrackingTouch(android.widget.SeekBar s) { }
            public void onStopTrackingTouch(android.widget.SeekBar s) {
                btnAlpha = s.getProgress();
                store.edit().putInt("alpha", btnAlpha).apply();
            }
        });
        alphaRow.addView(tvAlpha);
        alphaRow.addView(sb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(alphaRow);

        box.addView(label(T("按键边框", "Button border")));
        final LinearLayout chipsWrap = new LinearLayout(this);
        chipsWrap.setOrientation(LinearLayout.VERTICAL);
        box.addView(chipsWrap);
        renderBorderChips(chipsWrap);

        LinearLayout bdRow = new LinearLayout(this);
        bdRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView tvBd = new TextView(this);
        tvBd.setText(borderDp + " dp");
        tvBd.setTextColor(cText);
        tvBd.setTextSize(13 * scale);
        tvBd.setWidth(dp(56));
        android.widget.SeekBar sbBd = new android.widget.SeekBar(this);
        sbBd.setMax(5);
        sbBd.setProgress(Math.max(0, Math.min(5, borderDp - 1)));
        sbBd.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean fromUser) {
                borderDp = p + 1;
                tvBd.setText(borderDp + " dp");
                if (!fromUser) { return; }
                refreshButtonColors();
            }
            public void onStartTrackingTouch(android.widget.SeekBar s) { }
            public void onStopTrackingTouch(android.widget.SeekBar s) { persistBorder(); }
        });
        bdRow.addView(tvBd);
        bdRow.addView(sbBd, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(bdRow);

        LinearLayout baRow = new LinearLayout(this);
        baRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView tvBa = new TextView(this);
        tvBa.setText(borderAlphaPct + "%");
        tvBa.setTextColor(cText);
        tvBa.setTextSize(13 * scale);
        tvBa.setWidth(dp(56));
        android.widget.SeekBar sbBa = new android.widget.SeekBar(this);
        sbBa.setMax(100);
        sbBa.setProgress(borderAlphaPct);
        sbBa.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean fromUser) {
                borderAlphaPct = p;
                tvBa.setText(p + "%");
                if (!fromUser) { return; }
                refreshButtonColors();
            }
            public void onStartTrackingTouch(android.widget.SeekBar s) { }
            public void onStopTrackingTouch(android.widget.SeekBar s) { persistBorder(); }
        });
        baRow.addView(tvBa);
        baRow.addView(sbBa, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(baRow);

        box.addView(label(T("背景配色", "Background")));
        LinearLayout swatches = new LinearLayout(this);
        for (int i = 0; i < BG.length; i++) {
            final int idx = i;
            Button sw = new Button(this);
            sw.setBackgroundColor(BG[i]);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            slp.setMargins(dp(2), dp(2), dp(2), dp(2));
            sw.setLayoutParams(slp);
            sw.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { themeIdx = idx; store.edit().putInt("theme", idx).apply(); recreate(); }
            });
            swatches.addView(sw);
        }
        box.addView(swatches);

        box.addView(label(T("字号", "Font size")));
        LinearLayout sizes = new LinearLayout(this);
        String[] names = { T("小", "Small"), T("标准", "Normal"), T("大", "Large") };
        float[] vals = { 0.9f, 1.0f, 1.15f };
        for (int i = 0; i < 3; i++) {
            final float v = vals[i];
            Button b = smallBtn(names[i], new View.OnClickListener() {
                public void onClick(View x) { store.edit().putFloat("scale", v).apply(); recreate(); }
            });
            sizes.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        box.addView(sizes);

        box.addView(label(T("语言", "Language")));
        LinearLayout langs = new LinearLayout(this);
        Button zh = smallBtn("中文", new View.OnClickListener() {
            public void onClick(View v) { store.edit().putString("lang", "zh").apply(); recreate(); }
        });
        Button en = smallBtn("English", new View.OnClickListener() {
            public void onClick(View v) { store.edit().putString("lang", "en").apply(); recreate(); }
        });
        langs.addView(zh, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        langs.addView(en, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(langs);

        box.addView(label(T("背景图", "Wallpaper")));
        LinearLayout wallRow = new LinearLayout(this);
        Button pickWall = smallBtn(T("选择图片…", "Choose image…"), new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i, REQ_WALL);
            }
        });
        Button clearWall = smallBtn(T("清除", "Clear"), new View.OnClickListener() {
            public void onClick(View v) {
                store.edit().putString("wall", "").apply();
                File f = new File(getFilesDir(), "wall.jpg");
                if (f.exists()) { f.delete(); }
                recreate();
            }
        });
        wallRow.addView(pickWall, new LinearLayout.LayoutParams(0, dp(46), 2f));
        wallRow.addView(clearWall, new LinearLayout.LayoutParams(0, dp(46), 1f));
        box.addView(wallRow);

        box.addView(label(T("遥控 PC", "Remote PC")));
        final EditText host = new EditText(this);
        host.setHint("192.168.1.10:8765");
        host.setText(store.getString("host", ""));
        box.addView(host);

        final EditText token = new EditText(this);
        token.setHint(T("6 位配对码", "6-digit pairing code"));
        token.setText(store.getString("token", ""));
        box.addView(token);

        final EditText part = new EditText(this);
        part.setHint(T("默认分区 boot", "Default partition"));
        part.setText(store.getString("part", "boot"));
        box.addView(part);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        new AlertDialog.Builder(this)
                .setTitle(T("设置", "Settings"))
                .setView(sv)
                .setPositiveButton(T("保存", "Save"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        store.edit()
                                .putString("host", host.getText().toString().trim())
                                .putString("token", token.getText().toString().trim())
                                .putString("part", part.getText().toString().trim())
                                .apply();
                        append(T("设置已保存", "Settings saved"), cDim);
                    }
                })
                .setNeutralButton(T("切换模式", "Switch mode"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { chooseMode(false); }
                })
                .setNegativeButton(T("取消", "Cancel"), null)
                .show();
    }

    private void persistBorder() {
        store.edit()
                .putBoolean("bOn", borderOn)
                .putBoolean("bRain", borderRain)
                .putInt("bCol", borderColor)
                .putInt("bDp", borderDp)
                .putInt("bA", borderAlphaPct)
                .apply();
    }

    private void setBorder(boolean on, boolean rain, int color, LinearLayout wrap) {
        borderOn = on;
        borderRain = rain;
        borderColor = color;
        persistBorder();
        refreshButtonColors();
        renderBorderChips(wrap);
    }

    /** 边框配色小方块：彩虹 / 7 个预设 / 自定义 / 无 */
    private void renderBorderChips(final LinearLayout wrap) {
        wrap.removeAllViews();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(2));

        android.graphics.drawable.GradientDrawable rainbow =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        PillDrawable.RAINBOW);
        rainbow.setCornerRadius(dp(10));
        row.addView(chip(rainbow, borderOn && borderRain, new View.OnClickListener() {
            public void onClick(View v) { setBorder(true, true, borderColor, wrap); }
        }));

        for (int i = 0; i < BD_PRESETS.length; i++) {
            final int c = BD_PRESETS[i];
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            g.setColor(c);
            g.setCornerRadius(dp(10));
            row.addView(chip(g, borderOn && !borderRain && borderColor == c, new View.OnClickListener() {
                public void onClick(View v) { setBorder(true, false, c, wrap); }
            }));
        }

        // 当前自定义色（和预设不重复时才显示这一格）
        if (!isPreset(borderColor)) {
            android.graphics.drawable.GradientDrawable cg = new android.graphics.drawable.GradientDrawable();
            cg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            cg.setColor(borderColor);
            cg.setCornerRadius(dp(10));
            row.addView(chip(cg, borderOn && !borderRain, new View.OnClickListener() {
                public void onClick(View v) { setBorder(true, false, borderColor, wrap); }
            }));
        }

        Button custom = smallBtn(T("自定义…", "Custom…"));
        themed.remove(custom);
        custom.setBackground(pill(cBtn, 10, true));
        custom.setLayoutParams(new LinearLayout.LayoutParams(dp(92), dp(40)));
        custom.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { customBorderDialog(wrap); }
        });
        row.addView(custom);

        Button none = smallBtn(T("无", "None"));
        themed.remove(none);
        none.setBackground(pill(cBtn, 10, true));
        none.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(40)));
        none.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setBorder(false, borderRain, borderColor, wrap); }
        });
        row.addView(none);

        hs.addView(row);
        wrap.addView(hs);
    }

    private boolean isPreset(int c) {
        for (int p : BD_PRESETS) { if ((p | 0xFF000000) == (c | 0xFF000000)) { return true; } }
        return false;
    }

    private View chip(android.graphics.drawable.Drawable inner, boolean selected, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38));
        lp.setMargins(dp(3), 0, dp(3), 0);
        f.setLayoutParams(lp);
        f.setPadding(dp(3), dp(3), dp(3), dp(3));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(12));
        bg.setColor(Color.TRANSPARENT);
        if (selected) {
            bg.setStroke(dp(2), isLight(backdrop) ? 0xFF11151C : 0xFFFFFFFF);
        }
        f.setBackground(bg);
        View v = new View(this);
        v.setBackground(inner);
        f.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        f.setOnClickListener(l);
        return f;
    }

    /** 自定义边框色：RGB 三根滑杆 + 实时预览 */
    private void customBorderDialog(final LinearLayout wrap) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(10), dp(16), dp(6));

        final int[] rgb = { Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor) };
        final View prev = new View(this);
        prev.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        plp.bottomMargin = dp(8);
        box.addView(prev, plp);

        final String[] names = { "R", "G", "B" };
        final TextView[] vals = new TextView[3];
        final TextView hex = new TextView(this);
        hex.setTextColor(cDim);
        hex.setTextSize(12 * scale);
        hex.setPadding(0, dp(6), 0, 0);
        for (int i = 0; i < 3; i++) {
            final int k = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(this);
            name.setText(names[i]);
            name.setTextColor(cText);
            name.setTextSize(13 * scale);
            name.setWidth(dp(24));
            vals[i] = new TextView(this);
            vals[i].setText("" + rgb[i]);
            vals[i].setTextColor(cDim);
            vals[i].setTextSize(12 * scale);
            vals[i].setWidth(dp(42));
            vals[i].setGravity(Gravity.END);
            android.widget.SeekBar sb = new android.widget.SeekBar(this);
            sb.setMax(255);
            sb.setProgress(rgb[i]);
            sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(android.widget.SeekBar s, int p, boolean fromUser) {
                    rgb[k] = p;
                    vals[k].setText("" + p);
                    prev.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
                    hex.setText(String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]));
                }
                public void onStartTrackingTouch(android.widget.SeekBar s) { }
                public void onStopTrackingTouch(android.widget.SeekBar s) { }
            });
            row.addView(name);
            row.addView(sb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(vals[i]);
            box.addView(row);
        }
        hex.setText(String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]));
        box.addView(hex);

        new AlertDialog.Builder(this)
                .setTitle(T("边框颜色", "Border color"))
                .setView(box)
                .setPositiveButton(T("确定", "OK"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        setBorder(true, false, Color.rgb(rgb[0], rgb[1], rgb[2]), wrap);
                    }
                })
                .setNegativeButton(T("取消", "Cancel"), null)
                .show();
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13 * scale);
        t.setTextColor(cText);
        t.setPadding(0, dp(10), 0, dp(2));
        return t;
    }

    private void chooseMode(final boolean first) {
        String[] items = {
                T("OTG 主机：用 OTG 线刷另一台手机", "OTG host: flash another phone"),
                T("本机 root：备份/刷写自己", "Local root: manage this phone"),
                T("遥控 PC：指挥电脑上的桌面版", "Remote: drive the desktop app")
        };
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(T("选择使用模式", "Choose mode"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) { applyMode(which); }
                });
        b.setCancelable(!first);
        b.show();
    }

    // ---------------- 执行 ----------------

    private void runBg(final Runnable r) {
        new Thread(new Runnable() {
            public void run() {
                try { r.run(); } catch (final Exception e) { append(T("异常：", "Error: ") + e, cErr); }
            }
        }).start();
    }

    private RemoteClient remote() {
        return new RemoteClient(store.getString("host", ""), store.getString("token", ""));
    }

    // ---------------- OTG ----------------

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context ctx, Intent intent) {
            if (!ACTION_USB_PERM.equals(intent.getAction())) { return; }
            UsbDevice d = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && d != null) {
                pendingDevice = d;
                runBg(new Runnable() { public void run() { fb.open(MainActivity.this, pendingDevice); } });
            } else {
                append(T("USB 权限被拒绝", "USB permission denied"), cErr);
            }
        }
    };

    private void connectUsb() {
        UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDevice d = FastbootUsb.findFastbootDevice(um);
        if (d == null) {
            append(T("没找到 fastboot 设备：检查 OTG 线和对方是否在 fastboot 界面",
                    "No fastboot device: check the OTG cable and the other phone's fastboot screen"), cErr);
            return;
        }
        if (!um.hasPermission(d)) {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) { flags |= PendingIntent.FLAG_IMMUTABLE; }
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERM), flags);
            um.requestPermission(d, pi);
            append(T("等待 USB 授权…", "Waiting for USB permission…"), cDim);
            return;
        }
        final UsbDevice dev = d;
        runBg(new Runnable() { public void run() { fb.open(MainActivity.this, dev); } });
    }

    private void otgInfo() {
        runBg(new Runnable() {
            public void run() {
                if (!fb.isOpen()) { append(T("请先连接设备", "Connect first"), cErr); return; }
                append("product: " + fb.getvar("product"), cText);
                append("version: " + fb.getvar("version"), cText);
                append("serialno: " + fb.getvar("serialno"), cText);
            }
        });
    }

    private void otgPartitions() {
        runBg(new Runnable() {
            public void run() {
                if (!fb.isOpen()) { append(T("请先连接设备", "Connect first"), cErr); return; }
                List<String[]> parts = fb.getPartitions();
                lastParts.clear();
                lastParts.addAll(parts);
                showPartitions(parts, "fastboot getvar all");
            }
        });
    }

    private void otgEdl() {
        runBg(new Runnable() {
            public void run() {
                if (!fb.isOpen()) { append(T("请先连接设备", "Connect first"), cErr); return; }
                boolean ok = fb.oem("edl");
                append(ok ? T("EDL 已请求", "EDL requested") : T("EDL 失败（可能未解锁）", "EDL failed (bootloader locked?)"), ok ? cOk : cErr);
            }
        });
    }

    private void otgErase() {
        final EditText et = new EditText(this);
        et.setText(pendingPart);
        new AlertDialog.Builder(this).setTitle(T("擦除分区", "Erase partition")).setView(et)
                .setPositiveButton(T("擦除", "Erase"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        final String p = et.getText().toString().trim();
                        runBg(new Runnable() { public void run() { fb.erase(p); } });
                    }
                }).setNegativeButton(T("取消", "Cancel"), null).show();
    }

    private void otgReboot(final String target) {
        runBg(new Runnable() {
            public void run() {
                if (!fb.isOpen()) { append(T("请先连接设备", "Connect first"), cErr); return; }
                boolean ok = fb.reboot(target);
                append(ok ? T("已发送重启指令", "Reboot sent") : T("重启失败", "Reboot failed"), ok ? cOk : cErr);
            }
        });
    }

    // ---------------- 本机 root ----------------

    private void checkRoot() {
        runBg(new Runnable() {
            public void run() {
                boolean ok = RootShell.hasRoot();
                append(ok ? T("root 可用", "root available") : T("没有 root，本机模式不可用", "No root, local mode unavailable"), ok ? cOk : cErr);
            }
        });
    }

    private void localPartitions() {
        runBg(new Runnable() {
            public void run() {
                List<String[]> parts = RootShell.partitions();
                lastParts.clear();
                lastParts.addAll(parts);
                showPartitions(parts, "/dev/block/by-name");
            }
        });
    }

    private void localReboot(final String target) {
        runBg(new Runnable() {
            public void run() {
                RootShell.Result r = RootShell.reboot(target);
                append(r.ok ? (T("已请求重启：", "Reboot requested: ") + (target.length() == 0 ? "system" : target))
                        : (T("重启失败：", "Reboot failed: ") + r.out), r.ok ? cOk : cErr);
            }
        });
    }

    private void backupDialog() {
        new AlertDialog.Builder(this)
                .setTitle(T("备份基带", "Backup modem"))
                .setMultiChoiceItems(BAK_PARTS, bakPick, new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface d, int which, boolean checked) { bakPick[which] = checked; }
                })
                .setPositiveButton(T("开始", "Start"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { doBackup(); }
                })
                .setNegativeButton(T("取消", "Cancel"), null)
                .show();
    }

    private void doBackup() {
        final List<String> picked = new ArrayList<String>();
        for (int i = 0; i < BAK_PARTS.length; i++) { if (bakPick[i]) { picked.add(BAK_PARTS[i]); } }
        if (picked.isEmpty()) { append(T("至少选一个分区", "Pick at least one partition"), cErr); return; }

        runBg(new Runnable() {
            public void run() {
                File dir = new File(getExternalFilesDir(null), "backup-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()));
                dir.mkdirs();
                append(T("开始备份：", "Backing up: ") + dir.getAbsolutePath(), cDim);
                for (String p : picked) {
                    File out = new File(dir, p + ".img");
                    RootShell.Result r = RootShell.backup(p, out);
                    if (out.exists() && out.length() > 0) {
                        append(p + "  " + String.format(Locale.US, "%.1f MB  ", out.length() / 1048576.0) + sha256(out), cOk);
                    } else {
                        append(p + "  " + T("失败", "failed") + " " + r.out, cErr);
                    }
                }
                append(T("备份完成：", "Backup done: ") + dir.getAbsolutePath(), cOk);
            }
        });
    }

    private String sha256(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) { md.update(buf, 0, n); }
            in.close();
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) { sb.append(String.format("%02x", b)); }
            return sb.substring(0, 16) + "…";
        } catch (Exception e) { return ""; }
    }

    // ---------------- 分区表 ----------------

    private void showPartitions(final List<String[]> parts, final String title) {
        ui.post(new Runnable() {
            public void run() {
                if (parts.isEmpty()) { append(T("没有读到分区", "No partitions found"), cErr); return; }
                StringBuilder sb = new StringBuilder();
                for (String[] p : parts) { sb.append(p[0]).append("  ").append(human(p[1])).append('\n'); }
                final String text = sb.toString();

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(title + "  (" + parts.size() + ")")
                        .setMessage(text)
                        .setPositiveButton(T("复制", "Copy"), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                cm.setPrimaryClip(ClipData.newPlainText("partitions", text));
                                append(T("已复制分区表", "Partitions copied"), cDim);
                            }
                        })
                        .setNeutralButton(T("导出 CSV", "Export CSV"), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { exportCsv(parts); }
                        })
                        .setNegativeButton(T("关闭", "Close"), null)
                        .show();
            }
        });
    }

    private void exportCsv(List<String[]> parts) {
        try {
            File dir = getExternalFilesDir("exports");
            if (dir == null) { dir = getFilesDir(); }
            dir.mkdirs();
            File f = new File(dir, "partitions-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".csv");
            StringBuilder sb = new StringBuilder("partition,size_bytes,size_human,type\n");
            for (String[] p : parts) {
                sb.append(p[0]).append(',').append(p[1]).append(',').append(human(p[1])).append(',').append(p[2]).append('\n');
            }
            FileOutputStream out = new FileOutputStream(f);
            out.write(sb.toString().getBytes("UTF-8"));
            out.close();
            append(T("已导出：", "Exported: ") + f.getAbsolutePath(), cOk);
        } catch (Exception e) {
            append(T("导出失败：", "Export failed: ") + e.getMessage(), cErr);
        }
    }

    private String human(String hex) {
        try {
            long v = hex.startsWith("0x") ? Long.parseLong(hex.substring(2), 16) : Long.parseLong(hex);
            if (v >= 1073741824L) { return String.format(Locale.US, "%.2f GB", v / 1073741824.0); }
            if (v >= 1048576L) { return String.format(Locale.US, "%.1f MB", v / 1048576.0); }
            if (v >= 1024L) { return String.format(Locale.US, "%.0f KB", v / 1024.0); }
            return v + " B";
        } catch (Exception e) { return "-"; }
    }

    // ---------------- 刷入镜像 ----------------

    private void pickImage() {
        final EditText et = new EditText(this);
        et.setText(pendingPart);
        new AlertDialog.Builder(this).setTitle(T("刷入到哪个分区", "Which partition"))
                .setView(et)
                .setPositiveButton(T("选镜像", "Pick image"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        pendingPart = et.getText().toString().trim();
                        store.edit().putString("part", pendingPart).apply();
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        startActivityForResult(i, REQ_IMAGE);
                    }
                })
                .setNegativeButton(T("取消", "Cancel"), null).show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) { return; }
        Uri uri = data.getData();

        if (req == REQ_IMAGE) {
            File f;
            try { f = copyToCache(uri, "flash.img"); }
            catch (Exception e) { append(T("读取失败：", "Read failed: ") + e.getMessage(), cErr); return; }

            final File img = f;
            append(T("已选择：", "Selected: ") + String.format(Locale.US, "%.1f MB", f.length() / 1048576.0) + " → " + pendingPart, cDim);
            new AlertDialog.Builder(this).setTitle(T("确认刷入", "Confirm flash"))
                    .setMessage(T("将写入分区 ", "Write to partition ") + pendingPart + "\n"
                            + String.format(Locale.US, "%.1f MB", f.length() / 1048576.0) + "\n\n"
                            + T("写错分区可能无法开机", "A wrong partition can brick the device"))
                    .setPositiveButton(T("刷入", "Flash"), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { doFlash(pendingPart, img); }
                    })
                    .setNegativeButton(T("取消", "Cancel"), null).show();
        } else if (req == REQ_ROM) {
            try { startRom(extractRom(copyToCache(uri, "rom.zip"))); }
            catch (Exception e) { append(T("解压失败：", "Extract failed: ") + e.getMessage(), cErr); }
        } else if (req == REQ_WALL) {
            try {
                android.graphics.Bitmap bmp = loadScaled(uri);
                showCrop(bmp);
            } catch (Exception e) { append(T("读取图片失败：", "Load image failed: ") + e.getMessage(), cErr); }
        } else if (req == REQ_ROM_DIR) {
            try { startRom(copyTree(uri)); }
            catch (Exception e) { append(T("读取目录失败：", "Read folder failed: ") + e.getMessage(), cErr); }
        }
    }

    // ---------------- 壁纸 ----------------

    /** 把任意比例的图按屏幕尺寸居中裁切，保证铺上去不变形 */
    private android.graphics.Bitmap fitToScreen(android.graphics.Bitmap src) {
        if (src == null) { return null; }
        int[] scr = screenSize();
        int w = scr[0], h = scr[1];
        if (src.getWidth() == w && src.getHeight() == h) { return src; }
        float sc = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        float dw = src.getWidth() * sc, dh = src.getHeight() * sc;
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(out);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.postScale(sc, sc);
        m.postTranslate((w - dw) / 2f, (h - dh) / 2f);
        cv.drawBitmap(src, m, new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        return out;
    }
    private android.graphics.Bitmap loadScaled(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeStream(in, null, o);
        in.close();
        int sample = 1;
        while (o.outWidth / sample > 2000 || o.outHeight / sample > 2000) { sample *= 2; }
        android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
        o2.inSampleSize = sample;
        in = getContentResolver().openInputStream(uri);
        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in, null, o2);
        in.close();
        return bmp;
    }

    private void showCrop(final android.graphics.Bitmap bmp) {
        // 裁剪框严格用屏幕比例的尺寸，避免拉伸
        int[] scr = screenSize();
        float aspect = (float) scr[0] / scr[1];

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int availW = (int) (dm.widthPixels * 0.92f);
        int availH = (int) (dm.heightPixels * 0.62f);
        int fw = availW;
        int fh = (int) (fw / aspect);
        if (fh > availH) { fh = availH; fw = (int) (fh * aspect); }

        final CropView cv = new CropView(this);
        cv.setBitmap(bmp);
        cv.setLayoutParams(new ViewGroup.LayoutParams(fw, fh));
        cv.setMinimumHeight(fh);

        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.setLayoutParams(new ViewGroup.LayoutParams(fw, fh));
        wrap.addView(cv, new android.widget.FrameLayout.LayoutParams(fw, fh));

        new AlertDialog.Builder(this)
                .setTitle(T("拖动 / 双指缩放，框内即壁纸", "Drag / pinch — the frame becomes the wallpaper"))
                .setView(wrap)
                .setPositiveButton(T("使用", "Use"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { saveWallpaper(cv.crop()); }
                })
                .setNegativeButton(T("取消", "Cancel"), null)
                .show();
    }

    private void saveWallpaper(android.graphics.Bitmap bmp) {
        if (bmp == null) { return; }
        try {
            File f = new File(getFilesDir(), "wall.jpg");
            FileOutputStream out = new FileOutputStream(f);
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out);
            out.close();
            store.edit().putString("wall", f.getAbsolutePath()).apply();
            append(T("壁纸已设置", "Wallpaper set"), cOk);
            recreate();
        } catch (Exception e) {
            append(T("保存壁纸失败：", "Save wallpaper failed: ") + e.getMessage(), cErr);
        }
    }
    private File copyToCache(Uri uri, String name) throws Exception {
        File f = new File(getCacheDir(), name);
        InputStream in = getContentResolver().openInputStream(uri);
        OutputStream out = new FileOutputStream(f);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); }
        out.close();
        in.close();
        return f;
    }

    private void doFlash(final String part, final File img) {
        runBg(new Runnable() {
            public void run() {
                if (mode == MODE_OTG) {
                    if (!fb.isOpen()) { append(T("请先连接设备", "Connect first"), cErr); return; }
                    fb.flashFile(part, img, null);
                } else if (mode == MODE_LOCAL) {
                    RootShell.Result r = RootShell.flash(part, img);
                    append(r.ok ? (T("刷入完成：", "Flashed: ") + part) : (T("刷入失败：", "Flash failed: ") + r.out), r.ok ? cOk : cErr);
                } else {
                    try { append(remote().flashFile(part, img), cText); }
                    catch (Exception e) { append(T("失败：", "Failed: ") + e.getMessage(), cErr); }
                }
            }
        });
    }

    // ---------------- 线刷 ROM ----------------

    private void pickRom() {
        String[] items = {
                T("选择 .zip 线刷包", "Pick a .zip ROM"),
                T("选择 .tgz 线刷包", "Pick a .tgz ROM"),
                T("选择已解压的目录", "Pick an unpacked folder")
        };
        new AlertDialog.Builder(this).setTitle(T("线刷 ROM", "Flash ROM"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (w == 2) {
                            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_ROM_DIR);
                        } else {
                            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.setType("*/*");
                            startActivityForResult(i, REQ_ROM);
                        }
                    }
                }).show();
    }

    private File extractRom(File archive) throws Exception {
        File root = new File(getCacheDir(), "rom");
        deleteRec(root);
        root.mkdirs();

        String name = archive.getName().toLowerCase(Locale.US);
        if (name.endsWith(".zip")) {
            ZipInputStream zin = new ZipInputStream(new FileInputStream(archive));
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                File out = new File(root, e.getName());
                if (e.isDirectory()) { out.mkdirs(); continue; }
                File parent = out.getParentFile();
                if (parent != null) { parent.mkdirs(); }
                FileOutputStream fos = new FileOutputStream(out);
                byte[] buf = new byte[65536];
                int n;
                while ((n = zin.read(buf)) > 0) { fos.write(buf, 0, n); }
                fos.close();
            }
            zin.close();
        } else {
            Process p = Runtime.getRuntime().exec(new String[]{ "tar", "-xzf", archive.getAbsolutePath(), "-C", root.getAbsolutePath() });
            p.waitFor();
        }

        File[] subs = root.listFiles();
        if (subs != null && subs.length == 1 && subs[0].isDirectory() && !new File(root, "images").exists()) {
            return subs[0];
        }
        return root;
    }

    private File copyTree(Uri tree) throws Exception {
        File root = new File(getCacheDir(), "rom");
        deleteRec(root);
        root.mkdirs();
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
        copyChildren(children, root);
        return root;
    }

    private void copyChildren(Uri children, File dest) throws Exception {
        Cursor c = getContentResolver().query(children,
                new String[]{ DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE }, null, null, null);
        if (c == null) { return; }
        while (c.moveToNext()) {
            String docId = c.getString(0);
            String name = c.getString(1);
            String mime = c.getString(2);
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                File d = new File(dest, name);
                d.mkdirs();
                Uri sub = DocumentsContract.buildChildDocumentsUriUsingTree(children, docId);
                copyChildren(sub, d);
            } else {
                Uri doc = DocumentsContract.buildDocumentUriUsingTree(children, docId);
                InputStream in = getContentResolver().openInputStream(doc);
                FileOutputStream out = new FileOutputStream(new File(dest, name));
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); }
                out.close();
                in.close();
            }
        }
        c.close();
    }

    private void deleteRec(File f) {
        if (f == null || !f.exists()) { return; }
        File[] kids = f.listFiles();
        if (kids != null) { for (File k : kids) { deleteRec(k); } }
        f.delete();
    }

    private void startRom(File dir) {
        File bat = null;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.US);
                if (n.startsWith("flash_all") && n.endsWith(".bat")) { bat = f; break; }
            }
        }

        List<String[]> tasks = new ArrayList<String[]>();
        if (bat != null) { tasks = parseScript(bat, dir); }
        if (tasks.isEmpty()) {
            File imgDir = new File(dir, "images");
            if (!imgDir.exists()) { imgDir = dir; }
            File[] imgs = imgDir.listFiles();
            if (imgs != null) {
                for (File f : imgs) {
                    String n = f.getName();
                    if (n.endsWith(".img")) {
                        tasks.add(new String[]{ "flash", n.substring(0, n.length() - 4), f.getAbsolutePath() });
                    }
                }
            }
        }
        if (tasks.isEmpty()) { append(T("没有找到可刷的镜像", "No images found"), cErr); return; }

        StringBuilder sb = new StringBuilder();
        for (String[] t : tasks) { sb.append(t[0]).append("  ").append(t[1]).append('\n'); }

        final List<String[]> finalTasks = tasks;
        new AlertDialog.Builder(this).setTitle(T("确认线刷 ", "Confirm flashing "))
                .setMessage(T("共 ", "Steps: ") + tasks.size() + "\n\n" + sb.toString())
                .setPositiveButton(T("开始", "Start"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { runRom(finalTasks); }
                })
                .setNegativeButton(T("取消", "Cancel"), null).show();
    }

    private List<String[]> parseScript(File bat, File root) {
        List<String[]> out = new ArrayList<String[]>();
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(bat));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.toLowerCase(Locale.US).startsWith("fastboot")) { continue; }
                String rest = line.replaceFirst("(?i)^fastboot\\s+(%\\*|\\$\\*)?\\s*", "").trim();
                String[] p = rest.split("\\s+");
                if (p.length == 0) { continue; }
                String cmd = p[0].toLowerCase(Locale.US);
                if (cmd.equals("flash") && p.length >= 3) {
                    File f = new File(p[2]);
                    if (!f.isAbsolute()) { f = new File(root, p[2]); }
                    if (f.exists()) { out.add(new String[]{ "flash", p[1], f.getAbsolutePath() }); }
                } else if (cmd.equals("erase") && p.length >= 2) {
                    out.add(new String[]{ "erase", p[1], "" });
                } else if (cmd.equals("set_active") && p.length >= 2) {
                    out.add(new String[]{ "set_active", p[1], "" });
                }
            }
            br.close();
        } catch (Exception e) { append(T("解析脚本失败：", "Parse script failed: ") + e.getMessage(), cErr); }
        return out;
    }

    private void runRom(final List<String[]> tasks) {
        runBg(new Runnable() {
            public void run() {
                int i = 0;
                for (String[] t : tasks) {
                    i++;
                    final String kind = t[0], a = t[1], b = t[2];
                    append("[" + i + "/" + tasks.size() + "] " + kind + " " + a, cAccent);
                    boolean ok = true;
                    if (kind.equals("flash")) {
                        if (mode == MODE_OTG) { ok = fb.flashFile(a, new File(b), null); }
                        else if (mode == MODE_LOCAL) { ok = RootShell.flash(a, new File(b)).ok; }
                        else {
                            try { remote().flashFile(a, new File(b)); }
                            catch (Exception e) { ok = false; }
                        }
                    } else if (kind.equals("erase")) {
                        if (mode == MODE_OTG) { ok = fb.erase(a); }
                    }
                    if (!ok) { append(T("中断：", "Aborted: ") + a, cErr); return; }
                }
                append(T("线刷完成", "Flashing done"), cOk);
            }
        });
    }

    // ---------------- 遥控 ----------------

    private void remoteStatus() {
        runBg(new Runnable() {
            public void run() {
                try { append(T("PC 在线：", "PC online: ") + remote().ping(), cOk); }
                catch (Exception e) { append(T("连接失败：", "Connect failed: ") + e.getMessage(), cErr); }
            }
        });
    }

    private void remotePartitions() {
        runBg(new Runnable() {
            public void run() {
                try { append(remote().partitions(), cText); }
                catch (Exception e) { append(T("读取失败：", "Failed: ") + e.getMessage(), cErr); }
            }
        });
    }

    private void remoteReboot(final String target) {
        runBg(new Runnable() {
            public void run() {
                try { append(remote().reboot(target), cText); }
                catch (Exception e) { append(T("失败：", "Failed: ") + e.getMessage(), cErr); }
            }
        });
    }

    // ---------------- 命令输入 ----------------

    private void runTyped() {
        final String cmd = input.getText().toString().trim();
        if (cmd.length() == 0) { return; }
        input.setText("");
        if (!history.contains(cmd)) { history.add(cmd); }
        if (history.size() > 50) { history.remove(0); }
        append("> " + cmd, cAccent);

        runBg(new Runnable() {
            public void run() {
                if (mode == MODE_LOCAL) {
                    RootShell.Result r = RootShell.run(cmd);
                    append(r.out.length() > 0 ? r.out : ("exit " + (r.ok ? 0 : 1)), r.ok ? cText : cErr);
                } else if (mode == MODE_REMOTE) {
                    try { append(remote().run(cmd), cText); }
                    catch (Exception e) { append(T("失败：", "Failed: ") + e.getMessage(), cErr); }
                } else {
                    if (!fb.isOpen()) { append(T("请先连接设备", "Connect first"), cErr); return; }
                    String r = fb.command(cmd);
                    append(r, r.startsWith("OKAY") ? cOk : cErr);
                }
            }
        });
    }
}
