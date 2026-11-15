# 语光 · Lumo 视觉重设计（UI / UE / UX）

> 一句话：把「白色卡片 + 靛紫按钮」的通用聊天界面，换成**可翻动的中国山水画册**——
> 每一页挂一幅公版古画作背景，正文落在磨砂宣纸（玻璃）上，动作只用墨、印、云三种语言。

- 改动范围：`app/src/main/java/com/yuguang/lumo/ui/**`、`MainActivity.kt`、`res/values{,-en}/strings*.xml`、新增 `res/drawable-nodpi/` 16 张背景、`tools/build_backdrops.py`、新增 1 个纯 JVM 测试类。
- 未动：`data/`、`domain/`、`di/`、ViewModel 逻辑、Room schema、`build.gradle.kts`、`AndroidManifest.xml`。
- 依赖与权限：**零新增**（动画全部用 Compose 自带 `androidx.compose.animation`；图片加载复用已有的 Coil 2.7.0）。

---

## 1. 设计语言

### 1.1 色板（只换值，不改字段名）

`AiColors` 的字段名被 18 个文件直接引用，所以改造采取「字段名保留、色值整体替换」，
Material3 的颜色槽（primary/background/surface/outline…）也同步指向这套色。

| 语义 | 旧值 | 新值 | 用途 |
| --- | --- | --- | --- |
| 石青（Indigo） | `#5B6CFF` | `#35626E` | 主行动、选中态、链接、图表「输入」 |
| 石青淡（IndigoLight） | `#7C87FF` | `#4E8791` | 次级强调、开关轨道 |
| 石青雾（IndigoSoft） | `#EDEFFF` | `#DDE8E2` | 选中胶囊底 |
| 朱砂（Coral） | `#FF7A59` | `#B23A2E` | 印章、发送、录音、图表「输出」 |
| 鎏金（Warning） | `#FFB86B` | `#B08A4A` | 刻度、金线、货币 |
| 苔绿（Success） | `#34D399` | `#4E8C6A` | 成功态 |
| 宣纸（LightBackground） | `#F6F7FB` | `#F2EADA` | 浅色纸底 / 蒙版色 |
| 纸面（LightSurface） | `#FFFFFF` | `#FAF5EA` | 浅色卡面 |
| 墨（LightInk） | `#1C2030` | `#232A26` | 正文 |
| 夜墨（DarkBackground） | `#0B1220` | `#0F1513` | 夜间纸底 |
| 夜纸（DarkSurface） | `#141D33` | `#16201C` | 夜间卡面 |
| 月白（DarkInk） | `#E9EDFB` | `#EFE7D6` | 夜间正文 |

夜间 `primary` 从薄荷感很重的 `#8FBFB2`（G>B）改为偏蓝的 **`#7FA9B0`**：M3 的 `Button`、
图表图例都用它做大面积填充，原来的绿味在深色纸面上会读成一整条薄荷绿实条。

### 1.2 字体

- 标题族（`displaySmall`/`headlineSmall`/`titleLarge`）走 `FontFamily.Serif`——设备上就是
  `NotoSerifCJK`，即宋刻感，字距 1–1.6sp。
- 正文保持系统无衬线（小字可读性优先）。
- 两个专用样式：`LumoType.poem`（衬线 18sp / 行高 32sp / 字距 4sp，竖排诗句）、
  `LumoType.seal`（衬线 11sp，印章与题款）。

### 1.3 材质：玻璃 = 磨砂宣纸

`ui/components/Glass.kt` 提供 `Modifier.glass(shape, tone, level)`：纸色半透明底 + 一道
自上而下的 1dp 高光边 + 墨色环境阴影。`level` 0→1 表示「薄纱→厚纸」。

- 约定（写进 `ContrastTest`）：**次级小字必须落在 `level ≥ 0.8` 的卡上**，否则在浅色
  纸底上只有 3.0–3.3:1。
- `GlassCard` / `GlassChip` / `GlassTopBar` / `SealedSectionTitle` 是它的四个成品。

### 1.4 印章与墨线

`ui/components/Ink.kt`：

