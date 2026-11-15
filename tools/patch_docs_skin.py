"""Append section 12 (two-skin system) to docs/UI-REDESIGN.md. Pure-ASCII source, \\uXXXX CJK."""

from pathlib import Path

p = Path("docs/UI-REDESIGN.md")
s = p.read_text(encoding="utf-8")

SECTION = """
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

- 弧光 16 张背景（720×1584 WebP q78）合计 **245 KB**，单张最大 13.7 KB；山水 16 张 649 KB；
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
"""

if "## 12. 第二套皮肤" in s:
    print("already present")
else:
    s = s.rstrip("\n") + "\n" + SECTION
    p.write_text(s, encoding="utf-8")
    print("section appended; ufffd =", p.read_bytes().count(b"\xef\xbf\xbd"))
