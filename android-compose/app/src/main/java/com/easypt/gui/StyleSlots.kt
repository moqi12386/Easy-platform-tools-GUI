package com.easypt.gui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop

/**
 * ============ 风格槽位（Style Slots） ============
 *
 * 调用点只认这里的 AppXxx，具体实现按 LocalStyle 分派到各风格的组件：
 *
 *   style/Glass    —— GlassCard（drawBackdrop / 取样玻璃）
 *   style/Material —— MaterialCard / MaterialRow / ...（M3 组件）
 *   style/Miuix    —— MiuixCardSlot / MiuixRow / ...（miuix 库）
 *   style/Native   —— NativeCard（深色半透明板）
 *
 * 每种风格的实现各自独立，互不影响；改一种不会碰到另一种。
 */

/** 卡片容器 */
@Composable
fun AppCard(
    style: UiStyle,
    assets: GlassAssets?,
    backdrop: Backdrop,
    screen: IntSize,
    blur: Float,
    density: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = when (LocalStyle.current) {
    UiStyle.Material -> MaterialCard(modifier, content)
    UiStyle.Miuix -> MiuixCardSlot(modifier, content)
    UiStyle.Native -> NativeCard(modifier, content)
    UiStyle.Glass -> GlassCard(assets, screen, blur, density, alpha, modifier, content)
}

/** 一行设置项：图标 + 标题 + 副标题 + 右侧内容 */
@Composable
fun AppRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    chevron: Boolean = true,
    tint: Color? = null,
    onClick: (() -> Unit)? = null
) = when (LocalStyle.current) {
    UiStyle.Material -> MaterialRow(icon, title, subtitle, trailing, chevron, onClick)
    UiStyle.Miuix -> MiuixRow(icon, title, subtitle, trailing, chevron, tint, onClick)
    else -> CommonRow(icon, title, subtitle, trailing, chevron, tint, onClick)
}

/** 带开关的一行 */
@Composable
fun AppSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) = when (LocalStyle.current) {
    UiStyle.Material -> MaterialSwitchRow(icon, title, subtitle, checked, enabled, onCheckedChange)
    UiStyle.Miuix -> MiuixSwitchRow(icon, title, subtitle, checked, enabled, onCheckedChange)
    else -> CommonSwitchRow(icon, title, subtitle, checked, enabled, onCheckedChange)
}

/** 输入框（命令、分区、配对码……） */
@Composable
fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    numeric: Boolean = false,
    onClick: (() -> Unit)? = null
) = when (LocalStyle.current) {
    UiStyle.Material -> MaterialField(value, onValueChange, placeholder, modifier, readOnly, numeric)
    UiStyle.Miuix -> MiuixField(value, onValueChange, placeholder, modifier)
    else -> CommonField(value, onValueChange, placeholder, modifier, readOnly, numeric, onClick)
}

/** 选择行：右侧当前值 + 下拉箭头，点开弹窗选 */
@Composable
fun AppSelectRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    value: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    deletable: Set<Int> = emptySet(),
    onDelete: ((Int) -> Unit)? = null
) = KSelectRow(icon, title, subtitle, value, options, selected, onPick, deletable, onDelete)

/** 页面大标题 */
@Composable
fun AppTitle(text: String) = when (LocalStyle.current) {
    UiStyle.Material -> MaterialTitle(text)
    else -> CommonTitle(text)
}

/** 弹窗容器（标题 + 内容） */
@Composable
fun AppDialog(
    style: UiStyle,
    title: String,
    onDismiss: () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit
) = KDialog(style, title, onDismiss, body)

/** 无标题的玻璃风格弹窗容器（在线壁纸 / 关于 / 向导那一类） */
@Composable
fun AppGlassPanel(
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) = GlassDialogPanel(backdrop, onDismiss, content)
