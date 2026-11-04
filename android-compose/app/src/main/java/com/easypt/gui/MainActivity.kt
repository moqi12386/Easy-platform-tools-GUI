package com.easypt.gui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.easypt.gui.glass.LiquidBottomTabs
import com.easypt.gui.glass.LiquidBottomTab
import com.easypt.gui.glass.LiquidButton
import com.easypt.gui.glass.LiquidSlider
import com.easypt.gui.glass.LiquidToggle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDark
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLight
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class UiStyle(val labelZh: String, val labelEn: String) {
    Native("原生", "Native"),
    Material("Material", "Material"),
    Miuix("MIUIX", "MIUIX"),
    Glass("液态玻璃", "Liquid glass")
}

enum class Lang { Zh, En }

fun tr(lang: Lang, zh: String, en: String): String = if (lang == Lang.En) en else zh

data class LogLine(val text: String, val color: Color)

private const val ACTION_PERM = "com.easypt.gui.USB_PERMISSION"
private const val PREF = "easyptc"

/** 运行期降级开关：上次渲染崩过就关掉 backdrop，保证还能用 */
internal var safeRender by androidx.compose.runtime.mutableStateOf(false)

/** 用户在设置里手动强制安全模式 */
internal var forceSafe by androidx.compose.runtime.mutableStateOf(false)

/**
 * backdrop 库靠 GraphicsLayer + RenderEffect，在 Android 17 (API 37) 上会整段崩掉，
 * 所以只在 Android 12~15 (31..35) 启用，其余走纯位图取样玻璃。
 */
internal var useBackdrop: Boolean = false

// 全局配色走 Palette（Tail 定义），getter 让旧代码零改动跟随主题
private val Bg: Color get() = Palette.bg
private val Surface: Color get() = Palette.surface
private val TextPrimary: Color get() = Palette.text
private val TextSecondary: Color get() = Palette.text2
private val Accent: Color get() = Palette.accent
private val Danger = Color(0xFFE5484D)
private val OkColor: Color get() = if (Palette.dark) Color(0xFF3DD68C) else Color(0xFF12A150)
private val ErrColor: Color get() = if (Palette.dark) Color(0xFFFF7B72) else Color(0xFFD93B3B)
private val LogColor: Color get() = if (Palette.dark) Color(0xFFC9CDD6) else Color(0xFF4A5160)

class MainActivity : ComponentActivity(), FastbootUsb.Logger {

    private lateinit var fb: FastbootUsb
    private val ui = Handler(Looper.getMainLooper())
    private var pendingDevice: UsbDevice? = null

    // ---------- 业务状态 ----------
    private val logs = mutableStateListOf<LogLine>()
    private var mode by mutableIntStateOf(0)
    private var connected by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var parts by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var showParts by mutableStateOf(false)
    private var part by mutableStateOf("boot")
    private var host by mutableStateOf("192.168.1.10:8765")
    private var token by mutableStateOf("")

    // ---------- 线刷 / 备份 ----------
    private var romSteps by mutableStateOf<List<RomFlasher.Step>>(emptyList())
    private var showRom by mutableStateOf(false)
    private var romFiles: Map<String, Uri> = emptyMap()
    private var pendingDest = ""
    private var eraseTarget by mutableStateOf<String?>(null)
    private var showMode by mutableStateOf(false)

    // ---------- 界面状态 ----------
    private var style by mutableStateOf(UiStyle.Native)
    private var lang by mutableStateOf(Lang.Zh)
    private var page by mutableIntStateOf(0)
    private var uiAlpha by mutableFloatStateOf(85f)
    private var glassBlur by mutableFloatStateOf(55f)
    private var glassDensity by mutableFloatStateOf(40f)
    private var glassAssets by mutableStateOf<GlassAssets?>(null)
    private var customWallpaper by mutableStateOf(false)
    private var themeMode by mutableStateOf(ThemeMode.System)
    private var monet by mutableStateOf(false)
    private var showAbout by mutableStateOf(false)
    private var showPlayground by mutableStateOf(false)
    private var showWpSource by mutableStateOf(false)
    private var showBing by mutableStateOf(false)


    private fun t(zh: String, en: String) = tr(lang, zh, en)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 上一次没跑完？（成功启动会把轨迹删掉，残留 = 上次没跑完）
        val prevTrace = readTrace()
        val prevCrash = readCrash()
        val spBoot = getSharedPreferences(PREF, MODE_PRIVATE)
        if (prevTrace != null) {
            val fails = spBoot.getInt("bootFails", 0) + 1
            spBoot.edit().putInt("bootFails", fails).apply()
            if (fails >= 2) {
                // 降级后还是崩 -> 停在诊断页
                showCrashScreen(
                    (prevCrash ?: "没有 Java 堆栈 —— 大概率是渲染层原生崩溃（SIGSEGV）\n") +
                        "\n—— 上次启动走到哪一步 ——\n" + prevTrace
                )
                return
            }
            // 第一次失败：自动降级（关掉 backdrop / 特效）+ 强制回到原生样式，避免死循环
            safeRender = true
            style = UiStyle.Native
            try {
                getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("style", UiStyle.Native.ordinal).apply()
            } catch (_: Throwable) { }
            try { File(filesDir, "boot-trace.txt").delete() } catch (_: Throwable) { }
        } else {
            safeRender = false
            // 上次是不是在切样式之后崩的？（原生崩溃没有 Java 堆栈，只能靠这个）
        }

        // 本次启动：清空旧轨迹再开始记
        try { File(filesDir, "boot-trace.txt").delete() } catch (_: Throwable) { }
        mark("01-onCreate")
        installCrashHandler()
        mark("02-handler")
        enableEdgeToEdge()
        setupBars()
        mark("03-bars")

        val sp = getSharedPreferences(PREF, MODE_PRIVATE)
        mode = sp.getInt("mode", 0)
        part = sp.getString("part", "boot") ?: "boot"
        host = sp.getString("host", "192.168.1.10:8765") ?: "192.168.1.10:8765"
        token = sp.getString("token", "") ?: ""
        style = UiStyle.entries.getOrElse(sp.getInt("style", UiStyle.Glass.ordinal)) { UiStyle.Glass }
        lang = if (sp.getInt("lang", 0) == 1) Lang.En else Lang.Zh
        uiAlpha = sp.getFloat("alpha", 85f)
        glassBlur = sp.getFloat("gblur", 55f)
        glassDensity = sp.getFloat("gdens", 40f)
        customWallpaper = sp.getBoolean("wp", false)
        themeMode = ThemeMode.entries.getOrElse(sp.getInt("theme", 2)) { ThemeMode.System }
        monet = sp.getBoolean("monet", false)
        forceSafe = sp.getBoolean("forceSafe", false)

