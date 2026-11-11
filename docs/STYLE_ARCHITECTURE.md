# 四种界面风格的代码结构

> 目的：让「液态玻璃 / MIUIX / Material / 原生」四种皮肤各自独立、共用一套骨架，
> 不再出现「同一个判断写三遍」的情况。本文档记录分层、约定，以及还没做完的清理项。

## 分层

```
MainActivity.kt          业务页面（设置 / 首页 / 各种弹窗），只调用 AppXxx 槽位
  │
StyleSlots.kt            槽位派发：按 LocalStyle 选实现
  ├── MaterialSlots.kt   MaterialCard / Row / SwitchRow / Field / Dialog / ChoiceDialog / Title
  ├── MiuixSlots.kt      MiuixThemed / CardSlot / Row / SwitchRow / Field / Dialog / ChoiceDialog / BottomBar
  ├── NativeSlots.kt     NativeCard + NativeAccent
  └── GlassSlots.kt      GlassCard（取样玻璃）/ GlassDialog
Widgets.kt               跨风格共用：IconTile / GlassButton / DialogActions / miuixIconColor
Palette                 所有颜色的唯一来源（含 Material You 转发）
```

### 约定

1. **叶子文件不判断风格**：`MaterialSlots.kt` 里不出现 `when (LocalStyle.current)`，
   它假设「我就是在画 Material」；选择由 `StyleSlots` 完成。
2. **按钮与按钮行禁止内联三分支**：一律走 `GlassButton` / `DialogActions`。
3. **颜色只从 `Palette` 取**：`*Slots.kt` 里不允许出现 `Color(0xFF…)`（`Widgets.kt` 的
   图标色表除外，它是语义色表）。
4. **玻璃效果优先取样**：`drawBackdrop` 的 RenderEffect 在部分 ROM（含本机移植版澎湃）
   会被静默跳过，所以卡片 / 弹窗 / 输入框都先走 `glassSurface`（预烘焙取样），
   `drawBackdrop` 只作为有实时特效设备的兜底。

## 已完成的清理

- 图标色块 3 份 → `IconTile` 1 份
- 弹窗皮肤 3 段内联 → `MaterialDialog` / `MiuixDialog` / `GlassDialog`
- 弹窗按钮行 → `DialogActions`
- 单/双按钮的 backdrop 分支 → `GlassButton`（覆盖 MIUIX、玻璃、胶囊、文字四种形态），已迁移 21 处
- 玻璃卡片、玻璃弹窗、输入框、日志框统一走取样玻璃（滑杆真实生效）
- 危险红 2 个定义 → `Palette.danger`
- 删除死代码：`MiuixDialogCard`、`fieldColors`、`OnlineFontRow`、`primaryButtonColors`、2 段不可达分支

## 现状快照（第 40 轮）

- MainActivity 剩余 `if (useBackdrop …)` 分支：8 处
- MainActivity 剩余 `Color(0xFF…)` 字面量：55 处
- 已迁移到 GlassButton / DialogActions 的按钮分支：22 处
- 玻璃四类面（卡片 / 弹窗 / 输入框 / 日志框）均走取样玻璃，糊度滑杆真实生效

## 待办（按优先级）

1. `MainActivity` 里仍有 9 处 `if (useBackdrop && …)` 按钮分支未迁移到 `GlassButton`，
   当前行号：2879, 2948, 4164, 4656, 4701, 4772, 4959, 5032, 5214
   （更新检查、字体删除 chip、日志颜色弹窗、分区表关闭、Playground 等）
2. MainActivity 还有 67 处 `Color(0xFF…)`，其中一部分是可归入 `Palette` 的成对色。
3. `Glass.kt`（demo 组件内部）保留 8 处色值，属于渲染取样常数，暂不迁移，但应加注释说明。

## 坑：弹窗子树里的 LocalAccent 是旧值（2026-11-12 实测）

`AppGlassPanel` / `MaterialDialog` 这些弹窗容器里，`LocalAccent.current` 会拿到**启动首帧**的值
（莫奈还没取到色时的兜底蓝 `#3B82F6`），之后即使 `Palette.m3` 已经变成莫奈 seed 也不会更新。

实测日志（同一次组合）：

    accent  = #A88028 (金)   m3pri = #A88028   monet = #A88028
    local   = #3B82F6 (蓝)   ← LocalAccent 在弹窗里是过期值

**规则**：弹窗（关于 / 使用向导 / 必应壁纸 / 日志颜色 / 确认框）里的按钮颜色一律直读
`Palette.accent`，不要用 `LocalAccent.current`。主页面不受影响（那里的 LocalAccent 是新的）。

按钮统一口径（A 方案）：

| 角色 | 底色 | 文字 |
|---|---|---|
| 主按钮（选择 / 刷新 / 下一步 / 好 / 完成） | `Palette.accent` 实心 | 白 |
| 次按钮（取消 / 关闭 / 先不看） | `Palette.accent` 16% 胶囊 | 跟随壁纸明暗 |

`GlassButton(primary = true)` 现在两条渲染路径（LiquidButton / 实心胶囊）都按这个来，
`plainBackground` 不再压过 `primary`，`height` 参数也不再被写死成 48dp。
