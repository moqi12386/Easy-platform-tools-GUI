# Easy-platform-tools-GUI v3.51.1 (Android)

- **实况通知第一次不出现** —— 装好后第一次发实况通知时，SystemUI 的焦点插件还没缓存这个包，
  直接把通知吞掉（`dumpsys notification` 里连记录都没有，`startForeground()` 却返回成功；
  服务日志显示 10 次 startForeground 全部"完成"）。现在第一次先发一条普通通知把它喂熟，
  200ms 后再发真正的实况通知，之后同一会话里直接更新。
- **弹窗按钮不吃莫奈色** —— 弹窗子树里 `LocalAccent` 拿到的是启动首帧的旧值
  （深色主题下等于纯白），所以「选择 / 刷新 / 下一步 / 好 / 完成」全是白底。
  现在弹窗内按钮直读 `Palette.accent`：主按钮主色实心 + 白字，次按钮主色 16%。
- 通知通道改在 `onCreate` 就建好，不再等第一次刷机才现建。
- 跑普通 shell 命令不再弹「刷写完成」。
- `GlassButton` 的 `height` 参数之前被写死 48dp，已修。

真机验证（MIX 2S / Android 17 / HyperOS 3）：冷启动后第一次执行 `ls /sdcard`，
通知栏当帧出现「执行中 · ls /sdcard」；测试实况入口连发 11 条进度更新全部落到同一条通知；
弹窗主按钮像素值均为 `#A88028`（莫奈金）。
# Easy-platform-tools-GUI v3.50.3 (Android)

- **Material 风格底栏在深色模式下是浅色的** —— 那段底栏写在页面 `MaterialTheme` 外面，拿到的是 M3 默认浅色主题。现在给它单独套一层，`surfaceContainer` 跟卡片同色，选中药丸和图标跟着主色走
- 重出 `docs/styles.png`（原来 Material 那一格是修复前的浅色底栏）

---
# Easy-platform-tools-GUI v3.50.1

- 线刷「开始刷写」、日志放大「清空 / 关闭」、读分区表「关闭」、擦除确认、崩溃日志「关闭」——都换成液态玻璃按钮（非玻璃样式仍用原来的 Material 按钮）
- 点「开始刷写」加一条 Toast：已开始线刷，请在日志里看进度
- 去掉两行遗留的调试日志

---
# Easy-platform-tools-GUI v3.50.0 — OTG 分块刷大分区（Android 端）

上一版让 OTG 能认到 fastboot、能读分区表；这一版让 OTG **真的能把大分区刷进去**。
用 MIX 2S（polaris）真机把 1410 MB 的 `system.img` 刷完并正常开机，才算过关。

## 症状

刷 `system` / `product` 这种大分区时，界面卡在 `第 1 包数据未被确认：null`，白等 30 秒超时。
小分区（`logo` 20 MB、`misc` 8 KB）反而一次就过。

## 根因一：镜像不是 raw，是 Android sparse

`system.img` / `system_ext.img` / `product.img` / `vendor.img` 开头都是 `3aff26ed`
（sparse magic `0xED26FF3A`）。之前按字节切片，把 12 字节的 chunk header 劈成两半发出去，
设备收到的是废数据 —— 刷完起不来，只会显示 AOSP 的警告页。

## 根因二：`download:` 声明的字节数算错（决定性 bug）

`flushPiece()` 原来这样算包长：

```java
size = 28 + 12 * nChunks + body.size();   // 错
```

而 `body` 里**已经**包含了每块的 12 字节头，等于把块头算了两遍。
`download:0012xxxx` 声明的字节数比实际发出的大 `12 × 块数`，
设备发完数据后一直在等那些根本不存在的字节，30 秒后超时 → `第 1 包数据未被确认：null`。

改成先把整包拼好，再用它的真实长度去声明：

```java
java.io.ByteArrayOutputStream out = new ByteArrayOutputStream(body.size() + 64);
... 写头 + 前置 DONTCARE + body + 结尾 DONTCARE ...
byte[] all = out.toByteArray();
long size = all.length;                    // 对
```

## 做法：按块边界重打包，语义对齐 libsparse

每个包都写成一张完整的 sparse 镜像：