        androidx.core.content.ContextCompat.registerReceiver(
            this, usbReceiver, IntentFilter(ACTION_PERM),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        append("Easy-platform-tools-GUI · Compose", Accent)
        append("Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}", TextSecondary)

        mark("04-prefs")
        reloadWallpaper()
        mark("05-setContent")
        setContent { EasyApp() }
    }

    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                File(filesDir, "last-crash.txt").writeText(
                    "EasyPT 3.3.3 crash\n" +
                        "device=${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT}\n\n" +
                        android.util.Log.getStackTraceString(e)
                )
            } catch (_: Throwable) {
            }
            prev?.uncaughtException(thread, e)
        }
    }

    /** 日志同时写到 内部 files/logs 与 外部 Android/data/<pkg>/files/logs，方便导出 */
    private fun logLine(stage: String) {
        val line = "[" + SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()) + "] " + stage + "\n"
        try {
            val dir = File(filesDir, "logs").apply { mkdirs() }
            val f = File(dir, "session.log")
            if (f.length() > 256 * 1024) f.delete()
            f.appendText(line)
        } catch (_: Throwable) {
        }
        try {
            val ext = getExternalFilesDir(null)
            if (ext != null) {
                val dir = File(ext, "logs").apply { mkdirs() }
                val f = File(dir, "session.log")
                if (f.length() > 256 * 1024) f.delete()
                f.appendText(line)
            }
        } catch (_: Throwable) {
        }
    }

    private fun mark(stage: String) {
        logLine(stage)
        try {
            File(filesDir, "boot-trace.txt").appendText("$stage\n")
        } catch (_: Throwable) {
        }
    }

    private fun collectLog(): String {
        val sb = StringBuilder()
        sb.append("Easy-platform-tools-GUI · 日志\n")
        sb.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append("  API ").append(Build.VERSION.SDK_INT).append('\n')
        sb.append("style=").append(style.name).append("  dark=").append(Palette.dark)
            .append("  safeRender=").append(safeRender).append('\n')
        sb.append("---- last crash ----\n")
        sb.append(readCrash() ?: "(无)")
        sb.append("\n---- last action ----\n")
        sb.append(readLastAction() ?: "(无)")
        sb.append("\n---- session log ----\n")
        try {
            val f = File(File(filesDir, "logs"), "session.log")
            if (f.exists()) sb.append(f.readText().takeLast(60_000))
        } catch (_: Throwable) {
        }
        return sb.toString()
    }

    private fun shareLog() {
        val text = try { collectLog() } catch (e: Throwable) { "collect failed: ${e.message}" }
        try {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "EasyPT 日志")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(i, "分享日志"))
        } catch (_: Throwable) {
            toast("没有可用的分享应用")
        }
    }

    private fun readLastAction(): String? {
        val f = File(filesDir, "last-action.txt")
        if (!f.exists()) return null
        return try { f.readText() } catch (_: Throwable) { null }
    }

    private fun readTrace(): String? {
        val f = File(filesDir, "boot-trace.txt")
        if (!f.exists()) return null
        return try { f.readText() } catch (_: Throwable) { null }
    }

    private fun readCrash(): String? {
        val f = File(filesDir, "last-crash.txt")
        if (!f.exists()) return null
        val s = try { f.readText() } catch (_: Throwable) { null }
        return if (s.isNullOrBlank()) null else s
    }

    /** 纯 Android View 的崩溃页：不依赖 Compose，崩溃了也不会被带走 */
    private fun showCrashScreen(text: String) {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val title = android.widget.TextView(this).apply {
            this.text = "上次启动崩溃了"
            textSize = 20f
            setTypeface(getTypeface(), android.graphics.Typeface.BOLD)
            setPadding(dp(20), dp(24), dp(20), dp(8))
        }
        val hint = android.widget.TextView(this).apply {
            this.text = "下面是堆栈，点发送/截图给开发者即可；点「重试」重新启动"
            textSize = 12f
            setPadding(dp(20), 0, dp(20), dp(12))
        }
        val body = android.widget.TextView(this).apply {
            this.text = text
            textSize = 9f
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val scroll = android.widget.ScrollView(this).apply {
            addView(body)
            setBackgroundColor(android.graphics.Color.parseColor("#F5F6F8"))
        }
        val share = android.widget.Button(this).apply {
            this.text = "发送日志"
            setOnClickListener { shareLog() }
        }
        val copy = android.widget.Button(this).apply {
            this.text = "复制"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", text))
                Toast.makeText(this@MainActivity, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }
        val retry = android.widget.Button(this).apply {
            this.text = "重试"
            setOnClickListener {
                try { File(filesDir, "last-crash.txt").delete() } catch (_: Throwable) { }
                try { File(filesDir, "boot-trace.txt").delete() } catch (_: Throwable) { }
                try {
                    getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("bootFails", 0).apply()
                } catch (_: Throwable) { }
                val i = intent
                finish()
                startActivity(i)
            }
        }
        val bar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            addView(share, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(copy, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(retry, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
            addView(title)
            addView(hint)
            addView(scroll, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            addView(bar)
        }
        setContentView(root)
    }

    override fun onDestroy() {
        try { unregisterReceiver(usbReceiver) } catch (_: Exception) { }
        try { fb.close() } catch (_: Throwable) { }
        super.onDestroy()
    }

    // ---------------- 系统栏 ----------------

    private fun setupBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /** 浅色界面：状态栏深色图标；玻璃样式盖壁纸时按顶部亮度决定 */
    private fun applyBars(assets: GlassAssets?) {
        val darkIcons = !Palette.dark && (assets?.topLum ?: 0f) > 0.45f
        try {
            val c = WindowCompat.getInsetsController(window, window.decorView)
            c.isAppearanceLightStatusBars = darkIcons
            c.isAppearanceLightNavigationBars = darkIcons
        } catch (_: Throwable) {
        }
    }

    // ---------------- 壁纸 ----------------

    private fun reloadWallpaper() {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        Thread {
            try {
                val src = Wallpaper.load(this) ?: Wallpaper.default(w, h)
                val full = Wallpaper.prepare(src, w, h)
                val assets = Wallpaper.build(full)
                ui.post { glassAssets = assets }
            } catch (e: Throwable) {
                ui.post { append("壁纸加载失败：${e.message}", ErrColor) }
            }
        }.start()
    }

    private fun setWallpaperFrom(uri: Uri) {
        append(t("读取壁纸…", "Loading wallpaper…"), TextSecondary)
        Thread {
            if (!Wallpaper.save(this, uri)) {
                ui.post { append(t("壁纸读取失败", "Failed to load wallpaper"), ErrColor) }
                return@Thread
            }
            customWallpaper = true
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("wp", true).apply()
            reloadWallpaper()
        }.start()
    }

    /** 在线壁纸：下载后走本地同一套流程 */
    fun setWallpaperFromUrl(url: String) {
        append(t("下载壁纸…", "Downloading wallpaper…"), TextSecondary)
        Thread {
            val f = File(cacheDir, "wp_online.jpg")
            try {
                java.net.URL(url).openStream().use { input ->
                    f.outputStream().use { out -> input.copyTo(out) }
                }
            } catch (e: Throwable) {
                ui.post { append(t("下载失败：${e.message}", "Download failed: ${e.message}"), ErrColor) }
                return@Thread
            }
            runOnUiThread { setWallpaperFrom(Uri.fromFile(f)) }
        }.start()
    }

    private fun resetWallpaper() {
        Wallpaper.clear(this)
        customWallpaper = false
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("wp", false).apply()
        reloadWallpaper()
        append(t("已恢复默认壁纸", "Wallpaper reset"), OkColor)
    }

    // ---------------- 日志 ----------------

    override fun log(line: String) = append(line, guessColor(line))

    private fun guessColor(line: String): Color {
        val l = line.lowercase(Locale.US)
        return when {
            l.contains("fail") || l.contains("失败") || l.contains("错误") -> ErrColor
            l.startsWith("ok") || l.contains("成功") || l.contains("完成") -> OkColor
            line.startsWith(">") -> Accent
            else -> LogColor
        }
    }

    fun append(text: String, color: Color = LogColor) {
        ui.post {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            logs.add(LogLine("[$ts] $text", color))
            while (logs.size > 300) logs.removeAt(0)
        }
    }

    fun clearLog() { logs.clear() }

    private fun bg(block: () -> Unit) {
        busy = true
        Thread {
            try { block() } catch (e: Exception) {
                append("异常：${e.message}", ErrColor)
            } finally {
                ui.post { busy = false }
            }
        }.start()
    }

    fun applyMode(m: Int) {
        mode = m
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("mode", m).apply()
    }

    fun modeName(): String = when (mode) {
        0 -> t("OTG 主机", "OTG host")
        1 -> t("本机 root", "Local root")
        else -> t("遥控 PC", "Remote PC")
    }

    // ---------------- OTG ----------------

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_PERM) return
            val d = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted && d != null) {
                pendingDevice = d
                bg {
                    val ok = fb.open(this@MainActivity, d)
                    connected = ok
                    if (ok) append(t("已连接：${fb.deviceName()}", "Connected: ${fb.deviceName()}"), OkColor)
                }
            } else {
                append(t("USB 权限被拒绝", "USB permission denied"), ErrColor)
            }
        }
    }

    fun connectUsb() {
        val um = getSystemService(Context.USB_SERVICE) as UsbManager
        val d = FastbootUsb.findFastbootDevice(um)
        if (d == null) {
            append(
                t("没找到 fastboot 设备：检查 OTG 线和对方是否在 fastboot 界面",
                    "No fastboot device: check the OTG cable and that the target is in fastboot"),
                ErrColor
            )
            return
        }
        if (!um.hasPermission(d)) {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(this, 0, Intent(ACTION_PERM), flags)
            um.requestPermission(d, pi)
            append(t("等待 USB 授权…", "Waiting for USB permission…"), TextSecondary)
            return
        }
        bg {
            val ok = fb.open(this, d)
            connected = ok
            if (ok) append(t("已连接：${fb.deviceName()}", "Connected: ${fb.deviceName()}"), OkColor)
            else append(t("打开 USB 失败", "Failed to open USB"), ErrColor)
        }
    }

    fun otgInfo() = bg {
        if (!fb.isOpen()) { append(t("请先连接设备", "Connect a device first"), ErrColor); return@bg }
        append("product: ${fb.getvar("product")}")
        append("version: ${fb.getvar("version")}")
        append("serialno: ${fb.getvar("serialno")}")
        append("secure: ${fb.getvar("secure")}")
    }

    fun otgParts() = bg {
        if (!fb.isOpen()) { append(t("请先连接设备", "Connect a device first"), ErrColor); return@bg }
        append(t("读取分区表…", "Reading partitions…"), TextSecondary)
        val list = fb.getPartitions()
        if (list.isEmpty()) { append(t("没有读到分区", "No partitions found"), ErrColor); return@bg }
        parts = list.map { it[0] to (if (it.size > 1) it[1] else "") }
        showParts = true
        append(t("共 ${list.size} 个分区", "${list.size} partitions"), OkColor)
    }

    fun otgReboot(target: String) = bg {
        if (!fb.isOpen()) { append(t("请先连接设备", "Connect a device first"), ErrColor); return@bg }
        val ok = fb.reboot(target)
        append(
            if (ok) t("重启指令已发送", "Reboot sent") else t("重启失败", "Reboot failed"),
            if (ok) OkColor else ErrColor
        )
    }

    fun otgEdl() = bg {
        if (!fb.isOpen()) { append(t("请先连接设备", "Connect a device first"), ErrColor); return@bg }
        val ok = fb.oem("edl")
        append(
            if (ok) t("EDL 已请求", "EDL requested") else t("EDL 失败（可能未解锁）", "EDL failed (locked?)"),
            if (ok) OkColor else ErrColor
        )
    }

    fun otgErase(p: String) = bg {
        if (!fb.isOpen()) { append(t("请先连接设备", "Connect a device first"), ErrColor); return@bg }
        val ok = fb.erase(p)
        append(
            if (ok) t("已擦除：$p", "Erased: $p") else t("擦除失败：$p", "Erase failed: $p"),
            if (ok) OkColor else ErrColor
        )
    }

    fun flashImage(uri: Uri?) {
        if (uri == null) return
        bg {
            if (!fb.isOpen()) { append(t("请先连接设备", "Connect a device first"), ErrColor); return@bg }
            val f = File(cacheDir, "flash.img")
            contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { output -> input.copyTo(output) }
            }
            append(t("开始刷入 $part（${f.length() / 1048576.0} MB）…",
                "Flashing $part (${f.length() / 1048576.0} MB)…"), Accent)
            val ok = fb.flashFile(part, f, FastbootUsb.Progress { done, total ->
                if (total > 0 && done % (total / 10 + 1) < 65536) {
                    append("  ${done * 100 / total}%", TextSecondary)
                }
            })
            append(
                if (ok) t("刷入完成：$part", "Flashed: $part") else t("刷入失败：$part", "Flash failed: $part"),
                if (ok) OkColor else ErrColor
            )
            f.delete()
        }
    }

    // ---------------- 线刷 ROM ----------------

    private fun loadRom(tree: Uri) = bg {
        append(t("扫描线刷包…", "Scanning ROM folder…"), TextSecondary)
        val doc = DocumentFile.fromTreeUri(this, tree)
        if (doc == null || !doc.isDirectory) {
            append(t("无法访问该目录", "Cannot access this folder"), ErrColor)
            return@bg
        }
        val files = doc.listFiles().filter { it.isFile && it.name != null }
        val byName = HashMap<String, Uri>()
        files.forEach { f ->
            f.name?.lowercase(Locale.US)?.let { byName[it] = f.uri }
        }
        val script = files.firstOrNull {
            it.name?.lowercase(Locale.US)?.startsWith("flash_all") == true
        }
        val steps: List<RomFlasher.Step>
        if (script != null) {
            val txt = try {
                contentResolver.openInputStream(script.uri)?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: Exception) { "" }
            append(t("使用脚本 ${script.name}", "Using script ${script.name}"), Accent)
            steps = RomFlasher.fromScript(txt)
        } else {
            append(t("没有 flash_all 脚本，按文件名自动生成", "No flash_all script, generating from file names"), TextSecondary)
            steps = RomFlasher.fromImages(files.mapNotNull { it.name })
        }
        if (steps.isEmpty()) {
            append(t("没有可执行的步骤", "No executable steps found"), ErrColor)
            return@bg
        }
        steps.forEach { s ->
            if (s.kind == "flash" && !byName.containsKey(s.file)) s.missing = true
        }
        romFiles = byName
        romSteps = steps
        showRom = true
        val miss = steps.count { it.missing }
        append(
            t("共 ${steps.size} 步${if (miss > 0) "，$miss 步缺文件" else ""}",
                "${steps.size} steps${if (miss > 0) ", $miss missing" else ""}"),
            if (miss > 0) ErrColor else OkColor
        )
    }

    private fun runRom() = bg {
        if (!fb.isOpen()) { append(t("请先连接设备", "Connect a device first"), ErrColor); return@bg }
        showRom = false
        val steps = romSteps
        var okCount = 0
        var failCount = 0
        for ((i, s) in steps.withIndex()) {
            append("[${i + 1}/${steps.size}] ${s.raw}", Accent)
            val ok = when (s.kind) {
                "flash" -> if (s.missing) {
                    append(t("  缺少文件：${s.file}", "  missing file: ${s.file}"), ErrColor)
                    false
                } else {
                    val uri = romFiles[s.file]
                    if (uri == null) {
                        append(t("  找不到：${s.file}", "  not found: ${s.file}"), ErrColor)
                        false
                    } else {
                        val f = File(cacheDir, "rom.img")
                        try {
                            contentResolver.openInputStream(uri)?.use { input ->
                                f.outputStream().use { out -> input.copyTo(out) }
                            }
                        } catch (e: Exception) {
                            append("  ${e.message}", ErrColor)
                        }
                        append("  ${s.file} · ${f.length() / 1048576.0} MB", TextSecondary)
                        val r = fb.flashFile(s.part, f, FastbootUsb.Progress { done, total ->
                            if (total > 0 && done % (total / 10 + 1) < 65536) {
                                append("  ${done * 100 / total}%", TextSecondary)
                            }
                        })
                        f.delete()
                        r
                    }
                }
                "erase" -> fb.erase(s.part)
                "reboot" -> {
                    val r = fb.reboot(s.arg)
                    if (r) append(t("  已重启，后续步骤中止", "  rebooted, stopping"), OkColor)
                    r
                }
                "oem" -> fb.oem(s.arg)
                "wipe" -> fb.erase("userdata") and fb.erase("metadata")
                else -> fb.command(s.raw.removePrefix("fastboot ")).startsWith("OKAY")
            }
            if (ok) {
                okCount++
                append("  OK", OkColor)
            } else {
                failCount++
                append("  FAIL", ErrColor)
            }
            if (s.kind == "reboot" && ok) break
        }
        append(
            t("线刷结束：成功 $okCount · 失败 $failCount", "Flash finished: $okCount ok · $failCount failed"),
            if (failCount == 0) OkColor else ErrColor
        )
    }

    // ---------------- 备份 ----------------

    private fun backupTo(uri: Uri) = bg {
        val p = pendingDest
        if (p.isEmpty()) return@bg
        append(t("备份 $p …", "Backing up $p …"), TextSecondary)
        val f = File(cacheDir, "backup.img")
        val r = RootShell.backup(p, f)
        if (!f.exists() || f.length() == 0L) {
            append(t("备份失败：${r.out}", "Backup failed: ${r.out}"), ErrColor)
            return@bg
        }
        val size = f.length()
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                f.inputStream().use { it.copyTo(out) }
            }
            append(t("已备份 $p · ${size / 1048576.0} MB", "Backed up $p · ${size / 1048576.0} MB"), OkColor)
        } catch (e: Exception) {
            append(t("写入失败：${e.message}", "Write failed: ${e.message}"), ErrColor)
        }
        f.delete()
    }

    private fun backupBaseband(tree: Uri) = bg {
        append(t("读取分区…", "Reading partitions…"), TextSecondary)
        val list = RootShell.partitions()
        if (list.isEmpty()) {
            append(t("读取失败（需要 root）", "Failed (root required)"), ErrColor)
            return@bg
        }
        val targets = list.map { it[0] }.filter { n ->
            val l = n.lowercase(Locale.US)
            l.contains("modem") || l.contains("persist") || l.contains("fsg") ||
                l.contains("fsc") || l.contains("efs") || l.contains("nvram") ||
                l.contains("nvdata") || l.contains("radio")
        }
        if (targets.isEmpty()) {
            append(t("没有找到基带相关分区", "No baseband partitions found"), ErrColor)
            return@bg
        }
        val dir = DocumentFile.fromTreeUri(this, tree)
        if (dir == null) {
            append(t("无法访问目标目录", "Cannot access target folder"), ErrColor)
            return@bg
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        var okCount = 0
        for (name in targets) {
            append(t("备份 $name …", "Backing up $name …"), TextSecondary)
            val f = File(cacheDir, "bb.img")
            val r = RootShell.backup(name, f)
            if (!f.exists() || f.length() == 0L) {
                append(t("  失败：${r.out}", "  failed: ${r.out}"), ErrColor)
                continue
            }
            val doc = dir.createFile("application/octet-stream", "${name}_$stamp.img")
            if (doc == null) {
                append(t("  无法创建文件", "  cannot create file"), ErrColor)
                f.delete()
                continue
            }
            try {
                contentResolver.openOutputStream(doc.uri)?.use { out ->
                    f.inputStream().use { it.copyTo(out) }
                }
                append("  ${name} · ${f.length() / 1048576.0} MB", OkColor)
                okCount++
            } catch (e: Exception) {
                append("  ${e.message}", ErrColor)
            }
            f.delete()
        }
        append(t("基带备份完成：$okCount/${targets.size}", "Baseband backup: $okCount/${targets.size}"), OkColor)
    }

    // ---------------- 本机 root ----------------

    fun localRoot() = bg {
        append(t("检测 root…", "Checking root…"), TextSecondary)
        if (RootShell.hasRoot()) append(t("已获取 root", "Root granted"), OkColor)
        else append(t("没有 root（su 不可用）", "No root (su unavailable)"), ErrColor)
    }

    fun localParts() = bg {
        append(t("读取分区…", "Reading partitions…"), TextSecondary)
        val list = RootShell.partitions()
        if (list.isEmpty()) { append(t("读取失败（需要 root）", "Failed (root required)"), ErrColor); return@bg }
        parts = list.map { it[0] to (if (it.size > 1) it[1] else "") }
        showParts = true
        append(t("共 ${list.size} 个分区", "${list.size} partitions"), OkColor)
    }

    fun localReboot(target: String) = bg {
        val r = RootShell.reboot(target)
        append(
            if (r.ok) t("重启指令已发送", "Reboot sent") else t("重启失败：${r.out}", "Reboot failed: ${r.out}"),
            if (r.ok) OkColor else ErrColor
        )
    }

    // ---------------- 遥控 ----------------

    private fun remote() = RemoteClient(host, token)

    fun remotePing() = bg {
        val r = remote().ping()
        append(
            if (r.isNotBlank()) t("遥控服务在线", "Remote service online") else t("无响应", "No response"),
            if (r.isNotBlank()) OkColor else ErrColor
        )
    }

    fun remoteParts() = bg {
        append(t("读取分区表…", "Reading partitions…"), TextSecondary)
        append(remote().partitions().take(2000))
        showParts = false
    }

    fun remoteReboot(target: String) = bg {
        append(remote().reboot(target))
    }

    // ---------------- 命令 ----------------

    fun runTyped(cmd: String) {
        if (cmd.isBlank()) return
        append("> $cmd", Accent)
        bg {
            when (mode) {
                1 -> {
                    val r = RootShell.run(cmd)
                    append(if (r.out.isNotEmpty()) r.out else "exit ${if (r.ok) 0 else 1}")
                }
                else -> {
                    if (!fb.isOpen()) { append(t("请先连接设备", "Connect a device first"), ErrColor); return@bg }
                    val r = fb.command(cmd)
                    append(r, if (r.startsWith("OKAY")) OkColor else ErrColor)
                }
            }
        }
    }

    fun choosePart(p: String) {
        part = p
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("part", p).apply()
    }

    fun chooseHost(h: String) {
        host = h
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("host", h).apply()
    }

    fun chooseToken(tk: String) {
        token = tk
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("token", tk).apply()
    }

    private fun chooseStyle(s: UiStyle) {
        try {
            File(filesDir, "last-action.txt").writeText("切换到界面样式：${s.labelZh} (${s.name})")
        } catch (_: Throwable) {
        }
        style = s
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("style", s.ordinal).apply()
    }

    private fun chooseLang(l: Lang) {
        lang = l
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("lang", l.ordinal).apply()
    }

    private fun chooseTheme(m: ThemeMode) {
        themeMode = m
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("theme", m.ordinal).apply()
    }

    private fun chooseForceSafe(v: Boolean) {
        forceSafe = v
        safeRender = v
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("forceSafe", v).apply()
    }

    private fun chooseMonet(v: Boolean) {
        monet = v
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("monet", v).apply()
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------------- Compose ----------------

    @Composable
    fun EasyApp() {
        val assets = glassAssets
        val dm = LocalContext.current.resources.displayMetrics
        val screen = remember(dm.widthPixels, dm.heightPixels) {
            IntSize(dm.widthPixels, dm.heightPixels)
        }
        val systemDark = isSystemInDarkTheme()
        val dark = when (themeMode) {
            ThemeMode.Day -> false
            ThemeMode.Night -> true
            ThemeMode.System -> systemDark
        }
        Palette.dark = dark
        // 莫奈取色：直接分析当前壁纸位图（自定义壁纸也算），且只对 Material 风格生效
        var monetColor by remember { mutableStateOf<Color?>(null) }
        LaunchedEffect(monet, style, assets, customWallpaper) {
            monetColor = if (monet && style == UiStyle.Material && assets != null) {
                withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching {
                        val bmp = assets.full.asAndroidBitmap()
                        val pal = androidx.palette.graphics.Palette.from(bmp).clearFilters().generate()
                        val c = pal.getVibrantColor(0)
                            .takeIf { it != 0 }
                            ?: pal.getLightVibrantColor(0).takeIf { it != 0 }
                            ?: pal.getDominantColor(0)
                        if (c == 0) null else Color(c)
                    }.getOrNull()
                }
            } else null
        }
        Palette.monetAccent = monetColor

        // 只有「液态玻璃」这一档才启用 backdrop 库（libhwui 渲染树在部分机型上会原生崩溃，
        // 其他样式完全不碰渲染特效，保证默认稳定）
        // 库里那套效果（vibrancy + blur + lens）在 Android 12~15 上照抄使用；
        // Android 16+（含这台 Xiaomi）系统 libhwui 处理 RenderEffect 会原生崩溃，降到取样玻璃
        useBackdrop = style == UiStyle.Glass && Build.VERSION.SDK_INT >= 31 && !safeRender
        LaunchedEffect(assets, dark) { applyBars(assets) }
        // 每次样式变化：两帧内没崩就认为这次切换是安全的
        LaunchedEffect(style) {
            withFrameNanos { }
            withFrameNanos { }
            try { File(filesDir, "last-action.txt").delete() } catch (_: Throwable) { }
        }
        LaunchedEffect(Unit) {
            mark("06-composed")
            withFrameNanos { }
            mark("07-first-frame")
            withFrameNanos { }
            // 跑到这里说明这一帧画出来了：清轨迹、清面包屑、失败计数归零
            try { File(filesDir, "boot-trace.txt").delete() } catch (_: Throwable) { }
            try { File(filesDir, "last-action.txt").delete() } catch (_: Throwable) { }
            try {
                getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("bootFails", 0).apply()
            } catch (_: Throwable) { }
        }

        val backdrop = rememberLayerBackdrop()
        val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) setWallpaperFrom(uri)
        }
        val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            flashImage(uri)
        }
        val pickRom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) loadRom(uri)
        }
        val pickBackupDest =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                if (uri != null) backupTo(uri)
            }
        val pickBasebandDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) backupBaseband(uri)
        }

        // 主题切换时整棵树重建，避免读到半新半旧的配色
        key(dark, monet) {
            val onWall = if ((assets?.topLum ?: 0f) > 0.45f) Palette.text else Color.White
            // 照 demo：图层里只有背景（壁纸），玻璃元件在图层外面取样
            val wallBackdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize().background(Bg)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (useBackdrop) Modifier.layerBackdrop(wallBackdrop) else Modifier)
                ) {
                    WallpaperView(assets)
                }
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalWallBackdrop provides wallBackdrop,
                        LocalTitleColor provides onWall,
                        LocalCardText provides Palette.text,
                        LocalStyle provides style
                    ) {
                        MiuixTheme(colors = if (dark) miuixDark() else miuixLight()) {
                            MaterialTheme(
                                colorScheme = if (dark) {
                                    darkColorScheme(
                                        primary = Accent,
                                        surface = Surface,
                                        onSurface = TextPrimary,
                                        background = Bg,
                                        onBackground = TextPrimary
                                    )
                                } else {
                                    lightColorScheme(
                                        primary = Accent,
                                        onPrimary = Color.White,
                                        surface = Surface,
                                        onSurface = TextPrimary,
                                        background = Bg,
                                        onBackground = TextPrimary
                                    )
                                }
                            ) {
                                if (page == 0) {
                                    HomePage(
                                        style = style,
                                        assets = assets,
                                        backdrop = backdrop,
                                        screen = screen,
                                        blur = glassBlur / 100f,
                                        density = glassDensity / 100f,
                                        alpha = uiAlpha / 100f,
                                        lang = lang,
                                        mode = mode,
                                        modeName = modeName(),
                                        host = host,
                                        connected = connected,
                                        busy = busy,
                                        logs = logs,
                                        part = part,
                                        onModePick = { applyMode(it) },
                                        onConnect = { connectUsb() },
                                        onInfo = { otgInfo() },
                                        onParts = { if (mode == 1) localParts() else otgParts() },
                                        onFlash = { pickImage.launch(arrayOf("*/*")) },
                                        onRom = { pickRom.launch(null) },
                                        onBackup = { p ->
                                            pendingDest = p
                                            pickBackupDest.launch(
                                                "${p}_${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.img"
                                            )
                                        },
                                        onBackupBaseband = { pickBasebandDir.launch(null) },
                                        onReboot = { target ->
                                            when (mode) {
                                                0 -> otgReboot(target)
                                                1 -> localReboot(target)
                                                else -> remoteReboot(target)
                                            }
                                        },
                                        onEdl = { if (mode == 1) localReboot("edl") else otgEdl() },
                                        onErase = { p ->
                                            if (mode == 0) eraseTarget = p
                                            else toast(t("只有 OTG 模式支持擦除", "Erase is OTG-only"))
                                        },
                                        onRoot = { localRoot() },
                                        onPing = { remotePing() },
                                        onRun = { runTyped(it) },
                                        onClear = { clearLog() }
                                    )
                                } else {
                                    SettingsPage(
                                        style = style,
                                        assets = assets,
                                        backdrop = backdrop,
                                        screen = screen,
                                        blur = glassBlur / 100f,
                                        density = glassDensity / 100f,
                                        alpha = uiAlpha / 100f,
                                        lang = lang,
                                        onShareLog = { shareLog() },
                                        onForceSafe = { chooseForceSafe(it) },
                                        onAbout = { showAbout = true },
                                        onPlayground = { showPlayground = true },
                                        themeMode = themeMode,
                                        onTheme = { chooseTheme(it) },
                                        monet = monet,
                                        onMonet = { chooseMonet(it) },
                                        canMonet = Build.VERSION.SDK_INT >= 31,
                                        onStyle = { chooseStyle(it) },
                                        onLang = { chooseLang(it) },
                                        opacity = uiAlpha,
                                        onOpacity = {
                                            uiAlpha = it
                                            getSharedPreferences(PREF, MODE_PRIVATE).edit().putFloat("alpha", it).apply()
                                        },
                                        glassBlur = glassBlur,
                                        onGlassBlur = {
                                            glassBlur = it
                                            getSharedPreferences(PREF, MODE_PRIVATE).edit().putFloat("gblur", it).apply()
                                        },
                                        glassDensity = glassDensity,
                                        onGlassDensity = {
                                            glassDensity = it
                                            getSharedPreferences(PREF, MODE_PRIVATE).edit().putFloat("gdens", it).apply()
                                        },
                                        customWallpaper = customWallpaper,
                                        onPickWallpaper = { showWpSource = true },
                                        onResetWallpaper = { resetWallpaper() },
                                        parts = parts.map { it.first },
                                        part = part,
                                        onPart = { choosePart(it) },
                                        host = host,
                                        onHost = { chooseHost(it) },
                                        token = token,
                                        onToken = { chooseToken(it) }
                                    )
                                }
                            }
                        }
                    }

                    // —— 直接照抄 AndroidLiquidGlass demo 的 LiquidBottomTabs ——
                    if (useBackdrop) {
                        LiquidBottomTabs(
                            dark = Palette.dark,
                            selectedTabIndex = { page },
                            onTabSelected = { page = it },
                            backdrop = wallBackdrop,
                            tabsCount = 2,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            LiquidBottomTab(onClick = { page = 0 }) {
                                Icon(
                                    Icons.Filled.Home, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(24.dp)
                                )
                                Text(t("主页", "Home"), color = Color.White, fontSize = 10.sp)
                            }
                            LiquidBottomTab(onClick = { page = 1 }) {
                                Icon(
                                    Icons.Filled.Settings, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(24.dp)
                                )
                                Text(t("设置", "Settings"), color = Color.White, fontSize = 10.sp)
                            }
                        }
                    } else {
                        LiquidBottomBar(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            assets = assets,
                            backdrop = backdrop,
                            screen = screen,
                            page = page,
                            blur = glassBlur / 100f,
                            density = glassDensity / 100f,
                            items = listOf(
                                BarItem(Icons.Filled.Home, t("主页", "Home")),
                                BarItem(Icons.Filled.Settings, t("设置", "Settings"))
                            ),
                            onPage = { page = it }
                        )
                    }
                }

                if (showPlayground) {
                    GlassPlaygroundOverlay(assets, lang) { showPlayground = false }
                }

                if (showWpSource) {
                    LiquidChoiceDialog(
                        backdrop = wallBackdrop,
                        title = tr(lang, "壁纸来源", "Wallpaper source"),
                        options = listOf(
                            tr(lang, "在线壁纸（必应）", "Online (Bing)"),
                            tr(lang, "本地图片", "Local image")
                        ),
                        selected = -1,
                        onPick = { i ->
                            showWpSource = false
                            if (i == 0) showBing = true else pickWallpaper.launch(arrayOf("image/*"))
                        },
                        onDismiss = { showWpSource = false }
                    )
                }
                if (showBing) {
                    BingWallpaperDialog(
                        onPick = { url -> showBing = false; setWallpaperFromUrl(url) },
                        onDismiss = { showBing = false }
                    )
                }

                MiuixTheme(colors = if (dark) miuixDark() else miuixLight()) {
                    MaterialTheme(
                        colorScheme = if (dark) {
                            darkColorScheme(primary = Accent, surface = Surface, onSurface = TextPrimary)
                        } else {
                            lightColorScheme(primary = Accent, surface = Surface, onSurface = TextPrimary)
                        }
                    ) {
                        if (showParts) {
                            PartsDialog(style, lang, parts, onPick = { p ->
                                choosePart(p)
                                showParts = false
                                toast(t("默认分区：$p", "Default partition: $p"))
                            }, onClose = { showParts = false })
                        }
                        if (showRom) {
                            RomDialog(style, lang, romSteps, onStart = { runRom() }, onDismiss = { showRom = false })
                        }
                        if (showAbout) {
                            AboutDialog(style, lang, wallBackdrop, onDismiss = { showAbout = false })
                        }
                        eraseTarget?.let { target ->
                            ConfirmDialog(
                                style, lang,
                                title = t("擦除分区", "Erase partition"),
                                message = t("确定擦除 $target ？此操作不可撤销。", "Erase $target? This cannot be undone."),
                                onConfirm = { eraseTarget = null; otgErase(target) },
                                onDismiss = { eraseTarget = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ======================= 主题与调色 =======================

enum class ThemeMode(val zh: String, val en: String) {
    Day("日间模式", "Light"),
    Night("夜间模式", "Dark"),
    System("跟随系统", "Follow system")
}

/** 全局调色：用普通 var 存，配合 key() 重建整棵树，避免组合期间写状态 */
object Palette {
    var dark: Boolean = false
    var monetAccent: Color? = null

    val bg: Color
        get() = run {
            val base = if (dark) Color(0xFF101114) else Color(0xFFF2F3F5)
            monetAccent?.let { androidx.compose.ui.graphics.lerp(base, it, if (dark) 0.10f else 0.05f) } ?: base
        }
    val surface: Color
        get() = run {
            val base = if (dark) Color(0xFF1B1C20) else Color(0xFFFFFFFF)
            monetAccent?.let { androidx.compose.ui.graphics.lerp(base, it, if (dark) 0.12f else 0.06f) } ?: base
        }
    val text: Color get() = if (dark) Color(0xFFECEDEF) else Color(0xFF16181D)
    val text2: Color get() = if (dark) Color(0xFF9A9DA5) else Color(0xFF8C9199)
    val accent: Color get() = monetAccent ?: Color(0xFF3B82F6)
    val chevron: Color get() = if (dark) Color(0xFF5C6068) else Color(0xFFC2C6CC)
    val inset: Color get() = if (dark) Color(0xFF121316) else Color(0xFFF5F6F8)
    val chip: Color get() = if (dark) Color(0xFF2A2B30) else Color(0xFFF2F3F5)
    val nativeCard: Color get() = Color(0xFF1B1F27).copy(alpha = 0.92f)
}

/** 卡片里的文字颜色：玻璃/深色卡片用白字，其余跟随主题 */
val LocalCardText = androidx.compose.runtime.staticCompositionLocalOf { Palette.text }
/** 当前界面风格，供组件内部做分化样式 */
val LocalStyle = androidx.compose.runtime.staticCompositionLocalOf { UiStyle.Native }
/** 只含壁纸的图层，卡片从这里取样 */
val LocalWallBackdrop = androidx.compose.runtime.staticCompositionLocalOf<Backdrop?> { null }
/** 直接画在壁纸上的标题颜色 */
val LocalTitleColor = androidx.compose.runtime.staticCompositionLocalOf { Palette.text }

data class BarItem(val icon: ImageVector, val label: String)

/** MIUIX 风格下的彩色图标底色 */
fun miuixIconColor(icon: ImageVector): Color = when (icon) {
    Icons.Filled.PlayArrow -> Color(0xFF4C8DFF)
    Icons.Filled.Info -> Color(0xFF9AA0A6)
    Icons.Filled.List -> Color(0xFF5B8DEF)
    Icons.Filled.Build -> Color(0xFFFF9F0A)
    Icons.Filled.Refresh -> Color(0xFF34C759)
    Icons.Filled.Delete -> Color(0xFFFF3B30)
    Icons.Filled.Share -> Color(0xFF0A84FF)
    Icons.Filled.Lock -> Color(0xFF636366)
    Icons.Filled.Warning -> Color(0xFFFF9500)
    Icons.Filled.Star -> Color(0xFFFFCC00)
    Icons.Filled.Public -> Color(0xFF007AFF)
    Icons.Filled.Home -> Color(0xFF4C8DFF)
    Icons.Filled.Settings -> Color(0xFF8E8E93)
    Icons.Filled.Search -> Color(0xFF8E8E93)
    else -> Color(0xFF5B8DEF)
}

// ======================= 通用组件 =======================

@Composable
fun KCard(
    style: UiStyle,
    assets: GlassAssets?,
    backdrop: Backdrop,
    screen: IntSize,
    blur: Float,
    density: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = remember { RoundedCornerShape(22.dp) }
    when (style) {
        // 原生：重制版之前的深色半透明板
        UiStyle.Native -> CompositionLocalProvider(LocalCardText provides Color.White) {
            Column(
                modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Palette.nativeCard)
                    .padding(vertical = 6.dp),
                content = content
            )
        }
        // Material：跟随 M3 表面 + 投影，可选莫奈取色
        UiStyle.Material -> CompositionLocalProvider(LocalCardText provides Palette.text) {
            Column(
                modifier
                    .fillMaxWidth()
                    .shadow(if (Palette.dark) 4.dp else 8.dp, shape, clip = false)
                    .clip(shape)
                    .background(Palette.surface)
                    .padding(vertical = 6.dp),
                content = content
            )
        }
        // MIUIX：走 miuix 库自己的卡片
        UiStyle.Miuix -> CompositionLocalProvider(LocalCardText provides Palette.text) {
            MiuixCard(modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column(Modifier.padding(vertical = 6.dp), content = content)
            }
        }
        // 液态玻璃：真透明，从壁纸图层取样
        UiStyle.Glass -> {
            val tint = if (Palette.dark) Color(0xFF0A0F16) else Color.White
            // 滑杆 -> 实际参数：模糊 2~26dp，折射 4~44dp，底料 15%~70%
            val d = androidx.compose.ui.platform.LocalDensity.current
            val blurPx = with(d) { (2f + blur * 24f).dp.toPx() }
            val lensPx = with(d) { (4f + density * 40f).dp.toPx() }
            val tintA = 0.15f + alpha * 0.55f
            val mod = when {
                // 降级模式：纯色，不碰任何渲染特效
                safeRender -> Modifier
                    .shadow(6.dp, shape, clip = false)
                    .clip(shape)
                    .background(if (Palette.dark) Color(0xFF1F2026) else Color.White)
                useBackdrop -> LocalWallBackdrop.current?.let { wall ->
                    Modifier.drawBackdrop(
                        backdrop = wall,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(blurPx)
                            lens(lensPx, lensPx)
                        },
                        onDrawSurface = { drawRect(tint.copy(alpha = tintA)) }
                    )
                } ?: Modifier
                // 低版本 / 降级：取样玻璃
                else -> Modifier.glassSurface(
                    assets = assets,
                    shape = shape,
                    cornerDp = 22,
                    screen = screen,
                    blurLevel = blur,
                    density = density,
                    light = !Palette.dark,
                    tint = tint,
                    tintAlpha = tintA
                )
            }
            CompositionLocalProvider(LocalCardText provides if (Palette.dark) Color.White else Palette.text) {
                Column(modifier.fillMaxWidth().then(mod).padding(vertical = 6.dp), content = content)
            }
        }
    }
}

/** 一行设置项：图标 + 标题 + 副标题 + 右侧尾随内容 */
@Composable
fun KRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val text = LocalCardText.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val uiStyle = LocalStyle.current
        if (icon != null) {
            if (uiStyle == UiStyle.Miuix) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(miuixIconColor(icon)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
            } else {
                Icon(icon, contentDescription = null, tint = text, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = text, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, color = text.copy(alpha = 0.55f), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        if (trailing != null) {
            trailing()
        } else if (chevron && onClick != null) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = text.copy(alpha = 0.35f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** 选择行：右侧当前值 + 下拉箭头，弹窗贴行右对齐 */
@Composable
fun KSelectRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    value: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val text = LocalCardText.current
    Box {
        KRow(
            icon = icon,
            title = title,
            subtitle = subtitle,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(value, color = text.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = text.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            chevron = false,
            onClick = { open = true }
        )
        if (open) {
            LiquidChoiceDialog(
                backdrop = LocalWallBackdrop.current,
                title = title,
                options = options,
                selected = selected,
                onPick = { open = false; onPick(it) },
                onDismiss = { open = false }
            )
        }
    }
}

@Composable
fun KSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val text = LocalCardText.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val uiStyleS = LocalStyle.current
        if (uiStyleS == UiStyle.Miuix) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (enabled) miuixIconColor(icon) else miuixIconColor(icon).copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
        } else {
            Icon(
                icon, contentDescription = null,
                tint = if (enabled) text else text.copy(alpha = 0.35f),
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) text else text.copy(alpha = 0.4f),
                fontSize = 16.sp
            )
            if (subtitle != null) {
                Text(subtitle, color = text.copy(alpha = 0.55f), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        val wall = LocalWallBackdrop.current
        if (useBackdrop && wall != null) {
            Box(Modifier.width(58.dp).height(32.dp)) {
                LiquidToggle(
                    selected = { checked },
                    onSelect = { if (enabled) onCheckedChange(it) },
                    backdrop = wall,
                    modifier = Modifier.matchParentSize()
                )
            }
        } else {
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Palette.accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = if (Palette.dark) Color(0xFF3A3C42) else Color(0xFFCED2D8),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun PageTitle(text: String) {
    val color = LocalTitleColor.current
    val shadow = if (color.luminance() > 0.5f) {
        Shadow(Color.White.copy(alpha = 0.55f), Offset(0f, 1f), 6f)
    } else {
        Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 6f)
    }
    Text(
        text,
        color = color,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        style = LocalTextStyle.current.copy(shadow = shadow),
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 14.dp)
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Palette.text,
    unfocusedTextColor = Palette.text,
    disabledTextColor = Palette.text2,
    focusedContainerColor = Palette.inset,
    unfocusedContainerColor = Palette.inset,
    cursorColor = Palette.accent,
    focusedBorderColor = Palette.accent.copy(alpha = 0.6f),
    unfocusedBorderColor = if (Palette.dark) Color(0xFF3A3C42) else Color(0xFFE3E5E9),
    focusedLabelColor = Palette.accent,
    unfocusedLabelColor = Palette.text2,
    focusedPlaceholderColor = Palette.text2,
    unfocusedPlaceholderColor = Palette.text2
)

@Composable
fun SliderRow(style: UiStyle, label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = LocalCardText.current, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("${value.toInt()}%", color = LocalCardText.current.copy(alpha = 0.55f), fontSize = 14.sp)
        }
        val wall = LocalWallBackdrop.current
        if (useBackdrop && wall != null) {
            // demo 的 LiquidSlider 原样使用（手势已经在 DragGestureInspector 里改成 Initial 阶段抢占）
            LiquidSlider(
                value = { value },
                onValueChange = { onChange(it) },
                valueRange = 0f..100f,
                visibilityThreshold = 0.01f,
                backdrop = wall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )
        } else if (style == UiStyle.Miuix) {
            // 用 miuix 库自带的滑块
            MiuixSlider(
                value = value,
                onValueChange = onChange,
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Palette.accent,
                    inactiveTrackColor = if (Palette.dark) Color(0xFF3A3C42) else Color(0xFFE1E4E8)
                )
            )
        }
    }
}

/** 弹窗：跟随主题的卡片 */
@Composable
fun KDialog(
    style: UiStyle,
    title: String,
    onDismiss: () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val shape = remember { RoundedCornerShape(26.dp) }
        val wall = LocalWallBackdrop.current
        Box(Modifier.fillMaxSize().padding(26.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (useBackdrop && wall != null) {
                            Modifier.drawBackdrop(
                                backdrop = wall,
                                shape = { shape },
                                effects = {
                                    vibrancy()
                                    blur(8.dp.toPx())
                                    lens(24.dp.toPx(), 24.dp.toPx())
                                },
                                onDrawSurface = {
                                    drawRect(
                                        if (Palette.dark) Color(0xFF121212).copy(alpha = 0.6f)
                                        else Color(0xFFFAFAFA).copy(alpha = 0.6f)
                                    )
                                }
                            )
                        } else {
                            Modifier.clip(shape).background(Palette.surface)
                        }
                    )
                    .padding(20.dp)
            ) {
                Text(title, color = Palette.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                body()
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    style: UiStyle,
    lang: Lang,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    KDialog(style, title, onDismiss) {
        Text(message, color = Palette.text2, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(tr(lang, "取消", "Cancel"), color = Palette.text2) }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) { Text(tr(lang, "擦除", "Erase")) }
        }
    }
}

@Composable
private fun RomDialog(
    style: UiStyle,
    lang: Lang,
    steps: List<RomFlasher.Step>,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    KDialog(style, tr(lang, "线刷步骤（${steps.size}）", "Flash steps (${steps.size})"), onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            steps.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${i + 1}.", color = Palette.text2, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        s.label(),
                        color = if (s.missing) ErrColor else Palette.text,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(tr(lang, "取消", "Cancel"), color = Palette.text2) }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) { Text(tr(lang, "开始刷写", "Start")) }
        }
    }
}

@Composable
private fun PartsDialog(
    style: UiStyle,
    lang: Lang,
    list: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onClose: () -> Unit
) {
    KDialog(style, tr(lang, "分区表（${list.size}）", "Partitions (${list.size})"), onClose) {
        if (list.isEmpty()) {
            Text(tr(lang, "没有分区信息", "No partitions"), color = Palette.text2, fontSize = 14.sp)
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(list) { (name, size) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(name) }
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, color = Palette.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(size, color = Palette.text2, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text(tr(lang, "关闭", "Close"), color = Palette.text2) }
        }
    }
}

// ======================= 主页 =======================

@Composable
fun HomePage(
    style: UiStyle,
    assets: GlassAssets?,
    backdrop: Backdrop,
    screen: IntSize,
    blur: Float,
    density: Float,
    alpha: Float,
    lang: Lang,
    mode: Int,
    modeName: String,
    host: String,
    connected: Boolean,
    busy: Boolean,
    logs: List<LogLine>,
    part: String,
    onModePick: (Int) -> Unit,
    onConnect: () -> Unit,
    onInfo: () -> Unit,
    onParts: () -> Unit,
    onFlash: () -> Unit,
    onRom: () -> Unit,
    onBackup: (String) -> Unit,
    onBackupBaseband: () -> Unit,
    onReboot: (String) -> Unit,
    onEdl: () -> Unit,
    onErase: (String) -> Unit,
    onRoot: () -> Unit,
    onPing: () -> Unit,
    onRun: (String) -> Unit,
    onClear: () -> Unit
) {
    var cmd by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 110.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (safeRender) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (Palette.dark) Color(0xFF3A2E12) else Color(0xFFFFF3D6))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    Icons.Filled.Warning, null,
                    tint = if (Palette.dark) Color(0xFFFFC04D) else Color(0xFF8A5A00),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    tr(
                        lang,
                        "上次启动异常，已用安全模式启动（关闭玻璃特效）。设置里可查看崩溃日志",
                        "Last launch failed; running in safe mode (glass disabled). See crash log in Settings"
                    ),
                    color = if (Palette.dark) Color(0xFFFFD98A) else Color(0xFF6B4700),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        PageTitle("Easy-platform-tools")
        Text(
            when (mode) {
                0 -> tr(lang, "OTG 主机 · ${if (connected) "已连接" else "未连接"}",
                    "OTG host · ${if (connected) "connected" else "disconnected"}")
                1 -> tr(lang, "本机 root · 需要 su", "Local root · requires su")
                else -> tr(lang, "遥控 PC · $host", "Remote PC · $host")
            },
            color = LocalTitleColor.current.copy(alpha = 0.75f),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        KCard(style, assets, backdrop, screen, blur, density, alpha) {
            KSelectRow(
                icon = Icons.Filled.Build,
                title = tr(lang, "使用模式", "Mode"),
                subtitle = null,
                value = modeName,
                options = listOf(
                    tr(lang, "OTG 主机", "OTG host"),
                    tr(lang, "本机 root", "Local root"),
                    tr(lang, "遥控 PC", "Remote PC")
                ),
                selected = mode,
                onPick = onModePick
            )
        }

        Spacer(Modifier.height(12.dp))
        KCard(style, assets, backdrop, screen, blur, density, alpha) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassField(
                    value = cmd,
                    onValueChange = { cmd = it },
                    placeholder = if (mode == 1) tr(lang, "shell 命令", "shell command")
                        else tr(lang, "fastboot 命令", "fastboot command"),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onRun(cmd); cmd = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(tr(lang, "执行", "Run")) }
            }
        }
        Spacer(Modifier.height(16.dp))

        Spacer(Modifier.height(12.dp))

        KCard(style, assets, backdrop, screen, blur, density, alpha) {
            val text = LocalCardText.current
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.List, null, tint = text, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(14.dp))
                Text(tr(lang, "日志", "Log"), color = text, fontSize = 16.sp)
                if (busy) {
                    Spacer(Modifier.width(8.dp))
                    Text(tr(lang, "· 执行中", "· running"), color = Palette.accent, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    tr(lang, "清空", "Clear"),
                    color = Palette.accent, fontSize = 13.sp,
                    modifier = Modifier.clickable { onClear() }
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (style == UiStyle.Native || style == UiStyle.Glass) Color.Black.copy(alpha = 0.22f) else Palette.inset)
            ) {
                LazyColumn(Modifier.fillMaxSize().padding(10.dp)) {
                    items(logs) { l -> Text(l.text, color = l.color, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))

        when (mode) {
            0 -> KCard(style, assets, backdrop, screen, blur, density, alpha) {
                KRow(Icons.Filled.PlayArrow, tr(lang, "连接设备", "Connect device"),
                    tr(lang, "USB 授权后自动打开", "auto after USB grant"), onClick = onConnect)
                KRow(Icons.Filled.Info, tr(lang, "设备信息", "Device info"),
                    "product / version / serialno", onClick = onInfo)
                KRow(Icons.Filled.List, tr(lang, "读分区表", "Partitions"),
                    "fastboot getvar all", onClick = onParts)
                KRow(Icons.Filled.Build, tr(lang, "刷入镜像", "Flash image"),
                    tr(lang, "当前分区：$part", "target: $part"), onClick = onFlash)
                KRow(Icons.Filled.Refresh, tr(lang, "线刷 ROM", "Flash ROM"),
                    tr(lang, "选目录，按脚本批量刷", "pick a folder, flash in order"), onClick = onRom)
                KRow(Icons.Filled.Delete, tr(lang, "擦除分区", "Erase partition"), part,
                    onClick = { onErase(part) })
                KRow(Icons.Filled.Refresh, tr(lang, "重启系统", "Reboot"), null, onClick = { onReboot("") })
                KRow(Icons.Filled.Refresh, tr(lang, "重启 Recovery", "Reboot recovery"), "recovery",
                    onClick = { onReboot("recovery") })
                KRow(Icons.Filled.Refresh, tr(lang, "重启 Bootloader", "Reboot bootloader"), "bootloader",
                    onClick = { onReboot("bootloader") })
                KRow(Icons.Filled.Refresh, tr(lang, "进入 EDL", "Enter EDL"), "oem edl", onClick = onEdl)
            }
            1 -> KCard(style, assets, backdrop, screen, blur, density, alpha) {
                KRow(Icons.Filled.Lock, tr(lang, "检测 root", "Check root"), "su -c id", onClick = onRoot)
                KRow(Icons.Filled.List, tr(lang, "读分区表", "Partitions"), "/dev/block/by-name", onClick = onParts)
                KRow(Icons.Filled.Star, tr(lang, "备份分区", "Back up partition"),
                    tr(lang, "把 $part 导出为镜像", "dump $part to a file"), onClick = { onBackup(part) })
                KRow(Icons.Filled.Star, tr(lang, "备份基带", "Back up baseband"),
                    tr(lang, "modem / persist / fsg 等", "modem / persist / fsg …"), onClick = onBackupBaseband)
                KRow(Icons.Filled.Refresh, tr(lang, "重启系统", "Reboot"), "reboot", onClick = { onReboot("") })
                KRow(Icons.Filled.Refresh, tr(lang, "重启 Recovery", "Reboot recovery"), "reboot recovery",
                    onClick = { onReboot("recovery") })
                KRow(Icons.Filled.Refresh, tr(lang, "重启 EDL", "Reboot EDL"), "reboot edl", onClick = onEdl)
            }
            else -> KCard(style, assets, backdrop, screen, blur, density, alpha) {
                KRow(Icons.Filled.PlayArrow, tr(lang, "测试连接", "Ping"), "/api/status", onClick = onPing)
                KRow(Icons.Filled.List, tr(lang, "读分区表", "Partitions"), "/api/partitions", onClick = onParts)
                KRow(Icons.Filled.Refresh, tr(lang, "重启系统", "Reboot"), "/api/reboot", onClick = { onReboot("") })
                KRow(Icons.Filled.Refresh, tr(lang, "重启 Recovery", "Reboot recovery"), "recovery",
                    onClick = { onReboot("recovery") })
            }
        }

        Spacer(Modifier.height(12.dp))

        }
}

// ======================= 设置 =======================

@Composable
fun SettingsPage(
    style: UiStyle,
    assets: GlassAssets?,
    backdrop: Backdrop,
    screen: IntSize,
    blur: Float,
    density: Float,
    alpha: Float,
    lang: Lang,
    onShareLog: () -> Unit,
    onForceSafe: (Boolean) -> Unit,
    onAbout: () -> Unit,
    onPlayground: () -> Unit,
    themeMode: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    monet: Boolean,
    onMonet: (Boolean) -> Unit,
    canMonet: Boolean,
    onStyle: (UiStyle) -> Unit,
    onLang: (Lang) -> Unit,
    opacity: Float,
    onOpacity: (Float) -> Unit,
    glassBlur: Float,
    onGlassBlur: (Float) -> Unit,
    glassDensity: Float,
    onGlassDensity: (Float) -> Unit,
    customWallpaper: Boolean,
    onPickWallpaper: () -> Unit,
    onResetWallpaper: () -> Unit,
    parts: List<String>,
    part: String,
    onPart: (String) -> Unit,
    host: String,
    onHost: (String) -> Unit,
    token: String,
    onToken: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 110.dp)
            .verticalScroll(rememberScrollState())
    ) {
        var showPartPicker by remember { mutableStateOf(false) }
        val partOptions = remember(parts) {
            if (parts.isEmpty()) listOf("boot", "init_boot", "vbmeta", "dtbo", "super", "system", "vendor", "recovery")
            else parts
        }

        PageTitle(tr(lang, "设置", "Settings"))

        KCard(style, assets, backdrop, screen, blur, density, alpha) {
            KSelectRow(
                icon = Icons.Filled.Edit,
                title = tr(lang, "界面样式", "Style"),
                subtitle = tr(lang, "选择应用的界面风格", "Choose the app style"),
                value = tr(lang, style.labelZh, style.labelEn),
                options = UiStyle.entries.map { tr(lang, it.labelZh, it.labelEn) },
                selected = style.ordinal,
                onPick = { onStyle(UiStyle.entries[it]) }
            )
            KSelectRow(
                icon = Icons.Filled.Public,
                title = tr(lang, "语言", "Language"),
                subtitle = null,
                value = if (lang == Lang.Zh) "中文" else "English",
                options = listOf("中文", "English"),
                selected = lang.ordinal,
                onPick = { onLang(Lang.entries[it]) }
            )
        }

        Spacer(Modifier.height(12.dp))

        KCard(style, assets, backdrop, screen, blur, density, alpha) {
            KSelectRow(
                icon = Icons.Filled.Star,
                title = tr(lang, "夜间模式", "Dark mode"),
                subtitle = tr(lang, "日间 / 夜间 / 跟随系统", "Light / Dark / Follow system"),
                value = tr(lang, themeMode.zh, themeMode.en),
                options = ThemeMode.entries.map { tr(lang, it.zh, it.en) },
                selected = themeMode.ordinal,
                onPick = { onTheme(ThemeMode.entries[it]) }
            )
            if (style == UiStyle.Material) {
                KSwitchRow(
                    icon = Icons.Filled.Edit,
                    title = tr(lang, "莫奈取色", "Monet colors"),
                    subtitle = if (canMonet) tr(lang, "从壁纸提取主题色（Material 风格）", "Extract theme color from wallpaper (Material)")
                    else tr(lang, "需要 Android 12 以上", "Requires Android 12+"),
                    checked = monet && canMonet,
                    enabled = canMonet,
                    onCheckedChange = onMonet
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        KCard(style, assets, backdrop, screen, blur, density, alpha) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(tr(lang, "壁纸", "Wallpaper"), color = LocalCardText.current, fontSize = 16.sp)
                Text(
                    if (customWallpaper) tr(lang, "当前：自定义壁纸", "Current: custom wallpaper")
                    else tr(lang, "当前：内置壁纸", "Current: built-in wallpaper"),
                    color = LocalCardText.current.copy(alpha = 0.55f), fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val wall = LocalWallBackdrop.current
                    if (useBackdrop && wall != null) {
                        // 照抄 demo：一个 tint 的、一个 surfaceColor 的
                        LiquidButton(
                            onClick = onPickWallpaper,
                            backdrop = wall,
                            modifier = Modifier.weight(1f).height(44.dp),
                            tint = Palette.accent
                        ) {
                            Text(tr(lang, "选择图片", "Choose"), color = Color.White, fontSize = 14.sp)
                        }
                        LiquidButton(
                            onClick = onResetWallpaper,
                            backdrop = wall,
                            modifier = Modifier.weight(1f).height(44.dp),
                            surfaceColor = Color.White.copy(alpha = 0.3f)
                        ) {
                            Text(tr(lang, "恢复默认", "Reset"), color = LocalCardText.current, fontSize = 14.sp)
                        }
                    } else {
                        Button(
                            onClick = onPickWallpaper,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(tr(lang, "选择图片", "Choose"), fontSize = 13.sp) }
                        Button(
                            onClick = onResetWallpaper,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalCardText.current.copy(alpha = 0.10f),
                                contentColor = LocalCardText.current
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(tr(lang, "恢复默认", "Reset"), fontSize = 13.sp) }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (style == UiStyle.Glass) {
        KCard(style, assets, backdrop, screen, blur, density, alpha) {
            Column(Modifier.padding(vertical = 8.dp)) {
                SliderRow(style, tr(lang, "面板不透明度", "Panel opacity"), opacity, onOpacity)
                SliderRow(style, tr(lang, "玻璃模糊", "Glass blur"), glassBlur, onGlassBlur)
                SliderRow(style, tr(lang, "玻璃浓度", "Glass density"), glassDensity, onGlassDensity)
                if (!realGlass) {
                    Text(
                        tr(
                            lang,
                            "本机 API ${Build.VERSION.SDK_INT}：不支持实时模糊，已用取样玻璃",
                            "API ${Build.VERSION.SDK_INT}: no real-time blur, using sampled glass"
                        ),
                        color = LocalCardText.current.copy(alpha = 0.45f), fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )
                }
            }
        }
        }

        Spacer(Modifier.height(12.dp))

        KCard(style, assets, backdrop, screen, blur, density, alpha) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(tr(lang, "刷写", "Flashing"), color = LocalCardText.current, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                var p by remember { mutableStateOf(part) }
                LaunchedEffect(part) { p = part }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassField(
                        value = p,
                        onValueChange = { p = it; onPart(it) },
                        placeholder = tr(lang, "目标分区，例如 boot", "partition, e.g. boot"),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Palette.accent.copy(alpha = 0.16f))
                            .clickable { showPartPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.List, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        if (showPartPicker) {
            LiquidChoiceDialog(
                backdrop = LocalWallBackdrop.current,
                title = tr(lang, "选择分区", "Pick partition"),
                options = partOptions,
                selected = partOptions.indexOf(part),
                onPick = { showPartPicker = false; onPart(partOptions[it]) },
                onDismiss = { showPartPicker = false }
            )
        }

        Spacer(Modifier.height(12.dp))

        KCard(style, assets, backdrop, screen, blur, density, alpha) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(tr(lang, "遥控 PC", "Remote PC"), color = LocalCardText.current, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                GlassField(
                    value = host,
                    onValueChange = onHost,
                    placeholder = tr(lang, "电脑地址，例如 192.168.1.10:8765", "PC address"),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                GlassField(
                    value = token,
                    onValueChange = onToken,
                    placeholder = tr(lang, "配对码", "Pairing code"),
                    numeric = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        KCard(style, assets, backdrop, screen, blur, density, alpha) {
            val ctx = LocalContext.current
            var showCrash by remember { mutableStateOf(false) }
            val crashFile = remember { File(ctx.filesDir, "last-crash.txt") }
            val actionFile = remember { File(ctx.filesDir, "last-action.txt") }
            KRow(
                Icons.Filled.Share,
                tr(lang, "导出/分享日志", "Export / share log"),
                tr(lang, "把日志发给开发者", "Send the log to the developer"),
                onClick = { onShareLog() }
            )
            if (crashFile.exists() || actionFile.exists()) {
                KRow(
                    Icons.Filled.Warning,
                    tr(lang, "查看崩溃日志", "View crash log"),
                    if (actionFile.exists()) tr(lang, "上次崩在：切换样式", "Crashed after: style switch")
                    else tr(lang, "上次启动的异常信息", "Last launch exception"),
                    onClick = { showCrash = true }
                )
            }
            KSwitchRow(
                icon = Icons.Filled.Refresh,
                title = tr(lang, "安全模式", "Safe mode"),
                subtitle = tr(lang, "关闭玻璃特效，渲染出问题时用", "Disable glass effects when rendering fails"),
                checked = safeRender,
                enabled = false,
                onCheckedChange = { }
            )
            if (showCrash) {
                val crashText = remember {
                    buildString {
                        if (crashFile.exists()) append(runCatching { crashFile.readText() }.getOrDefault(""))
                        if (actionFile.exists()) {
                            append("\n\n—— 最后动作 ——\n")
                            append(runCatching { actionFile.readText() }.getOrDefault(""))
                        }
                    }
                }
                KDialog(style, tr(lang, "崩溃日志", "Crash log"), onDismiss = { showCrash = false }) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(crashText, color = Palette.text2, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showCrash = false }) {
                            Text(tr(lang, "关闭", "Close"), color = Palette.text2)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        KCard(style, assets, backdrop, screen, blur, density, alpha) {
            KRow(
                Icons.Filled.Info,
                tr(lang, "关于", "About"),
                tr(lang, "版本信息与许可", "Version and license"),
                onClick = { onAbout() }
            )
            if (style == UiStyle.Glass) {
                KRow(
                    Icons.Filled.Build,
                    tr(lang, "Glass Playground", "Glass Playground"),
                    tr(lang, "实时调玻璃参数", "Tune glass parameters live"),
                    onClick = { onPlayground() }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ======================= 悬浮底栏 =======================

@Composable
fun LiquidBottomBar(
    modifier: Modifier = Modifier,
    assets: GlassAssets?,
    backdrop: Backdrop,
    screen: IntSize,
    page: Int,
    blur: Float,
    density: Float,
    items: List<BarItem>,
    onPage: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val n = items.size
    val anim = remember { Animatable(page.toFloat()) }
    var drag by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    val capsule = remember { RoundedCornerShape(32.dp) }
    val pillShape = remember { RoundedCornerShape(26.dp) }
    val dark = Palette.dark
    val labelIdle = if (dark) Color(0xFFD8DAE0) else Palette.text

    LaunchedEffect(page) {
        if (abs(anim.value - page) > 0.01f) anim.animateTo(page.toFloat(), spring(0.85f, 420f, 0.5f))
    }

    // 手势闭包不能捕获 page（会一直是首帧的值），用 rememberUpdatedState 取最新
    val pageState = rememberUpdatedState(page)
    val onPageState = rememberUpdatedState(onPage)

    val settle: () -> Unit = {
        scope.launch {
            pressed = false
            val cur = (anim.value + drag).coerceIn(0f, (n - 1).toFloat())
            drag = 0f
            anim.snapTo(cur)
            val target = cur.roundToInt().coerceIn(0, n - 1)
            anim.animateTo(target.toFloat(), spring(0.85f, 420f, 0.5f))
            onPageState.value(target)
        }
    }

    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(66.dp)) {
            val tabW = maxWidth / n
            val tabPx = with(LocalDensity.current) { tabW.toPx() }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(n, tabPx) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                pressed = true
                                scope.launch { anim.stop() }
                            },
                            onDragEnd = { settle() },
                            onDragCancel = { settle() },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                val next = (anim.value + drag + amount / tabPx).coerceIn(0f, (n - 1).toFloat())
                                drag = next - anim.value
                            }
                        )
                    }
            ) {
                // —— 以下参数照抄 AndroidLiquidGlass 的 LiquidBottomTabs ——
                val containerColor = if (dark) Color(0xFF121212).copy(alpha = 0.4f)
                else Color(0xFFFAFAFA).copy(alpha = 0.4f)
                val surfaceMod = when {
                    useBackdrop -> Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { capsule },
                        effects = {
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(24f.dp.toPx(), 24f.dp.toPx())
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    !safeRender -> Modifier.glassSurface(
                        assets = assets,
                        shape = capsule,
                        cornerDp = 32,
                        screen = screen,
                        blurLevel = 0.1f,
                        density = 0.5f,
                        light = !dark,
                        tint = if (dark) Color(0xFF14161A) else Color.White,
                        tintAlpha = if (dark) 0.72f else 0.62f
                    )
                    else -> Modifier
                        .shadow(12.dp, capsule, clip = false)
                        .clip(capsule)
                        .background(if (dark) Color(0xFF1B1C20) else Color.White)
                }
                Box(Modifier.matchParentSize().then(surfaceMod))

                // 选中块（无阴影、无涟漪）
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                ((anim.value + drag).coerceIn(0f, (n - 1).toFloat()) * tabPx).roundToInt(),
                                0
                            )
                        }
                        .width(tabW)
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .clip(pillShape)
                        .background(Palette.accent.copy(alpha = if (dark) 0.28f else 0.14f))
                )

                Row(Modifier.matchParentSize()) {
                    items.forEachIndexed { i, item ->
                        val sel = pageState.value == i
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onPageState.value(i) },
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = if (sel) Palette.accent else labelIdle,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                item.label,
                                color = if (sel) Palette.accent else labelIdle,
                                fontSize = 11.sp,
                                fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

// ======================= 关于 / Glass Playground =======================

/**
 * 玻璃弹窗面板 —— 结构/参数照抄 AndroidLiquidGlass demo 的 DialogContent：
 * 压暗 + RoundedRectangle(48dp) + colorControls + blur + lens(depthEffect) + Highlight.Plain
 */
@Composable
fun GlassDialogPanel(
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val light = !Palette.dark
    val containerColor = if (light) Color(0xFFFAFAFA).copy(alpha = 0.6f) else Color(0xFF121212).copy(alpha = 0.4f)
    val dimColor = if (light) Color(0xFF29293A).copy(alpha = 0.23f) else Color(0xFF121212).copy(alpha = 0.56f)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            // 压暗层（点空白关闭）
            Box(
                Modifier
                    .fillMaxSize()
                    .background(dimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(40.dp)
                    .fillMaxWidth()
                    .then(
                        if (useBackdrop && backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(48.dp) },
                                effects = {
                                    colorControls(
                                        brightness = if (light) 0.2f else 0f,
                                        saturation = 1.5f
                                    )
                                    blur(if (light) 16.dp.toPx() else 8.dp.toPx())
                                    lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                                },
                                highlight = { Highlight.Plain },
                                onDrawSurface = { drawRect(containerColor) }
                            )
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(48.dp))
                                .background(if (light) Color(0xFFFAFAFA) else Color(0xFF1B1C20))
                        }
                    )
            ) {
                content()
            }
        }
    }
}

@Composable
fun AboutDialog(style: UiStyle, lang: Lang, backdrop: Backdrop?, onDismiss: () -> Unit) {
    val light = !Palette.dark
    val contentColor = if (light) Color.Black else Color.White
    val containerColor = if (light) Color(0xFFFAFAFA).copy(alpha = 0.6f) else Color(0xFF121212).copy(alpha = 0.4f)
    val ctx = LocalContext.current
    val ver = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    GlassDialogPanel(backdrop, onDismiss) {
        Text(
            tr(lang, "关于", "About"),
            color = contentColor, fontSize = 24.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(28.dp, 24.dp, 28.dp, 12.dp)
        )
        Text(
            "Easy-platform-tools-GUI",
            color = contentColor, fontSize = 17.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(24.dp, 0.dp, 24.dp, 2.dp)
        )
        Text(
            "$ver · Compose · MIT",
            color = contentColor.copy(alpha = 0.7f), fontSize = 14.sp,
            modifier = Modifier.padding(24.dp, 0.dp, 24.dp, 10.dp)
        )
        Text(
            tr(
                lang,
                "OTG 主机直连 fastboot（不依赖 adb / root）\n" +
                    "本机 root 下备份、刷写分区\n遥控 PC 上的服务\n线刷 ROM（解析 flash_all 脚本）",
                "OTG host fastboot (no adb / no root)\n" +
                    "Backup & flash partitions with root\nRemote PC service\nROM line-flashing"
            ),
            color = contentColor.copy(alpha = 0.68f), fontSize = 14.sp, lineHeight = 21.sp,
            modifier = Modifier.padding(24.dp, 0.dp, 24.dp, 10.dp)
        )
        Text(
            "玻璃效果来自 Kyant0/AndroidLiquidGlass (Apache-2.0)",
            color = contentColor.copy(alpha = 0.5f), fontSize = 11.sp,
            modifier = Modifier.padding(24.dp, 0.dp, 24.dp, 12.dp)
        )
        Row(
            Modifier.padding(24.dp, 4.dp, 24.dp, 24.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (useBackdrop && backdrop != null) {
                // demo 的 LiquidButton
                LiquidButton(
                    onClick = onDismiss,
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f).height(48.dp),
                    surfaceColor = Color.White.copy(alpha = 0.3f)
                ) {
                    Text(tr(lang, "关闭", "Close"), color = contentColor, fontSize = 16.sp)
                }
                LiquidButton(
                    onClick = onDismiss,
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f).height(48.dp),
                    tint = Palette.accent
                ) {
                    Text(tr(lang, "好", "OK"), color = Color.White, fontSize = 16.sp)
                }
            } else {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(containerColor.copy(alpha = 0.2f))
                        .clickable { onDismiss() }
                        .height(48.dp)
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) { Text(tr(lang, "关闭", "Close"), color = contentColor, fontSize = 16.sp) }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Palette.accent)
                        .clickable { onDismiss() }
                        .height(48.dp)
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) { Text(tr(lang, "好", "OK"), color = Color.White, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
fun GlassPlaygroundOverlay(assets: GlassAssets?, lang: Lang, onClose: () -> Unit) {
    var blurDp by remember { mutableFloatStateOf(8f) }
    var lensDp by remember { mutableFloatStateOf(24f) }
    var tintPct by remember { mutableFloatStateOf(40f) }
    val bd = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        // 背景进图层（照 demo：图层里只有背景）
        Box(
            Modifier
                .fillMaxSize()
                .then(if (useBackdrop) Modifier.layerBackdrop(bd) else Modifier)
        ) {
            WallpaperView(assets)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (Palette.dark) 0.45f else 0.25f)))
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Glass Playground", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .then(
                        if (useBackdrop) {
                            Modifier.drawBackdrop(
                                backdrop = bd,
                                shape = { RoundedCornerShape(40.dp) },
                                effects = {
                                    vibrancy()
                                    blur(blurDp.dp.toPx())
                                    lens(lensDp.dp.toPx(), lensDp.dp.toPx())
                                },
                                onDrawSurface = { drawRect(Color.White.copy(alpha = tintPct / 100f)) }
                            )
                        } else {
                            Modifier.clip(RoundedCornerShape(40.dp)).background(Color(0x66FFFFFF))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("Liquid Glass", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            SliderRow(lang, bd, tr(lang, "模糊", "Blur"), blurDp, 0f, 48f) { blurDp = it }
            SliderRow(lang, bd, tr(lang, "折射", "Lens"), lensDp, 0f, 48f) { lensDp = it }
            SliderRow(lang, bd, tr(lang, "底料", "Tint"), tintPct, 0f, 100f) { tintPct = it }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (useBackdrop) {
                    LiquidButton(onClick = onClose, backdrop = bd, modifier = Modifier.height(48.dp)) {
                        Text(tr(lang, "关闭", "Close"), color = Color.White, fontSize = 15.sp)
                    }
                } else {
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Color.White),
                        shape = RoundedCornerShape(50)
                    ) { Text(tr(lang, "关闭", "Close")) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SliderRow(
    lang: Lang,
    bd: Backdrop,
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.width(64.dp))
            Text("${value.toInt()}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        if (useBackdrop) {
            LiquidSlider(
                value = { value },
                onValueChange = onChange,
                valueRange = min..max,
                visibilityThreshold = 0.01f,
                backdrop = bd,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Slider(value = value, onValueChange = onChange, valueRange = min..max)
        }
    }
}

/**
 * 选择弹窗：结构照抄 demo 的 DialogContent ——
 * 压暗 + 48dp 玻璃面板 + colorControls/blur/lens(depthEffect) + Highlight.Plain + 胶囊按钮
 */
@Composable
fun LiquidChoiceDialog(
    backdrop: Backdrop?,
    title: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val light = !Palette.dark
    val contentColor = if (light) Color.Black else Color.White
    val containerColor = if (light) Color(0xFFFAFAFA).copy(alpha = 0.6f) else Color(0xFF121212).copy(alpha = 0.4f)
    val dimColor = if (light) Color(0xFF29293A).copy(alpha = 0.23f) else Color(0xFF121212).copy(alpha = 0.56f)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(dimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(40.dp)
                    .fillMaxWidth()
                    .then(
                        if (useBackdrop && backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(48.dp) },
                                effects = {
                                    colorControls(brightness = if (light) 0.2f else 0f, saturation = 1.5f)
                                    blur(if (light) 16.dp.toPx() else 8.dp.toPx())
                                    lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                                },
                                highlight = { Highlight.Plain },
                                onDrawSurface = { drawRect(containerColor) }
                            )
                        } else {
                            Modifier.clip(RoundedCornerShape(48.dp)).background(Palette.surface)
                        }
                    )
            ) {
                Text(
                    title,
                    color = contentColor, fontSize = 24.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(28.dp, 24.dp, 28.dp, 12.dp)
                )
                options.forEachIndexed { i, name ->
                    val sel = i == selected
                    Row(
                        Modifier
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) containerColor.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable { onPick(i) }
                            .height(48.dp)
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, color = if (sel) Palette.accent else contentColor, fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        if (sel) Icon(Icons.Filled.Check, null, tint = Palette.accent, modifier = Modifier.size(20.dp))
                    }
                }
                Row(
                    Modifier.padding(24.dp, 12.dp, 24.dp, 24.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(containerColor.copy(alpha = 0.2f))
                            .clickable { onDismiss() }
                            .height(48.dp)
                            .weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) { Text("取消", color = contentColor, fontSize = 16.sp) }
                }
            }
        }
    }
}

/**
 * 玻璃输入框：内部是 BasicTextField，外观用 demo 的 drawBackdrop（vibrancy + blur），
 * 不再用 M3 OutlinedTextField 那个白底黑字的方块。
 */
@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    numeric: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val wall = LocalWallBackdrop.current
    val text = LocalCardText.current
    val shape = RoundedCornerShape(16.dp)

    val surface = if (useBackdrop && wall != null) {
        Modifier.drawBackdrop(
            backdrop = wall,
            shape = { shape },
            effects = {
                vibrancy()
                blur(8.dp.toPx())
            },
            onDrawSurface = {
                drawRect(
                    if (Palette.dark) Color(0xFF121212).copy(alpha = 0.45f)
                    else Color(0xFFFAFAFA).copy(alpha = 0.55f)
                )
            }
        )
    } else {
        Modifier
            .clip(shape)
            .background(if (Palette.dark) Color(0xFF1C1D22) else Color(0xFFF1F3F7))
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        singleLine = true,
        textStyle = TextStyle(color = text, fontSize = 15.sp),
        cursorBrush = SolidColor(Palette.accent),
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number)
        else KeyboardOptions.Default,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(surface)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, color = text.copy(alpha = 0.45f), fontSize = 15.sp)
                }
                inner()
            }
        }
    )
}