| 组件 | 作用 |
| --- | --- |
| `SealBadge(text, size)` | 朱砂方块 + 印白一个字（語光、墨、朱、设/听/山/数/今/印） |
| `BreathingSeal(text)` | 2.4s 呼吸的印章，用于「正在等待」 |
| `InkDots` | 三滴墨依次晕开，替代「正在思考」三个点 |
| `InkRipple` | 落笔扩散涟漪（发送、首页艺术卡） |
| `PoemLine(text, vertical)` | 竖排诗句，字距 4sp |
| `InkDivider` | 淡墨细线 |

`ui/theme/LumoIcons.kt` 另画了 11 枚墨线图标（`Chat`/`Translate`/`Voice`/`Settings`/
`Backup`/`Arena`/`Realtime`/`TextLines`/`Picture`/`Face`/`Search`），替换 M3 实心图标。

---

## 2. 背景系统

### 2.1 八幅山水（CC0 公版）

| Scene | 作品 | 出处编号 | 主要用在哪 |
| --- | --- | --- | --- |
| `Cloud` 云山 | Lonely Retreat Overlooking a Misty Valley | CMA 1961.88 | 会话列表（默认/每日轮换） |
| `Stream` 溪山 | Looking for a Monastery in the Misty Mountains | CMA 1979.28 | 对话页 |
| `WindBamboo` 风竹 | Bamboo in the Wind | CMA 1953.246 | 翻译 |
| `Falls` 飞瀑 | Waterfall | CMA 1972.157 | 语音（可手选） |
| `SnowBamboo` 雪竹 | Bamboo in Snow | CMA 2023.161 | 设置 |
| `Peaks` 千岩 | The Mountain of Five Cataracts | CMA 1966.366 | 擂台 |
| `Winter` 寒林 | Old Trees by a Wintry Brook | CMA 1997.102 | 设置分区/统计 |
| `Qinglu` 青绿 | Xu Jingyang Moving His Family | CMA 1961.90 | 语音（默认）、引导页 |

全部来自克利夫兰艺术博物馆 Open Access（CC0），每幅 720×1584 日用/夜用两张，
共 16 张 WebP q78，**合计 649 KB**。出处、许可与复现步骤见
[`docs/BACKGROUNDS-CREDITS.md`](BACKGROUNDS-CREDITS.md) 与 `tools/build_backdrops.py`。

### 2.2 分层合成（`ui/backdrop/SceneBackdrop.kt`）

```
z0 纸色底 → z1 画作（Coil 解码，Ken Burns 26s，scale 1.0↔1.09，漂移 ±12dp）
          → z2 三带云雾（40s/68s/44s，alpha 0.04–0.07，反向漂移）
          → z3 天光晕（浅色 0.16 / 深色 0.10）
          → z4 六段蒙版（护住状态栏与底部输入条，中部只留薄纱）
```

- 蒙版采用「轻全局 + 局部加重」：浅色 top 40% / mid 8% / bottom 50%，深色 40% / 10% / 52%。
  第一版是 56/19/60，真机上把山水压成了灰纸，第二轮整体减淡约 30%。
- 图片用 `ImageRequest.size(1080, 2376).allowHardware(true).crossfade(false)` 加载，
  避免 16 张位图常驻内存；切换页面时 1400ms 交叉淡入（`CrossfadeScene`）。
- `SceneryCatalog`：按路由映射场景；首页在开启「每日换景」时按
  `SceneryCatalog.daily(epochDay)` 轮换 8 幅；用户在「外观 → 山水」里手选则全局覆盖。

### 2.3 素材管线的两个教训（写进脚本注释）

1. **WebP q78 会把半径 2 的高频几乎全部量化掉**：内存图 Laplacian 方差 11.6，存盘再解码只剩 4.0，
   于是「指标达标、真机一片灰」。所以 `render_final()` 的流程是「渲染 → 存盘 → 解码回来量 → 不够就重来」。
2. 补偿锐化半径必须 ≥4 才在 q78 里留得下来（半径 2 连加两次只从 3.71 到 3.91，半径 4 两次到 6.64）。

最终 16 张编码后实测：日景亮度 185.9–188.5、夜景 48.3–52.3；日景 lap4s 3.22–4.45、夜景 2.73–3.17。

---

## 3. 动效系统（`ui/theme/Motion.kt`）