| 段 | 内容 |
|---|---|
| 头 | `magic/total_sz/chunk_hdr_sz/...`，`total_blks` 用**整个镜像**的块数 |
| 可选 | 前置 `DONTCARE(pieceStart)`，补上本包之前跳过的块 |
| 主体 | 若干个（必要时从原始 RAW 块里切开的）chunk |
| 可选 | 结尾 `DONTCARE(totalBlocks - curBlock)`，补到整镜像末尾 |

和 Android 官方 `libsparse` 的 `sparse_file_resparse()` 输出一致。
在 WSL 里装 `android-libsparse-dev`，用真实的 `resparse` 逐包比对过，
块数、chunk 数、`total_blks` 全部吻合。

每包上限取 `min(设备 max-download-size, 12 MB)`。
设备报的 768 MB 不是真能一次发完 —— **单次 `bulkTransfer` 超过 64 KB 会被直接拒（ret=-1）**，
所以底层固定按 16 KB 切片，并在失败时 `CLEAR_FEATURE(ENDPOINT_HALT)` 后重试一次。

## 顺带修掉

- 传输中途掉线：`reopen()` 重新扫描设备再打开，`FAIL:write` / `FAIL:timeout` 自动重发一次
- `flash:` / `erase:` / `format:` 的读超时单独放宽到 180 秒（普通 `getvar` 仍是 30 秒）
- 线刷脚本里的 `getvar product` 预检行不再真的发给设备，改由 App 从脚本里正则出机型名自己校验
- 脚本里的 `$*` / `$1` / `%*` 这类转发占位符不再被当成命令
- `format <分区>` 按 `erase` 处理
- 移除调试用的第 1 包转储

## 真机验证

| 步骤 | 结果 |
|---|---|
| `system.img` 1410 MB / 385070 块 | 拆成 **118 包**，全部 `download:` + `flash:` 成功 |
| `product.img` 4502 MB / 1225847 块 | 拆成 **376 包**，全部成功，用时 3 分 18 秒（约 23 MB/s） |
| 两次刷完各自重启 | 都正常进系统（OTG 侧厂商识别回 `Xiaomi`） |
| 对比组：官方 `resparse` 出的 2 个分片 | 也刷成功并正常开机，证明传输层本身没问题 |

---
# Easy-platform-tools-GUI v3.49.0 — OTG 直连 fastboot 修好了（双端）

「一台手机 OTG 连另一台 fastboot 手机，读不到东西」这个问题做完真机排查，是三个独立问题叠在一起。

## 1. bootloader 不认 FASTBOOT 前缀（主因）

实测 MIX 2S：标准帧 `FASTBOOTgetvar:product` 被回 `FAILunknown command`，
去掉前缀的裸帧 `getvar:product` 才回 `OKAYpolaris`。它的回包还是 `OKAY<值>` 格式，
不是标准的 `INFO...` + `OKAY`。

- 新增自动探测：标准帧被回 unknown command 时，自动换裸帧重发一次，成功后记住这个模式
- `command()` 兼容 `OKAY<值>`（把数据塞在 OKAY 后面）这种固件
- `getvar` 以前返回状态字 `OKAY`，界面上显示成 `product: OKAY`；现在返回真实值

## 2. 只认 0xFF/0x42/0x03 一种 descriptor

- 改成三轮匹配：标准 fastboot 接口 → 任意厂商自定义接口 → 任意带 bulk 进/出端点的接口
- 第三轮会跳过存储(0x08)/音频(0x01)/HID(0x03)/打印机(0x07)/集线器(0x09)/视频(0x0E)，插着 U 盘也不会误开
- 日志写明是按哪一轮命中的

## 3. 失败时没有任何可查信息

- 新增「USB 诊断」：列出每个 USB 设备的 VID:PID、接口 class、端点、授权状态；一个都没认到时直接给 4 条物理层排查建议
- 新增「协议自测」：一键试 4 种帧格式（标准 / 裸帧 / 512 补齐 / 大写），结果进日志
- 增加 USB 插入/拔出广播，插拔实时可见
- 只扫一次 → 扫 6 次（每次间隔 400ms），对方枚举慢一点也能认到

## 真机排查结论

- 两台手机 C-to-C 直连：`num_connects` 一次都不涨，`/sys/bus/usb/devices/` 始终只有根 hub。
  两边 CC 都放 Rp，协商不出合同，物理上就不可能，不是 App 的问题
