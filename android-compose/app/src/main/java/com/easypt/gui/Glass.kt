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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
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
    // 顶部高光：旧版在 140dp 内叠到 0.30，整张卡上半截发白；
    // 现在收短到 110dp、峰值减半，只留一道“灯打上去”的光
    val sheenEnd = with(LocalDensity.current) { 110.dp.toPx() }
    // 画刷提前建好，避免每帧分配
    val sheen = remember(dens, sheenEnd) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.10f + dens * 0.10f),
            0.55f to Color.Transparent,
            endY = sheenEnd
        )
    }
    // 描边改成 135° 渐变：左上最亮、右下收暗，圆角处才有“玻璃边缘”的立体感
    val edge = remember(light) {
        if (light) Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.80f),
            0.35f to Color.White.copy(alpha = 0.30f),
            0.75f to Color(0xFF9FB6CC).copy(alpha = 0.30f),
            1f to Color(0xFF9FB6CC).copy(alpha = 0.58f)
        ) else Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.55f),
            0.38f to Color.White.copy(alpha = 0.10f),
            0.75f to Color.White.copy(alpha = 0.04f),
            1f to Color.White.copy(alpha = 0.26f)
        )
    }
    val topShadowEnd = with(LocalDensity.current) { 96.dp.toPx() }
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
            // 玻璃自带的色：按“这一块壁纸有多亮”决定压暗多少。
            // 旧版固定 0.28：亮壁纸上的白字发飘，暗壁纸又闷；现在亮处多压、
            // 暗处保持通透，卡片滚到哪都不会忽明忽暗。
            val cy = if (screen.height > 0) {
                ((p.y + size.height / 2f) / screen.height).coerceIn(0f, 1f)
            } else 0.5f
            val localLum = if (a != null) {
                a.topLum + (a.bottomLum - a.topLum) * cy
            } else 0.5f
            val bright = ((localLum - 0.42f) / 0.40f).coerceIn(0f, 1f)
            val scrim = if (light) 0.06f + dens * 0.08f + bright * 0.12f
            else 0.05f + dens * 0.09f + bright * 0.20f
            if (light) drawRect(Color(0xFFF8FBFF).copy(alpha = scrim))
            else drawRect(Color(0xFF080D14).copy(alpha = scrim))
            drawRect(tint.copy(alpha = tintAlpha))
            drawRoundRect(brush = sheen, cornerRadius = CornerRadius(cornerPx))
            // 内侧顶边暗影：玻璃有厚度，不是一张贴纸
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.10f + dens * 0.05f),
                    0.45f to Color.Transparent,
                    endY = topShadowEnd.coerceAtMost(size.height * 0.5f).coerceAtLeast(1f)
                )
            )
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
        // 旧版底料 0.56 接近“磨砂黑”，壁纸颜色几乎全被压掉；现在降到 0.40 左右，
        // 通透度交给 colorControls，暗角交给 innerShadow。
        val lum = assets?.avgLum ?: 0.35f
        val base = if (light) 0.40f else 0.33f
        val tintA = (base + 0.12f * surfaceAlpha).coerceIn(0.28f, 0.52f)
        val surface = if (light) Color(0xFFF7FAFF) else Color(0xFF090E16)
        // 壁纸越亮，背板越提亮；越暗越收一点，避免白字飘在亮色上
        val lift = ((lum - 0.35f) * 0.30f).coerceIn(-0.04f, 0.08f)
        return Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(
                    brightness = 0.03f + lift,
                    contrast = 1.04f,
                    saturation = 1.55f
                )
                blur((6f + blurAmount * 18f).dp.toPx())
                // 折射从“固定 7dp”改成跟控件尺寸挂钩：小卡片不糊，大卡片边缘才有液态弯折
                val minPx = size.minDimension
                lens(
                    refractionHeight = (minPx * (0.05f + 0.04f * density))
                        .coerceIn(8.dp.toPx(), 26.dp.toPx()),
                    refractionAmount = (minPx * (0.08f + 0.06f * density))
                        .coerceIn(10.dp.toPx(), 44.dp.toPx()),
                    depthEffect = true,
                    chromaticAberration = true
                )
            },
            // 默认高光只有 0.5dp/50%；加到 1dp，边缘更“利”
            highlight = { Highlight.Default.copy(width = 1.dp, alpha = 0.85f) },
            // 卡片浮起来一点（库默认 24dp/10%）
            shadow = {
                Shadow.Default.copy(
                    radius = 28.dp,
                    color = Color.Black.copy(alpha = 0.14f)
                )
            },
            // 内侧顶边暗影，给玻璃厚度
            innerShadow = {
                InnerShadow(
                    radius = 10.dp,
                    offset = DpOffset(0.dp, 5.dp),
                    color = Color.Black.copy(alpha = 0.10f)
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