| 名称 | 规格 | 出现处 |
| --- | --- | --- |
| `sceneFadeMillis` | 1400ms | 换景交叉淡入 |
| `kenBurnsMillis` | 26000ms | 背景极缓推近 |
| `mistMillis*` | 40–70s | 云雾横向漂移 |
| `navEnterMillis` / `navExitMillis` | 220 / 180ms | 页面转场（fade + scale 0.985 + 12dp 位移） |
| `staggerMillis` | 40ms | 列表逐项浮现 |
| `inkRippleMillis` | 400ms | 墨点涟漪 |
| `thinkingMillis` | 1200ms | 三滴墨「正在思考」 |
| `sealBreathMillis` | 2400ms | 印章呼吸、同传舞台 |
| `chartBarMillis` + `chartBarStagger` | 500 + 60ms | 统计柱状图逐柱生长 |
| 按压 | `spring(dampingRatio 0.55)` 缩到 0.96 | 所有主行动 |

「行云流水」开关（外观页）→ `UiPreferences.motion` → `LocalMotionEnabled`；
关闭后所有 `rememberInfiniteTransition` 不创建，一次性动画直接落终态（无动画但有结果）。

---

## 4. 逐页改造

| 页面 | 主要变化 |
| --- | --- |
| 会话列表 | 去掉「对话」大标题与 FAB：`語光` 方印 + 每日轮换诗句 + 日期 + 「我」玻璃圆点；搜索收成 34dp 放大镜；5 个等宽快捷块换成 4 张艺术卡（新对话/翻译/语音/擂台，卡面直接取场景日/夜图）；会话行改玻璃卡 + 40dp 场景缩略 + 40ms 错峰浮现；预览文字剥掉 Markdown 记号 |
| 对话 | `GlassTopBar`；用户气泡朱砂淡玻璃、助手宣纸玻璃；头像换「墨」「朱」方印；「正在思考」换墨滴；输入条两态（折叠 56dp 单行 / 写作多行），发送是朱砂圆印 + 涟漪；空态竖排诗句 + 玻璃 chip |
| 翻译 | 四段模式从等宽分段改成**可横滑玻璃 chip**（22dp 墨线图标 + 13sp 标签，选中朱砂描边 + 底印）；语言行与音色/模型合并成**一张 `LanguageCard`**（中文 ⇄ 英语 + 竖线 + 参数 pill）；同传舞台改 `weight(1f)` 自适应（< 380dp 自动收成只剩「听」印）；「上次」记录从裸文字变成玻璃卡（译文首行 + 时间 + 40dp 播放钮）；主操作贴底 + `navigationBarsPadding`（同传 56dp 墨押按钮、文本按住说话同基线）；文本模式原文/译文合成**一张双栏 `TextWorkCard`** + 5 颗 48dp 触达的操作图标；面对面/图片页 Surface 全部玻璃化 |
| 语音 | 固定青绿山水 + 0.16 压暗层；中央「光球」重画为宣纸圆盘 + 朱砂环 + 9 道墨线音量条 + 衬线模型名；三个玻璃 chip |
| 擂台 | 透明 Scaffold + 雪竹/千岩背景；模型卡、提示区、历史全部玻璃化；「开战」改墨押按钮（原本是 M3 实心 Button，深色下会变成一条薄荷绿） |
| 设置 | 主页从 5 组 12 行压到 **4 组 7 行 + 1 条独立行**：密钥三行共用一条组内说明、语音三项合成一条（去 `VoicePrefs` 页）、外观一条（主题 · 场景）、数据两条、关于一条（版本号右对齐）；`用量统计` 从列表行提到标题栏图标；二级页重排为「备份 → 维护 → 危险操作」三段，危险项朱砂描边 + 二次确认；音色改 chip 组、引擎改自绘 `SegmentPicker`；每行 `heightIn(min = 48.dp)`、value 单行省略 |
| 外观 | 「山水」画廊：随页面 + 8 张场景卡（内嵌纸片题款）、行云流水开关、每日换景开关、明暗三段 |
| 统计 | 玻璃卡 + 石青/朱砂/鎏金三色图例与逐柱生长；说明文字垫纸卡保证 4.5:1 |
| 引导页 | 三页横滑（青绿→云山→千岩），画心 64% + 纸片承载正文，「启程」朱砂印居中，页码点改短笔，一字印落诗句末字下方 |

