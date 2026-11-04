package com.easypt.gui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** 预留字体：系统族直接可用，MiSans 需要下载 */
enum class AppFont(val zh: String, val en: String, val family: FontFamily?) {
    System("系统默认", "System", null),
    Serif("衬线（宋体）", "Serif", FontFamily.Serif),
    Mono("等宽", "Monospace", FontFamily.Monospace),
    Cursive("手写体", "Cursive", FontFamily.Cursive),
    Harmony("HarmonyOS Sans（点击下载）", "HarmonyOS Sans (download)", null),
    Local("本地字体（选择文件）", "Local font file", null);

    val label: String get() = zh
}

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
