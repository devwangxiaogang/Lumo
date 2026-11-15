# 背景图出处与处理说明（BACKGROUNDS CREDITS）

「语光 · Lumo」的青绿山水沉浸式背景，全部取自 **克利夫兰艺术博物馆（Cleveland Museum of Art）Open Access** 的中国立轴高清图。

> **版权**：所选用作品在 CMA Open Access 下标记为 **CC0 1.0 通用（公有领域贡献）**，可用于任何用途、无需授权、无需署名。
> **用途声明**：本仓库中的 `bg_*.webp` **仅用于本应用内的背景显示**，不单独分发、不再许可、不用于任何素材转售；原作属**公有领域**（public domain），本仓库仅对裁切／柔焦／调色后的派生图主张无额外权利。

生成脚本：[`tools/build_backdrops.py`](../tools/build_backdrops.py)（Pillow，可复跑）。产物：8 场景 × 日/夜 = 16 张 `720x1584` WebP（q78，method 6）。

## 总览

| 场景 | 文件 | 作品 | 作者 | 年代 | Accession | 许可 |
| --- | --- | --- | --- | --- | --- | --- |
| `cloud` | `bg_cloud_day.webp` / `bg_cloud_night.webp` | Lonely Retreat Overlooking a Misty Valley | Sheng Maoye (Chinese, active early 1600s) | 1630 | 1961.88 | CC0 |
| `stream` | `bg_stream_day.webp` / `bg_stream_night.webp` | Looking for a Monastery in the Misty Mountains | Chen Chun (Chinese, 1483-1544) | 1500-1544 | 1979.28 | CC0 |
| `snow_bamboo` | `bg_snow_bamboo_day.webp` / `bg_snow_bamboo_night.webp` | Bamboo in Snow | Hong Fan (Chinese, active late 1700s-early 1800s) | 1818 | 2023.161 | CC0 |
| `wind_bamboo` | `bg_wind_bamboo_day.webp` / `bg_wind_bamboo_night.webp` | Bamboo in the Wind | Puming (Xuechuang) (Chinese, active before 1274-after 1329) | 1300s | 1953.246 | CC0 |
| `falls` | `bg_falls_day.webp` / `bg_falls_night.webp` | Waterfall | Unknown (Chinese) | 1271-1368 | 1972.157 | CC0 |
| `winter` | `bg_winter_day.webp` / `bg_winter_night.webp` | Old Trees by a Wintry Brook | Wen Zhengming (Chinese, 1470-1559) | 1551 | 1997.102 | CC0 |
| `peaks` | `bg_peaks_day.webp` / `bg_peaks_night.webp` | The Mountain of Five Cataracts | Chen Hongshou (Chinese, 1598/99-1652) | c. 1630 | 1966.366 | CC0 |
| `qinglu` | `bg_qinglu_day.webp` / `bg_qinglu_night.webp` | Xu Jingyang Moving His Family | Cui Zizhong (Chinese, 1574-1644) | 1644 | 1961.90 | CC0 |

## 处理流程（每一张都按此顺序）