- 换 OTG 转接头 + A-to-C 数据线：U 盘、MIX 2S 都能正常枚举
- MIX 2S 的 bootloader USB 会卡死，插上完全没反应，**重新进一次 fastboot 才好**
- OTG 下读分区表成功，得到 88 个分区；product/serialno/secure 与官方 fastboot 逐字一致

---# Easy-platform-tools-GUI v3.48.0 — 新手向导 + 设置页收尾

- **首次使用向导**：三页说明（三种使用模式 / 界面样式 / 遥控 PC 怎么连），液态玻璃面板，底部页码点
  - 看完写 `onboarded=true`，之后不再自动弹
  - **只有「先不看」「开始使用」能关掉**：点外面、按返回键都不关（之前的「跳过」还会被广告跳过类软件自动点掉，已改名「先不看」）
- 设置页末尾新增**「使用向导」**入口，随时能再看一遍
- 设置页末尾新增**「开源地址」**：`github.com/moqi12386/Easy-platform-tools-GUI`，点了直接开浏览器
- **Glass Playground 暂时隐藏**（代码保留）
- 字体列表：**删掉「系统字体文件」**（`/system/fonts` 扫描那套），**新增 JetBrains Mono**（和电脑端打包的同款等宽字体，多源回退下载）

## 真机验证（小米 24122RKC7C / Android 17）

- 向导弹窗：点外面 + 返回键都不关；「下一步」翻页到第 3 页变「开始使用」；点「开始使用」关闭
- 设置页：`关于 / 使用向导 / 开源地址` 三行齐全，`Glass Playground` 不见了
- 字体下拉：末尾是 `vivo Sans / JetBrains Mono（在线）/ 本地字体`，没有「系统字体文件」

---
# Easy-platform-tools-GUI v3.45.0 — 遥控连接修好了（双端）

手机端连不上电脑端的「遥控 PC」，实测定位到三个原因，全部修掉：

## 1. Android 17 的局域网权限（主因）

`targetSdk=37` 之后，系统新增了 `android.permission.ACCESS_LOCAL_NETWORK`：
**没声明这个权限，App 发往局域网（192.168.x.x）的 TCP 连接会直接超时**，
而 `adb shell curl` 不受影响 —— 所以之前看起来像「电脑端没开服务」。

- Manifest 声明该权限
- `onCreate` 里在 SDK ≥ 36 时运行时申请（需要用户点「允许」）
- 实测：授权前 `failed to connect … after 4000ms`，授权后 `遥控服务在线`

## 2. 电脑端遥控服务默认不启动

以前必须打开「手机遥控服务」弹窗手动点「启动服务」，忘点就永远连不上
（实测 8765 端口根本没在监听）。

- 新增配置 `auto=1`：**开机自动启动**遥控服务，并在日志里打出地址和配对码
- 弹窗加了「自启：开/关」按钮

## 3. 配对码不一致

电脑端 `token=801654`、手机端 `251924` → 服务端直接 401。
而且清空 token 后程序启动时又会自动生成一个，永远对不上。

- 新增「清除配对码」按钮 + 配置 `tokenoff=1`：显式清空后**不再自动生成**，服务端不校验
- 「换一个配对码」会重新生成并复位 `tokenoff`

## 4. 顺带

- `/api/partitions` 在没有 fastboot 设备时会把请求挂死 90 秒以上
  （`fastboot getvar all` 一直等设备）→ 先 `fastboot devices` 探一下，没有就立刻返回提示
- 遥控弹窗加高，按钮排布调整

## 真机验证

- 手机 `curl http://192.168.59.63:8765/api/status` → `{"android":"3692074e","fastboot":"","version":"3.45.0"}`
- App 内「遥控 PC → 测试连接」→ 日志 `遥控服务在线`
- `/api/partitions` 从 90 秒挂死变成 0 秒返回「没有读到分区（设备是否在 fastboot？）」

---
# Easy-platform-tools-GUI v3.44.0 — 删除字体要二次确认

- 点「删除」不再直接删，先弹一个**液态玻璃确认框**：标题「删除字体」+「确定删除「xxx」？文件会一起删掉。」
  两个按钮都是 demo 的 `LiquidButton`（取消是玻璃白，删除是红色）
