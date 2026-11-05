# Easy-platform-tools-GUI v3.39.0 — 在线壁纸预览修复

- **预览一直空白**：网格里每张缩略图挂的是 `_UHD.jpg`（单张约 3.7 MB），10 张同时拉 ~37 MB，
  手机上基本等于永远加载不出来。现在缩略图用 `_400x240.jpg`（约 20 KB），原图仍用 UHD
- 必应接口增加域名回退：`cn.bing.com` → `www.bing.com`（国内直连 www 常不通），
  并带上浏览器 UA；失败时把具体域名和异常写进弹窗
- **选择壁纸要下两遍**：弹窗里已经下载完成，却把 *URL* 交给上层，`setWallpaperFromUrl` 又整份重下；
  改成把本地文件路径交给新增的 `setWallpaperFromFile`，一次下载

---
# Easy-platform-tools-GUI v3.38.0 — 原生风格纯中性色 / Material 风格改用 Material Design 3

## 界面

- **Material 风格改用真正的 MD3 组件**（`androidx.compose.material3`，Apache-2.0，没有手搓仿制）：
  卡片 `ElevatedCard` + `surfaceContainerLow`、列表行 `ListItem`、
  开关行 `ListItem + Switch`、底部导航 `NavigationBar / NavigationBarItem`、标题 `typography.displaySmall`
- **原生风格完全去彩色**：新增 `LocalAccent`，原生下主色被覆盖成中性色，
  按钮 / 清空 / 开关 / 滑块 / 光标 / 选中态 / 底栏全部变中性；
  底栏底板直接复用卡片色 `#1B1F27 @ 92%`，和上面的卡片同色
- **弹窗玻璃化修复**：所有弹窗以前写在 `CompositionLocalProvider` 外面，
  `LocalWallBackdrop` / `LocalGlassAssets` 恒为 null，只能退化成纯白板；
  现在把弹窗包进同一套 Provider，日志颜色弹窗与通用选择框在液态玻璃下走真 backdrop
- 日志「清空」在液态玻璃下改成 demo 的 `LiquidButton`
- **日志文字颜色真正生效**：补上设置入口（`onLogColors` 之前只有形参、没有任何调用点，弹窗根本进不去），
  4 种语义色接进 `append()`；调色盘从固定 8 色扩到 72 色 + 「命令 / 成功 / 失败 / 普通」角色切换
- 日志框底板与「fastboot 命令」输入框统一（同一套 vibrancy + blur(8dp) + 45%/55% 底料）

## 字体

- 在线字体从 2 个扩到 6 个：MiSans / HarmonyOS Sans / OPPO Sans 4.0 /
  一加 OneSans / 荣耀 HONOR Sans CN / vivo Sans
- 每个字体 2–3 个镜像顺序回退（`raw.githubusercontent.com` → `gh-proxy.com` → `gcore.jsdelivr.net`），
  下载后校验文件头（`00 01 00 00` / `OTTO` / `true` / `ttcf`），不是字体就换下一个源
- 字体落盘到 `filesDir/font-*.ttf`，**启动时直接加载**
  （之前只在内存里，重启就退回系统字体，再点还得重新下载）
- MiSans 主源从 227 MB 官方 zip 换成 6 MB 的 OTF，官方包留作备用源
- 字体列表可滚动，「已下载 / 在线」状态分别标注

## 修复

- **重置参数后滑块不动**：demo 的 `LiquidSlider` 靠闭包读状态，传 `Float` 参数会被定格在首帧值；
  改用 `rememberUpdatedState`，外部改值也能推动滑块
- **日志不自动滚到底**：`LazyColumn` 没有 `LazyListState`，新日志追加在末尾但视口停在顶部；
  加 `scrollToItem(lastIndex)`（先 `withFrameNanos` 等一帧，否则新行还没测量会落空）
- **原生风格日间模式文字看不清**：输入框字色跟 `LocalCardText`（原生恒为白）走，而底板是浅色；
  改成跟底板走的 `Palette.text`，日志框同理
- **OTG 模式完全不可用**：`private lateinit var fb: FastbootUsb` 从来没有被赋值过，
  点「连接设备」直接抛 `lateinit property fb has not been initialized`；在 `onCreate` 里初始化
- **遥控报 `failed to connect to /`**：host 为空没校验就直连，现在先检查并提示
- **升级后样式被偷偷降级成「原生」**：覆盖安装时进程被杀会留下 boot-trace，被误判成崩溃；
  现在用 `PackageInfo.lastUpdateTime` 区分「升级后的首次启动」和真崩溃
- **底栏文字反色取错参考系**：以前用壁纸**顶部**亮度算底栏颜色（底栏在屏幕最下面）；
  现在按壁纸底部亮度叠加 40% 底料算出真实底色，再按 WCAG 相对亮度对比选黑/白字

---
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
