package com.easypt.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDark
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLight

/**
 * ============ MIUIX 风格实现（miuix 库） ============
 *
 * 这里只放 MIUIX 的实现，不含其它风格的代码，也没有 `when (LocalStyle.current)`。
 */

/** MIUIX 主题（深色/浅色自动，和主界面同一套 MiuixTheme） */
@Composable
fun MiuixThemed(content: @Composable () -> Unit) {
    MiuixTheme(colors = if (Palette.dark) miuixDark() else miuixLight()) { content() }
}

@Composable
fun MiuixTitle(text: String) = MiuixThemed {
    // 之前用 SmallTopAppBar：它带一块不透明容器，直接铺成屏幕顶部一条大白条，
    // 和 MIUI 的「页内大标题」观感不符。改用库里的 SmallTitle（透明、无容器），
    // 并且左右留 16dp，和卡片的 16dp 对齐。
    // 标题色跟壁纸走（LocalTitleColor），否则浅色壁纸 + 浅色主题会糊成一片
    SmallTitle(
        text,
        Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
        LocalTitleColor.current
    )
}

@Composable
fun MiuixCardSlot(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = CompositionLocalProvider(LocalCardText provides Palette.text) {
    MiuixCard(modifier.fillMaxWidth(), cornerRadius = 22.dp) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
fun MiuixRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    chevron: Boolean = true,
    tint: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val text = LocalCardText.current
    val lead: (@Composable () -> Unit)? = if (icon != null) {
        {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, tint)
                Spacer(Modifier.width(14.dp))
            }
        }
    } else null
    val tail: (@Composable RowScope.() -> Unit)? = if (trailing != null) {
        { trailing() }
    } else if (chevron && onClick != null) {
        {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = text.copy(alpha = 0.35f),
                modifier = Modifier.size(22.dp)
            )
        }
    } else null
    BasicComponent(
        title = title,
        summary = subtitle,
        startAction = lead,
        endActions = if (tail == null) null else ({ tail!!.invoke(this) }),
        onClick = onClick
    )
}

@Composable
fun MiuixSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    BasicComponent(
        title = title,
        summary = subtitle,
        startAction = {
            IconTile(icon)
            Spacer(Modifier.width(14.dp))
        },
        endActions = {
            MiuixSwitch(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) },
                enabled = enabled
            )
        },
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null
    )
}

@Composable
fun MiuixField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    MiuixTextField(
        value = value,
        onValueChange = onValueChange,
        label = placeholder,
        modifier = modifier.fillMaxWidth()
    )
}


/** MIUIX 底栏：70dp 内容行 + 屏幕圆角，颜色用 miuix 的 surfaceContainer */
@Composable
fun MiuixBottomBar(
    page: Int,
    onPage: (Int) -> Unit,
    homeLabel: String,
    settingsLabel: String,
    modifier: Modifier = Modifier
) = MiuixThemed {
    // 用库自己的 NavigationBar / NavigationBarItem，不再手画图标+文字
    // 背景铺到手势条下面，内容区再让开 navigationBars，
    // 否则底部会露出一条灰色空隙（截图里能看到）
    Box(
        modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceContainer)
    ) {
    NavigationBar(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        NavigationBarItem(
            selected = page == 0,
            onClick = { onPage(0) },
            icon = Icons.Filled.Home,
            label = homeLabel
        )
        NavigationBarItem(
            selected = page == 1,
            onClick = { onPage(1) },
            icon = Icons.Filled.Settings,
            label = settingsLabel
        )
    }
    }
}
/**
 * MIUIX 弹窗容器：miuix 的 Card + 20dp 内边距。
 * KDialog 的 MIUIX 分支直接调用它，这样 miuix 的弹窗皮肤和其它 miuix 组件放在一起。
 */
@Composable
fun MiuixDialog(
    title: String,
    onDismiss: () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit
) = MiuixThemed {
    // MIUIX 库没有 dialog 组件，这里用它的 Card + 遮罩：
    // 点击遮罩关闭，卡片 28dp 圆角、20dp 内边距（与 MIUI 系统弹窗一致）
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (Palette.dark) 0.45f else 0.25f))
            .clickable(
                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.padding(32.dp).clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null
        ) { }) {
            MiuixCard(Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
                Column(Modifier.padding(20.dp)) {
                    SmallTitle(title)
                    Spacer(Modifier.height(8.dp))
                    body()
                }
            }
        }
    }
}