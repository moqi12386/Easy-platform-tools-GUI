# Easy-platform-tools-GUI v2.0.0 — 自定义壁纸

两端都能把任意图片设成界面背景。

## 桌面版

- 设置 → **背景图** → 选择图片…，支持 jpg / png / bmp
- 图片按窗口大小等比铺满，自动压暗保证文字可读
- 侧栏 / 顶栏 / 底栏改为自绘面板，**按窗体坐标对齐贴同一张图**，看上去是一整张背景
- 存进 `config.ini` 的 `wall=`，下次启动自动加载；点「移除」恢复纯色

![壁纸](https://github.com/moqi12386/Easy-platform-tools-GUI/raw/main/docs/wallpaper.png)

## Android 版

- 设置 → **背景图** → 选择图片…
- 进入裁剪界面：**单指拖动、双指缩放**，框内部分就是最终壁纸
- 确认后裁好的图存到应用私有目录，重启自动加载；「清除」恢复纯色
- 设了壁纸后，日志区自动变成半透明，背景从下面透出来

## 其它

- 两版版本号统一到 2.0.0

## 下载

| 文件 | 说明 |
|---|---|
| `EasyPT.apk` | Android 2.0.0，44 KB，minSdk 26 |
| `Easy-platform-tools-GUI-v2.0.0.zip` | Windows 桌面版 |
| `Easy-platform-tools-GUI.exe` | 桌面版单文件，107 KB |

## 说明

桌面版壁纸已在 Windows 上截图验证；Android 版裁剪界面按逻辑实现，
同样没有真机对照（开发机没有测试设备）。

## 许可

代码 MIT。platform-tools、MiSans、JetBrains Mono 遵循各自许可，不随本仓库分发。
