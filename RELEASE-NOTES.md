# Easy-platform-tools-GUI v1.6.0 — 中英文切换

界面上所有文案都做了英文版，左下角设置里一键切换。

## 新增

- **语言切换**：设置窗里新增「语言 / Language」下拉，中文 ⇄ English，切换后程序自动重启生效
- 选择写入 `config.ini` 的 `lang=` 项，下次启动直接按该语言显示
- 翻译覆盖：主窗口、设置、首次启动向导、读分区表、刷入镜像、线刷 ROM、备份基带
  的全部按钮、标签、状态栏、确认弹窗与提示
- 未收录的文案会原样显示中文，不会出现空白或异常

## 截图

| 中文 | English |
|---|---|
| ![zh](https://github.com/moqi12386/Easy-platform-tools-GUI/raw/main/docs/main.png) | ![en](https://github.com/moqi12386/Easy-platform-tools-GUI/raw/main/docs/main-en.png) |

## 实现方式

`src/Lang.cs` 是一张独立翻译表：精确匹配处理静态文案（按钮、标签），
正则模板处理带变量的消息（`── 退出码 0 ──` → `── exit code 0 ──`）。
主程序逻辑没有因为多语言改动，只是把显示层的文字过一遍 `Lang.Tr()`。

## 下载

| 文件 | 说明 |
|---|---|
| `Easy-platform-tools-GUI-v1.6.0.zip` | 解压即用 |
| `Easy-platform-tools-GUI.exe` | 单文件本体，98 KB |

## 许可

代码 MIT。platform-tools、MiSans、JetBrains Mono 遵循各自许可，不随本仓库分发。
