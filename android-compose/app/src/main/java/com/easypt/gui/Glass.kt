package com.easypt.gui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.roundToInt

/** API 31 起才有 RenderEffect，才能走库的实时模糊；以下走预烘焙取样 */
val realGlass: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** 面板里的文字颜色：浅玻璃上自动转深色 */
val LocalPanelText = androidx.compose.runtime.compositionLocalOf { Color.White }

/** 这个壁纸算不算“浅色”，决定玻璃是白玻璃还是黑玻璃 */
fun isLightWallpaper(assets: GlassAssets?): Boolean = (assets?.avgLum ?: 0f) > 0.5f

/**
 * 玻璃面：模糊取样壁纸（已提升饱和度）+ 轻色偏 + 顶部高光 + 1dp 描边。
 * 位置在 draw 阶段读取，滚动时只重绘不重组。
 */
@Composable
fun Modifier.glassSurface(
    assets: GlassAssets?,
    shape: Shape,
    cornerDp: Int,
    screen: IntSize,
    blurLevel: Float = 0.5f,
    density: Float = 0.5f,
    tint: Color = Color.White,
    tintAlpha: Float = 0.05f,
    rim: Boolean = true,
    light: Boolean = false,
    /** 弹窗等独立窗口取不到屏幕坐标时，直接给一个固定取样点 */
    fixedPos: Offset? = null
): Modifier {
    val pos = remember { mutableStateOf(Offset.Zero) }
    val cornerPx = with(LocalDensity.current) { cornerDp.dp.toPx() }
    val strokePx = with(LocalDensity.current) { 1.dp.toPx() }
    val level = blurLevel
    val dens = density
    val a = assets
    // 参考项目的做法：深色壁纸压暗一点，浅色壁纸反而提亮，保持通透不磨砂
    val scrim = if (light) 0.04f + dens * 0.10f else 0.10f + dens * 0.18f
    val sheenEnd = with(LocalDensity.current) { 140.dp.toPx() }
    // 画刷提前建好，避免每帧分配
    val sheen = remember(dens, sheenEnd) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.16f + dens * 0.14f),
            0.55f to Color.Transparent,
            endY = sheenEnd
        )
    }
    val edge = remember(light) {
        if (light) Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.75f),
            0.45f to Color.White.copy(alpha = 0.35f),
            1f to Color(0xFF9FB6CC).copy(alpha = 0.55f)
        ) else Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.40f),
            0.45f to Color.White.copy(alpha = 0.08f),
            1f to Color.White.copy(alpha = 0.22f)
        )
    }
    return this
        .onGloballyPositioned { c ->
            if (fixedPos == null) {
                val p = c.positionInRoot()
                if (p != pos.value) pos.value = p
            }
        }
        .clip(shape)
        .drawBehind {
            val p = fixedPos ?: pos.value
            if (a != null) {
                val img = a.level(level)
                // 只取面板覆盖的那块区域（模糊图是半分辨率），避免整屏缩放采样
                val iw = img.width
                val ih = img.height
                val px = p.x.roundToInt()
                val py = p.y.roundToInt()
                val sx = (px / 2).coerceIn(0, iw)
                val sy = (py / 2).coerceIn(0, ih)
                val sw = (size.width.toInt() / 2).coerceAtMost(iw - sx)
                val sh = (size.height.toInt() / 2).coerceAtMost(ih - sy)
                if (sw > 0 && sh > 0) {
                    drawImage(
                        image = img,
                        srcOffset = IntOffset(sx, sy),
                        srcSize = IntSize(sw, sh),
                        dstOffset = IntOffset(sx * 2 - px, sy * 2 - py),
                        dstSize = IntSize(sw * 2, sh * 2)
                    )
                }
            } else {
                drawRect(Color(0xFF151B24))
            }
            // 玻璃自带的色：浅壁纸提亮，深壁纸压暗，都保持通透
            if (light) drawRect(Color(0xFFF8FBFF).copy(alpha = scrim))
            else drawRect(Color(0xFF080D14).copy(alpha = scrim))
            drawRect(tint.copy(alpha = tintAlpha))
            drawRoundRect(brush = sheen, cornerRadius = CornerRadius(cornerPx))
            if (rim) {
                drawRoundRect(
                    brush = edge,
                    cornerRadius = CornerRadius(cornerPx),
                    style = Stroke(width = strokePx)
                )
            }
        }
}

/** 全屏壁纸，找不到就走渐变兜底 */
@Composable
fun WallpaperView(assets: GlassAssets?, modifier: Modifier = Modifier) {
    if (assets != null) {
        Image(
            bitmap = assets.full,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF101723), Color(0xFF23303F))))
        )
    }
}

/**
 * 统一取“玻璃或纯色”修饰符。API31+ 用库的实时 blur/lens，以下用取样玻璃。
 * [solid] 用于非玻璃样式，透明度和主题色由调用方给。
 */
@Composable
fun glassOrSolid(
    assets: GlassAssets?,
    backdrop: Backdrop,
    glass: Boolean,
    shape: Shape,
    cornerDp: Int,
    screen: IntSize,
    blurAmount: Float,
    density: Float,
    solidColor: Color,
    surfaceAlpha: Float = 0.85f
): Modifier {
    val light = isLightWallpaper(assets)
    if (!glass) return Modifier.clip(shape).background(solidColor)

    if (realGlass) {
        // 参考项目：vibrancy + blur + 轻微 lens，并且必须有一层 container color 打底，
        // 否则只剩纯透明模糊，文字会飘在壁纸上
        val tintA = ((0.14f + 0.34f * surfaceAlpha) * (0.72f + 0.58f * density))
            .coerceIn(0.06f, 0.60f)
        val surface = if (light) Color(0xFFF7FAFF) else Color(0xFF0A1018)
        return Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur((2f + blurAmount * 22f).dp.toPx())
                lens(
                    (2f + density * 7f).dp.toPx(),
                    (2f + density * 5f).dp.toPx()
                )
            },
            onDrawSurface = { drawRect(surface.copy(alpha = tintA)) }
        )
    }

    return Modifier.glassSurface(
        assets = assets,
        shape = shape,
        cornerDp = cornerDp,
        screen = screen,
        blurLevel = blurAmount,
        density = density,
        light = light
    )
}

/** 供 Material 组件取色时避免写死一串参数 */
@Composable
fun appTheme() = MaterialTheme.colorScheme