外壳：`AiApp` 的 Scaffold 透明，背景挂在 NavHost 下层；底部导航改悬浮玻璃药丸（16dp 外边距 +
`navigationBarsPadding`），选中项放大 1.12 并加朱砂墨点；`MainActivity` 保留 `enableEdgeToEdge()`，
按主题设置状态栏图标明暗，并在根部 `CompositionLocalProvider(LocalMotionEnabled provides …)`。

---

## 5. 可读性与无障碍

`app/src/test/java/com/yuguang/lumo/ContrastTest.kt`（9 例）用**三层合成**（画作 → 蒙版 → 玻璃）
计算真实对比度，而不是只比两个纯色：

| 组合 | 实测 |
| --- | --- |
| 浅色 墨 / 纸 | 13.50 |
| 浅色 次级 / 纸 | 6.94 |
| 浅色 墨 / 最暗场景上的 level-1 卡 | ≥4.5 |
| 深色 月白 / 夜墨 | 13.58 |
| 深色 次级 / 夜墨 | 7.87 |
| 朱砂 / 印白 | 5.54 |

- 所有图标控件都带 `contentDescription`（有文字的按钮用 `Role.Button` 语义）；纯装饰的画作与云雾
  显式 `contentDescription = null`，避免 TalkBack 念一串「图片」。
- 动效可关；关掉后不创建无限动画，省电且不晕。

---

## 6. 体积与性能

- APK：**17,109,203 bytes（16.32 MiB）**，其中背景素材 649 KB（16 张 WebP）。
- 依赖与权限：0 新增；反而**删掉**了 `androidx.compose.material:material-icons-extended`
  与 `compose ui-tooling`（详见第 11 节），debug APK 从 24,066,363 B 缩到 17,109,203 B。
- 背景用硬件位图 + 固定解码尺寸，一屏只持有一张；切换页面走 1400ms 交叉淡入而不是重建。
- 真机（PHZ110 / Android 16 / 1080×2376）滚动手感见 `adb shell dumpsys gfxinfo` 记录；
  动效关闭时无 `rememberInfiniteTransition`。

---

## 7. 验证记录

| 项目 | 结果 |
| --- | --- |
| `:app:clean :app:assembleDebug :app:testDebugUnitTest --offline` | BUILD SUCCESSFUL，51 tasks（1m 48s） |
| 单元测试 | 25 suites / 194 tests / 0 failures |
| 真机安装 + 冷启动 | 通过，无 FATAL EXCEPTION / ANR |
| 逐页截图（日/夜） | `docs/smoke/final-day-*.png`、`final-night-*.png` |
| 引导页（首次启动） | `docs/smoke/onbL2-1.png`、`onbL2-3.png` |
| 擂台 / 统计 | `docs/smoke/extra-*-arena.png`、`extra-*-stats.png` |
| 翻译 / 设置二次改版 | `docs/smoke/v3day-translate.png`、`v3day-translate-text.png`、`v3day-settings.png`、`v3night-settings.png` |

截图脚本：`docs/ui-shots.ps1`（七页标准镜头）、`tools/capture_extra.py`（擂台/统计等文本定位点击）。

---

## 8. 工程结构

```
ui/theme/
  AiTheme.kt      色板 / 字体 / 形状 / LumoPalette / LocalLumoPalette
  Motion.kt       LumoMotion 规格表 + LocalMotionEnabled
  Scenery.kt      enum Scene + SceneryCatalog（路由映射、每日轮换）
  LumoIcons.kt    11 枚墨线图标
ui/backdrop/
  SceneBackdrop.kt  五层合成（画作 / 云雾 / 光晕 / 蒙版 / Ken Burns）
  BackdropHost.kt   BackdropHost(scene) { … } + LocalScene + CrossfadeScene
ui/components/
  Glass.kt        Modifier.glass / GlassCard / GlassChip / GlassTopBar / SealedSectionTitle
  Ink.kt          InkRipple / InkDots / BreathingSeal / SealBadge / PoemLine / InkDivider
  Motion.kt       AnimatedEntrance / PressScale / LumoNavTransitions
  StateViews.kt   EmptyState / ErrorCard（签名不变）+ LoadingState
ui/prefs/UiPreferences.kt  + scene / dailyScene / motion 三个偏好
```

