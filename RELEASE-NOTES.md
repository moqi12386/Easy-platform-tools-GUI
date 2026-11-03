# Easy-platform-tools-GUI v2.8.0 — MIUIX 弹窗 + 底栏可拖

两件事：**弹窗换成 MIUIX 那种浮层**，**玻璃底栏能拖了**。

![MIUIX 弹窗](https://raw.githubusercontent.com/moqi12386/Easy-platform-tools-GUI/main/docs/popup.png)

## 改了什么

### 1. 弹窗全部换成 MIUIX 风格浮层

使用模式、界面样式、字号、语言，原来要么是系统 `AlertDialog` 的列表，要么是一排按钮。
现在统一成一种浮层（`showMenu`）：

| 特性 | 说明 |
|---|---|
| 锚定 | 从被点的那一行**下方**浮出，下方放不下自动翻到上方 |
| 结构 | 标题 + 副标题两行，无分隔线，只靠留白分隔 |
| 选中 | 主色文字 + 右侧 ✓ + 行底 8% 主色底 |
| 动效 | 92% 缩放 + 淡入，150 ms，锚点左上角为原点 |
| 宽度 | 300 dp（窄屏自动收），圆角 22 dp |

呼出的行也统一成「左边标题 / 右边当前值 + ›」的形状，主界面的模式切换行也是同一个组件。

四套样式各有皮：MIUIX 用 `#2C2C2E` + 蓝色主色（对着系统设置里的样式做的），
Material / 原生用主题面板色 + 1 px 细描边，玻璃是磨砂 `GlassDrawable`。

> 踩坑：点击回调里立刻 `showAtLocation`，抬起的那一下会被当成「点在浮层外」直接关掉。
> 改成 `anchor.post(...)` 下一帧再弹就正常了。

### 2. 底栏可以拖动

之前只能点 tab。现在按住滑块左右拖：

- `onInterceptTouchEvent` 里位移超过 **7 dp** 才接管，所以单击仍然走点击
- 拖动时滑块跟着手指，横向拉长到 1.14 倍
- 松手按**中线**判定，吸附到最近 tab 并播回弹动画；
  如果原地松手，会自己弹回原 tab

### 3. 顺手

- 设置页的「界面样式」卡片里，行标题改成「当前样式」，避免和卡片标题重复
- 模式切换行从 `Spinner` 换成自绘行（Spinner 的下拉框没法做成这个样式）

## 下载

| 文件 | 说明 |
|---|---|
| `EasyPT.apk` | Android 2.8.0，60 KB，minSdk 26（Android 8.0+），versionCode 12 |

桌面版这一版没动，仍是 v2.4.0。

## 已验证 / 未验证

平板（Android 15 / 1840×2944 / 350 dpi，竖屏）上验证：模式弹窗弹出与选中、样式弹窗切换、
字号弹窗、底栏左右拖动换页、拖动后滑块位置、四套样式下的弹窗皮。

**没有在手机上跑过**：300 dp 的弹窗在 1080 宽屏上占比、底部安全区够不够，需要实机看一眼。

## 已知取舍

- 弹窗没有背景变暗（`PopupWindow` 不带 dim），靠 18 dp 投影分离
- 弹窗宽度写死 300 dp，没有做「跟随锚点宽度」
- 拖动只支持水平，没有做「快速甩动」的速度判定
- 拖动过程中不预览目标页面，松手才切换

## 许可

代码 MIT。platform-tools、MiSans、JetBrains Mono 遵循各自许可，不随本仓库分发。
样式参考了 [miuix](https://github.com/compose-miuix-ui/miuix)、[Glasmio](https://github.com/NEORUAA/Glasmio)、
[liquid-glass-compose](https://github.com/muhammadalkady/liquid-glass-compose) 的设计语言，未使用其代码。
