package com.easypt.gui

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dynamiccolor.MaterialDynamicColors
import hct.Hct
import quantize.QuantizerCelebi
import scheme.SchemeTonalSpot
import score.Score

/**
 * Material Color Utilities（官方 java 实现，随仓库 vendor 在 app/src/main/java/{hct,dynamiccolor,...}）。
 *
 * 流程严格按 M3 动态配色：
 *   壁纸位图 → QuantizerCelebi 量化 + Score 打分取种子色
 *            → Hct.fromInt(seed)
 *            → SchemeTonalSpot(seedHct, isDark, contrastLevel)
 *            → MaterialDynamicColors 逐角色取值（tone/对比度曲线由 MCU 计算）
 *
 * 这样 onPrimary / onContainer 这些配对角色都由 MCU 保证对比度，
 * 不再用「vibrant 主色 + 手工 lerp」那套不合规做法。
 */
object Monet {
    private val colors = MaterialDynamicColors()

    /**
     * 从位图里按 MCU 算法取种子色（ARGB）。
     * 先缩到 96px 宽再量化：Celebi 是 O(n) 但壁纸是 1440p，全图跑会明显拖慢首帧。
     */
    fun seedFrom(bitmap: Bitmap): Int? = runCatching {
        val width = 96
        val height = (bitmap.height.toFloat() * width / bitmap.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, width, height, false)
        val px = IntArray(width * height)
        small.getPixels(px, 0, width, 0, 0, width, height)
        if (small !== bitmap) small.recycle()
        val quantized = QuantizerCelebi.quantize(px, 128)
        Score.score(quantized).firstOrNull()
    }.getOrNull()

    /** 种子色 + 明暗 → 完整 ColorScheme（所有角色都由 MCU 给出） */
    fun scheme(seedArgb: Int, dark: Boolean, contrast: Double = 0.0): ColorScheme {
        val s = SchemeTonalSpot(Hct.fromInt(seedArgb), dark, contrast)
        fun c(dc: dynamiccolor.DynamicColor) = Color(dc.getArgb(s))
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = c(colors.primary()),
            onPrimary = c(colors.onPrimary()),
            primaryContainer = c(colors.primaryContainer()),
            onPrimaryContainer = c(colors.onPrimaryContainer()),
            inversePrimary = c(colors.inversePrimary()),
            secondary = c(colors.secondary()),
            onSecondary = c(colors.onSecondary()),
            secondaryContainer = c(colors.secondaryContainer()),
            onSecondaryContainer = c(colors.onSecondaryContainer()),
            tertiary = c(colors.tertiary()),
            onTertiary = c(colors.onTertiary()),
            tertiaryContainer = c(colors.tertiaryContainer()),
            onTertiaryContainer = c(colors.onTertiaryContainer()),
            background = c(colors.background()),
            onBackground = c(colors.onBackground()),
            surface = c(colors.surface()),
            onSurface = c(colors.onSurface()),
            surfaceVariant = c(colors.surfaceVariant()),
            onSurfaceVariant = c(colors.onSurfaceVariant()),
            surfaceTint = c(colors.surfaceTint()),
            surfaceBright = c(colors.surfaceBright()),
            surfaceDim = c(colors.surfaceDim()),
            surfaceContainer = c(colors.surfaceContainer()),
            surfaceContainerHigh = c(colors.surfaceContainerHigh()),
            surfaceContainerHighest = c(colors.surfaceContainerHighest()),
            surfaceContainerLow = c(colors.surfaceContainerLow()),
            surfaceContainerLowest = c(colors.surfaceContainerLowest()),
            inverseSurface = c(colors.inverseSurface()),
            inverseOnSurface = c(colors.inverseOnSurface()),
            error = c(colors.error()),
            onError = c(colors.onError()),
            errorContainer = c(colors.errorContainer()),
            onErrorContainer = c(colors.onErrorContainer()),
            outline = c(colors.outline()),
            outlineVariant = c(colors.outlineVariant()),
            scrim = c(colors.scrim())
        )
    }
}