页面侧契约：`BackdropHost(scene = SceneryCatalog.forRoute(route)) { … }`，页面自己
`statusBarsPadding()`；正文必须落在 `glass()` 之上（中部蒙版只有 8%/10%）。

---

## 9. 已知取舍

- **Markdown 头号标题只做「去井号 + 加粗」**：内置渲染器是轻量实现（粗体/行内代码/链接/列表/代码块），
  这次补了块级记号收敛（`### `、`> `、`---`、`- `），但没做完整 CommonMark。
- **浅色 level-0 卡片上的次级小字只有 4.46:1**（差 0.04），所以约定「次级字必须落 level ≥ 0.8」；
  如果后续要放宽，得先把纸色提亮或把次级墨色加深。
- **`Falls`（飞瀑）是 8 幅里最弱的一幅**：绢丝网格有轻微浮雕感，已从语音页默认景换到青绿，
  仍保留在「外观」画廊里可选。
- 背景素材是 720×1584，在 1080p 屏上是放大 1.5 倍显示；靠 Ken Burns 与柔焦蒙版掩盖，
  1:1 放大看得到插值痕迹（`docs/smoke/backdrops-crop-v2.png` 有 1:1 对比）。

---

## 10. 应用图标

`tools/build_icon.py` 一个脚本出全套，参数与配色都对齐 `ui/theme/AiTheme.kt` 的 `LumoPalette`：

| 元素 | 取值 |
|---|---|
| 纸底 | `#F7F0E1 → #EADFC8` 竖直渐变 + 10% 细噪点（纸纹） |
| 印面 | `#C0412F → #A32E24` 竖直渐变，圆角 8.5%，叠印泥噪点 |
| 界格 | 内收 10.5% 的圆角细边，印白 @47% |
| 印文 | NotoSerifSC 变体字重 640，「光」占印面 62%，印白 `#FFF6E9` |

- 自适应图标用 108dp 画布、印章 60dp 居中（可见区约 72dp，圆角吸收四角裁切）；
  `monochrome` 层只保留「印框轮廓 + 字」，交给系统染成 Material You 的主题色。
- 老 launcher 用 `mipmap-*/ic_launcher(.webp|_round.webp)` 5 档位图（48/72/96/144/192px），
  方图圆角 22.5%，圆图直接裁圆。
- 自适应三件套是 **WebP q92**（同样的渐变+噪点用 PNG 要 88KB/张，WebP 后全套 72KB）。
- 重出：`python tools/build_icon.py`（`--preview` 只写预览到 `C:/tmp/icon`），
  最后会顺手删掉旧的 `drawable/ic_launcher_{background,foreground,monochrome}.xml`，
  避免与位图同资源名冲突。

---

## 11. 第二轮：翻译 / 设置重排与瘦身

这一轮只做三件事：翻译页更「顺手」、设置页更「短」、APK 更「小」。

### 11.1 翻译页（`ui/translate/**`）

| 改动 | 为什么 |
|---|---|
| 四段模式从「等宽分段 + 文字混排」改成 44dp 玻璃 chip 横滑（22dp 墨线图标 + 13sp 标签） | 分段控件占满整宽却只有 4 个选项，视线要横跨全屏；chip 让选中态用朱砂描边 + 底印，和全站语言一致 |
| 语言行 + 独立「音色 · 女声」两行（≈96dp + 一条悬空链接）合并成一张 `LanguageCard` | 「中文 / 英语 / 音色」本来就是同一件事的三个面，拆两行等于让用户多扫一次 |
| 同传舞台 `heightIn(min = 420.dp)` 改 `weight(1f)`，`compact = maxHeight < 380.dp` | 固定 420dp 在窄屏留出约 600px 空白；自适应后墨圈 180→132dp、印 64→52dp，键盘弹起也只留印 |
| 「上次」从裸字一行变成玻璃卡（译文首行 + 时间 + 40dp 播放钮） | 上一句译文是最高频的回看对象，值得一个可点区域 |
| 主按钮贴底 + `navigationBarsPadding()`，上方加 12sp 状态药丸（连接中 / 正在听 / 未开始） | 原来按钮在滚动列末尾，内容一多就被推走；状态从「无」变成一眼可见 |
| 文本模式原文 + 译文合成一张双栏 `TextWorkCard`，5 颗 48dp 操作图标 | 两张卡各带边距会白白吃掉 10dp×2 与两层描边；图标行统一 20dp 图形 + 48dp 触达 |
| 面对面 / 图片页的 `Surface(surfaceContainerHigh)` 全换 `glass(0.9f)` | 深色主题下 `surfaceContainerHigh` 是一块实心灰，把山水切断了 |