1. **解码**：`Image.open(src).convert('RGB')`。源图为 CMA `print` 分辨率（宽 1300–2100、高 3400 px，**含完整装裱**：上下绫边、题跋、轴头）。
2. **裁掉装裱绫边**：把源图缩到 1/6，逐列／逐行统计亮度均值与标准差。绫边很平（std ≈ 2–6 且亮度稳定），画心一进入纹样 std 立刻升到 13–60；据此从四边向内走到第一条「不像绫边」的线，再向内让 2 条线吃掉抗锯齿过渡，最后四边各内缩 0.4%。检测不理想的卷子改用脚本里手写的 `crop` 相对坐标——目前只有 `falls`（上方裱有独立题跋纸，检测会把白纸当画心）。
3. **取景缩放**：按 `focus=(fx, fy)` 取景中心等比放大铺满 `720x1584`（LANCZOS），再裁到目标尺寸。竖轴上部的题跋与留白不进画面，落点放在中下部的主体山水（主峰／竹丛／水口）。
4. **柔焦**：`GaussianBlur(radius=2.6–3.1)`（720 宽下）——只压掉扫描噪点，皴法与竹叶的笔锋要留住。
5. **纸白平衡 + 自适应对比归一**：先逐通道把 96 分位（≈纸/绢本色）映射到接近中性的纸白 `(248, 246, 240)`（强度 `wb` 0.55–0.88，逐场景可调，只走一部分），陈旧绢本的黄褐底就被清掉；增益在暗部按 smoothstep 回到 1.0（`knee=96`），否则 B 通道被整体放大，深褐绢上的墨会变成蓝灰；再 `ImageOps.autocontrast(cutoff=1, preserve_tone=True)` 把原画明暗拉开（避免纸色发灰导致整幅发闷），`ImageEnhance.Contrast(1.05–1.10)` 略提；随后把底图摆进「均值 170／幅度 ≤150」的安全工作区间。
6. **色调分级**：每场景单独给参——`ImageEnhance.Color(0.60–0.84)` 降饱和，再做 `(+2..+5, +1..+2, −4..−6)` 的暖偏通道偏移；夜色改用更低的`night_color`(0.40–0.55) 与 `(−3..−4, 0..1, +8..+9)` 冷偏。石色重的 `qinglu`、绢色偏褐的 `falls` 单独压得更低。
7. **按「画作权重」混色**：`Image.blend(art, paper, 1-w)`，白日 `w=0.85–0.90`（纸色 `#F2EADA`）、夜色 `w=0.65–0.72`（夜墨 `#0F1513`）。画作占九成左右，山形／竹影／水口都看得清，只是更淡、更柔、更暖。
8. **叠垂直渐变**：白日自顶向下纸色 alpha 0.35 → 0.15；夜色夜墨 alpha 0.40 → 0.14。顶部更重，让状态栏与标题区更干净。
9. **结构补偿**：混色后再走一次 `UnsharpMask(radius=2.0, percent=58–70)`；淡墨卷子（`cloud`/`stream`/`winter`）一次锐化仍到不了结构验收线，就按阶梯升级——先放大明暗幅度（至多 ×1.42），再加`UnsharpMask(radius=4, percent=60)` 至多 4 次。半径必须放大：q78 会把半径 2 的高频几乎全量化掉，补偿锐化等于白做。
10. **暗角**：四边向内 8% 用 smoothstep 平滑衰减到 -6% 亮度，给画面一点「装裱」纵深，也让四角更安静。
11. **亮度/幅度对齐**：给每张图定目标「平均亮度 + 结构幅度（p5–p95）」——白日 平均 186–189 / 幅度 84–92，夜色 平均 49–55 / 幅度 60–66；用一条仿射 LUT（同一条作用于 R/G/B，不改色相）拟合到目标，迭代若干次并保留「离目标最近」的那一版（避免过冲）。
12. **导出**：`convert('RGB').save(path, 'WEBP', quality=78, method=6)`，单张 ≤ 150 KB。

## 逐场景明细

### `cloud` — Lonely Retreat Overlooking a Misty Valley

- **作品**：Lonely Retreat Overlooking a Misty Valley
- **作者／年代**：Sheng Maoye (Chinese, active early 1600s)；1630
- **材质**：Hanging scroll; ink and slight color on silk
- **馆藏编号（Accession）**：`1961.88`
- **来源**：Cleveland Museum of Art, Chinese Art；Mr. and Mrs. William H. Marlatt Fund
- **许可**：CC0 1.0 Universal（Open Access，公有领域贡献）
- **API 源**：https://openaccess-api.clevelandart.org/api/artworks/137120
- **图像源（print）**：https://openaccess-cdn.clevelandart.org/1961.88/1961.88_print.jpg
- **参数**：柔焦 3.0px、对比 1.06；白日 画作权重 0.86 / `color=0.78` / 目标 188+88；夜色 画作权重 0.66 / `color=0.52` / 目标 50+66
- **取景**：focus `(0.46, 0.52)`，画心 自动检测
- **产物**：`bg_cloud_day.webp`（40 KB）、`bg_cloud_night.webp`（28 KB）

### `stream` — Looking for a Monastery in the Misty Mountains

- **作品**：Looking for a Monastery in the Misty Mountains
- **作者／年代**：Chen Chun (Chinese, 1483-1544)；1500-1544
- **材质**：Hanging scroll, ink on paper
- **馆藏编号（Accession）**：`1979.28`
- **来源**：Cleveland Museum of Art, Chinese Art；John L. Severance Fund
- **许可**：CC0 1.0 Universal（Open Access，公有领域贡献）
- **API 源**：https://openaccess-api.clevelandart.org/api/artworks/149628
- **图像源（print）**：https://openaccess-cdn.clevelandart.org/1979.28/1979.28_print.jpg
- **参数**：柔焦 3.1px、对比 1.06；白日 画作权重 0.85 / `color=0.82` / 目标 187+86；夜色 画作权重 0.65 / `color=0.52` / 目标 49+64
- **取景**：focus `(0.50, 0.55)`，画心 自动检测
- **产物**：`bg_stream_day.webp`（53 KB）、`bg_stream_night.webp`（33 KB）

