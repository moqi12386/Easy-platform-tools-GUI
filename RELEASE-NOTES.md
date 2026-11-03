# Easy-platform-tools-GUI v2.1.0 — 按键透明度 + 壁纸不再拉伸

真机截图暴露了两个问题：壁纸被纵向拉长、按键底板不透光。这版一起修。

## 壁纸拉伸修复

| 端 | 之前 | 现在 |
|---|---|---|
| Android | 裁剪框按对话框高度比例（58% 屏高），裁出来的图比例和屏幕不一致，铺回去就变形 | 先取**本机真实分辨率**（`getMaximumWindowMetrics` / `getRealMetrics`，含状态栏与导航栏），按屏幕宽高比摆好裁剪框，裁出的图正好铺满 |
| 桌面版 | `DrawImage` 直接拉伸到窗口尺寸 | **等比铺满 + 居中裁切**（cover），窗口比例怎么变都不变形 |

## 按键透明度

两端设置里都新增「按键透明度」滑杆（0–100%），实时生效：

- **桌面版**：`AlphaButton` 开启 `SupportsTransparentBackColor`，底板用带 alpha 的颜色，
  透出下面的壁纸；数值存进 `config.ini` 的 `alpha=`
- **Android**：按钮底改成 `GradientDrawable`（圆角 + alpha），顺带把原来的直角方块变圆角；
  日志区透明度也跟着这个值走

## 界面其它

- Android 按钮从直角改成 10dp 圆角 + 1dp 描边，视觉上更轻
- 桌面版壁纸的压暗层从「位图里压一遍 + 面板再压一遍」减为只压一次，壁纸能看清了

## 下载

| 文件 | 说明 |
|---|---|
| `EasyPT.apk` | Android 2.1.0，44 KB，minSdk 26 |
| `Easy-platform-tools-GUI-v2.1.0.zip` | Windows 桌面版 |
| `Easy-platform-tools-GUI.exe` | 桌面版单文件，108 KB |

## 说明

壁纸与透明度都已在 Windows 端截图核对；Android 端的裁剪比例与圆角按钮在真机（MIX 2S）上验证过壁纸能正常显示，
这次的调整还没有你那边的新截图确认。

## 许可

代码 MIT。platform-tools、MiSans、JetBrains Mono 遵循各自许可，不随本仓库分发。