/** 必应壁纸选择：拉 https://www.bing.com/HPImageArchive.aspx 的最近 10 张 */
@Composable
fun BingWallpaperDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var urls by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    val light = !Palette.dark
    val contentColor = if (light) Color.Black else Color.White

    LaunchedEffect(Unit) {
        val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val conn = java.net.URL("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=10&mkt=zh-CN")
                    .openConnection()
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val body = conn.getInputStream().bufferedReader().readText()
                val arr = org.json.JSONObject(body).getJSONArray("images")
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val base = o.getString("urlbase")
                    val title = o.optString("title", "")
                    title to ("https://www.bing.com" + base + "_UHD.jpg")
                }
            }
        }
        result.onSuccess { urls = it }.onFailure { err = it.message ?: "error" }
        loading = false
    }

    GlassDialogPanel(LocalWallBackdrop.current, onDismiss) {
        Text(
            "Bing Wallpaper",
            color = contentColor, fontSize = 24.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(28.dp, 24.dp, 28.dp, 8.dp)
        )
        when {
            loading -> Text(
                "加载中…",
                color = contentColor.copy(alpha = 0.6f), fontSize = 14.sp,
                modifier = Modifier.padding(24.dp, 8.dp, 24.dp, 24.dp)
            )
            err != null -> Text(
                "加载失败：$err",
                color = contentColor.copy(alpha = 0.6f), fontSize = 13.sp,
                modifier = Modifier.padding(24.dp, 8.dp, 24.dp, 24.dp)
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 420.dp).padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(urls.size) { i ->
                    val (title, url) = urls[i]
                    coil.compose.AsyncImage(
                        model = url,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .height(100.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPick(url) }
                    )
                }
            }
        }
        Row(
            Modifier.padding(24.dp, 12.dp, 24.dp, 24.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable { onDismiss() }
                    .height(48.dp)
                    .width(160.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) { Text("取消", color = contentColor, fontSize = 16.sp) }
        }
    }
}
