package com.easypt.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * ============ 跨风格共用的小部件 ============
 *
 * 这里只放「多个风格都要用、而且长得完全一样」的东西，避免同一段代码
 * 在 MainActivity / MiuixSlots 里各写一份然后慢慢跑偏。
 *
 * 图标色块（MIUIX 与原生 / 玻璃的 KRow 共用）：
 *   - 30dp 圆角 9dp 的方块
 *   - 19dp 白色图标
 *   - 底色按图标语义取色（miuixIconColor）
 */

/** 按图标语义取底色 —— 之前这段 when 在 MainActivity 和 MiuixSlots 各有一份 */
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

/** 彩色圆角图标块（MIUIX / 原生 / 玻璃设置行通用） */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color? = null,
    enabled: Boolean = true
) {
    val base = tint ?: miuixIconColor(icon)
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) base else base.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

/**
 * 玻璃风格按钮：有壁纸 backdrop 时用 demo 的 LiquidButton，没有时退回胶囊实心底。
 *
 * 这段「if (useBackdrop && bd != null) LiquidButton(...) else Row(胶囊 + clickable)」
 * 在 MainActivity 里出现了 24 次，每处十几行且细节各写各的；先抽成这个统一入口，
 * 逐轮把调用点换过来。
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = LocalCardText.current,
    fontSize: TextUnit = 15.sp,
    tint: Color = Color.Unspecified,
    surface: Color = Color.Unspecified,
    plainBackground: Color = Color.Unspecified,
    /** 无壁纸时用主色填充（等价于原来的主按钮） */
    primary: Boolean = false,
    /** 无壁纸时退化成文字按钮（原来的 TextButton 兜底） */
    plainText: Boolean = false,
    /** 高度，默认 48dp；首页「执行」这类按钮用 52dp */
    height: Dp = 48.dp
) {
    // MIUIX：直接借库里的按钮，省得每个调用点再写一遍三分支
    if (LocalStyle.current == UiStyle.Miuix) {
        if (primary) {
            top.yukonga.miuix.kmp.basic.Button(
                onClick = onClick,
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                modifier = modifier
            ) { Text(text, color = Color.White, fontSize = fontSize) }
        } else {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = text,
                onClick = onClick,
                modifier = modifier
            )
        }
        return
    }
    // A 口径：主按钮一律「主色底 + 白字」。两条渲染路径（有壁纸的 LiquidButton /
    // 没壁纸的实心胶囊）都按 primary 来，不再各走各的。
    val fill = if (tint != Color.Unspecified) tint else Palette.accent
    val labelColor = if (primary) Color.White else textColor
    val bd = LocalWallBackdrop.current
    if (useBackdrop && bd != null) {
        com.easypt.gui.glass.LiquidButton(
            onClick = onClick,
            backdrop = bd,
            modifier = modifier.height(height),
            tint = if (primary) fill else tint,
            surfaceColor = if (primary) fill.copy(alpha = 0.90f) else surface
        ) {
            Text(text, color = labelColor, fontSize = fontSize)
        }
    } else if (plainText) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            androidx.compose.material3.TextButton(onClick = onClick) {
                Text(text, color = Palette.text2, fontSize = fontSize)
            }
        }
    } else {
        val plainBg = when {
            primary -> fill
            plainBackground != Color.Unspecified -> plainBackground
            else -> textColor.copy(alpha = 0.10f)
        }
        // 主按钮固定白字；其余按底色对比（深底白字、浅底黑字），避免黑底黑字
        val onPlain =
            if (primary) Color.White
            else if (plainBg.luminance() < 0.45f) Color.White else Color.Black
        Box(
            modifier
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(plainBg)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = onPlain,
                fontSize = fontSize,
                maxLines = 1
            )
        }
    }
}

/**
 * 弹窗底部按钮行：一次写清四种风格，调用点不用再 if/else 三遍。
 *
 *   原生 / 玻璃：有壁纸用 demo 的 LiquidButton（取消 surfaceColor、确认 tint 主色），
 *                没有壁纸退回胶囊底；
 *   MIUIX      ：miuix 的 TextButton + 主色 Button；
 *   Material   ：M3 TextButton + Button（12dp 圆角）。
 *
 * 原来这段在「擦除确认 / 线刷步骤 / 分区表 / 崩溃日志」里各写一遍。
 */
@Composable
fun DialogActions(
    dismissText: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmColor: Color = Palette.accent,
    enabled: Boolean = true
) {
    when (LocalStyle.current) {
        UiStyle.Miuix -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            top.yukonga.miuix.kmp.basic.Button(
                onClick = { if (enabled) onConfirm() },
                // 危险操作（删除/擦除）也用红色，和 Material / 玻璃分支保持一致
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.weight(1f)
            ) { Text(confirmText, color = Color.White) }
        }

        UiStyle.Material -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(dismissText, color = Palette.text2)
            }
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Button(
                onClick = { if (enabled) onConfirm() },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = confirmColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text(confirmText) }
        }

        else -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                textColor = Palette.text,
                surface = Palette.accent.copy(alpha = 0.32f),
                plainBackground = Palette.accent.copy(alpha = 0.16f)
            )
            GlassButton(
                text = confirmText,
                onClick = { if (enabled) onConfirm() },
                modifier = Modifier.weight(1f),
                textColor = Color.White,
                tint = confirmColor
            )
        }
    }
}

/**
 * 手机屏幕本身的圆角半径（dp）。
 *
 * 正规途径：WindowInsets.getRoundedCorner(POSITION_*) → RoundedCorner.radius，
 * 由系统按机型给出，不用猜、不用按型号写死。取不到时退回 28dp。
 */
@Composable
fun rememberScreenCorner(): androidx.compose.ui.unit.Dp {
    val view = androidx.compose.ui.platform.LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px: Int = remember(view) {
        var best = 0
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val insets = view.rootWindowInsets
                if (insets != null) {
                    intArrayOf(
                        android.view.RoundedCorner.POSITION_TOP_LEFT,
                        android.view.RoundedCorner.POSITION_TOP_RIGHT,
                        android.view.RoundedCorner.POSITION_BOTTOM_LEFT,
                        android.view.RoundedCorner.POSITION_BOTTOM_RIGHT
                    ).forEach { p ->
                        val r = runCatching { insets.getRoundedCorner(p)?.radius ?: 0 }.getOrDefault(0)
                        if (r > best) best = r
                    }
                }
            }
        }
        best
    }
    return with(density) { if (px > 0) px.toDp() else 28.dp }
}
