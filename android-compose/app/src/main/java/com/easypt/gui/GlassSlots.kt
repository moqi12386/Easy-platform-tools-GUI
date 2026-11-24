package com.easypt.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * ============ 液态玻璃风格实现（AndroidLiquidGlass demo） ============
 *
 * 卡片三层：
 *   1. safeRender  —— 纯色，不碰任何渲染特效
 *   2. 实时特效    —— demo 的 drawBackdrop + colorControls / blur / lens
 *   3. 兜底        —— 半透明板
 *
 * 参数按上游 AndroidLiquidGlass 的最新 demo 回填：
 *   - 折射高度/强度跟控件尺寸走（原来固定 12/24dp，大卡片几乎看不出弯折）
 *   - 色散打开，边缘在亮色壁纸上会有一点点彩边
 *   - 底料从 0.28+0.42*alpha 降到 0.26+0.30*alpha，玻璃更透、壁纸颜色更明显
 */

@Composable
fun GlassCard(
    assets: GlassAssets?,
    screen: IntSize,
    blur: Float,
    density: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = remember { RoundedCornerShape(24.dp) }
    val tint = Palette.glassTint
    val d = LocalDensity.current
    val light = isLightWallpaper(assets)
    val wall = LocalWallBackdrop.current

    val tintA = (0.28f + alpha * 0.26f).coerceIn(0.26f, 0.56f)
    val blurPx = with(d) { (2f + blur * 8f).dp.toPx() }
    val lift = (((assets?.avgLum ?: 0.35f) - 0.35f) * 0.30f).coerceIn(-0.04f, 0.08f)
    val brightness = 0.03f + if (light) 0.10f else lift

    val effects: BackdropEffectScope.() -> Unit = remember(brightness, blurPx, density) {
        {
            colorControls(brightness = brightness, contrast = 1.04f, saturation = 1.55f)
            blur(blurPx)
            val minPx = size.minDimension
            lens(
                refractionHeight = (minPx * (0.08f + 0.04f * density))
                    .coerceIn(10.dp.toPx(), 32.dp.toPx()),
                refractionAmount = (minPx * (0.16f + 0.06f * density))
                    .coerceIn(16.dp.toPx(), 72.dp.toPx()),
                depthEffect = true,
                chromaticAberration = true
            )
        }
    }
    val surface: DrawScope.() -> Unit = remember(tint, tintA) {
        { drawRect(tint.copy(alpha = tintA)) }
    }
    val highlight = remember { { Highlight.Plain.copy(width = 1.dp, alpha = 0.55f) } }
    val shadowEffect = remember {
        {
            Shadow.Default.copy(
                radius = 24.dp,
                color = Color.Black.copy(alpha = 0.12f)
            )
        }
    }

    val glassMod: Modifier? = if (!safeRender && wall != null) {
        remember(wall, shape, effects, highlight, shadowEffect, surface) {
            Modifier.drawBackdrop(
                backdrop = wall,
                shape = { shape },
                effects = effects,
                highlight = highlight,
                shadow = shadowEffect,
                innerShadow = {
                    InnerShadow(
                        radius = 8.dp,
                        offset = DpOffset(0.dp, 4.dp),
                        color = Color.Black.copy(alpha = 0.08f)
                    )
                },
                onDrawSurface = surface
            )
        }
    } else null

    val mod = when {
        safeRender -> Modifier
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .background(Palette.glassSolid)
        glassMod != null -> glassMod
        else -> Modifier
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .background(tint)
    }

    CompositionLocalProvider(
        LocalCardText provides if (Palette.dark) Color.White else Palette.text
    ) {
        Column(
            modifier.fillMaxWidth().then(mod).padding(vertical = 6.dp),
            content = content
        )
    }
}

/**
 * 玻璃弹窗面板：和 MaterialDialog / MiuixDialog 对称的三段之一。
 * 26dp 圆角、20dp 内边距、19sp 半粗标题；有 backdrop 时贴 demo 的
 * colorControls + blur + lens(24/40dp, depth + 色散)，没有时退回实心底。
 */
@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    body: @Composable ColumnScope.() -> Unit
) {
    val shape = remember { RoundedCornerShape(26.dp) }
    val wall = LocalWallBackdrop.current
    val assets = LocalGlassAssets.current
    val light = isLightWallpaper(assets)
    val surface = Palette.glassTint.copy(alpha = if (light) 0.50f else 0.34f)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().padding(26.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (useBackdrop && wall != null) {
                            Modifier.drawBackdrop(
                                backdrop = wall,
                                shape = { shape },
                                effects = {
                                    colorControls(
                                        brightness = if (light) 0.16f else 0.03f,
                                        contrast = 1.04f,
                                        saturation = 1.50f
                                    )
                                    blur((if (light) 14f else 10f).dp.toPx())
                                    lens(
                                        24.dp.toPx(),
                                        40.dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = true
                                    )
                                },
                                highlight = { Highlight.Plain },
                                shadow = {
                                    Shadow.Default.copy(
                                        radius = 32.dp,
                                        color = Color.Black.copy(alpha = 0.18f)
                                    )
                                },
                                onDrawSurface = { drawRect(surface) }
                            )
                        } else {
                            Modifier.clip(shape).background(Palette.surface)
                        }
                    )
                    .padding(20.dp)
            ) {
                androidx.compose.material3.Text(
                    title,
                    color = Palette.text,
                    fontSize = 19.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                body()
            }
        }
    }
}
