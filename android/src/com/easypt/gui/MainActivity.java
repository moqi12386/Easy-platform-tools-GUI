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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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
import android.view.MotionEvent;
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
import android.widget.PopupWindow;
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
    private static final String VER = "2.16.0";

    /** 界面样式 */
    private static final int ST_NATIVE = 0, ST_MATERIAL = 1, ST_MIUIX = 2, ST_GLASS = 3;

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
    private int styleIdx = ST_NATIVE;
    private boolean borderOn = true, borderRain = true;
    private int borderColor = 0xFFFFFFFF, borderDp = 2, borderAlphaPct = 100;
    private int blurPct = 70;
    private int glassPct = 45;
    private int backdrop = 0;    // 壁纸平均色（无壁纸时 = 主题底色）
    private android.graphics.Bitmap wallBmp = null;
    private final java.util.HashMap<Button, Integer> themed = new java.util.HashMap<Button, Integer>();
    private final java.util.HashSet<Button> listRows = new java.util.HashSet<Button>();
    private final java.util.ArrayList<View> glassViews = new java.util.ArrayList<View>();
    private android.graphics.Bitmap[] wallLevels;
    private static final int[] LEVEL_DIV = { 3, 6, 12, 24 };

    private int cBg, cPanel, cText, cDim, cAccent, cOk, cErr, cBtn, cRun, cInput, cOut;

    private SharedPreferences store;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private FastbootUsb fb;
    private UsbDevice pendingDevice;
    private String pendingPart = "boot";

    private TextView logView, statusView, titleView;
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
        styleIdx = store.getInt("style", ST_NATIVE);
        borderOn = store.getBoolean("bOn", true);
        borderRain = store.getBoolean("bRain", true);
        borderColor = store.getInt("bCol", 0xFFFFFFFF);
        borderDp = store.getInt("bDp", 2);
        borderAlphaPct = store.getInt("bA", 100);
        blurPct = store.getInt("blurv", 70);
        glassPct = store.getInt("gconcv", 45);
        GlassDrawable.BLUR = blurPct;
        GlassDrawable.CONC = glassPct;
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

    @Override
    protected void onPause() {
        saveSettingsFields();
        super.onPause();
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
            styleBtn(b, baseColor, listRows.contains(b));
        }
        if (titleView != null && wallBmp != null) {
            titleView.setTextColor(textOn(localBackdrop(titleView)));
        }
        if (statusView != null && wallBmp != null) {
            int bg = localBackdrop(statusView);
            statusView.setTextColor(blend(textOn(bg), bg, 0.30));
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

    private LinearLayout rootView, homePage;
    private View settingsPage;
    private FrameLayout pageHost, navBar, navThumb;
    private android.animation.ValueAnimator navAnim;
    private TextView navHome, navSet, modeValue;
    private LinearLayout modeRow;
    private EditText setHost, setToken, setPart;
    private int page = 0;

    private void buildUi() {
        rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.VERTICAL);
        String wp = store.getString("wall", "");
        wallBmp = wp.length() > 0 ? fitToScreen(android.graphics.BitmapFactory.decodeFile(wp)) : null;
        if (wallBmp != null) {
            rootView.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), wallBmp));
            backdrop = averageColor(wallBmp);
            buildWallLevels();
        } else {
            rootView.setBackgroundColor(cBg);
            backdrop = cOut;
        }
        applyStatusBarIcons(isLight(backdrop));
        rootView.setPadding(dp(12), dp(10), dp(12), dp(4));

        pageHost = new FrameLayout(this);
        pageHost.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        homePage = buildHome();
        settingsPage = buildSettingsPage();
        settingsPage.setVisibility(View.GONE);
        pageHost.addView(homePage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageHost.addView(settingsPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootView.addView(pageHost);

        rootView.addView(buildNav());

        rootView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
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
                v.setPadding(dp(12) + left, dp(10) + top, dp(12) + right, dp(4) + bottom);
                return insets;
            }
        });
        setContentView(rootView);
        rootView.requestApplyInsets();
        final View rootRef = rootView;
        rootRef.post(new Runnable() {
            public void run() {
                refreshButtonColors();
                placeThumb(page, false);
            }
        });
    }

    /** 主页：日志 + 快捷按钮 + 命令输入（输入框在底栏上面，上调过） */
    private LinearLayout buildHome() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Easy-platform-tools");
        title.setTextColor(cText);
        title.setTextSize(18 * scale);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleView = title;
        page.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(12), 0);
        row.setMinimumHeight(dp(54));
        row.setBackground(menuRowBg(dp(16)));
        modeRow = row;
        modeValue = new TextView(this);
        modeValue.setTextSize((isGlass() ? 14 : 14) * scale);
        modeValue.setTextColor(fgColor());
        row.addView(modeValue, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView modeChev = new TextView(this);
        modeChev.setText("›");
        modeChev.setTextSize(20 * scale);
        modeChev.setTextColor(fgDim());
        row.addView(modeChev);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showMenu(v, new String[]{
                                T("OTG 主机", "OTG host"),
                                T("本机 root", "Local root"),
                                T("遥控 PC", "Remote PC")},
                        new String[]{
                                T("用 OTG 线刷另一台手机", "Flash another phone over OTG"),
                                T("备份 / 刷写自己，需要 root", "Backup or flash this phone, needs root"),
                                T("指挥电脑上的桌面版干活", "Drive the desktop app over Wi-Fi")},
                        mode, new Pick() {
                            public void onPick(int i) { applyMode(i); }
                        });
            }
        });
        page.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        updateModeRow();

        statusView = new TextView(this);
        statusView.setTextColor(fgDim());
        statusView.setTextSize(12 * scale);
        page.addView(statusView);

        ScrollView scroll = new GlassScroll(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp(6);
        slp.bottomMargin = dp(4);
        scroll.setLayoutParams(slp);
        if (isGlass()) {
            scroll.setBackground(new GlassDrawable(scroll).radius(dp(18))
                    .wall(wallLevels, LEVEL_DIV)
                    .tint(Color.argb(hasWall() ? 0x22 : 0x80, Color.red(cOut), Color.green(cOut), Color.blue(cOut)))
                    .border(dp(1), 0x2EFFFFFF));
            glassViews.add(scroll);
        } else if (styleIdx == ST_MIUIX) {
            scroll.setBackground(new PillDrawable(Color.argb(Math.min(255, Math.max(150, btnAlpha) * 255 / 100),
                    Color.red(cOut), Color.green(cOut), Color.blue(cOut)), dp(18)));
        } else {
            scroll.setBackgroundColor(Color.argb(hasWall() ? Math.min(235, Math.max(150, btnAlpha)) : 255, Color.red(cOut), Color.green(cOut), Color.blue(cOut)));
        }

        logView = new TextView(this);
        logView.setTextColor(fgColor());
        logView.setTextSize(11 * scale);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(dp(8), dp(6), dp(8), dp(6));
        logView.setTextIsSelectable(true);
        scroll.addView(logView);
        page.addView(scroll);

        ScrollView gridScroll = new GlassScroll(this);
        int gridH = (int) (getResources().getDisplayMetrics().heightPixels * (styleIdx == ST_MIUIX ? 0.40 : 0.34));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, gridH);
        glp.topMargin = dp(4);
        gridScroll.setLayoutParams(glp);

        grid = new GridLayout(this);
        grid.setColumnCount(styleIdx == ST_MIUIX ? 1 : 2);
        grid.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        gridScroll.addView(grid);
        page.addView(gridScroll);

        LinearLayout tools = new LinearLayout(this);
        tools.setPadding(0, dp(6), 0, 0);
        final LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        tlp.setMargins(dp(3), 0, dp(3), 0);
        tools.addView(smallBtn(T("清空", "Clear"), new View.OnClickListener() {
            public void onClick(View v) { logBuf = new SpannableStringBuilder(); logView.setText(""); }
        }), tlp);
        tools.addView(smallBtn(T("存日志", "Save log"), new View.OnClickListener() {
            public void onClick(View v) { saveLog(); }
        }), tlp);
        tools.addView(smallBtn(T("历史", "History"), new View.OnClickListener() {
            public void onClick(View v) { showHistory(); }
        }), tlp);
        page.addView(tools);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setPadding(0, dp(6), 0, dp(2));

        input = new EditText(this);
        input.setHint(T("输入命令", "Type a command"));
        input.setHintTextColor(fgDim());
        input.setTextColor(fgColor());
        input.setTextSize(14 * scale);
        input.setSingleLine(true);
        if (isGlass()) {
            input.setBackground(new GlassDrawable(input).radius(dp(16))
                    .wall(wallLevels, LEVEL_DIV)
                    .tint(Color.argb(0x33, Color.red(cInput), Color.green(cInput), Color.blue(cInput)))
                    .border(dp(1), 0x33FFFFFF));
            glassViews.add(input);
            input.setPadding(dp(14), dp(4), dp(14), dp(4));
        } else if (styleIdx == ST_MATERIAL) {
            input.setBackground(new PillDrawable(Color.argb(0xE6,
                    Color.red(cInput), Color.green(cInput), Color.blue(cInput)), dp(16)));
            input.setPadding(dp(14), dp(4), dp(14), dp(4));
        } else {
            input.setBackgroundColor(cInput);
        }
        input.setMinHeight(dp(46));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        ilp.setMargins(dp(2), 0, dp(6), 0);
        inputRow.addView(input, ilp);

        Button runBtn = smallBtn(T("执行", "Run"));
        themed.put(runBtn, cRun);
        styleBtn(runBtn, cRun);
        runBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(84), dp(48)));
        runBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { runTyped(); }
        });
        inputRow.addView(runBtn);
        page.addView(inputRow);

        return page;
    }

    /** 底栏：主页 / 设置，玻璃样式下是一整块磨砂 + 会滑动的玻璃滑块 */
    private View buildNav() {
        navBar = new NavBar(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66));
        blp.topMargin = dp(6);
        navBar.setLayoutParams(blp);

        int barTint = Color.argb(isGlass() ? (hasWall() ? 0x55 : 0xDA) : 0xF2,
                Color.red(cPanel), Color.green(cPanel), Color.blue(cPanel));
        if (isGlass()) {
            navBar.setBackground(new GlassDrawable(navBar).radius(dp(33))
                    .wall(wallLevels, LEVEL_DIV)
                    .tint(barTint)
                    .border(dp(1), 0x59FFFFFF));
            navBar.setElevation(dp(18));
            glassViews.add(navBar);
        } else {
            navBar.setBackground(new PillDrawable(barTint, dp(styleIdx == ST_MATERIAL ? 28 : 24)));
            navBar.setElevation(styleIdx == ST_MATERIAL ? dp(3) : 0);
        }

        navThumb = new FrameLayout(this);
        navThumb.setBackground(thumbBg());
        if (isGlass()) { glassViews.add(navThumb); }
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(dp(20), dp(54));
        thumbLp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        navBar.addView(navThumb, thumbLp);

        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.HORIZONTAL);
        navHome = navItem("主页", 0);
        navSet = navItem("设置", 1);
        items.addView(navHome, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        items.addView(navSet, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        navBar.addView(items, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        navBar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                placeThumb(page, false);
            }
        });
        return navBar;
    }

    /** 底栏容器：点按选 tab，也可以按住左右拖滑块 */
    private class NavBar extends FrameLayout {
        private float downX, startX;

        NavBar(Context c) { super(c); }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    startX = navThumb == null ? 0 : navThumb.getX();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (navThumb != null && Math.abs(ev.getX() - downX) > dp(7)) {
                        if (navAnim != null) { navAnim.cancel(); }
                        return true;
                    }
                    break;
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (navThumb == null) { return true; }
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    startX = navThumb.getX();
                    if (navAnim != null) { navAnim.cancel(); }
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float min = dp(5);
                    float max = Math.max(min, getWidth() - dp(5) - navThumb.getWidth());
                    navThumb.setX(Math.max(min, Math.min(max, startX + (ev.getX() - downX))));
                    float k = Math.min(0.14f, Math.abs(ev.getX() - downX) / dp(500));
                    navThumb.setScaleX(1f + k);
                    navThumb.invalidate();   // 位置变了，玻璃要重新取样
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    navThumb.setScaleX(1f);
                    float center = navThumb.getX() + navThumb.getWidth() / 2f;
                    int target = center < getWidth() / 2f ? 0 : 1;
                    showPage(target);
                    return true;
                }
            }
            return true;
        }
    }

    private TextView navItem(String text, final int idx) {        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14 * scale);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(idx == page ? cText : cDim);
        if (idx == page) { t.setTypeface(Typeface.DEFAULT_BOLD); }
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showPage(idx); }
        });
        return t;
    }

    private Drawable thumbBg() {
        if (isGlass()) {
            return new GlassDrawable(navThumb).radius(dp(28))
                    .wall(wallLevels, LEVEL_DIV)
                    .tint(0x26FFFFFF)
                    .border(dp(1), 0x66FFFFFF);
        }
        if (styleIdx == ST_MATERIAL) {
            return new PillDrawable(blend(cPanel, cAccent, 0.35), dp(26));
        }
        if (styleIdx == ST_MIUIX) {
            return new PillDrawable(blend(cPanel, cText, 0.13), dp(22));
        }
        return new PillDrawable(blend(cPanel, cText, 0.10), dp(22));
    }

    /** 摆滑块；animate=true 时带「液体」回弹 + 横向拉伸 */
    private void placeThumb(final int p, boolean animate) {
        if (navThumb == null || navBar == null) { return; }
        int pad = dp(5);
        int w = (navBar.getWidth() - pad * 2) / 2;
        if (w <= 0) { return; }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) navThumb.getLayoutParams();
        int wantH = Math.max(dp(40), navBar.getHeight() - pad * 2);
        if (lp.width != w || lp.height != wantH) {
            lp.width = w;
            lp.height = wantH;
            navThumb.setLayoutParams(lp);
        }
        float to = pad + p * (float) w;
        if (!animate) {
            navThumb.setX(to);
            navThumb.setScaleX(1f);
            applyNavColors();
            return;
        }
        final float from = navThumb.getX();
        if (Math.abs(from - to) < 1f) { navThumb.setX(to); applyNavColors(); return; }
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(isGlass() ? 420 : 240);
        a.setInterpolator(isGlass()
                ? new android.view.animation.OvershootInterpolator(1.7f)
                : new android.view.animation.DecelerateInterpolator(1.4f));
        a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator an) {
                float t = (float) an.getAnimatedValue();
                navThumb.setX(from + (to - from) * t);
                float s = (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, t)));
                navThumb.setScaleX(1f + (isGlass() ? 0.22f : 0.05f) * s);
            }
        });
        navAnim = a;
        a.start();
    }

    private void showPage(int p) {
        if (pageHost == null || p == page) {
            if (navThumb != null) { placeThumb(page, true); }
            return;
        }
        if (page == 1) { saveSettingsFields(); }
        page = p;
        View in = p == 0 ? homePage : settingsPage;
        View out = p == 0 ? settingsPage : homePage;
        out.setVisibility(View.GONE);
        in.setAlpha(0.6f);
        in.setVisibility(View.VISIBLE);
        in.animate().alpha(1f).setDuration(170).start();
        if (navHome != null) {
            navHome.setTypeface(p == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            navSet.setTypeface(p == 1 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            applyNavColors();
        }
        placeThumb(p, true);
        if (p == 0) { refreshButtonColors(); }
    }

    /** 底栏文字跟着它背后的底色走（选中项跟滑块走） */
    private void applyNavColors() {
        if (navBar == null || navHome == null) { return; }
        int wall = localBackdrop(navBar);
        int barEff = isGlass() ? blend(wall, cPanel, hasWall() ? 0.33 : 0.62) : cPanel;
        int thumbEff;
        if (isGlass()) {
            // 滑块 = 背后区域 + 白雾 + 高光，约等于往白里混四成
            thumbEff = blend(barEff, 0xFFFFFFFF, 0.45);
        } else if (styleIdx == ST_MATERIAL) {
            thumbEff = blend(cPanel, cAccent, 0.35);
        } else {
            thumbEff = blend(cPanel, cText, 0.13);
        }
        int onSel = textOn(thumbEff);
        int dim = blend(textOn(barEff), barEff, 0.28);
        navHome.setTextColor(page == 0 ? onSel : dim);
        navSet.setTextColor(page == 1 ? onSel : dim);
    }

    private void saveSettingsFields() {
        if (setHost == null) { return; }
        store.edit()
                .putString("host", setHost.getText().toString().trim())
                .putString("token", setToken.getText().toString().trim())
                .putString("part", setPart.getText().toString().trim())
                .apply();
    }

    private Button smallBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13 * scale);
        b.setMinHeight(dp(46));
        b.setMinimumHeight(dp(46));
        themed.put(b, cBtn);
        styleBtn(b, cBtn);
        return b;
    }

    // ---------------- 界面样式 ----------------

    private int radiusDp() {
        switch (styleIdx) {
            case ST_MATERIAL: return 24;
            case ST_MIUIX: return 16;
            case ST_GLASS: return 22;
            default: return 10;
        }
    }

    /** 按钮底的不透明度：Material 更实，玻璃几乎是透的 */
    private int fillPct() {
        switch (styleIdx) {
            case ST_MATERIAL: return btnAlpha;
            case ST_MIUIX: return btnAlpha;
            case ST_GLASS: return 22;
            default: return btnAlpha;
        }
    }

    /** Material 把底色往主色上带一点，其余样式照原样 */
    private int fillBase(int baseColor) {
        if (styleIdx == ST_MATERIAL) { return blend(baseColor, cAccent, 0.30); }
        return baseColor;
    }

    private boolean isGlass() { return styleIdx == ST_GLASS; }

    /** 玻璃样式下前景要跟着壁纸明暗走，否则浅色壁纸上看不见字 */
    private int fgColor() {
        if (!isGlass() || wallBmp == null) { return cText; }
        return textOn(blendOver(cPanel, 40, backdrop));
    }

    private int fgDim() {
        if (!isGlass() || wallBmp == null) { return cDim; }
        return blend(fgColor(), blendOver(cPanel, 40, backdrop), 0.38);
    }

    private int accentColor() {
        return isGlass() && wallBmp != null ? blend(fgColor(), cPanel, 0.15) : cAccent;
    }

    /** 预先烤好 4 级模糊壁纸（1/3、1/6、1/12、1/24），draw() 时只做一次 drawBitmap */
    private void buildWallLevels() {
        if (wallBmp == null) { wallLevels = null; return; }
        wallLevels = new android.graphics.Bitmap[LEVEL_DIV.length];
        android.graphics.Paint fp = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < LEVEL_DIV.length; i++) {
            int d = LEVEL_DIV[i];
            int w = Math.max(4, wallBmp.getWidth() / d);
            int h = Math.max(4, wallBmp.getHeight() / d);
            android.graphics.Bitmap small = android.graphics.Bitmap.createBitmap(
                    w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(small);
            cv.drawBitmap(wallBmp, new android.graphics.Rect(0, 0, wallBmp.getWidth(), wallBmp.getHeight()),
                    new android.graphics.Rect(0, 0, w, h), fp);
            boxBlur3(small);
            wallLevels[i] = small;
        }
    }

    /** 3x3 盒滤一次，把降采样的方块感抹掉 */
    private static void boxBlur3(android.graphics.Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0, a = 0, n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) { continue; }
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) { continue; }
                        int c = px[yy * w + xx];
                        a += (c >>> 24); r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF;
                        n++;
                    }
                }
                out[y * w + x] = ((a / n) << 24) | ((r / n) << 16) | ((g / n) << 8) | (b / n);
            }
        }
        bmp.setPixels(out, 0, w, 0, 0, w, h);
    }
    /** 进度条填充色：玻璃下固定用 MIUI 蓝，否则用主题主色 */
    private int seekAccent() {
        if (isGlass()) { return 0xFF3D7EFF; }
        if (styleIdx == ST_MIUIX) { return 0xFF5B8DFF; }
        return cAccent;
    }

    /** 进度条换成 MIUIX 那种：圆头粗轨道 + 白色圆拇指 */
    private void styleSeek(android.widget.SeekBar sb) {
        sb.setProgressDrawable(new BarDrawable(seekAccent(), 0x4D000000, dp(16)));
        android.graphics.drawable.GradientDrawable th = new android.graphics.drawable.GradientDrawable();
        th.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        th.setColor(0xFFF4F7FB);
        th.setSize(dp(26), dp(26));
        sb.setThumb(th);
        sb.setThumbOffset(0);
        sb.setSplitTrack(false);
        sb.setMinimumHeight(dp(34));
        sb.setMaxHeight(dp(34));
    }
    /** 按样式造按钮底 */
    private Drawable buttonBg(View host, int baseColor) {
        int base = fillBase(baseColor);
        if (isGlass()) {
            return new GlassDrawable(host).radius(dp(radiusDp()))
                    .wall(wallLevels, LEVEL_DIV)
                    .tint(Color.argb(0x18, Color.red(base), Color.green(base), Color.blue(base)))
                    .border(dp(Math.max(1, borderDp)), borderAlphaPct >= 40 ? 0x66FFFFFF : 0x33FFFFFF);
        }
        int a = Math.max(0, Math.min(100, fillPct())) * 255 / 100;
        PillDrawable p = new PillDrawable(
                Color.argb(a, Color.red(base), Color.green(base), Color.blue(base)), dp(radiusDp()));
        p.border(borderOn, borderRain, borderColor, dp(borderDp), borderAlphaPct);
        if (styleIdx == ST_MATERIAL) {
            PillDrawable mask = new PillDrawable(0xFFFFFFFF, dp(radiusDp()));
            return new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF), p, mask);
        }
        return p;
    }

    /** 统一套样式：底色、字色、对齐、箭头、阴影；listRow=true 才用 MIUIX 的列表行外观 */
    private void styleBtn(Button b, int baseColor) {
        styleBtn(b, baseColor, false);
    }

    private void styleBtn(Button b, int baseColor, boolean listRow) {
        b.setBackground(buttonBg(b, baseColor));
        b.setTextColor(textOn(blendOver(fillBase(baseColor), fillPct(), localBackdrop(b))));
        if (styleIdx == ST_MIUIX && listRow) {
            b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            b.setPadding(dp(18), 0, dp(38), 0);
            b.setTextSize(15 * scale);
            b.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    new ChevronDrawable(dp(18), cDim, dp(2)), null);
            b.setCompoundDrawablePadding(dp(8));
        } else {
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(10), dp(6), dp(10), dp(6));
            b.setTextSize((styleIdx == ST_MATERIAL || isGlass() ? 14 : 13) * scale);
            b.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }
        b.setAllCaps(false);
        b.setElevation(styleIdx == ST_MATERIAL ? dp(2) : isGlass() ? dp(8) : 0);
    }

    /** 模糊强度变了，让所有玻璃控件重画（按钮走 refreshButtonColors） */
    private void invalidateGlass() {
        invalidateGlassViews();
        refreshButtonColors();
    }

    /** 只让玻璃重画（滚动时每帧调，不能顺手重建按钮） */
    private void invalidateGlassViews() {
        if (glassViews.isEmpty()) { return; }
        android.graphics.Rect vis = new android.graphics.Rect();
        for (int i = 0; i < glassViews.size(); i++) {
            View v = glassViews.get(i);
            if (v == null || !v.isShown()) { continue; }
            if (v.getGlobalVisibleRect(vis)) { v.invalidate(); }
        }
    }

    /**
     * 滚动时硬件加速只平移显示列表、不会重新 draw()，玻璃里的模糊就会停在原位、
     * 和底下的壁纸错位。这里每帧把可见的玻璃控件标脏，逼它按新位置重新采样。
     */
    private class GlassScroll extends ScrollView {
        GlassScroll(Context c) { super(c); }
        @Override
        protected void onScrollChanged(int l, int t, int oldl, int oldt) {
            super.onScrollChanged(l, t, oldl, oldt);
            if (isGlass()) { invalidateGlassViews(); }
        }
    }
    private int localBackdrop(View v) {
        if (wallBmp == null || v == null) { return backdrop; }
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return averageRegion(wallBmp, loc[0], loc[1], Math.max(1, v.getWidth()), Math.max(1, v.getHeight()));
    }

    /** 圆角 + 可调不透明的按钮底，外挂一圈自定义边框 */
    private PillDrawable pill(int baseColor, int radiusDp) {
        return pill(baseColor, radiusDp, false);
    }    /** plain=true：给设置面板里的小控件用，不带边框 */
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
        listRows.add(b);
        styleBtn(b, cBtn, true);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { r.run(); }
        });
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(styleIdx == ST_MIUIX ? 52 : 48);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        int m = styleIdx == ST_MIUIX ? 3 : 4;
        lp.setMargins(dp(4), dp(m), dp(4), dp(m));
        b.setLayoutParams(lp);
        grid.addView(b);
    }

    private void applyMode(int m) {
        mode = m;
        store.edit().putInt("mode", m).apply();
        updateModeRow();
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

    // ---------------- 设置页 ----------------

    /** 设置是独立一页（底栏第二个 tab），不再用对话框盖住主界面 */
    private View buildSettingsPage() {
        ScrollView sv = new GlassScroll(this);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(2), 0, dp(10));
        sv.addView(col);

        TextView title = new TextView(this);
        title.setText(T("设置", "Settings"));
        title.setTextColor(fgColor());
        title.setTextSize(22 * scale);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(4), dp(4), dp(4), dp(8));
        col.addView(title);

        // 1) 界面样式
        LinearLayout cStyle = card(T("界面样式", "UI style"));
        String[] sNames = { T("原生", "Native"), "Material", "MIUIX", T("液态玻璃", "Liquid glass") };
        String[] sSubs = { T("圆角半透，最朴素", "Rounded and translucent"),
                T("药丸 + 主色 + 投影", "Pills, accent tint, elevation"),
                T("单列列表 + 右侧箭头", "List rows with chevrons"),
                T("磨砂玻璃 + 玻璃底栏", "Frosted glass with glass bar") };
        LinearLayout styleRow = menuRow(T("当前样式", "Current style"), sNames[styleIdx], new View.OnClickListener() {
            public void onClick(View v) {
                showMenu(v, sNames, sSubs, styleIdx, new Pick() {
                    public void onPick(int i) { store.edit().putInt("style", i).apply(); recreate(); }
                });
            }
        });
        cStyle.addView(styleRow);
        col.addView(cStyle, cardLp());

        // 2) 按键透明度
        LinearLayout cAlpha = card(T("按键透明度", "Button opacity"));
        LinearLayout alphaRow = new LinearLayout(this);
        alphaRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView tvAlpha = new TextView(this);
        tvAlpha.setText(btnAlpha + "%");
        tvAlpha.setTextColor(cText);
        tvAlpha.setTextSize(13 * scale);
        tvAlpha.setWidth(dp(56));
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        styleSeek(sb);
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
        cAlpha.addView(alphaRow);
        col.addView(cAlpha, cardLp());

        // 3) 按键边框
        LinearLayout cBorder = card(T("按键边框", "Button border"));
        final LinearLayout chipsWrap = new LinearLayout(this);
        chipsWrap.setOrientation(LinearLayout.VERTICAL);
        cBorder.addView(chipsWrap);
        renderBorderChips(chipsWrap);

        LinearLayout bdRow = new LinearLayout(this);
        bdRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView tvBd = new TextView(this);
        tvBd.setText(borderDp + " dp");
        tvBd.setTextColor(cText);
        tvBd.setTextSize(13 * scale);
        tvBd.setWidth(dp(56));
        android.widget.SeekBar sbBd = new android.widget.SeekBar(this);
        styleSeek(sbBd);
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
        cBorder.addView(bdRow);

        LinearLayout baRow = new LinearLayout(this);
        baRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView tvBa = new TextView(this);
        tvBa.setText(borderAlphaPct + "%");
        tvBa.setTextColor(cText);
        tvBa.setTextSize(13 * scale);
        tvBa.setWidth(dp(56));
        android.widget.SeekBar sbBa = new android.widget.SeekBar(this);
        styleSeek(sbBa);
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
        cBorder.addView(baRow);

        LinearLayout blRow = new LinearLayout(this);
        blRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView tvBl = new TextView(this);
        tvBl.setText(blurPct + "%");
        tvBl.setTextColor(cText);
        tvBl.setTextSize(13 * scale);
        tvBl.setWidth(dp(56));
        android.widget.SeekBar sbBl = new android.widget.SeekBar(this);
        styleSeek(sbBl);
        sbBl.setMax(100);
        sbBl.setProgress(blurPct);
        sbBl.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean fromUser) {
                blurPct = p;
                tvBl.setText(p + "%");
                if (!fromUser) { return; }
                GlassDrawable.BLUR = p;
                invalidateGlass();
            }
            public void onStartTrackingTouch(android.widget.SeekBar s) { }
            public void onStopTrackingTouch(android.widget.SeekBar s) {
                blurPct = s.getProgress();
                GlassDrawable.BLUR = blurPct;
                store.edit().putInt("blurv", blurPct).apply();
            }
        });
        blRow.addView(tvBl);
        blRow.addView(sbBl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cBorder.addView(blRow);

        TextView blHint = new TextView(this);
        blHint.setText(T("玻璃模糊（只影响液态玻璃样式）", "Glass blur (liquid glass only)"));
        blHint.setTextColor(fgDim());
        blHint.setTextSize(11 * scale);
        blHint.setPadding(dp(2), dp(4), 0, 0);
        cBorder.addView(blHint);

        LinearLayout gcRow = new LinearLayout(this);
        gcRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView tvGc = new TextView(this);
        tvGc.setText(glassPct + "%");
        tvGc.setTextColor(cText);
        tvGc.setTextSize(13 * scale);
        tvGc.setWidth(dp(56));
        android.widget.SeekBar sbGc = new android.widget.SeekBar(this);
        styleSeek(sbGc);
        sbGc.setMax(100);
        sbGc.setProgress(glassPct);
        sbGc.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean fromUser) {
                glassPct = p;
                tvGc.setText(p + "%");
                if (!fromUser) { return; }
                GlassDrawable.CONC = p;
                invalidateGlass();
            }
            public void onStartTrackingTouch(android.widget.SeekBar s) { }
            public void onStopTrackingTouch(android.widget.SeekBar s) {
                glassPct = s.getProgress();
                GlassDrawable.CONC = glassPct;
                store.edit().putInt("gconcv", glassPct).apply();
            }
        });
        gcRow.addView(tvGc);
        gcRow.addView(sbGc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cBorder.addView(gcRow);

        TextView gcHint = new TextView(this);
        gcHint.setText(T("玻璃浓度：越高壁纸越看不清，100% 基本就是磨砂", "Glass density: higher hides the wallpaper more"));
        gcHint.setTextColor(fgDim());
        gcHint.setTextSize(11 * scale);
        gcHint.setPadding(dp(2), dp(4), 0, 0);
        cBorder.addView(gcHint);

        col.addView(cBorder, cardLp());

        // 4) 背景
        LinearLayout cBg = card(T("背景", "Background"));
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
        cBg.addView(swatches);

        LinearLayout wallRow = new LinearLayout(this);
        wallRow.setPadding(0, dp(8), 0, 0);
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
        cBg.addView(wallRow);
        col.addView(cBg, cardLp());

        // 5) 字号 + 语言
        LinearLayout cText2 = card(T("字体与语言", "Text & language"));
        final String[] sizeNames = { T("小", "Small"), T("标准", "Normal"), T("大", "Large") };
        final float[] sizeVals = { 0.9f, 1.0f, 1.15f };
        int curSize = 1;
        for (int i = 0; i < 3; i++) { if (Math.abs(sizeVals[i] - scale) < 0.01f) { curSize = i; } }
        cText2.addView(menuRow(T("字号", "Font size"), sizeNames[curSize], new View.OnClickListener() {
            public void onClick(View v) {
                showMenu(v, sizeNames, new String[]{"", "", ""}, curSizePick(), new Pick() {
                    public void onPick(int i) { store.edit().putFloat("scale", sizeVals[i]).apply(); recreate(); }
                });
            }
        }));

        final String[] langNames = { "中文", "English" };
        cText2.addView(menuRow(T("语言", "Language"), langNames["en".equals(lang) ? 1 : 0],
                new View.OnClickListener() {
                    public void onClick(View v) {
                        showMenu(v, langNames, new String[]{"", ""}, "en".equals(lang) ? 1 : 0, new Pick() {
                            public void onPick(int i) {
                                store.edit().putString("lang", i == 0 ? "zh" : "en").apply();
                                recreate();
                            }
                        });
                    }
                }));
        col.addView(cText2, cardLp());

        // 6) 遥控 PC
        LinearLayout cRemote = card(T("遥控 PC", "Remote PC"));
        setHost = new EditText(this);
        setHost.setHint("192.168.1.10:8765");
        setHost.setText(store.getString("host", ""));
        setHost.setTextColor(fgColor());
        setHost.setHintTextColor(fgDim());
        cRemote.addView(setHost);

        setToken = new EditText(this);
        setToken.setHint(T("6 位配对码", "6-digit pairing code"));
        setToken.setText(store.getString("token", ""));
        setToken.setTextColor(fgColor());
        setToken.setHintTextColor(fgDim());
        cRemote.addView(setToken);

        setPart = new EditText(this);
        setPart.setHint(T("默认分区 boot", "Default partition"));
        setPart.setText(store.getString("part", "boot"));
        setPart.setTextColor(fgColor());
        setPart.setHintTextColor(fgDim());
        cRemote.addView(setPart);

        TextView hint = new TextView(this);
        hint.setText(T("改动会即时保存，切回主页生效", "Saved as you go, applied when you go back"));
        hint.setTextColor(fgDim());
        hint.setTextSize(11 * scale);
        hint.setPadding(dp(2), dp(8), 0, 0);
        cRemote.addView(hint);
        col.addView(cRemote, cardLp());

        // 7) 其它
        LinearLayout cAbout = card(T("其它", "More"));
        LinearLayout abRow = new LinearLayout(this);
        Button sw2 = smallBtn(T("切换使用模式", "Switch mode"), new View.OnClickListener() {
            public void onClick(View v) { chooseMode(false); }
        });
        Button about = smallBtn(T("关于", "About"), new View.OnClickListener() {
            public void onClick(View v) { showAbout(); }
        });
        abRow.addView(sw2, new LinearLayout.LayoutParams(0, dp(46), 1f));
        abRow.addView(about, new LinearLayout.LayoutParams(0, dp(46), 1f));
        cAbout.addView(abRow);

        TextView ver = new TextView(this);
        ver.setText("Easy-platform-tools-GUI · Android " + VER);
        ver.setTextColor(fgDim());
        ver.setTextSize(11 * scale);
        ver.setPadding(dp(2), dp(10), 0, 0);
        cAbout.addView(ver);
        col.addView(cAbout, cardLp());

        return sv;
    }

    /** 设置页里的一块卡片，按样式换底 */
    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(14));
        if (isGlass()) {
            c.setBackground(new GlassDrawable(c).radius(dp(20))
                    .wall(wallLevels, LEVEL_DIV)
                    .tint(Color.argb(hasWall() ? 0x33 : 0xCC, Color.red(cPanel), Color.green(cPanel), Color.blue(cPanel)))
                    .border(dp(1), 0x40FFFFFF));
            c.setElevation(dp(8));
            glassViews.add(c);
        } else if (styleIdx == ST_MATERIAL) {
            c.setBackground(new PillDrawable(Color.argb(0xF7,
                    Color.red(cPanel), Color.green(cPanel), Color.blue(cPanel)), dp(20)));
            c.setElevation(dp(1));
        } else if (styleIdx == ST_MIUIX) {
            c.setBackground(new PillDrawable(Color.argb(0xFF,
                    Color.red(cPanel), Color.green(cPanel), Color.blue(cPanel)), dp(18)));
        } else {
            int a = hasWall() ? Math.min(235, Math.max(150, btnAlpha)) : 255;
            c.setBackground(new PillDrawable(Color.argb(a,
                    Color.red(cPanel), Color.green(cPanel), Color.blue(cPanel)), dp(14)));
        }
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(accentColor());
        t.setTextSize(12 * scale);
        t.setLetterSpacing(0.06f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(2), 0, 0, dp(10));
        c.addView(t);
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(8));
        return lp;
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
        styleSeek(sb);
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
        final String[] items = {
                T("OTG 主机", "OTG host"),
                T("本机 root", "Local root"),
                T("遥控 PC", "Remote PC")
        };
        final String[] subs = {
                T("用 OTG 线刷另一台手机", "Flash another phone over OTG"),
                T("备份 / 刷写自己，需要 root", "Backup or flash this phone, needs root"),
                T("指挥电脑上的桌面版干活", "Drive the desktop app over Wi-Fi")
        };
        final View anchor = modeRow != null ? modeRow : navBar;
        if (anchor == null) { return; }
        anchor.post(new Runnable() {
            public void run() {
                showMenu(anchor, items, subs, mode, new Pick() {
                    public void onPick(int i) { applyMode(i); }
                });
            }
        });
    }

    // ---------------- MIUIX 风格弹窗 ----------------

    private interface Pick { void onPick(int i); }

    /** 圆角的浮层选项列表：标题 + 副标题，选中项染主色并打勾 */
    private void showMenu(final View anchor, String[] items, String[] subs, int selected, final Pick cb) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(10), dp(8), dp(10));
        box.setBackground(menuBg(box));   // 用自己的位置取样，不能用主页的 root
        box.setElevation(dp(20));

        final int menuW = Math.min(dp(300), getResources().getDisplayMetrics().widthPixels - dp(56));
        final PopupWindow pw = new PopupWindow(box, menuW, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setOutsideTouchable(false);   // 先关掉，等 300ms 再开，避免抬手被当成「点外面」
        pw.setFocusable(true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= 21) { pw.setElevation(dp(18)); }

        final int accent = menuAccent();
        final int on = fgColor();
        boolean anySub = false;
        for (String s : subs) { if (s != null && s.length() > 0) { anySub = true; break; } }

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            boolean sel = i == selected;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), anySub ? dp(11) : dp(15), dp(12), anySub ? dp(11) : dp(15));
            row.setBackground(rowState(sel));

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            TextView t = new TextView(this);
            t.setText(items[i]);
            t.setTextSize(16 * scale);
            t.setTextColor(sel ? accent : on);
            if (sel) { t.setTypeface(Typeface.DEFAULT_BOLD); }
            textCol.addView(t);
            if (anySub) {
                String sub = i < subs.length ? subs[i] : "";
                if (sub != null && sub.length() > 0) {
                    TextView s = new TextView(this);
                    s.setText(sub);
                    s.setTextSize(12 * scale);
                    s.setTextColor(sel ? accent : fgDim());
                    s.setPadding(0, dp(2), 0, 0);
                    textCol.addView(s);
                }
            }
            row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextSize(17 * scale);
            check.setTextColor(accent);
            check.setVisibility(sel ? View.VISIBLE : View.INVISIBLE);
            check.setPadding(dp(10), 0, dp(4), 0);
            row.addView(check);

            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    pw.dismiss();
                    cb.onPick(idx);
                }
            });
            box.addView(row);
        }

        // 位置：优先贴在锚点正下方，下方放不下就翻到上方
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int estH = dp(24) + items.length * dp(anySub ? 62 : 50);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int gap = dp(6);
        int y = loc[1] + anchor.getHeight() + gap;
        if (y + estH > screenH - dp(24)) {
            y = Math.max(dp(24), loc[1] - estH - gap);
        }
        // 贴着右边浮出（用户要求：不要压在左边）
        int x = getResources().getDisplayMetrics().widthPixels - menuW - dp(12);
        x = Math.max(dp(12), x);

        // 放到下一帧再弹，否则手指抬起会被当成「点到浮层外面」直接关掉
        final int fx = x, fy = y;
        anchor.postDelayed(new Runnable() {
            public void run() {
                pw.showAtLocation(rootView, Gravity.NO_GRAVITY, fx, fy);
                box.postDelayed(new Runnable() {
                    public void run() { pw.setOutsideTouchable(true); }
                }, 300);
                box.setScaleX(0.92f);
                box.setScaleY(0.92f);
                box.setAlpha(0f);
                box.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start();
                box.setPivotX(dp(24));
                box.setPivotY(0f);
            }
        }, 60);
    }

    private Drawable menuBg(View host) {
        if (isGlass()) {
            return new GlassDrawable(host).radius(dp(22))
                    .wall(wallLevels, LEVEL_DIV)
                    .tint(Color.argb(0xFF, Color.red(cPanel), Color.green(cPanel), Color.blue(cPanel)))
                    .alpha(0xE8)
                    .border(dp(1), 0x40FFFFFF);
        }
        if (styleIdx == ST_MIUIX) {
            PillDrawable p = new PillDrawable(0xF72C2C2E, dp(22));
            p.border(true, false, 0x1FFFFFFF, dp(1), 100);
            return p;
        }
        int base = blend(cPanel, cText, 0.12);
        PillDrawable p = new PillDrawable(0xFD000000 | (base & 0x00FFFFFF),
                dp(styleIdx == ST_MATERIAL ? 24 : 18));
        p.border(true, false, 0x1FFFFFFF, dp(1), 100);
        return p;
    }

    private int menuAccent() {
        if (styleIdx == ST_MIUIX) { return 0xFF6E9BFF; }
        return styleIdx == ST_MATERIAL ? cAccent : (isGlass() ? accentColor() : cAccent);
    }

    /** 每一行的按压反馈（选中行给一层淡淡的底色） */
    private Drawable rowState(boolean selected) {
        android.graphics.drawable.StateListDrawable d = new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable pressed = new android.graphics.drawable.GradientDrawable();
        pressed.setCornerRadius(dp(14));
        pressed.setColor(selected ? Color.argb(0x1F, Color.red(menuAccent()), Color.green(menuAccent()), Color.blue(menuAccent()))
                : 0x22FFFFFF);
        android.graphics.drawable.GradientDrawable normal = new android.graphics.drawable.GradientDrawable();
        normal.setCornerRadius(dp(14));
        normal.setColor(selected ? Color.argb(0x14, Color.red(menuAccent()), Color.green(menuAccent()), Color.blue(menuAccent()))
                : Color.TRANSPARENT);
        d.addState(new int[]{android.R.attr.state_pressed}, pressed);
        d.addState(new int[]{}, normal);
        return d;
    }

    /** 可点的「一行」：圆角底，右侧箭头，用来唤起 showMenu */
    private Drawable menuRowBg(int radiusDp) {
        if (isGlass()) {
            return new GlassDrawable(rootView).radius(dp(radiusDp))
                    .wall(wallLevels, LEVEL_DIV)
                    .tint(Color.argb(0x24, Color.red(cBtn), Color.green(cBtn), Color.blue(cBtn)))
                    .border(dp(Math.max(1, borderDp)), borderOn ? (borderRain ? 0x66FFFFFF : borderColor) : 0x33FFFFFF);
        }
        int a = Math.max(0, Math.min(100, fillPct())) * 255 / 100;
        PillDrawable p = new PillDrawable(Color.argb(a, Color.red(fillBase(cBtn)), Color.green(fillBase(cBtn)), Color.blue(fillBase(cBtn))), dp(radiusDp));
        p.border(borderOn, borderRain, borderColor, dp(borderDp), borderAlphaPct);
        return p;
    }

    private void updateModeRow() {
        if (modeValue == null) { return; }
        String[] names = { T("OTG 主机：刷别人", "OTG host: flash another"),
                T("本机 root：管自己", "Local root: manage itself"),
                T("遥控 PC：指挥电脑", "Remote: drive the PC") };
        modeValue.setText(names[Math.max(0, Math.min(2, mode))]);
        modeValue.setTextColor(fgColor());
    }

    private int curSizePick() {
        float[] v = { 0.9f, 1.0f, 1.15f };
        for (int i = 0; i < 3; i++) { if (Math.abs(v[i] - scale) < 0.01f) { return i; } }
        return 1;
    }

    /** 「左边标题 / 右边当前值 + ›」的一行，点一下弹 showMenu */
    private LinearLayout menuRow(String label, String value, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(4), 0, dp(2), 0);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(16 * scale);
        t.setTextColor(fgColor());
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14 * scale);
        v.setTextColor(fgDim());
        v.setMaxLines(1);
        row.addView(v);

        TextView chev = new TextView(this);
        chev.setText("›");
        chev.setTextSize(20 * scale);
        chev.setTextColor(fgDim());
        chev.setPadding(dp(6), 0, 0, 0);
        row.addView(chev);

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(l);
        // 某些系统（含分身/沙箱）里 LinearLayout 的 click 不稳定，这里再用触摸兜底
        row.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) { v.setPressed(true); return true; }
                if (a == MotionEvent.ACTION_UP) {
                    v.setPressed(false);
                    v.performClick();
                    return true;
                }
                if (a == MotionEvent.ACTION_CANCEL) { v.setPressed(false); return true; }
                return true;
            }
        });
        return row;
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