### `snow_bamboo` — Bamboo in Snow

- **作品**：Bamboo in Snow
- **作者／年代**：Hong Fan (Chinese, active late 1700s-early 1800s)；1818
- **材质**：Hanging scroll; ink on paper
- **馆藏编号（Accession）**：`2023.161`
- **来源**：Cleveland Museum of Art, Chinese Art；Severance and Greta Millikin Purchase Fund
- **许可**：CC0 1.0 Universal（Open Access，公有领域贡献）
- **API 源**：https://openaccess-api.clevelandart.org/api/artworks/543409
- **图像源（print）**：https://openaccess-cdn.clevelandart.org/2023.161/2023.161_print.jpg
- **参数**：柔焦 2.8px、对比 1.06；白日 画作权重 0.89 / `color=0.80` / 目标 187+90；夜色 画作权重 0.70 / `color=0.50` / 目标 50+64
- **取景**：focus `(0.56, 0.52)`，画心 自动检测
- **产物**：`bg_snow_bamboo_day.webp`（37 KB）、`bg_snow_bamboo_night.webp`（30 KB）

### `wind_bamboo` — Bamboo in the Wind

- **作品**：Bamboo in the Wind
- **作者／年代**：Puming (Xuechuang) (Chinese, active before 1274-after 1329)；1300s
- **材质**：Hanging scroll; ink on silk
- **馆藏编号（Accession）**：`1953.246`
- **来源**：Cleveland Museum of Art, Chinese Art；John L. Severance Fund
- **许可**：CC0 1.0 Universal（Open Access，公有领域贡献）
- **API 源**：https://openaccess-api.clevelandart.org/api/artworks/130496
- **图像源（print）**：https://openaccess-cdn.clevelandart.org/1953.246/1953.246_print.jpg
- **参数**：柔焦 2.8px、对比 1.05；白日 画作权重 0.89 / `color=0.76` / 目标 186+84；夜色 画作权重 0.70 / `color=0.46` / 目标 49+60
- **取景**：focus `(0.52, 0.52)`，画心 自动检测
- **产物**：`bg_wind_bamboo_day.webp`（44 KB）、`bg_wind_bamboo_night.webp`（32 KB）

### `falls` — Waterfall

- **作品**：Waterfall
- **作者／年代**：Unknown (Chinese)；1271-1368
- **材质**：Hanging scroll, ink and slight color on silk
- **馆藏编号（Accession）**：`1972.157`
- **来源**：Cleveland Museum of Art, Chinese Art；Gift of Mary B. Lee, C. Bingham Blossom, Dudley S. Blossom III, Laurel B. Kovacik, and Elizabeth B. Blossom, in memory of Elizabeth B. Blossom
- **许可**：CC0 1.0 Universal（Open Access，公有领域贡献）
- **API 源**：https://openaccess-api.clevelandart.org/api/artworks/146708
- **图像源（print）**：https://openaccess-cdn.clevelandart.org/1972.157/1972.157_print.jpg
- **参数**：柔焦 2.6px、对比 1.08；白日 画作权重 0.90 / `color=0.70` / 目标 186+86；夜色 画作权重 0.72 / `color=0.46` / 目标 50+62
- **取景**：focus `(0.50, 0.52)`，画心 手写 `crop`
- **产物**：`bg_falls_day.webp`（54 KB）、`bg_falls_night.webp`（46 KB）

### `winter` — Old Trees by a Wintry Brook

- **作品**：Old Trees by a Wintry Brook
- **作者／年代**：Wen Zhengming (Chinese, 1470-1559)；1551
- **材质**：Hanging scroll, ink and light color on paper
- **馆藏编号（Accession）**：`1997.102`
- **来源**：Cleveland Museum of Art, Chinese Art；Bequest of Mrs. A. Dean Perry
- **许可**：CC0 1.0 Universal（Open Access，公有领域贡献）
- **API 源**：https://openaccess-api.clevelandart.org/api/artworks/159727
- **图像源（print）**：https://openaccess-cdn.clevelandart.org/1997.102/1997.102_print.jpg
- **参数**：柔焦 3.0px、对比 1.10；白日 画作权重 0.87 / `color=0.84` / 目标 189+92；夜色 画作权重 0.68 / `color=0.55` / 目标 52+62
- **取景**：focus `(0.50, 0.58)`，画心 自动检测
- **产物**：`bg_winter_day.webp`（33 KB）、`bg_winter_night.webp`（27 KB）