删除的旧实现：`ModeSegment`、`LanguageRow`、`SettingLink`、`TextSourceCard`、`TextTargetCard`。

### 11.2 设置页（`ui/settings/**`）

- 主页：5 组 12 行 → **4 组 7 行 + 1 条独立行**。密钥三行共用一句组内说明（原来三行副标题一字不差地重复），
  语音三项合成一行直接进 `VoicePrefs`，外观一行显示「主题 · 场景」，数据两行，关于一行带版本号；
  `用量统计` 从列表行提升为标题栏图标（一条入口换 48dp 高度）。
- 二级页：数据页分「备份 → 模型维护 → 危险操作」，危险操作朱砂描边 + 二次确认，三段各自 40ms 错峰浮现；
  语音页音色改玻璃 chip 组、引擎改自绘 `SegmentPicker`（避开 experimental API）；
  关于页四项（版本 / 隐私 / 素材出处 / 开源许可）合并成一屏。
- 细节：每行 `heightIn(min = 48.dp)`；右侧 value 单行 + 省略号 + `widthIn(max = 140.dp)`；
  分组 `AnimatedEntrance(index)` 40ms 递进；「清空全部长期记忆」走新增的
  `SettingsViewModel.clearAllMemories()` → `MemoryStore.clearAll()` → `DELETE FROM memories`，
  一次事务一条提示（原来是 `forEach { deleteMemory }`，删 N 条刷 N 次 snackbar）。

### 11.3 体积：24.07 MB → 17.11 MB（−28.9%）

| 步骤 | 依据 | 收益 |
|---|---|---|
| 删 `material-icons-extended` | dex 字符串里 `androidx/compose/material/icons/` 出现 45,584 次，AAR 的 `classes.jar` 未压缩就有 35.7 MB | −6.8 MB |
| 自绘 `ui/theme/LumoIcons.kt`（37 枚墨线 `ImageVector`） | 图标本来就是这套设计的一部分（统一 24dp 视口 + 1.6–2.0 描边墨线），用系统 Material 图标反而风格不搭 | 0 依赖 |
| 删 `compose ui-tooling` / `ui-tooling-preview` | 全项目 `@Preview` 0 处 | −113 KB |
| 清 40 条未引用字符串（中英各 38–40 条） | 逐键 grep `R.string.` / `@string/` | 少量 resources.arsc |
| 删 `docs/screenshots` 33 张未被文档引用的旧图 | README 只引用 9 张 | 仓库 10.4 MB |

图标映射（Material → Lumo）写在 `LumoIcons.kt` 顶部注释里；替换脚本的正则必须带 `(?<![A-Za-z])` 前缀，
否则 `LumoIcons.Voice` 里的 `Icons.` 片段会被误匹配（这一轮真的踩到了）。

### 11.4 这一轮踩到的两个坑

1. **透明 Scaffold 不提供 `LocalContentColor`**：深色主题下所有「没写 color 的 Text」都被 Compose 兜成
   `Color.Black` —— 浅色主题碰巧看不出来，深色主题就是黑字压夜色（设置页行标题对比度只有 1.2:1）。
   修法是在 `BackdropHost` 里 `CompositionLocalProvider(LocalContentColor provides onSurface)`，
   一处修完全站。
2. **`fillMaxHeight()` 在高度不受约束的 Box 里会吃满父约束**：给模式 chip 加右端渐隐提示时，
   渐隐条把整页内容盖成一张纸。窄条一定要给显式 `.height()`，或者干脆不做（最终改回不加渐隐）。

## 12. 第二套皮肤：弧光学院（Arcanum）

### 12.1 为什么要做成「皮肤」而不是「新应用」

