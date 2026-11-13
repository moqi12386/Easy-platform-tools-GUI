package com.easypt.gui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 日志文字颜色：独立整页（不是弹窗），支持壁纸 + 液态按钮。
 *
 * 布局照原样：预览 → 四类切换 → 色板；现在只有色板可滑动，
 * 顶部返回和底部「重置 / 确认」都钉死。
 */
class LogColorActivity : ComponentActivity() {

    /** 返回时用和主页切页一致的滑出 */
    private fun closePage() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.lc_in, R.anim.lc_out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("easyptc", MODE_PRIVATE)

        val style = UiStyle.entries.getOrElse(prefs.getInt("style", 0)) { UiStyle.Native }
        val dark = when (prefs.getInt("theme", 2)) {
            0 -> false
            1 -> true
            else -> (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val lang = try {
            if (prefs.getString("lang", "zh") == "en") Lang.En else Lang.Zh
        } catch (e: ClassCastException) {
            if (prefs.getInt("lang", 0) == 1) Lang.En else Lang.Zh
        }
        val saved = listOf(
            Color(prefs.getInt("lc2Cmd", 0xFF3B82F6.toInt())),
            Color(prefs.getInt("lc2Ok", 0xFF12A150.toInt())),
            Color(prefs.getInt("lc2Err", 0xFFD93B3B.toInt())),
            Color(prefs.getInt("lc2Text", 0xFF4A5160.toInt()))
        )

        // 壁纸：和主界面同一套准备流程
        val dm = resources.displayMetrics
        val assets = try {
            val src = Wallpaper.load(this) ?: Wallpaper.default(dm.widthPixels, dm.heightPixels)
            Wallpaper.build(Wallpaper.prepare(src, dm.widthPixels, dm.heightPixels))
        } catch (e: Throwable) {
            null
        }

        setContent {
            Palette.dark = dark
            Palette.m3 = if (dark) {
                androidx.compose.material3.darkColorScheme(
                    primary = Palette.accent, surface = Palette.surface, onSurface = Palette.text
                )
            } else {
                androidx.compose.material3.lightColorScheme(
                    primary = Palette.accent, surface = Palette.surface, onSurface = Palette.text
                )
            }
            val backdrop = rememberLayerBackdrop()
            CompositionLocalProvider(
                LocalStyle provides style,
                LocalAccent provides Palette.accent,
                LocalCardText provides Palette.text,
                LocalGlassAssets provides assets,
                LocalWallBackdrop provides backdrop
            ) {
                MaterialTheme {
                    LogColorScreen(
                        lang = lang,
                        saved = saved,
                        assets = assets,
                        onClose = { closePage() },
                        onConfirm = { c ->
                            prefs.edit()
                                .putInt("lc2Cmd", c[0].toArgb())
                                .putInt("lc2Ok", c[1].toArgb())
                                .putInt("lc2Err", c[2].toArgb())
                                .putInt("lc2Text", c[3].toArgb())
                                .apply()
                            setResult(RESULT_OK)
                            closePage()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LogColorScreen(
    lang: Lang,
    saved: List<Color>,
    assets: GlassAssets?,
    onClose: () -> Unit,
    onConfirm: (List<Color>) -> Unit
) {
    var colors by remember { mutableStateOf(saved) }
    var active by remember { mutableIntStateOf(0) }
    val backdrop = rememberLayerBackdrop()
    val light = !Palette.dark
    val contentColor = if (light) Color.Black else Color.White

    val names = listOf(
        tr(lang, "命令", "Command"), tr(lang, "成功", "Success"),
        tr(lang, "失败", "Error"), tr(lang, "普通", "Normal")
    )
    val palette = listOf(
        0xFF000000, 0xFF16181D, 0xFF3A3D44, 0xFF5A5F68, 0xFF7C8598, 0xFF9AA0A6,
        0xFFC9CDD6, 0xFFECEDEF, 0xFF7F1D1D, 0xFFDC2626, 0xFFEF4444, 0xFFF87171,
        0xFFFCA5A5, 0xFFB91C1C, 0xFFFF7B72, 0xFFFFB3AE, 0xFF7C2D12, 0xFFEA580C,
        0xFFF97316, 0xFFFB923C, 0xFFFDBA74, 0xFFC2410C, 0xFFFF9F0A, 0xFFFFC078,
        0xFF713F12, 0xFFCA8A04, 0xFFEAB308, 0xFFFACC15, 0xFFFDE047, 0xFFA16207,
        0xFFFFC04D, 0xFFFFD98A, 0xFF14532D, 0xFF16A34A, 0xFF22C55E, 0xFF4ADE80,
        0xFF86EFAC, 0xFF12A150, 0xFF3DD68C, 0xFF9AE6B4, 0xFF164E63, 0xFF0891B2,
        0xFF06B6D4, 0xFF22D3EE, 0xFF67E8F9, 0xFF0E7490, 0xFF7DD3FC, 0xFFA5F3FC,
        0xFF1E3A8A, 0xFF2563EB, 0xFF3B82F6, 0xFF60A5FA, 0xFF93C5FD, 0xFF1D4ED8,
        0xFF0A84FF, 0xFFBFDBFE, 0xFF4C1D95, 0xFF7C3AED, 0xFF8B5CF6, 0xFFA78BFA,
        0xFFC4B5FD, 0xFF5E5CE6, 0xFFB794F6, 0xFFDDD6FE, 0xFF831843, 0xFFDB2777,
        0xFFEC4899, 0xFFF472B6, 0xFFF9A8D4, 0xFFBE185D, 0xFFFF4D6D, 0xFFFBB6CE
    )

    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        // 壁纸进图层，玻璃按钮在图层外取样
        if (assets != null && useBackdrop) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                WallpaperView(assets)
            }
        } else if (assets != null) {
            WallpaperView(assets)
        }

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // 顶部：返回 + 标题（固定）
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = contentColor)
                }
                Text(
                    tr(lang, "日志颜色", "Log colors"),
                    color = contentColor, fontSize = 22.sp, fontWeight = FontWeight.Medium
                )
            }

            // 预览（固定）
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    // 和主页日志框同一套底料
                    .then(glassInsetSurface(RoundedCornerShape(16.dp)))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("[12:00:00] > fastboot devices", color = colors[0], fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text("[12:00:01] ok 刷入完成", color = colors[1], fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text("[12:00:02] fail 失败：no such partition", color = colors[2], fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text("[12:00:03] 普通输出一行", color = colors[3], fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }

            // 四类切换（固定）
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                names.forEachIndexed { i, n ->
                    // 四类切换用液态按钮：选中项是该类颜色的实心胶囊
                    GlassButton(
                        text = n,
                        onClick = { active = i },
                        modifier = Modifier.weight(1f),
                        textColor = if (i == active) Color.White else Palette.text,
                        fontSize = 13.sp,
                        height = 36.dp,
                        primary = i == active,
                        tint = colors[i],
                        surface = Color.White.copy(alpha = 0.3f),
                        plainBackground = Palette.chip
                    )
                }
            }

            // 色板：只有这一块能滚动
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                palette.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { argb ->
                            val c = Color(argb)
                            val sel = c == colors[active]
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c)
                                    .then(
                                        if (sel) Modifier.border(2.dp, Palette.text, RoundedCornerShape(10.dp))
                                        else Modifier
                                    )
                                    .clickable {
                                        val next = colors.toMutableList()
                                        next[active] = c
                                        colors = next
                                    }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            // 底部按钮（固定，液态玻璃）
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassButton(
                    text = tr(lang, "重置", "Reset"),
                    onClick = { colors = saved; active = 0 },
                    modifier = Modifier.weight(1f),
                    textColor = contentColor,
                    fontSize = 16.sp,
                    surface = Color.White.copy(alpha = 0.3f),
                    plainBackground = contentColor.copy(alpha = 0.10f)
                )
                GlassButton(
                    text = tr(lang, "确认", "Apply"),
                    onClick = { onConfirm(colors) },
                    modifier = Modifier.weight(1f),
                    textColor = Color.White,
                    fontSize = 16.sp,
                    primary = true,
                    tint = LocalAccent.current
                )
            }
        }
    }
}
