# Easy-platform-tools-GUI v3.2.0 — Android 端 Compose 重写（KernelSU 风格 + 夜间模式）

Android 端从「纯 Java + 自绘」整体重写为 Compose，这是第一个对外发布的 Compose 版本。

## 界面

- 布局改为 KernelSU 风格：34sp 大标题、22dp 圆角卡片、图标 + 标题 + 副标题的设置行
- **悬浮底栏**：白色玻璃胶囊，用 `layerBackdrop` 录下整个内容层，底栏再用 `drawBackdrop` 取样 —— 滚动时卡片会从底栏后面实时模糊地滑过
- 底栏可**拖动**切换页面，松手弹簧吸附；选中项是淡色圆角块 + 蓝色图标
- 四种界面样式真正区分开：
  - **原生**：深色半透明板（重制前的观感）
  - **Material**：M3 表面 + 投影，支持**莫奈取色**（Android 12+，从壁纸取主题色）
  - **MIUIX**：走 miuix 库自带的 Card 与 Slider，不自己画
  - **液态玻璃**：卡片半透明，从「只含壁纸的图层」取样模糊，壁纸能透出来
- **夜间模式**：日间 / 夜间 / 跟随系统，弹窗选择，跟随主题重建整棵树
- 状态栏图标与页标题按壁纸亮度自动反色
- 中英文切换

## 功能

- **OTG 主机**：自己实现 fastboot USB 协议（不依赖 adb、不依赖 root），连接 / 设备信息 / 分区表 / 刷入镜像 / 擦除 / 重启 / EDL
- **本机 root**：`su` + `dd` 备份与刷写分区，基带批量导出（modem / modemst1 / modemst2 / persist / fsg / fsc / efs / nvram）
- **遥控 PC**：HTTP 接口
- **线刷 ROM**：选目录，解析 `flash_all.bat/.sh`，先列出全部步骤（含缺失文件提示），确认后逐条执行，日志实时输出
- **壁纸**：自定义图片，等比铺满并压暗，作为玻璃的取样源

## 修复

- `NoClassDefFoundError: Landroidx/compose/material/icons/Icons$Filled;`
  `material-icons-core` 只是 material3 的**编译期**传递依赖，没有打进 APK。已显式声明依赖。
- 底栏拖动只滑动不切页：`pointerInput` 的闭包只在首次组合创建，捕获的 `page` 永远是 0，导致 `target != page` 判断失效。改为无条件回调。
- 长按底栏出现灰色块：`clickable` 的水波纹，已关掉。

## 构建

```bash
cd android-compose
# 需要 JDK 17、Android SDK Platform 37 / Build-Tools 37.0.0、Gradle 9.3.1
EASYPT_KEYSTORE=/path/to/your.keystore gradle :app:assembleRelease
```

不设 `EASYPT_KEYSTORE` 也能编译，只是出未签名包。

---
# Easy-platform-tools-GUI v2.16.0 — 修好原生/Material 的按键透明度；AGSL 折射先关掉

## 1. 按键透明度在原生 / Material 下失效（已修）

根因在 `PillDrawable`：

```java
fillPaint.setColor(fillColor);     // fillColor 自带 alpha（比如 20%）
fillPaint.setAlpha(alpha);         // alpha 字段是 255 → 把颜色的 alpha 覆盖成不透明
```

`Paint.setAlpha()` 不是"相乘"，而是**直接改写颜色的 alpha 通道**。
所以原生和 Material（都走 `PillDrawable`）无论把滑杆拖到多少都是 100% 不透明；
液态玻璃走的是另一条绘制路径，才看起来正常。

现在改成显式计算：

```java
int a = ((fillColor >>> 24) * alpha) / 255;
fillPaint.setColor((fillColor & 0x00FFFFFF) | (a << 24));
```

顺带去掉了 Material 的 `Math.max(btnAlpha, 88)` 下限（之前 88% 以下拖不动也是同一类问题）。

## 2. 关于用 AndroidLiquidGlass

[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 是 **Compose Multiplatform** 库
（Gradle + Compose + AGSL + RenderEffect，Apache-2.0）。本项目是 `javac` + `aapt2` + `d8` 手搓的纯 View 工程，
**没法直接引它的依赖**——引进来等于把整个 Compose 运行时塞进来（十几 MB，还要改构建脚本）。

所以我做的是**移植它的 AGSL 片段**：

- 新增 `LiquidGlassShader.java`，内含该项目的 `RoundedRectRefractionShaderString`
  （保留其 Apache-2.0 声明，已注明出处链接）
- 通过 `RuntimeShader` + `BitmapShader` 输入，对预模糊壁纸做边缘折射
- 低版本或驱动不支持时自动退回位图模糊

**但默认是关的**（`LiquidGlassShader.ENABLED = false`）。原因：在你的手机上试了几版，
着色器的采样对齐一直不对（全糊成一块灰、或者折射范围过大），我没法在这种调试环境下把它调到可用的观感。
与其发一个半成品，不如先关掉、保持现在稳定的模糊玻璃。

想试的话把 `ENABLED` 改成 `true` 重新编译即可；需要我继续调也可以说。

## 下载

| 文件 | 说明 |
|---|---|
| `EasyPT.apk` | Android 2.16.0，64 KB，minSdk 26（Android 8.0+），versionCode 20 |

## 已验证 / 未验证

手机上验证：原生样式下把透明度拖到 20% 附近，按钮确实变成半透明、能看到壁纸；
液态玻璃样式仍是预模糊玻璃。

## 已知取舍

- AGSL 折射未启用（见上）
- `PillDrawable` 的按下高亮也改成了正确的 alpha 叠加

## 许可

本项目代码 MIT。
`LiquidGlassShader.java` 内的 AGSL 片段来自 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（Apache-2.0）。