山水那套已经把颜色、玻璃、蒙版、动效时长全部收敛成了令牌（`LumoPalette` / `Motion.kt` /
`Glass.kt`）。于是第二套皮不做分支页面，只做**同一套组件在不同令牌下的另一副面孔**：

| 层 | 山水（Lumos） | 弧光学院（Arcane） |
| --- | --- | --- |
| 纸面 | `#F2EADA` 宣纸 / 深色 `#0F1513` 夜墨 | `#EDE4D0` 羊皮纸 / 深色 `#0B1020` 午夜 |
| 玻璃卡 | `#FBF7EF` α0.76–0.82 | `#F8F2E3` α0.76–0.82（深色 `#1A2138` α0.62–0.80） |
| 主色 | 石青 `#35626E` | 星蓝 `#4B4E7A`（深色 `#9AA6DE`） |
| 强调 | 朱砂 `#B23A2E` | 余烬 `#A8402C`（深色 `#D2643F`） |
| 装饰 | 朱砂方印 + 墨线 + 墨滴 | 古金菱徽 + 金线 + 星火 + 符文环 |
| 环境层 | 三条云雾带 + 左上暖光 | 36 颗星 + 极光带 + 塔顶一盏灯 |

实现落点：
- `ui/theme/Skin.kt`：`enum Skin { Lumos, Arcane }` + `LocalSkin`（`staticCompositionLocalOf`）。
- `ui/theme/AiTheme.kt`：`AiTheme(darkTheme, skin)` 按 2×2 选 **4 套** M3 scheme 与 4 套 `LumoPalette`；
  `LumoPalette` 新增 `glow`（环境光）与 `spark`（粒子）两枚字段。
- `UiPreferences`：键 `skin`，默认 `Lumos`；`MainActivity` 把它喂给 `AiTheme`，并在 `setContent` 之前
  调 `IconSwitcher.ensure(...)` 自愈桌面图标。
- `ui/theme/Scenery.kt`：`Scene` 带 `skin` 字段，两套各 8 幅；`SceneryCatalog.forRoute/daily/primary/
  heroFor/byId` 全部接受 `skin` 参数，手选的场景 id 只在自己皮肤内有效。

### 12.2 动效：同一组件，两套「材质」

| 组件 | 山水 | 弧光 |
| --- | --- | --- |
| `InkRipple` | 墨点涟漪 400ms | 星火迸散 420ms + 1dp 金环（10 颗火星，角度按下标推） |
| `InkDots` | 三墨滴上下浮动 | 三颗星火亮度呼吸（`TwinkleDots`，不位移） |
| `BreathingSeal` | 朱砂方印 2.4s 呼吸 | 外圈符文环 12s 旋转 + 内芯金菱徽呼吸 |
| `SealBadge` | 18dp 朱砂方印写字 | 金→朱砂渐变菱徽 + 中心四芒星（不写字） |
| `InkDivider` | 1dp 水平渐隐墨线 | 金色渐隐细线 + 中心 3.4dp 菱点 |
| `MistLayer` | 三条云雾带 44–68s | `StarfieldLayer`：36 颗星（1.6–3.2s 不同周期）+ 9s 极光带 |
| `HaloLayer` | 左上暖光 | 光移到塔顶 `(0.62w, 0.14h)`，半径 1.05w |
| 页面转场 | fade + slide 1/16 + scale .985 | fade + slide 1/16 + scale **1.012**（更「推近」） |
| 换皮肤 | — | `SkinSwitchVeil` 600ms 星尘斜扫（首次组合不播，避免启动时闪一下） |

`Motion.kt` 新增：`sparkMillis=420`、`runeRotateMillis=12000`、`starTwinkleMin/Max=1600/3200`、
`auroraSweepMillis=9000`、`veilSweepMillis=600`。所有新动效都受「行云流水」总开关控制，关闭即静止终态。

### 12.3 桌面图标跟着换

Android 没有「运行时改应用图标」的 API，标准做法是 `activity-alias`：

- `AndroidManifest.xml` 里 `MainActivity` 只留分享/`PROCESS_TEXT` 过滤器；
  启动入口拆成 `.LauncherLumos`（默认启用，宣纸朱砂印）与 `.LauncherArcane`（默认禁用，午夜蓝金八芒星）。
