# Easy-platform-tools-GUI v2.3.0 — 沉浸状态栏 + 文字自动反色

## 安卓：沉浸状态栏

- 状态栏、导航栏都设为透明，界面画到它们下面（`setDecorFitsSystemWindows(false)`，旧系统走 `LAYOUT_FULLSCREEN`）
- 用 `WindowInsets` 把状态栏高度、导航栏高度补成根布局的 padding，内容不会被挡
- 状态栏图标颜色跟着壁纸平均亮度走：壁纸亮 → 黑图标，壁纸暗 → 白图标（`APPEARANCE_LIGHT_STATUS_BARS`）

## 双端：按钮文字自动反色

不再是固定浅色字，而是**按按钮底色算 WCAG 对比度**，挑黑字还是白字：

```
相对亮度 L = 0.2126R + 0.7152G + 0.0722B（sRGB 线性化后）
白字对比度 = 1.05 / (L + 0.05)
黑字对比度 = (L + 0.05) / 0.05
取大的那个
```

- 按钮半透明时，先把它**按透明度混合到底色上**再判断，所以「透明 + 浅壁纸」会自动变黑字
- 桌面版：WinForms 子控件不支持真透明（只会取父容器 BackColor），所以直接把按钮色混到壁纸平均色上，
  得到视觉等效的不透明色；顺带解决了"调了透明度但看不出来"的问题
- 桌面版壁纸遮罩从 120 降到 95，浅色壁纸不再被压成灰

![浅色壁纸 + 低透明度](https://github.com/moqi12386/Easy-platform-tools-GUI/raw/main/docs/wallpaper.png)

## 下载

| 文件 | 说明 |
|---|---|
| `EasyPT.apk` | Android 2.3.0，44 KB，minSdk 26 |
| `Easy-platform-tools-GUI-v2.3.0.zip` | Windows 桌面版 |
| `Easy-platform-tools-GUI.exe` | 桌面版单文件，110 KB |

## 许可

代码 MIT。platform-tools、MiSans、JetBrains Mono 遵循各自许可，不随本仓库分发。