### `peaks` — The Mountain of Five Cataracts

- **作品**：The Mountain of Five Cataracts
- **作者／年代**：Chen Hongshou (Chinese, 1598/99-1652)；c. 1630
- **材质**：Hanging scroll; ink on silk
- **馆藏编号（Accession）**：`1966.366`
- **来源**：Cleveland Museum of Art, Chinese Art；John L. Severance Fund
- **许可**：CC0 1.0 Universal（Open Access，公有领域贡献）
- **API 源**：https://openaccess-api.clevelandart.org/api/artworks/142641
- **图像源（print）**：https://openaccess-cdn.clevelandart.org/1966.366/1966.366_print.jpg
- **参数**：柔焦 3.0px、对比 1.05；白日 画作权重 0.86 / `color=0.76` / 目标 187+84；夜色 画作权重 0.66 / `color=0.46` / 目标 49+60
- **取景**：focus `(0.50, 0.54)`，画心 自动检测
- **产物**：`bg_peaks_day.webp`（51 KB）、`bg_peaks_night.webp`（42 KB）

### `qinglu` — Xu Jingyang Moving His Family

- **作品**：Xu Jingyang Moving His Family
- **作者／年代**：Cui Zizhong (Chinese, 1574-1644)；1644
- **材质**：Hanging scroll; ink and color on silk
- **馆藏编号（Accession）**：`1961.90`
- **来源**：Cleveland Museum of Art, Chinese Art；Mr. and Mrs. William H. Marlatt Fund
- **许可**：CC0 1.0 Universal（Open Access，公有领域贡献）
- **API 源**：https://openaccess-api.clevelandart.org/api/artworks/137125
- **图像源（print）**：https://openaccess-cdn.clevelandart.org/1961.90/1961.90_print.jpg
- **参数**：柔焦 3.0px、对比 1.08；白日 画作权重 0.85 / `color=0.60` / 目标 188+88；夜色 画作权重 0.65 / `color=0.40` / 目标 50+64
- **取景**：focus `(0.50, 0.56)`，画心 自动检测
- **产物**：`bg_qinglu_day.webp`（54 KB）、`bg_qinglu_night.webp`（44 KB）

## 人工复核提示

- **v2 变更**：真机截图反馈「洗得太淡」后，画作权重 0.74→0.85–0.90（夜 0.58→0.65–0.72）、模糊 4–5.5→2.6–3.1、加 UnsharpMask 与 8% 暗角，结构指标 `lap4_std` 从 1.5–2.6 提到 3.4–4.3（夜 2.6–3.2）。
- **最容易「偏花」**：`qinglu`（石青石绿 + 朱红人物，`color` 压到 0.60、`unsharp` 只给 48、`wb` 0.88）与 `falls`（绢色偏暖褐、纹理细密）。真机上若觉得压字，优先把这两张的 `art_w_day` 各降 0.02。
- **结构最足**：`falls`（语音页专用，`art_w_day` 0.90 取上限、`blur` 2.6 取下限，页面本身还叠压暗层）、`wind_bamboo`（一丛竹横穿画面）、`snow_bamboo`（竹叶 + 雪坡，细节最密）。
- **最「偏淡」**：`stream`、`peaks`——原作本来就是淡墨，远看接近纸纹，作为正文底最安全；若觉得太空，把 `art_w_day` 各 +0.02。
- **题跋在画面里**：`winter` 左上、`cloud` 上部的题跋与留白仍在，属于原作自有内容，不是裁边残留。

## 复习用图

- 16 张缩略拼图（4 列 × 4 行，标注 `scene_day` / `scene_night`）：[`backdrops-contact.png`](smoke/backdrops-contact.png)
- v2 同格式拼图：[`backdrops-contact-v2.png`](smoke/backdrops-contact-v2.png)
- v2 笔触复核（`falls`/`cloud`/`wind_bamboo` 日景 1:1 中央裁 720×720，不缩放）：[`backdrops-crop-v2.png`](smoke/backdrops-crop-v2.png)
- 正文可读性示意（4 张日景 + 30% 纸色蒙版 + 白色玻璃卡片）：[`backdrops-check.png`](smoke/backdrops-check.png)

## 重新生成

```bash
python tools/build_backdrops.py           # 全量重建（幂等，覆盖生成）
python tools/build_backdrops.py --measure  # 附带指标表与验收判定
```

源图缺失时脚本会自动从 CMA Open Access API 拉取 `print` 分辨率原图（**必须**带 `User-Agent`，否则 403），默认缓存于 `C:/tmp/cand/<cmaId>.jpg`。

