package com.easypt.gui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons as M3Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * ============ Material Design 3 风格实现 ============
 *
 * 这里只放 Material 的实现，不含其它风格的代码，也没有 `when (LocalStyle.current)`。
 * 由 StyleSlots.kt 的槽位按 LocalStyle 分派过来。
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialTitle(text: String) {
    LargeTopAppBar(
        title = { Text(text, maxLines = 1) },
        colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

@Composable
fun MaterialCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = CompositionLocalProvider(LocalCardText provides Palette.text) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Palette.surface),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (Palette.dark) 1.dp else 3.dp
        )
    ) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val text = LocalCardText.current
    val lead: (@Composable () -> Unit)? = if (icon != null) {
        { Icon(icon, contentDescription = null, tint = text, modifier = Modifier.size(24.dp)) }
    } else null
    val sub: (@Composable () -> Unit)? = if (subtitle != null) {
        {
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else null
    val tail: (@Composable () -> Unit)? = if (trailing != null) {
        { androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) { trailing() } }
    } else if (chevron && onClick != null) {
        {
            Icon(
                M3Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    } else null
    ListItem(
        modifier = Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = lead,
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = sub,
        trailingContent = tail
    )
}

@Composable
fun MaterialSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val text = LocalCardText.current
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = text, modifier = Modifier.size(24.dp))
        },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let { s ->
            {
                Text(
                    s,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = { onCheckedChange(it) }, enabled = enabled)
        }
    )
}

@Composable
fun MaterialField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    numeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        singleLine = true,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = if (numeric)
            androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
        else androidx.compose.foundation.text.KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDialog(
    title: String? = null,
    onDismiss: () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.material3.BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = Palette.surface,
            contentColor = Palette.text,
            tonalElevation = 0.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                if (title != null) {
                    Text(title, color = Palette.text, fontSize = androidx.compose.ui.unit.TextUnit(19f, androidx.compose.ui.unit.TextUnitType.Sp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                }
                body()
            }
        }
    }
}

/**
 * Material 风格的选择弹窗：M3 ListItem 列表 + 右侧勾选 + 底部 TextButton。
 * 由 LiquidChoiceDialog 派发过来，和 MiuixChoiceDialog / 玻璃面板对称。
 */
@Composable
fun MaterialChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    deletable: Set<Int> = emptySet(),
    onDelete: ((Int) -> Unit)? = null
) = MaterialDialog(title = title, onDismiss = onDismiss) {
    // 字体列表可能有十几个（含在线字体），必须能滑，否则下半截点不到
    androidx.compose.foundation.layout.Column(
        Modifier
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
    ) {
    options.forEachIndexed { i, name ->
        ListItem(
            modifier = Modifier.clickable { onPick(i) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(name, style = MaterialTheme.typography.bodyLarge, color = Palette.text) },
            trailingContent = {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    if (onDelete != null && deletable.contains(i)) {
                        androidx.compose.material3.Text(
                            "删除",
                            color = Palette.text2,
                            fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
                            modifier = Modifier
                                .clickable { onDelete(i) }
                                .padding(end = 10.dp)
                        )
                    }
                    if (i == selected) {
                        Icon(M3Icons.Filled.Check, contentDescription = null, tint = Palette.accent)
                    }
                }
            }
        )
    }
    }
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
    ) {
        androidx.compose.material3.TextButton(onClick = onDismiss) {
            androidx.compose.material3.Text("取消", color = Palette.text)
        }
    }
}