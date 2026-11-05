package com.easypt.gui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * 可选字体。
 *
 * 注意：ordinal 会被写进 SharedPreferences（"font"），
 * 所以只能在末尾追加，不能插到中间，否则覆盖安装后编号错位。
 */
enum class AppFont(val zh: String, val en: String, val family: FontFamily?) {
    System("系统默认", "System", null),
    Serif("衬线（宋体）", "Serif", FontFamily.Serif),
    Mono("等宽", "Monospace", FontFamily.Monospace),
    Cursive("手写体", "Cursive", FontFamily.Cursive),
    MiSans("MiSans", "MiSans", null),
    Harmony("HarmonyOS Sans", "HarmonyOS Sans", null),
    Local("本地字体（选择文件）", "Local font file", null),

    // —— 以下为后加，只能往后追加 ——
    Oppo("OPPO Sans", "OPPO Sans", null),
    OnePlus("一加 OneSans", "OnePlus Sans", null),
    Honor("荣耀 HONOR Sans", "HONOR Sans", null),
    Vivo("vivo Sans", "vivo Sans", null);

    val label: String get() = zh

    /** 是不是需要下载的在线字体 */
    val isOnline: Boolean get() = this != System && this != Serif && this != Mono &&
        this != Cursive && this != Local
}

/** 下拉框里的显示顺序（和 ordinal 无关，可以随便排） */
val AppFontOrder = listOf(
    AppFont.System, AppFont.Serif, AppFont.Mono, AppFont.Cursive,
    AppFont.MiSans, AppFont.Harmony, AppFont.Oppo, AppFont.OnePlus,
    AppFont.Honor, AppFont.Vivo,
    AppFont.Local
)

/** 把字体应用到 M3 的所有排版样式上 */
fun typographyWith(f: FontFamily?): Typography {
    if (f == null) return Typography()
    val t = Typography()
    fun s(x: TextStyle) = x.copy(fontFamily = f)
    return Typography(
        displayLarge = s(t.displayLarge), displayMedium = s(t.displayMedium), displaySmall = s(t.displaySmall),
        headlineLarge = s(t.headlineLarge), headlineMedium = s(t.headlineMedium), headlineSmall = s(t.headlineSmall),
        titleLarge = s(t.titleLarge), titleMedium = s(t.titleMedium), titleSmall = s(t.titleSmall),
        bodyLarge = s(t.bodyLarge), bodyMedium = s(t.bodyMedium), bodySmall = s(t.bodySmall),
        labelLarge = s(t.labelLarge), labelMedium = s(t.labelMedium), labelSmall = s(t.labelSmall)
    )
}
