# Easy-platform-tools-GUI v2.6.0 — Android 的四套界面样式

同一套功能，四种外壳：**原生 / Material / MIUIX / 液态玻璃**，设置里一点就换，模式、配色、边框、壁纸全部保留。

![四种样式](https://raw.githubusercontent.com/moqi12386/Easy-platform-tools-GUI/main/docs/styles.png)

## 改了什么

### 1. 样式层：一个开关换整套外观

`设置 → 界面样式`。样式不只是一层皮，它同时改这几项：

| | 圆角 | 底色 | 布局 | 其它 |
|---|---|---|---|---|
| **原生** | 10 dp | 半透明，按你调的不透明度走 | 两列 | 边框按你选的来 |
| **Material** | 24 dp 药丸 | 往主色带一点，最低 88% 不透明 | 两列 | 2 dp 投影 + 按下水波纹 |
| **MIUIX** | 16 dp | 62% 起 | **单列列表**，行高 52 dp，左标题右箭头 | 字号 15 sp，箭头自绘 |
| **液态玻璃** | 22 dp | 磨砂，背后就是壁纸 | 两列 + **底部 dock** | 白细边 + 顶部高光 + 8 dp 浮起 |

切样式走 `recreate()`，`mode` / `style` / `bg` / `alpha` / 边框 / 壁纸都落在 SharedPreferences，重建后原样恢复。

### 2. 液态玻璃不是贴图，是真取背后的壁纸

`RenderEffect` 只能糊自己、糊不了背后，而且要 API 31。这里的做法：

```
控件屏幕坐标 → 壁纸位图同一区域 → 缩到 1/8 → 拉回控件大小
→ 裁进圆角 → 叠薄雾 + 顶部高光 → 描白边 + 顶沿亮线
```

缩小再放大本身就是一次低通滤波，等于廉价高斯；每块玻璃糊的是**它自己背后**那块，
所以往下滚、换壁纸、按钮错位都不会穿帮。没有壁纸时退化成半透明纯色。

底部那条 dock 也是同一个 `GlassDrawable`：工具条 + 输入框装进去，圆角 24 dp、浮起 14 dp。

### 3. 顺手补的对比度

标题和状态栏小字以前用固定主题色，遇到浅色壁纸就糊在背景里。现在两处都按**自己所在位置的壁纸底色**算
（`averageRegion` + WCAG），状态栏小字再往背景混 30% 保留「次要信息」的弱化感。

## 下载

| 文件 | 说明 |
|---|---|
| `EasyPT.apk` | Android 2.6.0，56 KB，minSdk 26（Android 8.0+），versionCode 10 |

桌面版这一版没动，仍是 v2.4.0。

## 已验证 / 未验证

在平板（Android 15 / 1840×2944 / 350 dpi）上四种样式逐一看过，包括浅色壁纸下的取色反白、
玻璃模糊、MIUIX 单列、Material 水波纹与阴影、以及切样式后设置是否保留。

**没有在手机上跑过**：手机竖屏下 MIUIX 的单列列表、玻璃 dock 的高度观感可能和横屏平板不同，
需要你实机看一眼。

## 已知取舍

- Material / MIUIX / 液态玻璃仍然沿用你设置的「按键边框」颜色（默认彩虹），所以 Material 会出现彩虹描边。
  想要纯 Material 观感，把边框设成「无」。
- 玻璃的模糊是「缩小-放大」近似，不是真高斯，凑近看边缘会比真模糊脏一点。
- MIUIX 单列会让快捷按钮需要滚动，两列的密度没了。
- 样式只能四选一，没有做「深色/浅色」独立开关（跟着「背景配色」的 8 套走）。

## 许可

代码 MIT。platform-tools、MiSans、JetBrains Mono 遵循各自许可，不随本仓库分发。
样式参考了 [miuix](https://github.com/compose-miuix-ui/miuix)、[Glasmio](https://github.com/NEORUAA/Glasmio)、
[liquid-glass-compose](https://github.com/muhammadalkady/liquid-glass-compose) 的设计语言，未使用其代码。
