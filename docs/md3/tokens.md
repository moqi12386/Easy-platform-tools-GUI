# M3 令牌数值（本项目采用的规范值）

## 形状（corner-radius scale，单位 dp）

| token | dp | Compose `Shapes` 槽位 |
|---|---|---|
| none | 0 | —（`RectangleShape`） |
| extraSmall | 4 | ✔ |
| small | 8 | ✔ |
| medium | 12 | ✔ |
| large | 16 | ✔ |
| largeIncreased | 20 | —（就地 `RoundedCornerShape`） |
| extraLarge | 28 | ✔ |
| extraLargeIncreased | 32 | — |
| extraExtraLarge | 48 | — |
| full | 短边 / 2 | `CircleShape` / `RoundedCornerShape(percent = 50)` |

组件默认值：**dialog 28**、card 12、chip 8、button `full`（胶囊）。

## 字阶（baseline，单位 sp）

| style | size / line / weight | tracking（Compose 实现值） |
|---|---|---|
| displayLarge | 57 / 64 / 400 | -0.2 |
| displayMedium | 45 / 52 / 400 | 0 |
| displaySmall | 36 / 44 / 400 | 0 |
| headlineLarge | 32 / 40 / 400 | 0 |
| headlineMedium | 28 / 36 / 400 | 0 |
| headlineSmall | 24 / 32 / 400 | 0 |
| titleLarge | 22 / 28 / 400 | 0 |
| titleMedium | 16 / 24 / 500 | 0.2 |
| titleSmall | 14 / 20 / 500 | 0.1 |
| bodyLarge | 16 / 24 / 400 | 0.5 |
| bodyMedium | 14 / 20 / 400 | 0.2 |
| bodySmall | 12 / 16 / 400 | 0.4 |
| labelLarge | 14 / 20 / 500 | 0.1 |
| labelMedium | 12 / 16 / 500 | 0.5 |
| labelSmall | 11 / 16 / 500 | 0.5 |

行高按语言类别伸缩：拉丁系 1.0、中文/日文/韩文等 1.07、缅甸语等 1.3、Nastaliq 2.0。
固定高度组件不会自动适配，语言行高变大时要留余量。

## 组件取值

| 组件 | 规范 | 本项目采用 |
|---|---|---|
| Card | 12dp 圆角 | 12dp |
| Dialog | 28dp 圆角、24dp 内边距、标题 headlineSmall | 28dp / 24dp / headlineSmall |
| 列表项 | 最小高度 56dp，标题 bodyLarge，副标题 bodyMedium | 依赖 `ListItem` 默认 56dp |
| OutlinedTextField | 4dp 圆角 | 4dp |
| 按钮 | 高 40dp，labelLarge，胶囊形 | 按各风格按钮实现 |