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
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.toArgb
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
// 官方 miuix 组件（照 https://github.com/compose-miuix-ui/miuix 的文档用法直接调）：
// 行一律 BasicComponent，开关/输入框/底栏都用库里自己的实现，不再自绘
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
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
    // 日志放到进程级单例（见文件末尾 AppLog）：
    // 这样 FlashService 也能写，Activity 重建也不丢
    private val logs get() = AppLog.lines
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
    private var romScripts by mutableStateOf<List<Pair<String, Uri>>>(emptyList())
    private var showRomScript by mutableStateOf(false)
    private var romProduct = ""
    private var showBigLog by mutableStateOf(false)
    private val logWriter = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var romFiles: Map<String, Uri> = emptyMap()
    private var pendingDest = ""
    private var eraseTarget by mutableStateOf<String?>(null)
    private var showMode by mutableStateOf(false)

    // ---------- 界面状态 ----------
    private var style by mutableStateOf(UiStyle.Native)
    private var lang by mutableStateOf(Lang.Zh)
    private var page by mutableIntStateOf(0)
    private var uiAlpha by mutableFloatStateOf(0f)
    private var glassBlur by mutableFloatStateOf(30f)
    private var glassDensity by mutableFloatStateOf(100f)
    private var glassAssets by mutableStateOf<GlassAssets?>(null)
    private var customWallpaper by mutableStateOf(false)
    private var themeMode by mutableStateOf(ThemeMode.System)
    private var monet by mutableStateOf(false)
    private var fontChoice by mutableStateOf(AppFont.System)
    /** 启动时自动检查更新（默认开启，可在设置里关掉） */
    private var autoUpdate by mutableStateOf(true)
    private var customFont by mutableStateOf<androidx.compose.ui.text.font.FontFamily?>(null)
    /** 在线字体已加载的家族，key = AppFont.name */
    private val onlineFamilies =
        androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.text.font.FontFamily>()
    /** 正在下载哪个在线字体：0 无 1 MiSans 2 HarmonyOS */
    /** 正在下载的在线字体 key（"" = 没有在下载） */
    private var fontTargetKey by mutableStateOf("")

    /** 导入过的本地字体（显示名 -> 文件），持久化在 prefs，导入一次就一直可选 */
    private val customFonts =
        androidx.compose.runtime.mutableStateListOf<Pair<String, java.io.File>>()
    /** 当前使用的本地字体路径 */
    private var customFontPath by mutableStateOf<String?>(null)
    /** 路径 -> FontFamily 缓存 */
    private val customFamilies =
        androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.text.font.FontFamily>()
    /** 字体下载进度 0..100，-1 表示没在下载 */
    private var fontProgress by mutableIntStateOf(-1)
    // 日志 4 种颜色：null = 用主题默认（和以前逐像素一致），非 null = 用户在「日志颜色」里选过的
    private var logCmdColor by mutableStateOf<Color?>(null)
    private var logOkColor by mutableStateOf<Color?>(null)
    private var logErrColor by mutableStateOf<Color?>(null)
    private var logTextColor by mutableStateOf<Color?>(null)
    /** 日志实际使用的颜色（可配置优先，否则回落到主题默认） */
    private val CmdColor: Color get() = logCmdColor ?: Accent
    private val OkText: Color get() = logOkColor ?: OkColor
    private val ErrText: Color get() = logErrColor ?: ErrColor
    private val LogText: Color get() = logTextColor ?: LogColor
    private var showLogColors by mutableStateOf(false)
    private var fontPicker: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null
    private var pickFlash: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null
    private var pickRomZip: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null
    private var showAbout by mutableStateOf(false)
    private var showPlayground by mutableStateOf(false)
    /** 首次使用向导 */
    private var showOnboarding by mutableStateOf(false)
    private var crashBannerClosed by mutableStateOf(false)
    private var showWpSource by mutableStateOf(false)
    private var showBing by mutableStateOf(false)


    private fun t(zh: String, en: String) = tr(lang, zh, en)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // root 快捷刷入镜像 / 线刷压缩包
        pickFlash = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri -> if (uri != null) localFlash(uri) }
        pickRomZip = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri -> if (uri != null) loadRomArchive(uri) }
        // OTG fastboot 必须是单例：以前这个 lateinit 从没赋值，点「连接设备」就抛
        // lateinit property fb has not been initialized
        fb = FastbootUsb(this)

        // 本地字体选择器（ttf / otf / ttc）
        fontPicker = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri -> if (uri != null) installLocalFont(uri) }
        val spBoot = getSharedPreferences(PREF, MODE_PRIVATE)
        // 覆盖安装/升级时进程是被系统直接杀掉的，会留下看起来像「崩溃」的轨迹。
        // 用包的 lastUpdateTime 判断是不是升级后的第一次启动，是的话把残留轨迹当噪声清掉，
        // 否则每次更新都会把用户选的样式偷偷降级成原生。
        runCatching {
            val pkgTime = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
            if (spBoot.getLong("pkgTime", 0L) != pkgTime) {
                spBoot.edit().putLong("pkgTime", pkgTime).apply()
                File(filesDir, "boot-trace.txt").delete()
                File(filesDir, "last-crash.txt").delete()
                spBoot.edit().putInt("bootFails", 0).apply()
            }
        }
        // Android 16+ 访问局域网（遥控 PC）要运行时授权，否则 TCP 连接直接超时
        runCatching {
            val perm = "android.permission.ACCESS_LOCAL_NETWORK"
            if (Build.VERSION.SDK_INT >= 36 &&
                checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(perm), 2001)
            }
        }

        // 上一次没跑完？（成功启动会把轨迹删掉，残留 = 上次没跑完）
        val prevTrace = readTrace()
        val prevCrash = readCrash()
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
            val _prev = getSharedPreferences(PREF, MODE_PRIVATE).getInt("prevStyle", UiStyle.Native.ordinal)
            style = UiStyle.entries.getOrElse(_prev) { UiStyle.Native }
            try {
                getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("style", style.ordinal).apply()
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
        uiAlpha = sp.getFloat("alpha", 0f)
        glassBlur = sp.getFloat("gblur", 30f)
        glassDensity = sp.getFloat("gdens", 100f)
        customWallpaper = sp.getBoolean("wp", false)
        themeMode = ThemeMode.entries.getOrElse(sp.getInt("theme", 2)) { ThemeMode.System }
        monet = sp.getBoolean("monet", false)
        fontChoice = AppFont.entries.getOrElse(sp.getInt("font", 0)) { AppFont.System }
        val dk = sp.getBoolean("dark", false)
        logCmdColor = readLogColor(sp, "lc2Cmd")
        logOkColor = readLogColor(sp, "lc2Ok")
        logErrColor = readLogColor(sp, "lc2Err")
        logTextColor = readLogColor(sp, "lc2Text")
        // 下过的字体就存在 filesDir 里，启动时直接加载，别再当成没下载过（否则每次重启都变回系统字体、
        // 再点一次还得重新下 227MB）。路径：/data/data/com.easypt.gui.compose/files/font-*.ttf
        fun loadFont(fileName: String): androidx.compose.ui.text.font.FontFamily? = runCatching {
            val f = File(filesDir, fileName)
            if (!f.exists() || f.length() < 4096) return@runCatching null
            // 以前只判断「文件存在」就当已下载，坏文件会让字体静默失效（界面看不出任何变化）。
            // 现在要求：文件头是合法字体 + 系统真的能创建 Typeface，否则视为没下载。
            if (!isFontFile(f)) {
                android.util.Log.w("EasyPT", "loadFont $fileName 文件头不是字体，忽略")
                return@runCatching null
            }
            android.graphics.Typeface.createFromFile(f)
            android.util.Log.d("EasyPT", "loadFont $fileName OK (${f.length()} B)")
            androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(f))
        }.onFailure {
            android.util.Log.w("EasyPT", "loadFont $fileName 失败：${it.message}")
        }.getOrNull()
        OnlineFonts.forEach { def ->
            loadFont(def.fileName)?.let { onlineFamilies[def.app.name] = it }
        }

        // 导入过的本地字体：从 prefs 恢复，逐个用系统解一次，坏的丢掉
        runCatching {
            val raw = sp.getString("customFonts", "") ?: ""
            raw.split("\n").forEach { line ->
                if (line.isNotBlank()) {
                    val parts = line.split("|")
                    if (parts.size >= 2) {
                        val f2 = File(parts[1])
                        if (f2.exists() && f2.length() > 4096) {
                            runCatching {
                                android.graphics.Typeface.createFromFile(f2)
                                customFamilies[f2.absolutePath] = androidx.compose.ui.text.font.FontFamily(
                                    androidx.compose.ui.text.font.Font(f2)
                                )
                                customFonts.add(parts[0] to f2)
                            }
                        }
                    }
                }
            }
        }
        // 老版本只存过 custom-font.ttf，迁移成一条记录
        runCatching {
            val legacy = File(filesDir, "custom-font.ttf")
            if (legacy.exists() && customFonts.isEmpty()) {
                if (runCatching { android.graphics.Typeface.createFromFile(legacy) }.isSuccess) {
                    customFamilies[legacy.absolutePath] = androidx.compose.ui.text.font.FontFamily(
                        androidx.compose.ui.text.font.Font(legacy)
                    )
                    customFonts.add("custom-font" to legacy)
                }
            }
        }
        customFontPath = sp.getString("customFontPath", customFonts.firstOrNull()?.second?.absolutePath)
        customFont = customFontPath?.let { customFamilies[it] }
        forceSafe = sp.getBoolean("forceSafe", false)
        // 第一次用：弹一次向导（看完写 onboarded=true，不再弹）
        showOnboarding = !sp.getBoolean("onboarded", false)

        androidx.core.content.ContextCompat.registerReceiver(
            this, usbReceiver,
            IntentFilter(ACTION_PERM).apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        append("Easy-platform-tools-GUI · Compose", Accent)
        append("Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}", TextSecondary)

        mark("04-prefs")
        reloadWallpaper(sync = true)
        android.util.Log.i("EasyPT", "focusProbe: " + focusProbe())
        XmsfBypass.appCtx = applicationContext
        checkInterruptedFlash()
        XmsfBypass.appCtx = applicationContext
        checkInterruptedFlash()
        XmsfBypass.requestPermission()
        android.util.Log.i("EasyPT", "xmsfSelfTest: " + XmsfBypass.selfTest(this))
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

    private fun reloadWallpaper(sync: Boolean = false) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        if (sync) {
            // 冷启动：同步准备好，第一帧就有壁纸
            try {
                val src = Wallpaper.load(this) ?: Wallpaper.default(w, h)
                glassAssets = Wallpaper.build(Wallpaper.prepare(src, w, h))
            } catch (e: Throwable) {
                append("壁纸加载失败：${e.message}", ErrColor)
            }
            return
        }
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
    /** 在线壁纸已经在弹窗里下好了，这里直接用本地文件，不要再下一遍 */
    fun setWallpaperFromFile(path: String) {
        val f = File(path)
        if (!f.exists() || f.length() < 1024) {
            append(t("壁纸文件无效：$path", "Invalid wallpaper file: $path"), ErrColor)
            return
        }
        setWallpaperFrom(Uri.fromFile(f))
    }

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
        // 把「语义色」翻译成「可配置色」，这样设置里的日志颜色真的会生效
        val c = when (color) {
            OkColor -> OkText
            ErrColor -> ErrText
            Accent -> CmdColor
            LogColor, TextSecondary -> LogText
            else -> color
        }
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        // 同时落盘：App 一重启内存日志就没了，刷机失败没法复盘。
        // 用单线程写，保证顺序，也不卡 UI（getvar:all 一次有几百行）
        logWriter.execute {
            runCatching {
                val f = File(filesDir, "logs/run.log")
                if (f.length() > 2 * 1024 * 1024) { f.writeText("") }
                f.parentFile?.mkdirs()
                f.appendText("[$ts] $text\n")
            }
        }
        ui.post {
            logs.add(LogLine("[$ts] $text", c))
            while (logs.size > 300) logs.removeAt(0)
        }
        // 刷机期间：实况通知的文字跟着日志最新一行走（节流 300ms，避免刷爆通知）
        if (busy) {
            val now = System.currentTimeMillis()
            if (now - lastNotifyPush > 300L) {
                lastNotifyPush = now
                runCatching {
                    updateFlashLive(
                        LiveUpdate(
                            t("线刷中", "Flashing"),
                            (if (flashPart.isEmpty()) "" else "$flashPart · ") + text.trim().take(70),
                            flashStep, flashMax
                        )
                    )
                }
            }
        }
    }

    private var lastNotifyPush = 0L
    private var flashPart = ""
    private var flashStep = 0
    private var flashMax = 0
    fun clearLog() { logs.clear() }

    /** 刷机前尽量进「电池优化白名单」，小米系最关键的一环 */
    private fun ensureBatteryExempt() {
        runCatching {
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (pm?.isIgnoringBatteryOptimizations(packageName) == true) return
            val i = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:$packageName")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }
    }

    /** 上次刷机是否没跑完：开刷时留标记，正常结束删掉 */
    private fun flashMarker() = File(filesDir, "flash_inprogress")

    private fun checkInterruptedFlash() {
        runCatching {
            val m = flashMarker()
            if (!m.exists()) return
            val who = m.readText().trim().ifEmpty { "?" }
            append(
                t(
                    "⚠ 上次刷机未完成（$who）。设备可能停在半刷状态，别直接开机，建议重新完整刷一遍",
                    "⚠ Last flash did not finish ($who). Do not boot into it — flash again"
                ),
                ErrColor
            )
        }
    }

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
            if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                val d = usbExtra(intent)
                append(t("USB 插入：${d?.deviceName ?: "?"}", "USB attached: ${d?.deviceName ?: "?"}"), TextSecondary)
                if (d != null) {
                    append("  VID:PID ${Integer.toHexString(d.vendorId)}:${Integer.toHexString(d.productId)}", TextSecondary)
                }
                return
            }
            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                connected = false
                append(t("USB 已拔出（${usbExtra(intent)?.deviceName ?: "?"}）", "USB detached"), ErrColor)
                return
            }
            if (intent.action != ACTION_PERM) return
            val d = usbExtra(intent)
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted && d != null) {
                pendingDevice = d
                bg {
                    val ok = fb.open(this@MainActivity, d)
                    connected = ok
                    if (ok) append(t("已连接：${fb.deviceName()}", "Connected: ${fb.deviceName()}"), OkColor)
                    else append(t("打开 USB 失败", "Failed to open USB"), ErrColor)
                }
            } else {
                append(t("USB 权限被拒绝", "USB permission denied"), ErrColor)
            }
        }
    }

    private fun usbExtra(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

    /** 「USB 诊断」：把系统里所有 USB 设备、接口、端点、权限全打进日志 */
    fun usbDiag() {
        val um = getSystemService(Context.USB_SERVICE) as UsbManager
        FastbootUsb.dump(um, FastbootUsb.Logger { append(it, TextSecondary) })
    }

    fun connectUsb() {
        val um = getSystemService(Context.USB_SERVICE) as UsbManager
        bg {
            // 刚插上时枚举要一两秒，以前只查一次就判定「没找到」
            var d: UsbDevice? = null
            for (i in 0 until 6) {
                d = FastbootUsb.findFastbootDevice(um, FastbootUsb.Logger { append(it, TextSecondary) })
                if (d != null) break
                if (i == 0) append(t("扫描 USB 设备…", "Scanning USB…"), TextSecondary)
                try { Thread.sleep(400) } catch (_: InterruptedException) { }
            }
            if (d == null) {
                append(
                    t("没找到 fastboot 设备：检查 OTG 线和对方是否在 fastboot 界面",
                        "No fastboot device: check the OTG cable and that the target is in fastboot"),
                    ErrColor
                )
                usbDiag()
                return@bg
            }
            if (!um.hasPermission(d)) {
                val dev = d
                append(t("等待 USB 授权…", "Waiting for USB permission…"), TextSecondary)
                ui.post {
                    var flags = PendingIntent.FLAG_UPDATE_CURRENT
                    if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
                    val pi = PendingIntent.getBroadcast(this, 0, Intent(ACTION_PERM), flags)
                    um.requestPermission(dev, pi)
                }
                return@bg
            }
            val ok = fb.open(this, d)
            connected = ok
            if (ok) append(t("已连接：${fb.deviceName()}", "Connected: ${fb.deviceName()}"), OkColor)
            else append(t("打开 USB 失败", "Failed to open USB"), ErrColor)
        }
    }

    fun otgInfo() = bg {
        if (!fb.isOpen()) { append(t("请先连接设备", "Connect a device first"), ErrColor); return@bg }
        for (k in listOf("product", "version", "serialno", "secure")) {
            val v = fb.getvar(k)
            // 有的 bootloader 本来就没有某个变量，别把 "FAIL:..." 原样甩给用户
            if (v.startsWith("FAIL")) append("$k: ${t("（不支持）", "(unsupported)")}", TextSecondary)
            else append("$k: $v")
        }
        val maxDl = fb.maxDownloadSize()
        append(
            "max-download-size: " + if (maxDl > 0) "${maxDl / 1048576} MB" else t("（未知）", "(unknown)")
        )
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

    fun otgProtoTest() = bg {
        if (!fb.isOpen()) { append(t("请先连接设备", "Connect a device first"), ErrColor); return@bg }
        for (line in fb.protoTest().split("\n")) if (line.isNotBlank()) append(line, TextSecondary)
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
        // MIUI 线刷包的镜像都在 images/ 子目录里，只扫顶层会全部报「缺文件」
        var subCount = 0
        doc.listFiles().filter { it.isDirectory }.forEach { dir ->
            dir.listFiles().filter { it.isFile && it.name != null }.forEach { f ->
                f.name?.lowercase(Locale.US)?.let {
                    byName[it] = f.uri
                    subCount++
                }
            }
        }
        if (subCount > 0) {
            append(t("子目录里找到 $subCount 个镜像", "$subCount images found in subfolders"), TextSecondary)
        }
        // MIUI 线刷包一般同时带 flash_all.sh / flash_all.bat / flash_all_except_storage.sh，
        // 后者是「保数据」。以前只取第一个命中的，可能正好是清数据那个。
        val scripts = files.filter {
            it.name?.lowercase(Locale.US)?.startsWith("flash_all") == true
        }
        when {
            scripts.isEmpty() -> {
                append(t("没有 flash_all 脚本，按文件名自动生成", "No flash_all script, generating from file names"), TextSecondary)
                finishRom(byName, RomFlasher.fromImages(files.mapNotNull { it.name }))
            }
            scripts.size == 1 -> loadRomScript(scripts[0].uri, scripts[0].name ?: "flash_all", byName)
            else -> {
                romFiles = byName
                romScripts = scripts.map { (it.name ?: "flash_all") to it.uri }
                showRomScript = true
                append(
                    t("发现 ${scripts.size} 个刷机脚本，请选择用哪个", "${scripts.size} flash scripts found, pick one"),
                    TextSecondary
                )
            }
        }
    }

    /** 选定脚本后解析成步骤 */
    private fun loadRomScript(uri: Uri, name: String, byName: HashMap<String, Uri>) = bg {
        val txt = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) { "" }
        append(t("使用脚本 $name", "Using script $name"), Accent)
        // 脚本里本来就有 "Missmatching image and device" 的机型校验，这里照着做一遍
        val want = RomFlasher.expectedProduct(txt)
        if (want.isNotEmpty()) {
            romProduct = want
            if (fb.isOpen()) {
                val real = fb.getvar("product")
                if (real.isNotEmpty() && !real.startsWith("FAIL") && real != want) {
                    append(
                        t("机型不匹配：这个包是 $want，当前设备是 $real，已中止",
                            "Wrong package: ROM is $want but device is $real — aborted"),
                        ErrColor
                    )
                    return@bg
                }
            }
            append(t("机型校验通过：$want", "Product check passed: $want"), OkColor)
        }
        finishRom(byName, RomFlasher.fromScript(txt))
    }

    private fun finishRom(byName: HashMap<String, Uri>, steps: List<RomFlasher.Step>) {
        if (steps.isEmpty()) {
            append(t("没有可执行的步骤", "No executable steps found"), ErrColor)
            return
        }
        steps.forEach { s ->
            if (s.kind == "flash" && !byName.containsKey(s.file)) s.missing = true
        }
        romFiles = byName
        romSteps = steps
        showRom = true
        val miss = steps.count { it.missing }
        val wipes = steps.count { it.kind == "wipe" || (it.kind == "erase" && it.part == "userdata") }
        append(
            t(
                "共 ${steps.size} 步${if (miss > 0) "，$miss 步缺文件" else ""}${if (wipes > 0) "，会清空数据" else "，保数据"}",
                "${steps.size} steps${if (miss > 0) ", $miss missing" else ""}"
            ),
            if (miss > 0) ErrColor else OkColor
        )
    }

    private fun pickRomScript(i: Int) {
        if (i !in romScripts.indices) return
        val (name, uri) = romScripts[i]
        showRomScript = false
        loadRomScript(uri, name, HashMap(romFiles))
    }

    /** root 模式：拷到 cache 再用 su dd 写分区 */
    fun localFlash(uri: Uri) = bg {
        val tmp = File(cacheDir, "flash-tmp.img")
        try {
            contentResolver.openInputStream(uri)?.use { i ->
                tmp.outputStream().use { o -> i.copyTo(o) }
            } ?: run {
                append(t("无法读取镜像", "Cannot read image"), ErrColor)
                return@bg
            }
        } catch (e: Exception) {
            append(t("读取失败：${e.message}", "Read failed: ${e.message}"), ErrColor)
            return@bg
        }
        append(t("刷入 $part ← ${tmp.length() / 1024 / 1024} MB", "Flashing $part ← ${tmp.length() / 1024 / 1024} MB"), Accent)
        val r = RootShell.run("dd if='${tmp.absolutePath}' of=/dev/block/by-name/$part bs=4M")
        if (r.out.isNotBlank()) append(r.out, if (r.ok) LogColor else ErrColor)
        append(
            if (r.ok) t("刷入完成", "Flash complete") else t("刷入失败", "Flash failed"),
            if (r.ok) OkColor else ErrColor
        )
        tmp.delete()
    }

    /** 线刷压缩包：zip 用 ZipInputStream，tgz 用系统 tar，解到 cache 后按目录解析 */
    fun loadRomArchive(uri: Uri) = bg {
        val name = uri.lastPathSegment ?: "rom"
        append(t("解压线刷包 $name …", "Extracting $name …"), TextSecondary)
        val dir = File(cacheDir, "rom-extract")
        dir.deleteRecursively()
        dir.mkdirs()
        val tmp = File(cacheDir, "rom-archive.bin")
        try {
            contentResolver.openInputStream(uri)?.use { i ->
                tmp.outputStream().use { o -> i.copyTo(o) }
            }
        } catch (e: Exception) {
            append(t("读取失败：${e.message}", "Read failed: ${e.message}"), ErrColor)
            return@bg
        }
        val lower = name.lowercase(Locale.US)
        val ok = try {
            when {
                lower.endsWith(".zip") -> {
                    java.util.zip.ZipInputStream(tmp.inputStream().buffered()).use { zis ->
                        while (true) {
                            val e = zis.nextEntry ?: break
                            val out = File(dir, e.name)
                            if (e.isDirectory) { out.mkdirs() } else {
                                out.parentFile?.mkdirs()
                                out.outputStream().use { o -> zis.copyTo(o) }
                            }
                            zis.closeEntry()
                        }
                    }
                    true
                }
                else -> {
                    // tgz / tar.gz / tar：交给系统 tar
                    val p = ProcessBuilder("tar", "-xf", tmp.absolutePath, "-C", dir.absolutePath)
                        .redirectErrorStream(true).start()
                    p.waitFor() == 0
                }
            }
        } catch (e: Exception) {
            append(t("解压失败：${e.message}", "Extract failed: ${e.message}"), ErrColor)
            false
        }
        tmp.delete()
        if (!ok) {
            append(t("解压失败（zip 用内置解压，tgz 需要系统 tar）", "Extract failed"), ErrColor)
            return@bg
        }
        loadRomFromDir(dir)
    }

    /** 从解压后的目录解析刷机脚本 */
    private fun loadRomFromDir(dir: File) = bg {
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) {
            append(t("压缩包里没有文件", "Archive is empty"), ErrColor)
            return@bg
        }
        val byName = HashMap<String, Uri>()
        files.forEach { f -> byName[f.name.lowercase(Locale.US)] = Uri.fromFile(f) }
        val scripts = files.filter { it.name.lowercase(Locale.US).startsWith("flash_all") }
        when {
            scripts.isEmpty() -> {
                append(t("没有 flash_all 脚本，按镜像名推断", "No flash_all script, inferring from names"), TextSecondary)
                finishRom(byName, RomFlasher.fromImages(files.map { it.name }))
            }
            scripts.size == 1 -> {
                append(t("使用脚本 ${scripts[0].name}", "Using script ${scripts[0].name}"), Accent)
                finishRom(byName, RomFlasher.fromScript(scripts[0].readText()))
            }
            else -> {
                romFiles = byName
                romScripts = scripts.map { it.name to Uri.fromFile(it) }
                showRomScript = true
                append(
                    t("发现 ${scripts.size} 个刷机脚本，请选择用哪个", "${scripts.size} flash scripts found, pick one"),
                    TextSecondary
                )
            }
        }
    }

    private fun runRom() = bg {
        if (!fb.isOpen()) { append(t("请先连接设备", "Connect a device first"), ErrColor); return@bg }
        showRom = false
        // 最后一道保险：真开刷之前再核一次机型
        if (romProduct.isNotEmpty()) {
            val real = fb.getvar("product")
            if (real.isNotEmpty() && !real.startsWith("FAIL") && real != romProduct) {
                append(
                    t("机型不匹配：包是 $romProduct，设备是 $real，已中止（什么都没刷）",
                        "Wrong package: ROM is $romProduct, device is $real — aborted, nothing flashed"),
                    ErrColor
                )
                return@bg
            }
        }
        val steps = romSteps
        var okCount = 0
        var failCount = 0
        fun runStep(s: RomFlasher.Step): Boolean = when (s.kind) {
                "flash" -> if (s.missing) {
                    append(t("  缺少文件：${s.file}", "  missing file: ${s.file}"), ErrColor)
                    false
                } else {
                    val uri = romFiles[s.file]
                    if (uri == null) {
                        append(t("  找不到：${s.file}", "  not found: ${s.file}"), ErrColor)
                        false
                    } else {
                        // 直接从 URI 流式上传，不再整份拷进 cache（system.img 有 1.3 GB）
                        val len = try {
                            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                        } catch (e: Exception) { -1L }
                        val progress = FastbootUsb.Progress { done, total ->
                            if (total > 0 && done % (total / 10 + 1) < 65536) {
                                append("  ${done * 100 / total}%", TextSecondary)
                            }
                        }
                        if (len > 0) {
                            append("  ${s.file} · ${String.format(Locale.US, "%.1f", len / 1048576.0)} MB", TextSecondary)
                            val r = try {
                                contentResolver.openInputStream(uri)?.use { input ->
                                    fb.flashStream(s.part, input, len, progress)
                                } ?: run {
                                    append(t("  打不开文件：${s.file}", "  cannot open: ${s.file}"), ErrColor)
                                    false
                                }
                            } catch (e: Exception) {
                                append("  ${e.message}", ErrColor)
                                false
                            }
                            r
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
                            val r = fb.flashFile(s.part, f, progress)
                            f.delete()
                            r
                        }
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
        // 小米系会积极回收后台进程。刷机期间：保持屏幕常亮 + 尝试进电池白名单。
        ui.post { runCatching { window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
        ensureBatteryExempt()        // 实况通知：开刷就挂一条常驻进度，Android 16+ 会显示成实况通知/超级岛
        runCatching { flashMarker().writeText("${romProduct.ifEmpty { "未知机型" }} · ${steps.size} 步") }
        updateFlashLive(LiveUpdate(t("线刷中", "Flashing"), t("准备中…", "Preparing…"), 0, steps.size))
        for ((i, s) in steps.withIndex()) {
            flashPart = s.part
            flashStep = i + 1; flashMax = steps.size
            updateFlashLive(LiveUpdate(t("线刷中", "Flashing"), "${i + 1}/${steps.size} · ${s.part} · ${s.kind}", i + 1, steps.size))
            append("[${i + 1}/${steps.size}] ${s.raw}", Accent)
            var ok = runStep(s)
            if (!ok && s.kind != "reboot") {
                // 大镜像传完后设备端点经常卡死，重连一次能救回来
                append(t("  失败，重连后重试一次", "  failed, reconnecting and retrying"), TextSecondary)
                if (fb.reopen()) ok = runStep(s)
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
        runCatching { flashMarker().delete() }
        finishFlashLive(failCount == 0)
        ui.post { runCatching { window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
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

    /** 遥控前先检查地址，否则只会抛 "failed to connect to /" */
    private fun remoteReady(): Boolean {
        if (host.isBlank()) {
            append(t("请先在设置里填 PC 地址，例如 192.168.1.10:8765", "Set the PC address first, e.g. 192.168.1.10:8765"), ErrColor)
            return false
        }
        return true
    }

    fun remotePing() = bg {
        if (!remoteReady()) return@bg
        val r = remote().ping()
        append(
            if (r.isNotBlank()) t("遥控服务在线", "Remote service online") else t("无响应", "No response"),
            if (r.isNotBlank()) OkColor else ErrColor
        )
    }

    fun remoteParts() = bg {
        if (!remoteReady()) return@bg
        append(t("读取分区表…", "Reading partitions…"), TextSecondary)
        append(remote().partitions().take(2000))
        showParts = false
    }

    fun remoteReboot(target: String) = bg {
        if (!remoteReady()) return@bg
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
        // 回退用：记下这次的"上一个操作"
        try {
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("prevStyle", style.ordinal).apply()
        } catch (_: Throwable) { }
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
        if (v) {
            // 打开安全模式：立刻回退到上一个样式，并关掉所有特效
            val sp = getSharedPreferences(PREF, MODE_PRIVATE)
            val prev = sp.getInt("prevStyle", UiStyle.Native.ordinal)
            if (style == UiStyle.Glass) {
                style = UiStyle.entries.getOrElse(prev) { UiStyle.Native }
                sp.edit().putInt("style", style.ordinal).apply()
            }
            append(t("安全模式已开启：已回退到 ${style.labelZh}", "Safe mode on: rolled back to ${style.labelZh}"), OkColor)
        } else {
            append(t("安全模式已关闭", "Safe mode off"), OkColor)
        }
    }

    /** 字体 -> Compose FontFamily：在线字体用已下载的，其余用系统族 */
    fun familyFor(f: AppFont): androidx.compose.ui.text.font.FontFamily? = when (f) {
        AppFont.Local -> customFontPath?.let { customFamilies[it] } ?: customFont

        else -> onlineFamilies[f.name] ?: f.family
    }

    private fun chooseFont(f: AppFont) {
        when {
            f == AppFont.Local ->
                fontPicker?.launch(arrayOf("font/*", "application/octet-stream", "*/*"))

            f.isOnline -> chooseOnlineFont(f)
            else -> {
                fontChoice = f
                // 切到内置字体时要把「自定义字体」这个状态清掉。
                // 否则设置页那行的显示值会走 `customFontName != null -> customFontName` 这条分支，
                // 一直显示之前导入的那个字体名 —— 字体已经换了、名字却不动，就是这个原因。
                customFontPath = null
                customFont = null
                getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("font", f.ordinal).apply()
            }
        }
    }

    /** 本地字体：拷进 filesDir 后加载（ttf / otf / ttc） */
    /** 导入的字体文件名（用于列表显示） */
    private fun queryDisplayName(uri: android.net.Uri): String {
        return runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: (uri.lastPathSegment ?: "字体").substringBeforeLast('.').ifBlank { "字体" }
    }

    /** 删除一个导入的字体：文件 + 列表 + prefs 一起清；正在用的就退回系统字体 */
    fun deleteCustomFont(name: String) {
        val idx = customFonts.indexOfFirst { it.first == name }
        if (idx < 0) return
        val entry = customFonts[idx]
        val label = entry.first
        val file = entry.second
        val wasActive = customFontPath == file.absolutePath
        customFonts.removeAt(idx)
        customFamilies.remove(file.absolutePath)
        runCatching { file.delete() }
        if (wasActive) {
            customFontPath = customFonts.lastOrNull()?.second?.absolutePath
            customFont = customFontPath?.let { customFamilies[it] }
            if (customFontPath == null) {
                fontChoice = AppFont.System
                getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("font", AppFont.System.ordinal).apply()
            }
        }
        saveCustomFonts()
        append(t("已删除字体：$label", "Font deleted: $label"), OkColor)
    }

    /** 把列表写回 prefs */
    private fun saveCustomFonts() {
        val raw = customFonts.joinToString("\n") { "${it.first}|${it.second.absolutePath}" }
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
            .putString("customFonts", raw)
            .putString("customFontPath", customFontPath)
            .apply()
    }

    /**
     * 导入本地字体：拷进 filesDir 后【加进字体列表】，之后一直可以在「字体」下拉里直接选，
     * 不用每次再导入一遍。
     */
    fun installLocalFont(uri: android.net.Uri) {
        val name = queryDisplayName(uri)
        Thread {
            val out = File(filesDir, "custom-" + System.currentTimeMillis() + ".ttf")
            try {
                contentResolver.openInputStream(uri)?.use { i ->
                    out.outputStream().use { o -> i.copyTo(o) }
                } ?: throw IllegalStateException("无法读取文件")
                // 必须是系统能解的字体，否则不要塞进列表当死项
                android.graphics.Typeface.createFromFile(out)
                val fam = androidx.compose.ui.text.font.FontFamily(
                    androidx.compose.ui.text.font.Font(out)
                )
                runOnUiThread {
                    // 同名替换，避免重复导入出现一堆同类条目
                    val dup = customFonts.indexOfFirst { it.first == name }
                    if (dup >= 0) customFonts.removeAt(dup)
                    customFonts.add(name to out)
                    customFamilies[out.absolutePath] = fam
                    customFont = fam
                    customFontPath = out.absolutePath
                    fontChoice = AppFont.Local
                    saveCustomFonts()
                    getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("font", AppFont.Local.ordinal).apply()
                    append(t("已导入字体：$name（已加入字体列表）", "Font imported: $name (added to the list)"), OkColor)
                }
            } catch (e: Throwable) {
                runCatching { out.delete() }
                ui.post { append(t("字体加载失败（支持 ttf/otf/ttc）：${e.message}", "Font load failed: ${e.message}"), ErrColor) }
            }
        }.start()
    }

    // ---------------- 在线字体 ----------------

    /** 多源回退：raw 直连最稳，gh-proxy 备用，jsDelivr 兜底（国内 cdn 域名常被 RST） */
    private fun ghSources(user: String, repo: String, branch: String, path: String): List<String> = listOf(
        "https://raw.githubusercontent.com/$user/$repo/$branch/$path",
        "https://gh-proxy.com/https://raw.githubusercontent.com/$user/$repo/$branch/$path",
        "https://gcore.jsdelivr.net/gh/$user/$repo@$branch/$path"
    )

    /** 下载到 dst，边下边报进度；非 2xx 直接抛 */
    private fun httpDownload(url: String, dst: File) {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 120000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) EasyPT")
            setRequestProperty("Accept", "*/*")
            // 别让服务器给我们 brotli，否则拿到的字体是压缩流
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val total = conn.contentLengthLong
            conn.inputStream.use { i ->
                dst.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val r = i.read(buf)
                        if (r <= 0) break
                        o.write(buf, 0, r)
                        done += r
                        if (total > 0) {
                            val pc = ((done * 100) / total).toInt().coerceIn(0, 100)
                            if (pc != fontProgress) ui.post { fontProgress = pc }
                        } else {
                            // 服务器没给 Content-Length 时 total = -1，之前这里什么都不做，
                            // 界面上就永远停在「下载中 0%」，看着像卡死（其实在正常下）
                            val pc = (done / 262144L).toInt().coerceIn(0, 99)
                            if (pc != fontProgress) ui.post { fontProgress = pc }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (dst.length() < 4096) throw IllegalStateException("下载内容太小（${dst.length()} B）")
    }

    /** 字体文件头校验，防止把 404 的 HTML / 压缩流当字体 */
    private fun isFontFile(f: File): Boolean {
        if (f.length() < 1024) return false
        val h = ByteArray(4)
        f.inputStream().use { it.read(h) }
        val sig = ((h[0].toInt() and 0xFF) shl 24) or ((h[1].toInt() and 0xFF) shl 16) or
            ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
        return sig == 0x00010000 || sig == 0x4F54544F || sig == 0x74727565 || sig == 0x74746366
    }

    /** zip 里挑一个字体文件解出来 */
    private fun unzipFirstFont(zip: File, out: File) {
        java.util.zip.ZipFile(zip).use { zf ->
            val all = zf.entries().asSequence().filter {
                it.name.endsWith(".ttf", true) || it.name.endsWith(".otf", true)
            }.toList()
            val pick = all.firstOrNull { it.name.contains("Regular", true) && it.name.contains("SC", true) }
                ?: all.firstOrNull { it.name.contains("Regular", true) }
                ?: all.firstOrNull()
                ?: throw IllegalStateException("压缩包里没有字体文件")
            zf.getInputStream(pick).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        }
    }

    /** 下载某个在线字体（多源依次重试，全部失败才报错） */
    private fun downloadFont(def: OnlineFontDef) = bg {
        val label = def.label
        val out = File(filesDir, def.fileName)
        fontTargetKey = def.app.name
        fontProgress = 0
        append(t("开始下载 $label（约 ${def.sizeText}）…", "Downloading $label…"), TextSecondary)
        var lastErr = ""
        var got = false
        for (url in def.urls) {
            val tmp = File(cacheDir, "font-${def.app.name}.tmp")
            try {
                if (tmp.exists()) tmp.delete()
                httpDownload(url, tmp)
                if (url.endsWith(".ttf", true) || url.endsWith(".otf", true)) {
                    if (!isFontFile(tmp)) throw IllegalStateException("不是合法字体文件")
                    tmp.copyTo(out, overwrite = true)
                } else {
                    unzipFirstFont(tmp, out)
                    if (!isFontFile(out)) throw IllegalStateException("解压出的字体不合法")
                }
                got = true
                break
            } catch (e: Throwable) {
                lastErr = e.message ?: e.javaClass.simpleName
                append(t("下载源失败：$lastErr，换备用源…", "Source failed: $lastErr, retrying…"), ErrColor)
            } finally {
                runCatching { tmp.delete() }
            }
        }
        if (!got) {
            ui.post {
                fontProgress = -1
                fontTargetKey = ""
                append(t("$label 下载失败：$lastErr", "$label failed: $lastErr"), ErrColor)
            }
            return@bg
        }
        try {
            // 落盘后再用系统真正解一次，坏文件当场报错，不留下「看起来下载好了但没效果」
            android.graphics.Typeface.createFromFile(out)
            val fam = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(out))
            runOnUiThread {
                onlineFamilies[def.app.name] = fam
                fontChoice = def.app
                // 下载成功后同样要清掉自定义字体状态，否则显示值仍然停在旧字体名上
                customFontPath = null
                customFont = null
                fontProgress = -1
                fontTargetKey = ""
                getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("font", fontChoice.ordinal).apply()
                append(t("$label 已应用（已保存到 ${def.fileName}）", "$label applied (saved as ${def.fileName})"), OkColor)
            }
        } catch (e: Throwable) {
            ui.post {
                fontProgress = -1
                fontTargetKey = ""
                append(t("$label 字体加载失败：${e.message}", "$label load failed: ${e.message}"), ErrColor)
            }
        }
    }

    /** 已经下过就直接应用，没有才开始下 */
    fun chooseOnlineFont(f: AppFont) {
        val def = OnlineFonts.firstOrNull { it.app == f }
        if (def == null) {
            // 绝不能静默失败：没源就说清楚
            append(t("${f.zh} 暂时没有可用的下载源", "No download source available for ${f.zh}"), ErrColor)
            return
        }
        val fam = onlineFamilies[f.name]
        if (fam != null) {
            fontChoice = f
            // 同上：不清理的话设置页那行会一直显示旧的字体名
            customFontPath = null
            customFont = null
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("font", f.ordinal).apply()
            append(t("已切换字体：${def.label}", "Font switched: ${def.label}"), OkColor)
        } else {
            downloadFont(def)
        }
    }

    private fun chooseMonet(v: Boolean) {
        monet = v
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("monet", v).apply()
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------------- 实况通知（Live Update，Android 16+） ----------------

    /**
     * 查这台机器认不认我们上岛。
     * 小米的门是 content://miui.statusbar.notification.public 的 canShowFocus，
     * 只有「uid 真正拥有该包」的调用者能问，所以必须由 App 自己来问。
     */
    fun focusProbe(): String {
        return runCatching {
            val b = android.os.Bundle().apply { putString("package", packageName) }
            val r = contentResolver.call(
                android.net.Uri.parse("content://miui.statusbar.notification.public"),
                "canShowFocus", null, b
            )
            val can = r?.getBoolean("canShowFocus", false) ?: false
            val proto = runCatching {
                android.provider.Settings.System.getInt(contentResolver, "notification_focus_protocol", 0)
            }.getOrDefault(0)
            val island = runCatching {
                val c = Class.forName("android.os.SystemProperties")
                val m = c.getMethod("get", String::class.java)
                m.invoke(null, "persist.sys.feature.island") as? String ?: "?"
            }.getOrDefault("?")
            "canShowFocus=$can protocol=$proto island=$island"
        }.getOrElse { "canShowFocus 查询失败：${it.javaClass.simpleName} ${it.message}" }
    }

    private val flashChannel = "easypt_flash"
    private val flashNotifyId = 4101

    /** 建通道 + 权限（失败就算了，不能因为通知崩掉刷机） */
    private fun ensureFlashChannel(nm: android.app.NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val ch = android.app.NotificationChannel(
                flashChannel, t("线刷进度", "Flashing progress"),
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    /**
     * 线刷进度实况通知：
     * Android 16（API 36）起用官方 Notification.ProgressStyle —— 系统会把它显示成
     * 实况通知（HyperOS 3.0.300+ 直接进超级岛），不需要 root / Shizuku / 白名单。
     * 低版本退化成普通常驻通知，只更新文字。
     */
    fun updateFlashLive(island: LiveUpdate) {
        // 交给前台服务发通知：只有前台服务发布的常驻通知才会被系统提升成
        // 实况通知 / 超级岛，Activity 直接 notify 的不会
        val accent = when {
            island.failed -> ErrText
            island.done -> OkText
            else -> CmdColor
        }.toArgb()
        val i = android.content.Intent(this, FlashService::class.java)
            .putExtra(FlashService.EXTRA_TITLE, island.title)
            .putExtra(FlashService.EXTRA_TEXT, island.text)
            .putExtra(FlashService.EXTRA_STEP, island.step)
            .putExtra(FlashService.EXTRA_MAX, island.max)
            .putExtra(FlashService.EXTRA_COLOR, accent)
        // 启动失败要说出来，并且退回普通通知：上一版静默失败，
        // 用户只看到「通知没了」，日志里一个字都没有
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
            else startService(i)
        }.onFailure { e ->
            append(
                t(
                    "实况通知启动失败：${e.javaClass.simpleName} ${e.message}",
                    "Live update failed: ${e.javaClass.simpleName} ${e.message}"
                ),
                ErrColor
            )
            runCatching { notifyPlainFlash(island, accent) }
        }
    }

    /** 前台服务不可用时的退路：普通常驻通知，至少能看见进度 */
    private fun notifyPlainFlash(island: LiveUpdate, accent: Int) {
        val nm = getSystemService(android.app.NotificationManager::class.java) ?: return
        ensureFlashChannel(nm)
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val b = if (Build.VERSION.SDK_INT >= 33) {
            android.app.Notification.Builder(this, flashChannel)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }
        b.setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(island.title)
            .setContentText(island.text)
            .setOngoing(!island.done)
            .setOnlyAlertOnce(true)
            .setColor(accent)
        if (Build.VERSION.SDK_INT >= 36) {
            val ps = android.app.Notification.ProgressStyle()
                .setProgress(island.max.coerceAtLeast(1))
                .setProgressIndeterminate(island.max <= 0)
                .setProgress(island.step.coerceIn(0, island.max.coerceAtLeast(1)))
            b.setStyle(ps)
            runCatching { b.setShortCriticalText(island.text) }
        }
        runCatching { nm.notify(flashNotifyId, b.build()) }
    }

    fun finishFlashLive(success: Boolean) {
        val stop = android.content.Intent(this, FlashService::class.java)
            .putExtra(FlashService.EXTRA_STOP, true)
        runCatching { startService(stop) }
        runCatching { stopService(android.content.Intent(this, FlashService::class.java)) }
        // runRom 在后台线程跑，Toast 必须在主线程
        if (success) runCatching { ui.post { toast(t("刷写完成", "Flashing finished")) } }
    }

    /** 实况通知要显示的状态 */
    data class LiveUpdate(val title: String, val text: String, val step: Int, val max: Int, val done: Boolean = false, val failed: Boolean = false)
    // ---------------- 检查更新 ----------------

    /** 本机版本名，比如 3.50.3 */
    private fun currentVersionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
    }.getOrDefault("0")

    /** 比较 "3.51.0" 和 "3.50.3"：前者大返回 1 */
    private fun compareVersion(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val vx = x.getOrElse(i) { 0 }
            val vy = y.getOrElse(i) { 0 }
            if (vx != vy) return if (vx > vy) 1 else -1
        }
        return 0
    }

    /**
     * 查 GitHub 上最新 release 的 tag，和本机版本比。
     * [manual] = 用户手动点的：无论有没有新版本都写一条日志；自动检查只在有新版本时提示。
     */
    fun checkUpdate(manual: Boolean = false) {
        if (manual) append(t("正在检查更新…", "Checking for updates…"), TextSecondary)
        bg {
            val cur = currentVersionName()
            var latest: String? = null
            var err: String? = null
            runCatching {
                // 多个源依次试：官方 API -> 两个 GitHub 加速代理 -> 直接读 releases/latest 的跳转
                val repoPath = "moqi12386/Easy-platform-tools-GUI"
                val api = "https://api.github.com/repos/$repoPath/releases/latest"
                val sources = listOf(
                    api,
                    "https://gh-proxy.com/$api",
                    "https://ghproxy.net/$api"
                )
                var lastErr = ""
                for (u in sources) {
                    try {
                        val conn = (java.net.URL(u).openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 10000
                            readTimeout = 15000
                            instanceFollowRedirects = true
                            setRequestProperty("Accept", "application/vnd.github+json")
                            setRequestProperty("User-Agent", "EasyPT")
                        }
                        try {
                            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
                            val body = conn.inputStream.bufferedReader().use { it.readText() }
                            val m = Regex("\"tag_name\"\\s*:\\s*\"v?([0-9][0-9.]*)\"").find(body)
                            if (m != null) { latest = m.groupValues[1]; break }
                            throw IllegalStateException("没有 tag_name")
                        } finally {
                            conn.disconnect()
                        }
                    } catch (e: Throwable) {
                        lastErr = e.message ?: e.javaClass.simpleName
                    }
                }
                // 兜底：不查 API，直接看 releases/latest 会 302 到哪个 tag
                if (latest == null) {
                    try {
                        val conn = (java.net.URL("https://github.com/$repoPath/releases/latest")
                            .openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 10000
                            readTimeout = 15000
                            instanceFollowRedirects = false
                            setRequestProperty("User-Agent", "EasyPT")
                        }
                        try {
                            val loc = conn.getHeaderField("Location") ?: conn.url.toString()
                            val m = Regex("/tag/v?([0-9][0-9.]*)").find(loc)
                            if (m != null) latest = m.groupValues[1] else lastErr = "跳转里没有版本号"
                        } finally {
                            conn.disconnect()
                        }
                    } catch (e: Throwable) {
                        lastErr = e.message ?: e.javaClass.simpleName
                    }
                }
                if (latest == null) throw IllegalStateException(lastErr.ifBlank { "所有源都不可用" })
            }.onFailure { err = it.message ?: it.javaClass.simpleName }

            ui.post {
                val l = latest
                when {
                    l == null -> {
                        // 自动检查时安静失败，免得每次启动都被网络问题刷屏
                        if (manual) {
                            val m = t("检查更新失败：${err ?: "没有拿到版本号"}",
                                "Update check failed: ${err ?: "no version found"}")
                            append(m, ErrColor)
                            toast(m)
                        }
                    }
                    compareVersion(l, cur) > 0 -> {
                        append(
                            t("发现新版本 $l（当前 $cur）→ https://github.com/moqi12386/Easy-platform-tools-GUI/releases/latest",
                                "New version $l (current $cur) → https://github.com/moqi12386/Easy-platform-tools-GUI/releases/latest"),
                            OkColor
                        )
                        toast(t("发现新版本 $l", "New version $l"))
                    }
                    else -> if (manual) {
                        val m = t("已是最新版本 $cur", "Already up to date ($cur)")
                        append(m, OkColor)
                        toast(m)
                    }
                }
            }
        }
    }


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
        // Material 风格：整个 Palette 转发到 Material You 动态色（系统从壁纸取色）
        Palette.m3 = if (style != UiStyle.Material) null
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) androidx.compose.material3.dynamicDarkColorScheme(this@MainActivity)
            else androidx.compose.material3.dynamicLightColorScheme(this@MainActivity)
        } else null
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

        LaunchedEffect(useBackdrop, style, safeRender) {            // 这行以前会往日志框里写，每次切样式都多一行，很碍事；
            // 诊断信息只留 logcat（adb logcat -s EasyPT）
            android.util.Log.d(
                "EasyPT",
                "玻璃诊断：useBackdrop=$useBackdrop · style=${style.name} · safe=$safeRender · API=${Build.VERSION.SDK_INT}"
            )
        }
        // 启动时按设置决定要不要检查更新（默认开启，设置里可关）
        LaunchedEffect(Unit) {
            autoUpdate = getSharedPreferences(PREF, MODE_PRIVATE).getBoolean("autoupdate", true)
            if (autoUpdate) checkUpdate()
        }
        // 字体诊断：每次字体变化打一条，便于确认「已应用但没效果」到底卡在哪
        LaunchedEffect(fontChoice, onlineFamilies.size) {
            val fam = familyFor(fontChoice)
            android.util.Log.d(
                "EasyPT",
                "字体: choice=${fontChoice.name} family=${fam != null} loaded=${onlineFamilies.keys}"
            )
        }
        val backdrop = rememberLayerBackdrop()
        val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) setWallpaperFrom(uri)
        }
        // 实况通知要 POST_NOTIFICATIONS（Android 13+）：开刷前申请一次，
        // 不申请的话 updateFlashLive 会被静默跳过，用户永远看不到进度通知
        val askNotifyPerm = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) append(
                t("没有通知权限，刷机进度不会显示在通知栏", "No notification permission — progress won't show"),
                ErrColor
            )
        }
        fun ensureNotifyPerm() {
            if (Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                askNotifyPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
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
            // 底栏在屏幕最下面，得用壁纸「底部」的亮度；以前用 topLum，壁纸上下亮度不一样就看不见字。
            // 再叠上底栏那层 40% 玻璃底料，算出真实底色亮度，然后按对比度挑黑字还是白字
            val veilLum = if (dark) 0.07f else 0.93f
            val barLum = 0.4f * veilLum + 0.6f * (assets?.bottomLum ?: 0.5f)
            val onBar = readableOn(barLum)
            // 照 demo：图层里只有背景（壁纸），玻璃元件在图层外面取样
            val wallBackdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize().background(Bg).imePadding()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (useBackdrop) Modifier.layerBackdrop(wallBackdrop) else Modifier)
                ) {
                    WallpaperView(assets)
                }
                // 顶部渐变：和底栏同一套 Brush，标题仍然清晰
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Bg.copy(alpha = 0.85f),
                                    Bg.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalWallBackdrop provides wallBackdrop,
                        LocalGlassAssets provides assets,
                        LocalGlassScreen provides screen,
                        LocalTitleColor provides onWall,
                        LocalBarColor provides onBar,
                        LocalCardText provides Palette.text,
                        LocalStyle provides style,
                        // 原生风格：主色位一律用中性色，界面里不再出现任何彩色
                        LocalAccent provides if (style == UiStyle.Native) Color(0xFFECEDEF) else LocalAccent.current
                    ) {
                        MiuixTheme(colors = if (dark) miuixDark() else miuixLight()) {
                            MaterialTheme(
                                typography = typographyWith(
                                    familyFor(fontChoice)
                                ),
                                colorScheme = run {
                                    // Material You：API 31+ 直接用系统从壁纸取的动态配色，
                                    // 低版本回退到项目自己这套
                                    val c = LocalContext.current
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        if (dark) androidx.compose.material3.dynamicDarkColorScheme(c)
                                        else androidx.compose.material3.dynamicLightColorScheme(c)
                                    } else if (dark) {
                                        darkColorScheme(
                                            primary = LocalAccent.current,
                                            surface = Surface,
                                            surfaceContainer = Surface,
                                            onSurface = TextPrimary,
                                            background = Bg,
                                            onBackground = TextPrimary
                                        )
                                    } else {
                                        lightColorScheme(
                                            primary = LocalAccent.current,
                                            onPrimary = Color.White,
                                            surface = Surface,
                                            surfaceContainer = Surface,
                                            onSurface = TextPrimary,
                                            background = Bg,
                                            onBackground = TextPrimary
                                        )
                                    }
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
                                        crashBannerVisible = safeRender && !crashBannerClosed,
                                        onDismissCrashBanner = { crashBannerClosed = true },
                                        onConnect = { connectUsb() },
                                        onUsbDiag = { usbDiag() },
                                        onProtoTest = { otgProtoTest() },
                                        onBigLog = { showBigLog = true },
                                        onInfo = { otgInfo() },
                                        onParts = { if (mode == 1) localParts() else otgParts() },
                                        onFlash = { pickImage.launch(arrayOf("*/*")) },
                                        onLocalFlash = { pickFlash?.launch(arrayOf("*/*")) },
                                        onRomArchive = { pickRomZip?.launch(arrayOf("application/zip","application/gzip","application/x-tar","*/*")) },
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
                                        onShowGuide = { showOnboarding = true },
                                        onOpenSource = {
                                            runCatching {
                                                startActivity(
                                                    Intent(Intent.ACTION_VIEW,
                                                        android.net.Uri.parse("https://github.com/moqi12386/Easy-platform-tools-GUI"))
                                                )
                                            }
                                        },
                                        onPlayground = { showPlayground = true },
                                        themeMode = themeMode,
                                        onTheme = { chooseTheme(it) },
                                        monet = monet,
                                        onMonet = { chooseMonet(it) },
                                        canMonet = Build.VERSION.SDK_INT >= 31,
                                        onStyle = { chooseStyle(it) },
                                        onLang = { chooseLang(it) },
                                        font = fontChoice,
                                        onFont = { chooseFont(it) },

                                        customFontNames = customFonts.map { it.first },
                                        customFontName = customFontPath?.let { p ->
                                            customFonts.firstOrNull { it.second.absolutePath == p }?.first
                                        },
                                        onDeleteCustomFont = { name -> deleteCustomFont(name) },
                                        onPickCustomFont = { name ->
                                            val hit = customFonts.firstOrNull { it.first == name }
                                            if (hit != null) {
                                                customFontPath = hit.second.absolutePath
                                                customFont = customFamilies[hit.second.absolutePath]
                                                fontChoice = AppFont.Local
                                                saveCustomFonts()
                                                getSharedPreferences(PREF, MODE_PRIVATE).edit()
                                                    .putInt("font", AppFont.Local.ordinal).apply()
                                                append(t("已切换字体：$name", "Font switched: $name"), OkColor)
                                            }
                                        },
                                        appliedFont = familyFor(fontChoice),
                                        onResetGlass = {
                                            uiAlpha = 0f; glassBlur = 30f; glassDensity = 100f
                                            getSharedPreferences(PREF, MODE_PRIVATE).edit()
                                                .putFloat("alpha", 0f).putFloat("gblur", 30f).putFloat("gdens", 100f).apply()
                                        },
                                        fontProgress = fontProgress,
                                        onLogColors = { showLogColors = true },
                                        autoUpdate = autoUpdate,
                                        onAutoUpdate = { v ->
                                            autoUpdate = v
                                            getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("autoupdate", v).apply()
                                        },
                                        onCheckUpdate = { checkUpdate(true) },
                                        downloadedFonts = onlineFamilies.keys.mapNotNull { k ->
                                            AppFont.entries.firstOrNull { it.name == k }
                                        }.toSet(),
                                        fontTargetKey = fontTargetKey,
                                        onOnlineFont = { chooseOnlineFont(it) },
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
                    // Material Design 3：底部导航直接用 M3 的 NavigationBar + NavigationBarItem
                    if (style == UiStyle.Material) {
                        // 这段在页面的 MaterialTheme 外面，不自己套一层的话拿到的是 M3 默认【浅色】主题
                        // —— 深色模式下整屏都黑了，只有底栏还是浅紫色的，就是这里的原因。
                        MaterialTheme(
                            typography = typographyWith(familyFor(fontChoice)),
                            colorScheme = if (dark) {
                                darkColorScheme(
                                    primary = LocalAccent.current,
                                    surface = Surface,
                                    // 底栏底色跟卡片用同一个色，M3 默认的 #211F26 偏紫、比卡片亮一档，
                                    // 摆在一起像两个东西
                                    surfaceContainer = Surface,
                                    // NavigationBarItem 的选中药丸取 secondaryContainer、图标取 onSecondaryContainer，
                                    // 不覆盖的话是 M3 基线那套紫色，跟本项目的蓝色主色对不上
                                    secondaryContainer = LocalAccent.current.copy(alpha = 0.24f),
                                    onSecondaryContainer = LocalAccent.current,
                                    onSurface = TextPrimary,
                                    onSurfaceVariant = Palette.text2,
                                    background = Bg,
                                    onBackground = TextPrimary
                                )
                            } else {
                                lightColorScheme(
                                    primary = LocalAccent.current,
                                    onPrimary = Color.White,
                                    surface = Surface,
                                    surfaceContainer = Surface,
                                    secondaryContainer = LocalAccent.current.copy(alpha = 0.14f),
                                    onSecondaryContainer = LocalAccent.current,
                                    onSurface = TextPrimary,
                                    onSurfaceVariant = Palette.text2,
                                    background = Bg,
                                    onBackground = TextPrimary
                                )
                            }
                        ) {
                            // containerColor / tonalElevation 都不写，直接用 NavigationBar 的官方默认
                            // （surfaceContainer + 0dp），跟其它 M3 组件一致
                            NavigationBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                            ) {
                                NavigationBarItem(
                                    selected = page == 0,
                                    onClick = { page = 0 },
                                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                                    label = { Text(t("主页", "Home")) }
                                )
                                NavigationBarItem(
                                    selected = page == 1,
                                    onClick = { page = 1 },
                                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                    label = { Text(t("设置", "Settings")) }
                                )
                            }
                        }
                    } else if (style == UiStyle.Miuix) {
                        // MIUIX 底栏已搬到 MiuixSlots.kt（MiuixBottomBar）
                        MiuixBottomBar(
                            page = page,
                            onPage = { page = it },
                            homeLabel = t("主页", "Home"),
                            settingsLabel = t("设置", "Settings"),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    } else if (useBackdrop) {
                        // 照抄 demo 的 Progressive blur：底栏上方做一层渐变模糊
                        // 底栏上方的渐变淡出：内容照样能滑到底栏下面，只是越靠下越淡
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Bg.copy(alpha = 0.55f),
                                            Bg.copy(alpha = 0.92f)
                                        )
                                    )
                                )
                        )

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
                                    tint = onBar, modifier = Modifier.size(24.dp)
                                )
                                Text(t("主页", "Home"), color = onBar, fontSize = 10.sp)
                            }
                            LiquidBottomTab(onClick = { page = 1 }) {
                                Icon(
                                    Icons.Filled.Settings, contentDescription = null,
                                    tint = onBar, modifier = Modifier.size(24.dp)
                                )
                                Text(t("设置", "Settings"), color = onBar, fontSize = 10.sp)
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
                            onPage = { page = it },
                            mono = style == UiStyle.Native
                        )
                    }

                }
                // 所有弹窗都在这层 Provider 里，才能拿到壁纸 backdrop 画真玻璃；
                // 以前弹窗写在 Provider 外面，LocalWallBackdrop/LocalGlassAssets 全是 null，
                // 于是日志颜色弹窗只能走纯白兜底、按钮也退回 Material 样式。
                CompositionLocalProvider(
                    LocalWallBackdrop provides wallBackdrop,
                    LocalGlassAssets provides assets,
                    LocalGlassScreen provides screen,
                    LocalTitleColor provides onWall,
                    LocalBarColor provides onBar,
                    LocalCardText provides Palette.text,
                    LocalStyle provides style
                ) {
                if (showOnboarding) {
                    OnboardingDialog(
                        lang = lang,
                        onDone = {
                            showOnboarding = false
                            getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
                        }
                    )
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
                        lang = lang,
                        onPick = { path -> showBing = false; setWallpaperFromFile(path) },
                        onDismiss = { showBing = false }
                    )
                }

                MiuixTheme(colors = if (dark) miuixDark() else miuixLight()) {
                    MaterialTheme(
                        typography = typographyWith(
                                    familyFor(fontChoice)
                                ),
                        colorScheme = if (dark) {
                            darkColorScheme(primary = LocalAccent.current, surface = Surface, onSurface = TextPrimary)
                        } else {
                            lightColorScheme(primary = LocalAccent.current, surface = Surface, onSurface = TextPrimary)
                        }
                    ) {
                        if (showParts) {
                            PartsDialog(style, lang, parts, onPick = { p ->
                                choosePart(p)
                                showParts = false
                                toast(t("默认分区：$p", "Default partition: $p"))
                            }, onClose = { showParts = false })
                        }
                        if (showRomScript) {
                            LiquidChoiceDialog(
                                backdrop = wallBackdrop,
                                title = tr(lang, "选择刷机脚本", "Pick flash script"),
                                options = romScripts.map { (n, _) ->
                                    if (n.lowercase(Locale.US).contains("except_storage"))
                                        tr(lang, "$n（保数据）", "$n (keep data)")
                                    else if (n.lowercase(Locale.US).endsWith(".sh") || n.lowercase(Locale.US).endsWith(".bat"))
                                        tr(lang, "$n（会清数据）", "$n (wipes data)")
                                    else n
                                },
                                selected = -1,
                                onPick = { i -> pickRomScript(i) },
                                onDismiss = { showRomScript = false }
                            )
                        }
                        if (showRom) {
                            RomDialog(
                                style, lang, romSteps,
                                onStart = {
                                    // 先要通知权限，否则实况进度条会被静默跳过
                                    ensureNotifyPerm()
                                    // 点了「开始刷写」立刻给个反馈：弹窗马上关掉，
                                    // 后面是跑几分钟的刷写，不给提示会以为没反应
                                    toast(tr(lang, "已开始线刷，请在日志里看进度", "Flashing started — see the log"))
                                    runRom()
                                },
                                onDismiss = { showRom = false }
                            )
                        }
                        if (showBigLog) {
                            BigLogOverlay(
                                logs = logs,
                                lang = lang,
                                onClose = { showBigLog = false },
                                onClear = { clearLog() }
                            )
                        }
                        if (showLogColors) {
                    LogColorDialog(
                        lang = lang,
                        initial = listOf(CmdColor, OkText, ErrText, LogText),
                        onSave = { c ->
                            logCmdColor = c[0]; logOkColor = c[1]; logErrColor = c[2]; logTextColor = c[3]
                            getSharedPreferences(PREF, MODE_PRIVATE).edit()
                                .putInt("lc2Cmd", android.graphics.Color.argb(c[0].alpha, c[0].red, c[0].green, c[0].blue))
                                .putInt("lc2Ok", android.graphics.Color.argb(c[1].alpha, c[1].red, c[1].green, c[1].blue))
                                .putInt("lc2Err", android.graphics.Color.argb(c[2].alpha, c[2].red, c[2].green, c[2].blue))
                                .putInt("lc2Text", android.graphics.Color.argb(c[3].alpha, c[3].red, c[3].green, c[3].blue))
                                .apply()
                            append(t("日志颜色已保存", "Log colors saved"), OkColor)
                            showLogColors = false
                        },
                        onDismiss = { showLogColors = false }
                    )
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
                }  // CompositionLocalProvider（弹窗玻璃上下文）
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
    // 必须是 Compose 状态：之前是普通 var，莫奈取色是异步算出来的，
    // 赋值时没有任何订阅者会重组，于是底栏/按钮一直停在默认蓝，切了莫奈也不变
    var monetAccent: Color? by androidx.compose.runtime.mutableStateOf(null)

    /**
     * Material 风格专用的 Material You 配色。非 Material 时为 null。
     * 之所以放在这里：Palette 被四种风格共用，不能只改 Material 分支，
     * 只能让 Material 时这些 getter 转发到动态色。
     */
    var m3: androidx.compose.material3.ColorScheme? = null

    val bg: Color
        get() = m3?.background ?: run {
            val base = if (dark) Color(0xFF101114) else Color(0xFFF2F3F5)
            monetAccent?.let { androidx.compose.ui.graphics.lerp(base, it, if (dark) 0.10f else 0.05f) } ?: base
        }
    val surface: Color
        get() = m3?.surfaceContainer ?: run {
            val base = if (dark) Color(0xFF1B1C20) else Color(0xFFFFFFFF)
            monetAccent?.let { androidx.compose.ui.graphics.lerp(base, it, if (dark) 0.12f else 0.06f) } ?: base
        }
    val text: Color get() = m3?.onSurface ?: if (dark) Color(0xFFECEDEF) else Color(0xFF16181D)
    val text2: Color get() = m3?.onSurfaceVariant ?: if (dark) Color(0xFF9A9DA5) else Color(0xFF8C9199)
    val accent: Color get() = m3?.primary ?: (monetAccent ?: Color(0xFF3B82F6))
    val chevron: Color get() = if (dark) Color(0xFF5C6068) else Color(0xFFC2C6CC)
    val inset: Color get() = if (dark) Color(0xFF121316) else Color(0xFFF5F6F8)
    val chip: Color get() = if (dark) Color(0xFF2A2B30) else Color(0xFFF2F3F5)

    /** 滑块/开关的未选中轨道、输入框描边 —— 三处原本各写一份同样的成对色 */
    val track: Color get() = if (dark) Color(0xFF3A3C42) else Color(0xFFCED2D8)

    /** 输入框描边（比 track 浅一点） */
    val outline: Color get() = if (dark) Color(0xFF3A3C42) else Color(0xFFE3E5E9)

    /** 滑条未激活轨道 */
    val trackFaint: Color get() = if (dark) Color(0xFF3A3C42) else Color(0xFFE1E4E8)

    /** 弹窗容器（比 glassTint 更实一点） */
    val dialogSurface: Color get() = if (dark) Color(0xFF14161B) else Color(0xFFFAFBFD)

    /** 行内小卡片 / 内嵌块 */
    val insetBlock: Color get() = if (dark) Color(0xFF1C1D22) else Color(0xFFF1F3F7)

    /** 警告条：底色 + 前景 */
    val warnBg: Color get() = if (dark) Color(0xFF3A2E12) else Color(0xFFFFF3D6)
    val warnFg: Color get() = if (dark) Color(0xFFFFC04D) else Color(0xFF8A5A00)
    val nativeCard: Color get() = Color(0xFF1B1F27).copy(alpha = 0.92f)

    /** 原生风格在中性底板上用的主色（不跟莫奈走） */
    val nativeAccent: Color get() = Color(0xFFECEDEF)

    /** 液态玻璃卡片的底料：浅色 #FAFAFA / 深色 #121212（demo 原值） */
    val glassTint: Color get() = if (dark) Color(0xFF121212) else Color(0xFFFAFAFA)

    /** 玻璃 / 原生 在不支持取样的机器上的纯色兜底 */
    val glassSolid: Color get() = if (dark) Color(0xFF1F2026) else Color(0xFFFFFFFF)

    /** 弹窗背后的遮罩：浅色用偏蓝的深色，深色直接压黑 */
    val dim: Color get() = if (dark) Color(0xFF121212).copy(alpha = 0.56f) else Color(0xFF29293A).copy(alpha = 0.23f)
}

/** 卡片里的文字颜色：玻璃/深色卡片用白字，其余跟随主题 */
val LocalCardText = androidx.compose.runtime.staticCompositionLocalOf { Palette.text }
/** 当前界面风格，供组件内部做分化样式 */
val LocalStyle = androidx.compose.runtime.staticCompositionLocalOf { UiStyle.Native }
/**
 * 主色（强调色）。原生风格下会被覆盖成纯中性色，保证「原生风格不出现任何彩色」。
 */
val LocalAccent = androidx.compose.runtime.staticCompositionLocalOf<Color> { Palette.accent }
/** 只含壁纸的图层，卡片从这里取样 */
val LocalWallBackdrop = androidx.compose.runtime.staticCompositionLocalOf<Backdrop?> { null }
/** 弹窗降级用：壁纸资源（拿来做取样玻璃） */
val LocalGlassAssets = androidx.compose.runtime.staticCompositionLocalOf<GlassAssets?> { null }
/** 弹窗降级用：屏幕尺寸 */
val LocalGlassScreen = androidx.compose.runtime.staticCompositionLocalOf<IntSize> { IntSize.Zero }
/** 直接画在壁纸上的标题颜色 */
val LocalTitleColor = androidx.compose.runtime.staticCompositionLocalOf { Palette.text }
/** 底栏文字/图标颜色：按壁纸「底部」亮度算，不能用顶部 */
val LocalBarColor = androidx.compose.runtime.staticCompositionLocalOf { Color.White }

data class BarItem(val icon: ImageVector, val label: String)


// ======================= 通用组件 =======================


/** 一行设置项：图标 + 标题 + 副标题 + 右侧尾随内容 */
@Composable
fun KRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    chevron: Boolean = true,
    tint: Color? = null,
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
        if (icon != null) {
            // MIUIX 的行由 MiuixRow 负责，这里只剩玻璃 / 原生的纯图标
            Icon(icon, contentDescription = null, tint = text, modifier = Modifier.size(23.dp))
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
    onPick: (Int) -> Unit,
    deletable: Set<Int> = emptySet(),
    onDelete: ((Int) -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }
    val text = LocalCardText.current
    Box {
        AppRow(
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
                onDismiss = { open = false },
                deletable = deletable,
                onDelete = if (onDelete == null) null else { i -> open = false; onDelete!!.invoke(i) }
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
        // MIUIX 的开关行由 MiuixSwitchRow 负责（用 miuix 的 Switch 控件）
        Icon(
            icon, contentDescription = null,
            tint = if (enabled) text else text.copy(alpha = 0.35f),
            modifier = Modifier.size(23.dp)
        )
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
            // 照 demo 原样：LiquidToggle 自带 64x28 胶囊尺寸，不要套盒子改尺寸
            LiquidToggle(
                selected = { checked },
                onSelect = { onCheckedChange(it) },
                backdrop = wall
            )
        } else {
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = LocalAccent.current,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Palette.track,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * 页面底部要给悬浮底栏 + 系统导航栏让出的高度。
 * 以前这里写死 120dp，是拍脑袋的常数：手势导航和三键导航的导航栏差很多，
 * 换字体/语言后底栏高度也会变，于是内容可能被遮住或留一大块空白。
 * 现在按「当前风格的底栏高度 + 系统导航栏 inset + 12dp 呼吸」算。
 */
fun bottomSpacer(): androidx.compose.ui.unit.Dp {
    val barHeight = when (LocalStyle.current) {
        UiStyle.Material -> 80.dp   // M3 NavigationBar 默认 80dp
        UiStyle.Miuix -> 64.dp      // miuix NavigationBar ItemHeight 64dp
        else -> 84.dp               // 玻璃/原生：胶囊 + 上下 10dp 留白
    }
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return barHeight + nav + 12.dp
}

@OptIn(ExperimentalMaterial3Api::class)
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
                    activeTrackColor = LocalAccent.current,
                    inactiveTrackColor = Palette.trackFaint
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
    // Material：用官方 M3 对话框（BasicAlertDialog + Surface），不套自绘面板
    if (style == UiStyle.Material) {
        MaterialDialog(title = title, onDismiss = onDismiss, body = body)
        return
    }
    // 玻璃 / 原生：自绘面板，皮肤在 GlassSlots.GlassDialog
    GlassDialog(title, onDismiss, body)
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
    AppDialog(style, title, onDismiss) {
        Text(message, color = Palette.text2, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        DialogActions(
            dismissText = tr(lang, "取消", "Cancel"),
            onDismiss = onDismiss,
            confirmText = tr(lang, "擦除", "Erase"),
            onConfirm = onConfirm,
            confirmColor = Danger
        )
    }
}

@Composable
private fun BigLogOverlay(
    logs: List<LogLine>,
    lang: Lang,
    onClose: () -> Unit,
    onClear: () -> Unit
) {
    val text = LocalCardText.current
    val style = LocalStyle.current
    val shape = RoundedCornerShape(28.dp)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isEmpty()) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        runCatching { listState.scrollToItem(logs.lastIndex) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onClose() }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp)
                .clip(shape)
                .background(
                            Palette.dialogSurface.copy(alpha = 0.97f)
                )
                .clickable { }
                .padding(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr(lang, "日志", "Log"), color = text, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text("(${logs.size})", color = text.copy(alpha = 0.45f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                val bigBd = LocalWallBackdrop.current
                if (useBackdrop && bigBd != null) {
                    // 放大后的「清空」「关闭」也用液态按钮，和主页日志头那两个一致
                    val btnSurface = if (Palette.dark) Color.Black.copy(alpha = 0.22f)
                    else Color.White.copy(alpha = 0.35f)
                    LiquidButton(
                        onClick = onClear,
                        backdrop = bigBd,
                        modifier = Modifier.height(48.dp),
                        surfaceColor = btnSurface
                    ) { Text(tr(lang, "清空", "Clear"), color = text, fontSize = 13.sp) }
                    Spacer(Modifier.width(22.dp))
                    LiquidButton(
                        onClick = onClose,
                        backdrop = bigBd,
                        modifier = Modifier.height(48.dp),
                        surfaceColor = btnSurface
                    ) { Text(tr(lang, "关闭", "Close"), color = text, fontSize = 13.sp) }
                } else {
                    Text(
                        tr(lang, "清空", "Clear"),
                        color = LocalAccent.current, fontSize = 14.sp,
                        modifier = Modifier.clickable { onClear() }
                    )
                    Spacer(Modifier.width(18.dp))
                    Text(
                        tr(lang, "关闭", "Close"),
                        color = LocalAccent.current, fontSize = 14.sp,
                        modifier = Modifier.clickable { onClose() }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            val inset = if (style == UiStyle.Glass) {
                glassInsetSurface(RoundedCornerShape(16.dp))
            } else {
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.insetBlock)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().then(inset).padding(12.dp)
            ) {
                items(logs) { l ->
                    Text(l.text, color = l.color, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
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
    AppDialog(style, tr(lang, "线刷步骤（${steps.size}）", "Flash steps (${steps.size})"), onDismiss) {
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
        Spacer(Modifier.height(16.dp))
        DialogActions(
            dismissText = tr(lang, "取消", "Cancel"),
            onDismiss = onDismiss,
            confirmText = tr(lang, "开始刷写", "Start"),
            onConfirm = onStart,
            confirmColor = LocalAccent.current
        )
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
    AppDialog(style, tr(lang, "分区表（${list.size}）", "Partitions (${list.size})"), onClose) {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            GlassButton(
                text = tr(lang, "关闭", "Close"),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                textColor = Palette.text,
                surface = Color.White.copy(alpha = 0.3f)
            )
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
    crashBannerVisible: Boolean,
    onDismissCrashBanner: () -> Unit,
    onConnect: () -> Unit,
    onUsbDiag: () -> Unit,
    onProtoTest: () -> Unit,
    onBigLog: () -> Unit,
    onInfo: () -> Unit,
    onParts: () -> Unit,
    onFlash: () -> Unit,
    onLocalFlash: () -> Unit,
    onRomArchive: () -> Unit,
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
            .padding(bottom = bottomSpacer())
            .verticalScroll(rememberScrollState())
    ) {
        if (crashBannerVisible) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.warnBg)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    Icons.Filled.Warning, null,
                    tint = Palette.warnFg,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    tr(
                        lang,
                        "上次启动异常，已撤销那次操作。设置里可查看崩溃日志",
                        "Last launch failed; that action was rolled back. See crash log in Settings"
                    ),
                    color = if (Palette.dark) Color(0xFFFFD98A) else Color(0xFF6B4700),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Close, null,
                    tint = if (Palette.dark) Color(0xFFFFD98A) else Color(0xFF6B4700),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onDismissCrashBanner() }
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        AppTitle("Easy-platform-tools")
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

        AppCard(style, assets, backdrop, screen, blur, density, alpha) {
            AppSelectRow(
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
        AppCard(style, assets, backdrop, screen, blur, density, alpha) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppField(
                    value = cmd,
                    onValueChange = { cmd = it },
                    placeholder = if (mode == 1) tr(lang, "shell 命令", "shell command")
                        else tr(lang, "fastboot 命令", "fastboot command"),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                GlassButton(
                    text = tr(lang, "执行", "Run"),
                    onClick = { onRun(cmd); cmd = "" },
                    modifier = Modifier.height(52.dp),
                    primary = true,
                    tint = LocalAccent.current
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Spacer(Modifier.height(12.dp))

        AppCard(style, assets, backdrop, screen, blur, density, alpha) {
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
                    Text(tr(lang, "· 执行中", "· running"), color = LocalAccent.current, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                val clearWall = LocalWallBackdrop.current
                // 先「清空」，再隔开一段距离放「放大」：
                // 两个按钮贴在一起时，想放大很容易点到清空（清空是不可撤销的）
                if (style == UiStyle.Material) {
                    // Material 风格：直接用官方 TextButton（自带水波纹和 M3 内边距）
                    TextButton(onClick = onClear) {
                        Text(tr(lang, "清空", "Clear"), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onBigLog) {
                        Text(tr(lang, "放大", "Expand"), style = MaterialTheme.typography.labelLarge)
                    }
                } else if (style == UiStyle.Miuix) {
                    // MIUIX：官方 TextButton（docs/components/button.md）
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = tr(lang, "清空", "Clear"),
                        onClick = onClear
                    )
                    Spacer(Modifier.width(8.dp))
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = tr(lang, "放大", "Expand"),
                        onClick = onBigLog
                    )
                } else if (useBackdrop && clearWall != null) {
                    // 玻璃风格下「清空」是个真的液态按钮，不是一行蓝字
                    LiquidButton(
                        onClick = onClear,
                        backdrop = clearWall,
                        modifier = Modifier.height(48.dp),
                        surfaceColor = if (Palette.dark) Color.Black.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.35f)
                    ) {
                        Text(tr(lang, "清空", "Clear"), color = text, fontSize = 13.sp)
                    }
                } else {
                    Text(
                        tr(lang, "清空", "Clear"),
                        color = LocalAccent.current, fontSize = 13.sp,
                        modifier = Modifier.clickable { onClear() }
                    )
                }
                if (style != UiStyle.Material && style != UiStyle.Miuix) {
                    Spacer(Modifier.width(22.dp))
                    // 「放大」：把日志铺满整屏，刷机时能一次看到更多行（放最右边，最常用）
                    if (useBackdrop && clearWall != null) {
                        LiquidButton(
                            onClick = onBigLog,
                            backdrop = clearWall,
                            modifier = Modifier.height(48.dp),
                            surfaceColor = if (Palette.dark) Color.Black.copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.35f)
                        ) {
                            Text(tr(lang, "放大", "Expand"), color = text, fontSize = 13.sp)
                        }
                    } else {
                        Text(
                            tr(lang, "放大", "Expand"),
                            color = LocalAccent.current, fontSize = 13.sp,
                            modifier = Modifier.clickable { onBigLog() }
                        )
                    }
                }
            }
            // 日志输出框：和「fastboot 命令」输入框同一套底料
            val logShape = remember { RoundedCornerShape(16.dp) }
            val logSurface = if (style == UiStyle.Material) {
                // Material：日志区就是一块 M3 tonal surface，不用再自绘底板
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            } else if (style == UiStyle.Glass) {
                glassInsetSurface(logShape)
            } else {
                // 和输入框兜底底板完全一致：以前原生风格用「黑 45%」压深色卡片，
                // 深底上再写深色日志，日间模式直接看不清
                Modifier
                    .clip(logShape)
                    .background(Palette.insetBlock)
            }
            // 新日志进来就贴到最后一行；不滚的话窗口一直停在最旧那几行，得手动往下拉
            val logListState = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(logs.size) {
                if (logs.isEmpty()) return@LaunchedEffect
                // 新行这一帧还没测量完，等一帧再滚，否则 scrollToItem 会落空
                androidx.compose.runtime.withFrameNanos { }
                runCatching { logListState.scrollToItem(logs.lastIndex) }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .padding(horizontal = 14.dp)
                    .then(logSurface)
            ) {
                LazyColumn(state = logListState, modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    items(logs) { l -> Text(l.text, color = l.color, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))

        when (mode) {
            0 -> AppCard(style, assets, backdrop, screen, blur, density, alpha) {
                AppRow(Icons.Filled.PlayArrow, tr(lang, "连接设备", "Connect device"),
                    tr(lang, "USB 授权后自动打开", "auto after USB grant"), onClick = onConnect)
                AppRow(Icons.Filled.Build, tr(lang, "USB 诊断", "USB diagnosis"),
                    tr(lang, "列出系统认到的所有 USB 设备", "list every USB device the system sees"),
                    onClick = onUsbDiag)
                AppRow(Icons.Filled.Refresh, tr(lang, "协议自测", "Protocol self-test"),
                    tr(lang, "试几种 fastboot 帧格式", "try several fastboot framings"),
                    onClick = onProtoTest)
                AppRow(Icons.Filled.Info, tr(lang, "设备信息", "Device info"),
                    "product / version / serialno", onClick = onInfo)
                AppRow(Icons.Filled.List, tr(lang, "读分区表", "Partitions"),
                    "fastboot getvar all", onClick = onParts)
                AppRow(Icons.Filled.Build, tr(lang, "刷入镜像", "Flash image"),
                    tr(lang, "当前分区：$part", "target: $part"), onClick = onFlash)
                AppRow(Icons.Filled.Refresh, tr(lang, "线刷 ROM", "Flash ROM"),
                    tr(lang, "选目录，按脚本批量刷", "pick a folder, flash in order"), tint = Color(0xFF5E5CE6), onClick = onRom)
                AppRow(Icons.Filled.Refresh, tr(lang, "线刷 ROM（zip/tgz）", "Flash ROM (zip/tgz)"), tr(lang, "选压缩包，自动解压解析", "pick archive, auto-extract"), tint = Color(0xFF5E5CE6), onClick = onRomArchive)
                AppRow(Icons.Filled.Delete, tr(lang, "擦除分区", "Erase partition"), part,
                    onClick = { onErase(part) })
                AppRow(Icons.Filled.Refresh, tr(lang, "重启系统", "Reboot"), null, onClick = { onReboot("") }, tint = Color(0xFF34C759))
                AppRow(Icons.Filled.Refresh, tr(lang, "重启 Recovery", "Reboot recovery"), "recovery",
                    tint = Color(0xFFFF9F0A), onClick = { onReboot("recovery") })
                AppRow(Icons.Filled.Refresh, tr(lang, "重启 Bootloader", "Reboot bootloader"), "bootloader",
                    tint = Color(0xFF0A84FF), onClick = { onReboot("bootloader") })
                AppRow(Icons.Filled.Refresh, tr(lang, "进入 EDL", "Enter EDL"), "oem edl", onClick = onEdl, tint = Color(0xFFFF3B30))
            }
            1 -> AppCard(style, assets, backdrop, screen, blur, density, alpha) {
                AppRow(Icons.Filled.Lock, tr(lang, "检测 root", "Check root"), "su -c id", onClick = onRoot)
                AppRow(Icons.Filled.Build, tr(lang, "快捷刷入镜像", "Quick flash image"), tr(lang, "选镜像，直接 dd 到 $part", "pick an image, dd to $part"), onClick = onLocalFlash)
                AppRow(Icons.Filled.List, tr(lang, "读分区表", "Partitions"), "/dev/block/by-name", onClick = onParts)
                AppRow(Icons.Filled.Star, tr(lang, "备份分区", "Back up partition"),
                    tr(lang, "把 $part 导出为镜像", "dump $part to a file"), onClick = { onBackup(part) })
                AppRow(Icons.Filled.Star, tr(lang, "备份基带", "Back up baseband"),
                    tr(lang, "modem / persist / fsg 等", "modem / persist / fsg …"), onClick = onBackupBaseband)
                AppRow(Icons.Filled.Refresh, tr(lang, "重启系统", "Reboot"), "reboot", onClick = { onReboot("") }, tint = Color(0xFF34C759))
                AppRow(Icons.Filled.Refresh, tr(lang, "重启 Recovery", "Reboot recovery"), "reboot recovery",
                    tint = Color(0xFFFF9F0A), onClick = { onReboot("recovery") })
                AppRow(Icons.Filled.Refresh, tr(lang, "重启 EDL", "Reboot EDL"), "reboot edl", onClick = onEdl, tint = Color(0xFFFF3B30))
            }
            else -> AppCard(style, assets, backdrop, screen, blur, density, alpha) {
                AppRow(Icons.Filled.PlayArrow, tr(lang, "测试连接", "Ping"), "/api/status", onClick = onPing)
                AppRow(Icons.Filled.List, tr(lang, "读分区表", "Partitions"), "/api/partitions", onClick = onParts)
                AppRow(Icons.Filled.Refresh, tr(lang, "重启系统", "Reboot"), "/api/reboot", onClick = { onReboot("") }, tint = Color(0xFF34C759))
                AppRow(Icons.Filled.Refresh, tr(lang, "重启 Recovery", "Reboot recovery"), "recovery",
                    tint = Color(0xFFFF9F0A), onClick = { onReboot("recovery") })
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
    onOpenSource: () -> Unit,
    onShowGuide: () -> Unit,
    onPlayground: () -> Unit,
    themeMode: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    monet: Boolean,
    onMonet: (Boolean) -> Unit,
    canMonet: Boolean,
    onStyle: (UiStyle) -> Unit,
    onLang: (Lang) -> Unit,
    font: AppFont,
    onFont: (AppFont) -> Unit,

    customFontNames: List<String>,
    customFontName: String?,
    onPickCustomFont: (String) -> Unit,
    onDeleteCustomFont: (String) -> Unit,
    appliedFont: androidx.compose.ui.text.font.FontFamily?,
    onResetGlass: () -> Unit,
    fontProgress: Int,
    onLogColors: () -> Unit,
    autoUpdate: Boolean,
    onAutoUpdate: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    downloadedFonts: Set<AppFont>,
    fontTargetKey: String,
    onOnlineFont: (AppFont) -> Unit,
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
            .padding(bottom = bottomSpacer())
            .verticalScroll(rememberScrollState())
    ) {
        val styleOrder = listOf(UiStyle.Glass, UiStyle.Miuix, UiStyle.Material, UiStyle.Native)
        // 删字体要确认一次（液态玻璃弹窗）
        var pendingDeleteFont by remember { mutableStateOf<String?>(null) }
        var showPartPicker by remember { mutableStateOf(false) }
        val partOptions = remember(parts) {
            if (parts.isEmpty()) listOf("boot", "init_boot", "vbmeta", "dtbo", "super", "system", "vendor", "recovery")
            else parts
        }

        AppTitle(tr(lang, "设置", "Settings"))

        AppCard(style, assets, backdrop, screen, blur, density, alpha) {
            AppSelectRow(
                icon = Icons.Filled.Edit,
                title = tr(lang, "界面样式", "Style"),
                subtitle = tr(lang, "选择应用的界面风格", "Choose the app style"),
                value = tr(lang, style.labelZh, style.labelEn),
                options = styleOrder.map { tr(lang, it.labelZh, it.labelEn) },
                selected = styleOrder.indexOf(style),
                onPick = { onStyle(styleOrder[it]) }
            )
            AppSelectRow(
                icon = Icons.Filled.Public,
                title = tr(lang, "语言", "Language"),
                subtitle = null,
                value = if (lang == Lang.Zh) "中文" else "English",
                options = listOf("中文", "English"),
                selected = lang.ordinal,
                onPick = { onLang(Lang.entries[it]) }
            )
            AppSelectRow(
                icon = Icons.Filled.Build,
                title = tr(lang, "字体", "Font"),
                subtitle = null,
                value = when {
                    fontProgress >= 0 && fontTargetKey.isNotEmpty() ->
                        tr(lang, "下载中 $fontProgress%", "downloading $fontProgress%")
                    customFontName != null -> customFontName
                    font.isOnline && font in downloadedFonts ->
                        tr(lang, "${font.zh}（已下载）", "${font.en} (downloaded)")
                    font.isOnline -> tr(lang, "${font.zh}（在线）", "${font.en} (online)")
                    else -> tr(lang, font.zh, font.en)
                },
                options = AppFontOrder.map { f ->
                    when {
                        f.isOnline && f in downloadedFonts -> tr(lang, "${f.zh}（已下载）", "${f.en} (downloaded)")
                        f.isOnline -> tr(lang, "${f.zh}（在线）", "${f.en} (online)")
                        else -> tr(lang, f.zh, f.en)
                    }
                } + customFontNames,
                selected = if (customFontName != null)
                    AppFontOrder.size + customFontNames.indexOf(customFontName)
                else AppFontOrder.indexOf(font),
                onPick = { i ->
                    if (i < AppFontOrder.size) onFont(AppFontOrder[i])
                    else customFontNames.getOrNull(i - AppFontOrder.size)?.let { onPickCustomFont(it) }
                },
                // 只有自己导入的字体能删
                deletable = customFontNames.indices.map { AppFontOrder.size + it }.toSet(),
                onDelete = { i ->
                    // 先弹确认，不直接删
                    customFontNames.getOrNull(i - AppFontOrder.size)?.let { pendingDeleteFont = it }
                }
            )
            // 预览：当前字体到底有没有生效，一眼就能看出来
            Text(
                tr(lang, "预览：Aa 汉字永字八法 123", "Preview: Aa 汉字永字八法 123"),
                color = LocalCardText.current.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontFamily = appliedFont,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp)
            )
            // 删字体的确认框（液态玻璃）
            pendingDeleteFont?.let { name ->
                GlassConfirmDialog(
                    lang = lang,
                    title = tr(lang, "删除字体", "Delete font"),
                    message = tr(lang, "确定删除「$name」？文件会一起删掉。", "Delete \"$name\"? The file will be removed too."),
                    confirmText = tr(lang, "删除", "Delete"),
                    onConfirm = {
                        onDeleteCustomFont(name)
                        pendingDeleteFont = null
                    },
                    onDismiss = { pendingDeleteFont = null }
                )
            }

        }

        Spacer(Modifier.height(12.dp))

        AppCard(style, assets, backdrop, screen, blur, density, alpha) {
            AppSelectRow(
                icon = Icons.Filled.Star,
                title = tr(lang, "夜间模式", "Dark mode"),
                subtitle = tr(lang, "日间 / 夜间 / 跟随系统", "Light / Dark / Follow system"),
                value = tr(lang, themeMode.zh, themeMode.en),
                options = ThemeMode.entries.map { tr(lang, it.zh, it.en) },
                selected = themeMode.ordinal,
                onPick = { onTheme(ThemeMode.entries[it]) }
            )
            if (style == UiStyle.Material) {
                AppSwitchRow(
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

        AppCard(style, assets, backdrop, screen, blur, density, alpha) {
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
                            tint = LocalAccent.current
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
                            colors = primaryButtonColors(),
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
            var slidersOpen by remember { mutableStateOf(false) }
            AppCard(style, assets, backdrop, screen, blur, density, alpha) {
                AppRow(
                    Icons.Filled.Build,
                    tr(lang, "玻璃参数", "Glass parameters"),
                    if (slidersOpen) tr(lang, "点收起", "tap to collapse") else tr(lang, "点展开", "tap to expand"),
                    onClick = { slidersOpen = !slidersOpen }
                )
                if (slidersOpen) {
            Column(Modifier.padding(vertical = 8.dp)) {
                SliderRow(style, tr(lang, "面板不透明度", "Panel opacity"), opacity, onOpacity)
                SliderRow(style, tr(lang, "玻璃模糊", "Glass blur"), glassBlur, onGlassBlur)
                SliderRow(style, tr(lang, "玻璃浓度", "Glass density"), glassDensity, onGlassDensity)
                Spacer(Modifier.height(2.dp))
                // 按钮一律用 demo 的 LiquidButton（有按下液态形变 + 高光）
                val resetWall = LocalWallBackdrop.current
                if (useBackdrop && resetWall != null) {
                    LiquidButton(
                        onClick = onResetGlass,
                        backdrop = resetWall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
                    ) {
                        Text(
                            tr(lang, "重置为默认参数", "Reset to defaults"),
                            color = LocalAccent.current, fontSize = 14.sp
                        )
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(LocalAccent.current.copy(alpha = 0.14f))
                            .clickable { onResetGlass() }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            tr(lang, "重置为默认参数", "Reset to defaults"),
                            color = LocalAccent.current, fontSize = 14.sp
                        )
                    }
                }
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
        }

        Spacer(Modifier.height(12.dp))

        AppCard(style, assets, backdrop, screen, blur, density, alpha) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(tr(lang, "刷写", "Flashing"), color = LocalCardText.current, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                var p by remember { mutableStateOf(part) }
                LaunchedEffect(part) { p = part }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppField(
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
                            .background(LocalAccent.current.copy(alpha = 0.16f))
                            .clickable { showPartPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.List, contentDescription = null, tint = LocalAccent.current, modifier = Modifier.size(22.dp))
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

        AppCard(style, assets, backdrop, screen, blur, density, alpha) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(tr(lang, "遥控 PC", "Remote PC"), color = LocalCardText.current, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                AppField(
                    value = host,
                    onValueChange = onHost,
                    placeholder = tr(lang, "电脑地址，例如 192.168.1.10:8765", "PC address"),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                AppField(
                    value = token,
                    onValueChange = onToken,
                    placeholder = tr(lang, "配对码", "Pairing code"),
                    numeric = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        AppCard(style, assets, backdrop, screen, blur, density, alpha) {
            val ctx = LocalContext.current
            var showCrash by remember { mutableStateOf(false) }
            val crashFile = remember { File(ctx.filesDir, "last-crash.txt") }
            val actionFile = remember { File(ctx.filesDir, "last-action.txt") }
            AppRow(
                Icons.Filled.Share,
                tr(lang, "导出/分享日志", "Export / share log"),
                tr(lang, "把日志发给开发者", "Send the log to the developer"),
                onClick = { onShareLog() }
            )
            // 日志输出框里的文字颜色：命令 / 成功 / 失败 / 普通，可自己改
            AppRow(
                Icons.Filled.Create,
                tr(lang, "日志文字颜色", "Log text colors"),
                tr(lang, "自定义命令 / 成功 / 失败 / 普通颜色", "Customize command / ok / error / normal"),
                onClick = { onLogColors() }
            )
            if (crashFile.exists() || actionFile.exists()) {
                AppRow(
                    Icons.Filled.Warning,
                    tr(lang, "查看崩溃日志", "View crash log"),
                    if (actionFile.exists()) tr(lang, "上次崩在：切换样式", "Crashed after: style switch")
                    else tr(lang, "上次启动的异常信息", "Last launch exception"),
                    onClick = { showCrash = true }
                )
            }
            // 检查更新：默认开启、可关掉；下面另给一个手动检查入口
            AppSwitchRow(
                icon = Icons.Filled.Refresh,
                title = tr(lang, "自动检查更新", "Check for updates"),
                subtitle = tr(
                    lang,
                    "启动时查一次 GitHub 上的最新版本，有新版本会写进日志",
                    "Look up the latest GitHub release on launch and log it"
                ),
                checked = autoUpdate,
                onCheckedChange = { onAutoUpdate(it) }
            )
            AppRow(
                icon = Icons.Filled.Info,
                title = tr(lang, "立即检查更新", "Check now"),
                subtitle = tr(lang, "手动查一次最新版本", "look up the latest release now"),
                onClick = { onCheckUpdate() }
            )
            // Shizuku 授权：绕过小米超级岛白名单需要它去掐 XMSF 网络。
            // 状态实时反映，点一下就去 Shizuku 拉授权。
            AppRow(
                icon = Icons.Filled.Lock,
                title = tr(lang, "Shizuku 授权", "Shizuku permission"),
                subtitle = when {
                    !XmsfBypass.hasShizuku() -> tr(
                        lang, "Shizuku 未运行：先打开 Shizuku 并启动服务",
                        "Shizuku is not running: start it first"
                    )
                    XmsfBypass.hasPermission() -> tr(
                        lang, "已授权 · 点一下重新申请",
                        "Granted · tap to re-request"
                    )
                    else -> tr(
                        lang, "未授权 · 点一下拉起 Shizuku 授权框",
                        "Not granted · tap to request"
                    )
                },
                onClick = {
                    // 点一下：重新申请授权，并把自检结果写进 App 私有目录的 xmsf.txt，
                    // 这样 adb 能直接拉出来看（设置页拿不到主页日志面板）
        XmsfBypass.requestPermission()
                    XmsfBypass.dumpToFile()
                    // 没授权实况通知就直接把用户带到通知设置页
                    if (!XmsfBypass.canPromote(XmsfBypass.appCtx ?: return@AppRow)) XmsfBypass.openPromotedSettings()
                }
            )
            AppSwitchRow(
                icon = Icons.Filled.Refresh,
                title = tr(lang, "安全模式", "Safe mode"),
                subtitle = tr(
                    lang,
                    "手动关闭玻璃特效（崩溃时不会自动开启，只回退上一步）",
                    "Manually disable glass effects (crashes only roll back the last action)"
                ),
                checked = safeRender,
                onCheckedChange = { onForceSafe(it) }
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
                AppDialog(style, tr(lang, "崩溃日志", "Crash log"), onDismiss = { showCrash = false }) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(crashText, color = Palette.text2, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(10.dp))
                    GlassButton(
                        text = tr(lang, "关闭", "Close"),
                        onClick = { showCrash = false },
                        modifier = Modifier.fillMaxWidth(),
                        textColor = Palette.text,
                        surface = Color.White.copy(alpha = 0.3f),
                        plainText = true
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        AppCard(style, assets, backdrop, screen, blur, density, alpha) {
            AppRow(
                Icons.Filled.Info,
                tr(lang, "关于", "About"),
                tr(lang, "版本信息与许可", "Version and license"),
                onClick = { onAbout() }
            )
            AppRow(
                Icons.Filled.Info,
                tr(lang, "使用向导", "Guide"),
                tr(lang, "再看一遍新手引导", "Show the intro again"),
                onClick = { onShowGuide() }
            )
            AppRow(
                Icons.Filled.Share,
                tr(lang, "开源地址", "Source code"),
                "github.com/moqi12386/Easy-platform-tools-GUI",
                onClick = { onOpenSource() }
            )
            // Glass Playground 暂时隐藏（保留代码，需要时把上面这行注释去掉）
            if (false && style == UiStyle.Glass) {
                AppRow(
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
    onPage: (Int) -> Unit,
    mono: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val n = items.size
    val anim = remember { Animatable(page.toFloat()) }
    var drag by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    val capsule = remember { RoundedCornerShape(32.dp) }
    val pillShape = remember { RoundedCornerShape(26.dp) }
    val dark = Palette.dark
    // 原生风格：底栏和上面的卡片同色（深色板），所以字一律白；未选中略淡
    val labelIdle = if (mono) Color.White.copy(alpha = 0.55f) else LocalBarColor.current
    val accent = if (mono) Color.White else LocalAccent.current

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
                val containerColor = Palette.glassTint.copy(alpha = 0.4f)
                
                val surfaceMod = when {
                    // 原生风格：和上面卡片完全同一个色板，风格统一
                    mono -> Modifier
                        .shadow(10.dp, capsule, clip = false)
                        .clip(capsule)
                        .background(Palette.nativeCard)
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
                        tintAlpha = if (dark) 0.50f else 0.45f
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
                        .background(if (mono) Color.White.copy(alpha = 0.10f) else accent.copy(alpha = if (dark) 0.28f else 0.14f))
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
                                tint = if (sel) accent else labelIdle,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                item.label,
                                color = if (sel) accent else labelIdle,
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
    val containerColor = Palette.glassTint.copy(alpha = if (light) 0.6f else 0.4f)
    val dimColor = Palette.dim

    // Material：用官方 M3 对话框
    if (LocalStyle.current == UiStyle.Material) {
        MaterialDialog(title = null, onDismiss = onDismiss, body = content)
        return
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // MIUIX：容器换成库里的 Card。只加这一个分支，所有基于 GlassDialogPanel 的弹窗
        // （开机向导 / 确认框 / 关于 / 在线壁纸 / 日志颜色）一起变成 MIUIX 皮，
        // 液态玻璃那条路一行没动
        if (LocalStyle.current == UiStyle.Miuix) {
            MiuixTheme(colors = if (Palette.dark) miuixDark() else miuixLight()) {
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
                            .padding(32.dp)
                            .fillMaxWidth()
                            .heightIn(max = 620.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        MiuixCard(Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
                            Column { content() }
                        }
                    }
                }
            }
            return@Dialog
        }
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
                            val as2 = LocalGlassAssets.current
                            val sc = LocalGlassScreen.current
                            if (as2 != null && sc.width > 0) {
                                Modifier.glassSurface(
                                    assets = as2,
                                    shape = RoundedCornerShape(48.dp),
                                    cornerDp = 48,
                                    screen = sc,
                                    blurLevel = 0.25f,
                                    density = 0.6f,
                                    light = light,
                                    tint = Palette.glassTint,
                                    tintAlpha = if (light) 0.55f else 0.5f
                                )
                            } else {
                                Modifier
                                    .clip(RoundedCornerShape(48.dp))
                                    .background(if (light) Palette.glassTint else Color(0xFF1B1C20))
                            }
                        }
                    )
            ) {
                CompositionLocalProvider(
                    LocalAccent provides (if (light) Color(0xFF16181D) else Color(0xFFECEDEF))
                ) {
                    content()
                }
            }
        }
    }
}

/** 首次使用向导：几页说明 + 跳过/下一步，液态玻璃面板 */
@Composable
fun OnboardingDialog(lang: Lang, onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    // Material 的官方弹窗已经自带 24dp 内边距，这里再叠 28dp 会挤成一条窄缝，
    // 所以 Material 下把外层留白收掉
    val isM3 = LocalStyle.current == UiStyle.Material
    val light = !Palette.dark
    val contentColor = if (light) Color.Black else Color.White
    val pages = listOf(
        Triple(
            tr(lang, "欢迎使用 EasyPT", "Welcome to EasyPT"),
            tr(lang,
                "这是 adb / fastboot 的图形界面。\n\n三种使用模式：\n· OTG 主机 —— 手机直连设备，不依赖 adb、不需要 root\n· 本机 root —— 用 su 备份 / 刷写本机分区\n· 遥控 PC —— 和电脑端配合，命令在电脑上执行",
                "A GUI for adb / fastboot.\n\nThree modes:\n· OTG host — talk to fastboot over USB directly, no adb, no root\n· Local root — back up / flash this phone's partitions with su\n· Remote PC — pair with the desktop app and run commands there"),
            Icons.Filled.Info
        ),
        Triple(
            tr(lang, "先选界面样式", "Pick a look"),
            tr(lang,
                "设置 → 界面样式：\n· 液态玻璃 —— 从壁纸取样的真玻璃，可拖动底栏切换页面\n· Material —— Material Design 3\n· MIUIX —— 小米风格\n· 原生 —— 深色半透明板\n\n同一页里还能换字体、背景图和夜间模式。",
                "Settings → Style:\n· Liquid Glass — real sampled glass, drag the bottom bar\n· Material — Material Design 3\n· MIUIX — Xiaomi style\n· Native — dark translucent panels\n\nSame page: fonts, wallpaper, dark mode."),
            Icons.Filled.Build
        ),
        Triple(
            tr(lang, "遥控 PC 怎么连", "Pairing with the PC"),
            tr(lang,
                "1. 电脑端打开「手机遥控服务」，地址和配对码会显示出来（默认自动启动）\n2. 手机连同一个 Wi-Fi\n3. 手机切到「遥控 PC」模式，填地址和配对码\n4. 点「测试连接」，看到「遥控服务在线」就成了\n\n首次会请求「访问本地网络设备」权限，必须允许。",
                "1. On the PC open 'Remote service' — it shows the address and pairing code (auto-starts by default)\n2. Phone on the same Wi-Fi\n3. Switch the phone to 'Remote PC', enter address + code\n4. Tap 'Test' until it says online\n\nThe first time Android asks for local-network permission — allow it."),
            Icons.Filled.Share
        )
    )
    // 点了外面 / 返回键都不关，只有「先不看」「开始使用」能关掉（之前被广告跳过软件自动点掉了）
    AppGlassPanel(LocalWallBackdrop.current, onDismiss = { }) {
        Column(Modifier.padding(if (isM3) 0.dp else 28.dp, if (isM3) 0.dp else 26.dp, if (isM3) 0.dp else 26.dp, 0.dp)) {
            Text(
                pages[page].first, color = contentColor, fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(10.dp))
            Text(
                pages[page].second, color = contentColor.copy(alpha = 0.78f),
                fontSize = 14.sp, lineHeight = 21.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == page) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (i == page) contentColor else contentColor.copy(alpha = 0.28f)
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.padding(24.dp, 0.dp, 24.dp, 24.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val bd = LocalWallBackdrop.current
            val last = page == pages.lastIndex
            if (LocalStyle.current == UiStyle.Miuix) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = tr(lang, "先不看", "Later"),
                    onClick = { onDone() },
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = { if (last) onDone() else page++ },
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (last) tr(lang, "开始使用", "Get started") else tr(lang, "下一步", "Next"),
                        color = Color.White
                    )
                }
            } else if (useBackdrop && bd != null) {
                LiquidButton(
                    onClick = { if (last) onDone() else onDone() },
                    backdrop = bd,
                    modifier = Modifier.weight(1f).height(48.dp),
                    surfaceColor = Color.White.copy(alpha = 0.3f)
                ) { Text(tr(lang, "先不看", "Later"), color = contentColor, fontSize = 15.sp) }
                LiquidButton(
                    onClick = { if (last) onDone() else page++ },
                    backdrop = bd,
                    modifier = Modifier.weight(1f).height(48.dp),
                    tint = LocalAccent.current
                ) {
                    Text(
                        if (last) tr(lang, "开始使用", "Start") else tr(lang, "下一步", "Next"),
                        color = Color.White, fontSize = 15.sp
                    )
                }
            } else {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(contentColor.copy(alpha = 0.10f))
                        .clickable { onDone() }
                        .height(48.dp)
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) { Text(tr(lang, "先不看", "Later"), color = contentColor, fontSize = 15.sp) }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LocalAccent.current)
                        .clickable { if (last) onDone() else page++ }
                        .height(48.dp)
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (last) tr(lang, "开始使用", "Start") else tr(lang, "下一步", "Next"),
                        color = Color.White, fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/** 液态玻璃确认框：取消 / 确认，两个都是 demo 的 LiquidButton */
@Composable
fun GlassConfirmDialog(
    lang: Lang,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val light = !Palette.dark
    val contentColor = if (light) Color.Black else Color.White
    val destructive = LocalStyle.current != UiStyle.Native
    AppGlassPanel(LocalWallBackdrop.current, onDismiss) {
        Text(
            title, color = contentColor, fontSize = 22.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(28.dp, 26.dp, 28.dp, 8.dp)
        )
        Text(
            message, color = contentColor.copy(alpha = 0.72f), fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(28.dp, 0.dp, 28.dp, 10.dp)
        )
        Row(
            Modifier.padding(24.dp, 6.dp, 24.dp, 24.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val bd = LocalWallBackdrop.current
            if (LocalStyle.current == UiStyle.Miuix) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = tr(lang, "取消", "Cancel"),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = onConfirm,
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) { Text(confirmText, color = Color.White) }
            } else if (useBackdrop && bd != null) {
                LiquidButton(
                    onClick = onDismiss,
                    backdrop = bd,
                    modifier = Modifier.weight(1f).height(48.dp),
                    surfaceColor = Color.White.copy(alpha = 0.3f)
                ) { Text(tr(lang, "取消", "Cancel"), color = contentColor, fontSize = 16.sp) }
                LiquidButton(
                    onClick = onConfirm,
                    backdrop = bd,
                    modifier = Modifier.weight(1f).height(48.dp),
                    tint = if (destructive) Color(0xFFFF3B30) else LocalAccent.current
                ) { Text(confirmText, color = Color.White, fontSize = 16.sp) }
            } else {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(contentColor.copy(alpha = 0.10f))
                        .clickable { onDismiss() }
                        .height(48.dp)
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) { Text(tr(lang, "取消", "Cancel"), color = contentColor, fontSize = 16.sp) }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (destructive) Color(0xFFFF3B30) else LocalAccent.current)
                        .clickable { onConfirm() }
                        .height(48.dp)
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) { Text(confirmText, color = Color.White, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
fun AboutDialog(style: UiStyle, lang: Lang, backdrop: Backdrop?, onDismiss: () -> Unit) {
    val light = !Palette.dark
    val contentColor = if (light) Color.Black else Color.White
    val containerColor = Palette.glassTint.copy(alpha = if (light) 0.6f else 0.4f)
    val ctx = LocalContext.current
    val ver = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    AppGlassPanel(backdrop, onDismiss) {
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
            if (LocalStyle.current == UiStyle.Miuix) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = tr(lang, "关闭", "Close"),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = onDismiss,
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) { Text(tr(lang, "好", "OK"), color = Color.White) }
            } else if (useBackdrop && backdrop != null) {
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
                    tint = LocalAccent.current
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
                        .background(LocalAccent.current)
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
                GlassButton(
                    text = tr(lang, "关闭", "Close"),
                    onClick = onClose,
                    modifier = Modifier.height(48.dp),
                    primary = true,
                    tint = LocalAccent.current
                )
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
/** MIUIX 版选择弹窗：容器是库里的 Card，行是 BasicComponent，取消是官方 TextButton */
@Composable
fun MiuixChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    deletable: Set<Int> = emptySet(),
    onDelete: ((Int) -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        MiuixTheme(colors = if (Palette.dark) miuixDark() else miuixLight()) {
            Box(Modifier.fillMaxSize()) {
                // 点空白处关闭
                Box(
                    Modifier
                        .fillMaxSize()
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
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MiuixCard(Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(
                                title,
                                color = MiuixTheme.colorScheme.onBackground,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)
                            )
                            options.forEachIndexed { i, name ->
                                BasicComponent(
                                    title = name,
                                    onClick = { onPick(i) },
                                    endActions = {
                                        // 自己导入的字体多一个删除按钮（之前这分支被我漏掉了，
                                        // 结果 MIUIX 下自定义字体删不掉）
                                        if (onDelete != null && deletable.contains(i)) {
                                            top.yukonga.miuix.kmp.basic.TextButton(
                                                text = "删除",
                                                onClick = { onDelete.invoke(i) }
                                            )
                                        }
                                        if (i == selected) {
                                            Icon(
                                                Icons.Filled.Check, contentDescription = null,
                                                tint = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = "取消",
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidChoiceDialog(
    backdrop: Backdrop?,
    title: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    /** 这些下标（可删的项，比如自己导入的字体）右侧会多一个删除按钮 */
    deletable: Set<Int> = emptySet(),
    onDelete: ((Int) -> Unit)? = null
) {
    val light = !Palette.dark
    val contentColor = if (light) Color.Black else Color.White
    val containerColor = Palette.glassTint.copy(alpha = if (light) 0.6f else 0.4f)
    val dimColor = Palette.dim

    // Material：官方 M3 对话框，皮肤在 MaterialSlots.MaterialChoiceDialog
    if (LocalStyle.current == UiStyle.Material) {
        MaterialChoiceDialog(title, options, selected, onPick, onDismiss)
        return
    }

    // MIUIX：容器用库里的 Card，行用 BasicComponent，取消用官方 TextButton。
    // 窗口仍用 Compose 的 Dialog —— miuix 的 WindowDialog 需要
    // LocalNavigationEventDispatcherOwner（本工程没提供，会崩），
    // OverlayDialog 又必须放在根级 Box 才能盖满屏，嵌在页面里画不出来。
    if (LocalStyle.current == UiStyle.Miuix) {
        MiuixChoiceDialog(title, options, selected, onPick, onDismiss, deletable, onDelete)
        return
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    CompositionLocalProvider(
        LocalAccent provides (if (LocalStyle.current == UiStyle.Native) contentColor else LocalAccent.current)
    ) {
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
                // 选项多的时候要能滚（字体列表已经有 12 项）
                Column(
                    Modifier
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState())
                ) {
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
                        Text(name, color = if (sel) LocalAccent.current else contentColor, fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        if (onDelete != null && deletable.contains(i)) {
                            val delBd = LocalWallBackdrop.current
                            if (useBackdrop && delBd != null) {
                                LiquidButton(
                                    onClick = { onDelete(i) },
                                    backdrop = delBd,
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("删除", color = contentColor, fontSize = 12.sp)
                                }
                            } else {
                                Text(
                                    "删除",
                                    color = contentColor, fontSize = 12.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(contentColor.copy(alpha = 0.12f))
                                        .clickable { onDelete(i) }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        if (sel) Icon(Icons.Filled.Check, null, tint = LocalAccent.current, modifier = Modifier.size(20.dp))
                    }
                }
                }  // Column(可滚动选项)
                Row(
                    Modifier.padding(24.dp, 12.dp, 24.dp, 24.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 玻璃/其它：统一用 GlassButton（有壁纸走 LiquidButton，没有走胶囊底）
                    GlassButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        textColor = contentColor,
                        fontSize = 16.sp,
                        plainBackground = containerColor.copy(alpha = 0.2f)
                    )
                }
        }
    }
}
}
}

/**
 * 玻璃输入框：内部是 BasicTextField，外观用 demo 的 drawBackdrop（vibrancy + blur），
 * 不再用 M3 OutlinedTextField 那个白底黑字的方块。
 */
/**
 * 「fastboot 命令」输入框那一套玻璃底板（vibrancy + 8dp 模糊 + 45%/55% 底料）。
 * 日志输出框也用它，保证和输入框一模一样调色。
 */
@Composable
fun glassInsetSurface(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val wall = LocalWallBackdrop.current
    // 输入框 / 日志框底板：同样优先走取样玻璃（RenderEffect 在部分机器上会被跳过）
    val assets = LocalGlassAssets.current
    val screen = LocalGlassScreen.current
    return if (assets != null && screen.width > 0) {
        Modifier.glassSurface(
            assets = assets,
            shape = shape,
            cornerDp = 16,
            screen = screen,
            blurLevel = 0.35f,
            density = 1f,
            light = !Palette.dark,
            tint = Palette.glassTint,
            tintAlpha = if (Palette.dark) 0.45f else 0.55f
        )
    } else if (useBackdrop && wall != null) {
        Modifier.drawBackdrop(
            backdrop = wall,
            shape = { shape },
            effects = {
                vibrancy()
                blur(8.dp.toPx())
            },
            onDrawSurface = {
                drawRect(
                    Palette.glassTint.copy(alpha = if (Palette.dark) 0.45f else 0.55f)
                    
                )
            }
        )
    } else {
        Modifier
            .clip(shape)
            .background(Palette.insetBlock)
    }
}

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
    // 字色跟「底板」走：底板永远随主题（亮主题浅底 / 暗主题深底）。
    // 用 LocalCardText 的话，原生风格是深色卡片→恒为白字，压在浅色输入框上就看不见了。
    val text = Palette.text
    val shape = RoundedCornerShape(16.dp)

    val surface = glassInsetSurface(shape)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        singleLine = true,
        textStyle = TextStyle(color = text, fontSize = 15.sp),
        cursorBrush = SolidColor(LocalAccent.current),
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
fun BingWallpaperDialog(lang: Lang, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    // 预览图和原图分开：预览用 400x240（约 20 KB），原图才用 UHD（约 3.7 MB）。
    // 之前预览直接挂 UHD，10 张同时拉 ~37 MB，手机上基本等于永远空白。
    data class BingItem(val preview: String, val full: String)
    var items by remember { mutableStateOf<List<BingItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    // 正在下载第几张 + 进度
    var busyIndex by remember { mutableIntStateOf(-1) }
    var progress by remember { mutableIntStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val light = !Palette.dark
    val contentColor = if (light) Color.Black else Color.White

    LaunchedEffect(reload) {
        loading = true
        err = null
        items = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                // 国内连 www.bing.com 经常不通，按顺序试；用哪个域名成功，图片也跟着用那个域名
                val hosts = listOf("https://cn.bing.com", "https://www.bing.com")
                var lastErr: String? = null
                var list: List<BingItem>? = null
                for (host in hosts) {
                    try {
                        val conn = java.net.URL(
                            "$host/HPImageArchive.aspx?format=js&idx=" + (0..7).random() +
                                "&n=10&mkt=zh-CN&nc=" + System.currentTimeMillis()
                        ).openConnection()
                        conn.connectTimeout = 8000
                        conn.readTimeout = 10000
                        conn.setRequestProperty("Cache-Control", "no-cache")
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) EasyPT")
                        val body = conn.getInputStream().bufferedReader().readText()
                        val arr = org.json.JSONObject(body).getJSONArray("images")
                        list = (0 until arr.length()).map { i ->
                            val base = arr.getJSONObject(i).getString("urlbase")
                            BingItem(
                                preview = host + base + "_400x240.jpg",
                                full = host + base + "_UHD.jpg"
                            )
                        }
                        break
                    } catch (e: Throwable) {
                        lastErr = "${host.substringAfter("//")}: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
                list ?: throw IllegalStateException(lastErr ?: "unknown")
            }.onFailure { err = it.message ?: "error" }.getOrDefault(emptyList())
        }
        loading = false
    }

    AppGlassPanel(LocalWallBackdrop.current, onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(28.dp, 24.dp, 20.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Bing Wallpaper",
                color = contentColor, fontSize = 24.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            val refreshBd = LocalWallBackdrop.current
            if (LocalStyle.current == UiStyle.Miuix) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = tr(lang, "刷新", "Refresh"),
                    onClick = { reload++ }
                )
            } else if (useBackdrop && refreshBd != null) {
                // 刷新也是玻璃按钮，不是一行蓝字
                LiquidButton(onClick = { reload++ }, backdrop = refreshBd) {
                    Text(tr(lang, "刷新", "Refresh"), color = contentColor, fontSize = 14.sp)
                }
            } else {
                Text(
                    tr(lang, "刷新", "Refresh"),
                    color = LocalAccent.current, fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LocalAccent.current.copy(alpha = 0.12f))
                        .clickable { reload++ }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
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
                items(items.size) { i ->
                    val url = items[i].preview
                    val bd = LocalWallBackdrop.current
                    Column {
                        coil.compose.AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(92.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(Modifier.height(6.dp))
                        val isBusy = busyIndex == i
                        if (LocalStyle.current == UiStyle.Miuix) {
                            top.yukonga.miuix.kmp.basic.Button(
                                onClick = { if (!isBusy) { busyIndex = i; progress = 0 } },
                                colors = if (isBusy) {
                                    top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary()
                                } else {
                                    top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (isBusy) "${tr(lang, "下载中", "Downloading")} $progress%"
                                    else tr(lang, "选择", "Select")
                                )
                            }
                        } else if (useBackdrop && bd != null) {
                            LiquidButton(
                                onClick = {
                                    if (!isBusy) { busyIndex = i; progress = 0 }
                                },
                                backdrop = bd,
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                tint = if (isBusy) LocalAccent.current else Color.Unspecified
                            ) {
                                Text(
                                    if (isBusy) "${tr(lang, "下载中", "Downloading")} $progress%"
                                    else tr(lang, "选择", "Select"),
                                    color = contentColor, fontSize = 13.sp
                                )
                            }
                        } else {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(if (isBusy) LocalAccent.current else LocalAccent.current.copy(alpha = 0.18f))
                                    .clickable { if (!isBusy) { busyIndex = i; progress = 0 } },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isBusy) "${tr(lang, "下载中", "Downloading")} $progress%"
                                    else tr(lang, "选择", "Select"),
                                    color = if (isBusy) Color.White else contentColor,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        // 下载任务：用协程按 64KB 分块读，进度写进 progress
        LaunchedEffect(busyIndex) {
            if (busyIndex < 0 || busyIndex >= items.size) return@LaunchedEffect
            val url = items[busyIndex].full
            val file = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val f = File(
                        ctx.cacheDir,
                        "wp_online.jpg"
                    )
                    val conn = java.net.URL(url).openConnection()
                    val total = conn.contentLengthLong
                    conn.getInputStream().use { i ->
                        f.outputStream().use { o ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val r = i.read(buf)
                                if (r <= 0) break
                                o.write(buf, 0, r)
                                done += r
                                if (total > 0) progress = ((done * 100) / total).toInt().coerceIn(0, 100)
                            }
                        }
                    }
                    f
                }.getOrNull()
            }
            // 这里已经下好了，把本地文件路径交出去；以前传的是 url，上层又下一遍（3.7 MB 白下两次）
            if (file != null) onPick(file.absolutePath) else busyIndex = -1
        }
        Row(
            Modifier.padding(24.dp, 12.dp, 24.dp, 24.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            val bd = LocalWallBackdrop.current
            if (useBackdrop && bd != null) {
                LiquidButton(
                    onClick = onDismiss,
                    backdrop = bd,
                    modifier = Modifier.height(48.dp).width(160.dp),
                    surfaceColor = Color.White.copy(alpha = 0.3f)
                ) { Text(tr(lang, "取消", "Cancel"), color = contentColor, fontSize = 16.sp) }
            } else {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onDismiss() }
                        .height(48.dp)
                        .width(160.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) { Text(tr(lang, "取消", "Cancel"), color = contentColor, fontSize = 16.sp) }
            }
        }
    }
}


/** 日志颜色：先选「改哪一类」，再从大调色盘里挑，自动保证对比度 */
@Composable
fun LogColorDialog(
    lang: Lang,
    initial: List<Color>,
    onSave: (List<Color>) -> Unit,
    onDismiss: () -> Unit
) {
    val light = !Palette.dark
    val contentColor = if (light) Color.Black else Color.White
    // 和日志输出框同一套底料（glassInsetSurface），预览和对比度保护才准
    val bgSample = Palette.glassTint
    var colors by remember { mutableStateOf(initial) }
    var active by remember { mutableIntStateOf(0) }
    val names = listOf(
        tr(lang, "命令", "Command"),
        tr(lang, "成功", "Success"),
        tr(lang, "失败", "Error"),
        tr(lang, "普通", "Normal")
    )
    // 48 色：中性 8 档 + 8 个色相 × 5 档明度，够挑了
    val palette = listOf(
        0xFF000000, 0xFF16181D, 0xFF3A3D44, 0xFF5A5F68, 0xFF7C8598, 0xFF9AA0A6, 0xFFC9CDD6, 0xFFECEDEF,
        0xFF7F1D1D, 0xFFDC2626, 0xFFEF4444, 0xFFF87171, 0xFFFCA5A5, 0xFFB91C1C, 0xFFFF7B72, 0xFFFFB3AE,
        0xFF7C2D12, 0xFFEA580C, 0xFFF97316, 0xFFFB923C, 0xFFFDBA74, 0xFFC2410C, 0xFFFF9F0A, 0xFFFFC078,
        0xFF713F12, 0xFFCA8A04, 0xFFEAB308, 0xFFFACC15, 0xFFFDE047, 0xFFA16207, 0xFFFFC04D, 0xFFFFD98A,
        0xFF14532D, 0xFF16A34A, 0xFF22C55E, 0xFF4ADE80, 0xFF86EFAC, 0xFF12A150, 0xFF3DD68C, 0xFF9AE6B4,
        0xFF164E63, 0xFF0891B2, 0xFF06B6D4, 0xFF22D3EE, 0xFF67E8F9, 0xFF0E7490, 0xFF7DD3FC, 0xFFA5F3FC,
        0xFF1E3A8A, 0xFF2563EB, 0xFF3B82F6, 0xFF60A5FA, 0xFF93C5FD, 0xFF1D4ED8, 0xFF0A84FF, 0xFFBFDBFE,
        0xFF4C1D95, 0xFF7C3AED, 0xFF8B5CF6, 0xFFA78BFA, 0xFFC4B5FD, 0xFF5E5CE6, 0xFFB794F6, 0xFFDDD6FE,
        0xFF831843, 0xFFDB2777, 0xFFEC4899, 0xFFF472B6, 0xFFF9A8D4, 0xFFBE185D, 0xFFFF4D6D, 0xFFFBB6CE
    )
    val grid = palette.chunked(6)

    // 整页显示（不再用受高度限制的弹窗）：色块区吃掉剩余高度自己滚，
    // 底部取消/完成永远钉在屏幕底部
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onDismiss() }
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (Palette.dark) Color(0xFF17181C) else Color(0xFFF7F8FA))
        ) {
        Text(
            tr(lang, "日志颜色", "Log colors"),
            color = contentColor, fontSize = 24.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(28.dp, 24.dp, 28.dp, 8.dp)
        )
        // 预览
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(bgSample)
                .padding(12.dp)
        ) {
            Text("[12:00:00] > fastboot devices", color = colors[0], fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("[12:00:01] ok 刷入完成", color = colors[1], fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("[12:00:02] fail 失败：no such partition", color = colors[2], fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("[12:00:03] 普通输出一行", color = colors[3], fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        // 选「改哪一类」
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            names.forEachIndexed { idx, name ->
                val sel = active == idx
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (sel) colors[idx].copy(alpha = 0.22f)
                            else contentColor.copy(alpha = 0.08f)
                        )
                        .clickable { active = idx }
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(colors[idx])
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        name, color = if (sel) colors[idx] else contentColor.copy(alpha = 0.75f),
                        fontSize = 13.sp, fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // 调色盘：只有这块滑动，标题/预览/分类/按钮都固定
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            grid.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { argb ->
                        val c = Color(argb)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(c)
                                .then(
                                    if (colors[active] == ensureContrast(c, bgSample))
                                        Modifier.border(2.dp, contentColor, RoundedCornerShape(7.dp))
                                    else Modifier
                                )
                                .clickable {
                                    val next = colors.toMutableList()
                                    next[active] = ensureContrast(c, bgSample)
                                    colors = next
                                }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.padding(24.dp, 4.dp, 24.dp, 24.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val wall = LocalWallBackdrop.current
            if (LocalStyle.current == UiStyle.Miuix) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = tr(lang, "取消", "Cancel"),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = { onSave(colors) },
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) { Text(tr(lang, "完成", "Done"), color = Color.White) }
            } else if (useBackdrop && wall != null) {
                // 按钮抄 demo 的 LiquidButton
                LiquidButton(
                    onClick = onDismiss,
                    backdrop = wall,
                    modifier = Modifier.weight(1f).height(48.dp),
                    surfaceColor = Color.White.copy(alpha = 0.3f)
                ) { Text(tr(lang, "取消", "Cancel"), color = contentColor, fontSize = 16.sp) }
                LiquidButton(
                    onClick = { onSave(colors) },
                    backdrop = wall,
                    modifier = Modifier.weight(1f).height(48.dp),
                    tint = LocalAccent.current
                ) { Text(tr(lang, "完成", "Done"), color = Color.White, fontSize = 16.sp) }
            } else {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onDismiss() }
                        .height(48.dp)
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) { Text(tr(lang, "取消", "Cancel"), color = contentColor, fontSize = 16.sp) }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LocalAccent.current)
                        .clickable { onSave(colors) }
                        .height(48.dp)
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) { Text(tr(lang, "完成", "Done"), color = Color.White, fontSize = 16.sp) }
            }
        }
    }
    }
}

/** 主按钮配色：原生风格不要彩色，用中性白底 */
@Composable
fun primaryButtonColors(): ButtonColors =
    if (LocalStyle.current == UiStyle.Native)
        ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.16f),
            contentColor = Color.White
        )
    else
        ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Color.White)

/** sRGB 亮度(0..1) -> WCAG 相对亮度 */
fun relLuminance(v: Float): Float {
    val x = v.coerceIn(0f, 1f)
    return if (x <= 0.03928f) x / 12.92f
    else Math.pow(((x + 0.055) / 1.055), 2.4).toFloat()
}

/**
 * 给定背景的 sRGB 亮度，返回白字还是近黑字更清楚（比对比度，不是拍脑袋定阈值）。
 * #16181D 的相对亮度约 0.0104，和纯白在 bg≈0.202 时打平。
 */
fun readableOn(srgbLum: Float): Color {
    val bg = relLuminance(srgbLum)
    val contrastWhite = 1.05f / (bg + 0.05f)
    val contrastInk = (bg + 0.05f) / 0.0604f
    return if (contrastInk >= contrastWhite) Color(0xFF16181D) else Color.White
}

/** 一个在线字体：落盘名（filesDir 下）+ 体积 + 多源回退 */
data class OnlineFontDef(
    val app: AppFont,
    val label: String,
    val fileName: String,
    val sizeText: String,
    val urls: List<String>
)

private fun ghSources(user: String, repo: String, branch: String, path: String): List<String> = listOf(
    "https://raw.githubusercontent.com/$user/$repo/$branch/$path",
    "https://gh-proxy.com/https://raw.githubusercontent.com/$user/$repo/$branch/$path",
    "https://gcore.jsdelivr.net/gh/$user/$repo@$branch/$path"
)

/**
 * 在线字体清单。全部 URL 都实测过（HTTP 200 + 字体文件头合法）。
 * 文件名沿用旧的 font-misans.ttf / font-harmony.ttf，老用户已经下过的不必重下。
 */
val OnlineFonts: List<OnlineFontDef> = listOf(
    OnlineFontDef(
        AppFont.MiSans, "MiSans", "font-misans.ttf", "6 MB",
        listOf(
            "https://gcore.jsdelivr.net/npm/@fontpkg/mi-sans@4.3.0/MiSans-Regular.otf",
            "https://hyperos.mi.com/font-download/MiSans.zip"
        )
    ),
    OnlineFontDef(
        AppFont.Harmony, "HarmonyOS Sans", "font-harmony.ttf", "8 MB",
        ghSources("ajacocks", "harmonyos-sans-font", "master",
            "HarmonyOS_Sans_SC/HarmonyOS_Sans_SC_Regular.ttf")
    ),
    OnlineFontDef(
        AppFont.Oppo, "OPPO Sans 4.0", "font-oppo.ttf", "22 MB",
        ghSources("1adybug", "fonts", "main", "sources/oppo-sans/OPPO_Sans_4.0.ttf")
    ),
    OnlineFontDef(
        AppFont.OnePlus, "OneSans（一加）", "font-oneplus.ttf", "0.5 MB",
        ghSources("wlpne", "OneSans", "main", "static/OneSans-Regular.ttf")
    ),
    OnlineFontDef(
        AppFont.Honor, "HONOR Sans CN（荣耀）", "font-honor.ttf", "8 MB",
        ghSources("hechao98", "HONORSansCN", "main", "fonts/HONORSansCN-Regular.ttf")
    ),
    OnlineFontDef(
        AppFont.JetBrainsMono, "JetBrains Mono", "font-jetbrains.ttf", "0.3 MB",
        ghSources("JetBrains", "JetBrainsMono", "master", "fonts/ttf/JetBrainsMono-Regular.ttf")
    ),
    OnlineFontDef(
        AppFont.Vivo, "vivo Sans", "font-vivo.ttf", "7 MB",
        listOf("https://gcore.jsdelivr.net/npm/@fontpkg/vivo-sans@1.0.3/vivoSans-Regular.ttf") +
            ghSources("1adybug", "fonts", "main", "sources/vivo-sans/vivoSansSCVF.ttf")
    )
)

/** 读取用户自定义的日志颜色：0 = 没设过（用主题默认） */
fun readLogColor(sp: android.content.SharedPreferences, key: String): Color? {
    val v = sp.getInt(key, 0)
    return if (v == 0) null else Color(v)
}

/** 颜色跟背景太近时往黑/白方向拉，保证可读 */
fun ensureContrast(c: Color, bg: Color): Color {
    fun lum(x: Color): Float = 0.2126f * x.red + 0.7152f * x.green + 0.0722f * x.blue
    val lb = lum(bg)
    var out = c
    var guard = 0
    while (kotlin.math.abs(lum(out) - lb) < 0.28f && guard < 12) {
        out = if (lb > 0.5f) {
            Color(out.red * 0.82f, out.green * 0.82f, out.blue * 0.82f, out.alpha)
        } else {
            Color(
                (out.red + (1f - out.red) * 0.22f),
                (out.green + (1f - out.green) * 0.22f),
                (out.blue + (1f - out.blue) * 0.22f),
                out.alpha
            )
        }
        guard++
    }
    return out
}

/** 进程级日志缓冲：Activity 与 FlashService 共用 */
object AppLog {
    val lines = mutableStateListOf<LogLine>()
}