- 原生风格下删除按钮保持中性色（不引入红色），和其它控件一致
- 新增可复用组件 `GlassConfirmDialog`（标题 / 说明 / 取消+确认，全部走 `GlassDialogPanel`）
- 确认框弹出时自动先关掉字体下拉，避免两层弹窗叠在一起
- 真机验证（MIX 2S / Android 17）：点删除→弹确认框→点「取消」文件仍在→再点删除→点「删除」
  文件真的消失（`files/custom-*.ttf` 2 → 1），列表同步少一项

---
# Easy-platform-tools-GUI v3.43.0 — 导入的字体可删除

- 「字体」下拉里，自己导入的那几项右侧多了一个**删除按钮**（液态玻璃下是 demo 的 `LiquidButton`，
  其他风格是带底色的胶囊），点一下就删掉
- 删除会同时清掉：列表项、磁盘文件、prefs 记录
- 如果删的正好是**当前正在用的**字体，自动退回「系统默认」（有多条时退到剩下最后一条），并写回 prefs
- 真机验证（MIX 2S / Android 17）：导入两条 → 两条都带删除按钮 → 删除未选中的那条，
  列表少一项且 `files/custom-*.ttf` 少一个 → 再删正在用的那条，`字体: choice=System`，界面回退系统字体

---
# Easy-platform-tools-GUI v3.42.0 — 导入的字体常驻字体列表

- **导入一次就能一直用**：以前只会覆盖同一个 `custom-font.ttf`，列表里永远只有一条「本地字体」。
  现在每次导入单独存成 `custom-<时间戳>.ttf`，**并以原文件名加进「字体」下拉**，重启后依然在
- 支持导入多个字体；同名文件再导入会替换旧条目，不会堆重复项
- 导入的条目落盘为 `prefs.customFonts = "名字|路径"`，启动时逐个用 `Typeface.createFromFile` 校验，
  解不出来的自动丢弃（不放死项）
- 老版本留下的 `custom-font.ttf` 自动迁移成一条记录
- 真机验证（MIX 2S / Android 17）：导入 `宋体测试.ttc` → 下拉出现该条目 → 全界面变宋体 →
  force-stop 重启后字体与选中态仍在；logcat `字体: choice=Local family=true`

---
# Easy-platform-tools-GUI v3.41.0 — 双端

## Windows 端

- **在线壁纸一直是同一批**：必应接口的 `idx` 写死成 `0`，永远返回同样的图。
  现在每次打开/刷新随机取 `idx=0..7`，并带 `nc=时间戳` 防缓存
- **加了「刷新」按钮**：不用关掉弹窗重开，点一下换一批
- 接口域名回退 `cn.bing.com` → `www.bing.com`（国内直连 www 常不通）
- 版本号对齐 3.41.0

## Android 端

见上一节（字体双重校验 / 字体预览行 / 系统预装字体文件 / 刷新型玻璃按钮）

---
# Easy-platform-tools-GUI v3.41.0 — 字体生效可验证 + 系统预装字体

- **字体应用后看不出差别**：判「已下载」的条件只有「文件存在且 >4 KB」，坏文件会被当成可用，
  系统创建 Typeface 失败后一路静默回退到系统字体，界面毫无变化。
  现在要求：文件头合法（`00 01 00 00` / `OTTO` / `true` / `ttcf`）**并且** `Typeface.createFromFile` 真的能解出来，
  否则视为没下载；下载完成后也做同样校验
- 设置里加**字体预览行**：`预览：Aa 汉字永字八法 123`，直接用生效的 FontFamily 渲染，一眼看出有没有生效
- 新增**「系统字体文件」**：扫描 `/system/fonts` 里能直接加载的 ttf/otf（MiLanPro / NotoSansCJK /
  NotoSerifCJK / Roboto / DroidSansMono 等），纯本地文件加载，**不用联网、零等待**
- 必应壁纸右上角「刷新」改成玻璃按钮（原来是纯文字）
- 诊断日志（logcat tag `EasyPT`）：`系统字体发现 N 个：...`、`字体: choice=… family=… loaded=[…]`

---
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

### Windows 端（v3.44.0）

- 版本号对齐 3.44.0（源码无功能改动）

---
