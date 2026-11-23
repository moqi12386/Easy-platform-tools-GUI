package com.easypt.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * ============ 液态玻璃风格实现（AndroidLiquidGlass demo） ============
 *
 * 卡片三层：
 *   1. safeRender  —— 纯色，不碰任何渲染特效
 *   2. 取样玻璃    —— 从预烘焙的模糊壁纸里按控件位置取样（本项目自有实现）
 *   3. 实时特效    —— demo 的 drawBackdrop + vibrancy/blur/lens（需要可用 RenderEffect）
 *
 * 为什么取样优先：这台机器（移植版澎湃 / SD845）上 drawBackdrop 的 RenderEffect 会被
 * 静默跳过 —— 实测「玻璃模糊」0% 与 100% 截图像素完全一致。走取样路径时滑杆真的改变画面。
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
    // demo 的容器底色：浅色 #FAFAFA、深色 #121212
    val tint = Palette.glassTint
    // 照 LiquidButton 那套：vibrancy + 轻 blur(基准 2dp) + lens(12/24dp)，色由 onDrawSurface 叠。
    // 滑杆：模糊 2~12dp、浓度 12~24dp 折射、不透明度 0.28~0.70 底料。
    val d = LocalDensity.current
    val blurPx = with(d) { (2f + blur * 10f).dp.toPx() }
    val lensPx = with(d) { (12f + density * 12f).dp.toPx() }
    val lensPx2 = with(d) { 24f.dp.toPx() }
    val tintA = 0.28f + alpha * 0.42f
    val wall = LocalWallBackdrop.current

    val mod = when {
        safeRender -> Modifier
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .background(Palette.glassSolid)

        wall != null -> Modifier.drawBackdrop(
            backdrop = wall,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurPx)
                lens(lensPx, lensPx2)
            },
            onDrawSurface = { drawRect(tint.copy(alpha = tintA)) }
        )

        wall != null -> Modifier.drawBackdrop(
            backdrop = wall,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurPx)
                lens(lensPx, lensPx)
            },
            onDrawSurface = { drawRect(tint.copy(alpha = tintA)) }
        )

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
 * 24dp 圆角、20dp 内边距、19sp 半粗标题；有 backdrop 时贴 demo 的
 * vibrancy + blur(8dp) + lens(24dp)，没有时退回实心底。
 */
@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    body: @Composable ColumnScope.() -> Unit
) {
    val shape = remember { RoundedCornerShape(26.dp) }
    val wall = LocalWallBackdrop.current
    // 原版只走 drawBackdrop，但本机 RenderEffect 被跳过 → 实测弹窗一点都不糊。
    // 所以取样玻璃放在前面，drawBackdrop 作为支持实时特效设备的兜底（数值与原版一致）。
    val assets = LocalGlassAssets.current
    val screen = LocalGlassScreen.current

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
                                    vibrancy()
                                    blur(8.dp.toPx())
                                    lens(24.dp.toPx(), 24.dp.toPx())
                                },
                                onDrawSurface = { drawRect(Palette.glassTint.copy(alpha = 0.6f)) }
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