- `ui/theme/IconSwitcher.kt`：`apply()` 先启用目标再禁用另一个（`DONT_KILL_APP`，不杀进程）；
  `ensure()` 在每次启动时对账，**防止两个都被禁用导致应用从桌面消失**。
- 图标资源由 `tools/build_icon.py --skin arcane` 生成整套 27 个文件（mipmap 5 档 + 自适应三件套 + 2 个
  anydpi XML），合计 78 KB；同脚本 `--skin lumo` 的输出与历史产物**逐字节一致**（md5 26 个文件全等）。
- 换皮之后系统桌面可能仍在用旧缓存：设置页会 toast 一句「已换成「弧光学院」风格…回桌面滑一下就好」。

### 12.4 可读性守门

`ContrastTest` 从 9 条扩到 14 条：新增弧光浅/深「墨纸」「次级」「正文叠最亮/最暗场景（含蒙版与玻璃）」
「次级必须落在 level ≥ 0.8 的卡上」「余烬与古金的使用边界」。
两条由此得出的硬规矩：

1. **古金在浅色纸上只有 2.49:1** —— 它只做图形（菱徽、星点、细线），永远不做正文。
2. **弧光深色的次级字在 level 0 玻璃上只有 3.02:1** —— 次级文案必须落在 level ≥ 0.8 的卡上，
   与山水皮肤同一条约定（深色次级色因此从 `#A8ABC6` 提到 `#B0B3CC`，level 1 时 4.82:1）。

### 12.5 体积与素材

- 弧光 16 张背景（720×1584 WebP q78）合计 **251,306 B = 245.4 KB**，单张最大 21,184 B（`bg_arcane_nebula_night.webp`）、最小 11,308 B；山水 16 张 649 KB；
  画质存档（子任务实测，**lap = 全分辨率 4 邻域拉普拉斯方差，不预模糊**）：day luma 190.2–190.8 / lap 50.8–251.9；night luma 44.6 / lap 30.6–96.1；中央 60% 高对比硬边覆盖率 ≤ 0.069%。逐张数据见 `docs/smoke/magic-metrics.txt`；
  两套加起来仍是**不到 1 MB**的背景预算。
- 全部由 `tools/build_magic_backdrops.py` 逐像素绘制（Pillow + numpy），没有任何第三方图像、
  没有影视/游戏素材；魔法学院意象（尖塔、烛火、星图、禁林、药水、浮空书）是自己画的原创图形，
  与任何 IP 的商标、角色、校徽无关。重建命令与指标见 `docs/BACKGROUNDS-CREDITS.md` 的同名小节。
- 干净构建 APK：皮肤系统落地后 **17,577,482 B**（16.76 MiB），比上一轮 17,171,975 B 增加约 0.4 MB
  （16 张背景 245 KB + 图标 78 KB + 少量代码），仍低于 18.5 MB 的预算线。

### 12.6 验收记录（真机 PHZ110 / Android 16 / 1080×2376）

| 项 | 结果 |
| --- | --- |
| 两皮肤 × 日夜全页 | `docs/smoke/arc-light-*.png`、`arc-night-*.png`（home / chat / translate / voice / settings / appearance） |
| 皮肤切换 | 外观 → 皮肤 → 弧光学院：色板、菱徽、金线、环境层、场景画廊、开关轨道、状态色同时切换 |
| 图标切换 | `cmd package resolve-activity` 返回 `.LauncherArcane`，桌面抽屉里显示午夜蓝金图标（`docs/smoke/arc-launcher.png`） |
| 引导页跟随皮肤 | `docs/smoke/arc-onb-1.png`（城堡 + 「拾星为烛，照我长夜」）、`arc-onb-2/3.png` |
| 文案跟随皮肤 | 题跋、首页一字印、外观分区标题（山水 ↔ 幻境）、动效说明、关于页素材出处 |
| 单测 | 26 suites / **211 tests** / 0 failures（新增 `SkinTest` 12 条 + `ContrastTest` 5 条，`SceneryCatalogTest` 改写为两皮肤 16 幅） |
| 零新依赖 / 权限 | 未改 gradle 依赖、未改 Manifest 权限 |
