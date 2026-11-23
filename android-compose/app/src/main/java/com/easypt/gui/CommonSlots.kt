package com.easypt.gui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * ============ 液态玻璃 / 原生 共用的骨架实现 ============
 *
 * 这两种风格在「列表行 / 开关行 / 输入框 / 页标题」上的视觉一致，
 * 差别只在卡片（GlassCard 有玻璃取样，NativeCard 是深色半透明板），
 * 所以这四项共用一套实现，避免两份重复代码各自跑偏。
 *
 * 这几个函数都转发到 MainActivity.kt 里的 KRow / KSwitchRow / GlassField / PageTitle。
 */

@Composable
fun CommonRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    chevron: Boolean = true,
    tint: Color? = null,
    onClick: (() -> Unit)? = null
) = KRow(icon, title, subtitle, trailing, chevron, tint, onClick)

@Composable
fun CommonSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) = KSwitchRow(icon, title, subtitle, checked, enabled, onCheckedChange)

@Composable
fun CommonField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    readOnly: Boolean = false,
    numeric: Boolean = false,
    onClick: (() -> Unit)? = null
) = GlassField(value, onValueChange, placeholder, modifier, readOnly, numeric, onClick)

@Composable
fun CommonTitle(text: String) = PageTitle(text)
