package com.easypt.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ============ 原生风格实现 ============
 *
 * 原生风格本身的差异很小：只有卡片是一块深色半透明板（Palette.nativeCard）+ 固定白字，
 * 列表行 / 开关 / 输入框 / 标题都复用通用实现（和液态玻璃同一套骨架，但没有玻璃取样）。
 *
 * 这里不含任何其它风格的代码，也没有 `when (LocalStyle.current)`。
 */

/** 原生风格的主色一律中性，界面里不出现彩色 */
val NativeAccent: Color get() = Palette.nativeAccent

@Composable
fun NativeCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    CompositionLocalProvider(
        LocalCardText provides Color.White,
        LocalAccent provides NativeAccent
    ) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Palette.nativeCard)
                .padding(vertical = 6.dp),
            content = content
        )
    }
}
