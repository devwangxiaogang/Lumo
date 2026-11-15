#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""语光 · Lumo — 古画背景素材管线（工作流 W1）。

把克利夫兰艺术博物馆（CMA）Open Access 的 CC0 高清中国立轴，处理成应用内
「青绿山水 · 宣纸」沉浸式背景：8 个场景 × 日/夜 = 16 张 720x1584 WebP。

观感目标
--------
一眼能看出这是山水画，但比原画淡、柔、暖；远看是纸纹，近看能辨认山形／竹影，
绝不喧宾夺主。为避免「拍脑袋调比例」，每张图先定「平均亮度 + 结构幅度」两个
目标，再用一次仿射拟合精确压到目标上（``fit_render``）：

=========  ==========  ===================  ===============  ==========
层          平均亮度     结构幅度 p5–p95       画作权重          底色
=========  ==========  ===================  ===============  ==========
日景        188–194     84–92               0.74–0.79        #F2EADA 宣纸
夜色        48–52       60–66               0.58–0.64        #0F1513 夜墨
=========  ==========  ===================  ===============  ==========

验收线（``--measure`` 逐张判定，不达标返回非 0）::

    日景  182 ≤ 平均亮度 ≤ 192   90x198 灰度 std ≥ 9
          4 邻域 Laplacian 方差 lap4 ≥ 3.2（其标准差 √lap4 ≥ 3.2）
    夜景   45 ≤ 平均亮度 ≤  58   90x198 灰度 std ≥ 7
          4 邻域 Laplacian 方差 lap4 ≥ 2.6（其标准差 √lap4 ≥ 2.6）

特性
----
* 可复跑、幂等：直接覆盖生成全部产物。
* 源图缺失时自动从 CMA Open Access API 下载 print 分辨率原图（带 UA，否则 403）。
* 装裱绫边用「逐行/列亮度均值 + 标准差」自动检测；检测不理想的卷子用
  ``SCENES[scene]['crop']`` 手写相对坐标兜底。
* 参数集中在 ``SCENES``，一场景一组。

产物
----
1. ``app/src/main/res/drawable-nodpi/bg_<scene>_day.webp`` / ``bg_<scene>_night.webp``
2. ``docs/BACKGROUNDS-CREDITS.md``
3. ``docs/smoke/backdrops-contact.png``   —— 16 张缩略拼图（4×4）
4. ``docs/smoke/backdrops-check.png``     —— 4 张日景 + 30% 纸色蒙版 + 玻璃卡片
5. ``docs/smoke/backdrops-contact-v2.png`` —— v2 同格式拼图
6. ``docs/smoke/backdrops-crop-v2.png``   —— falls/cloud/wind_bamboo 日景 1:1 720×720

用法
----
    python tools/build_backdrops.py              # 全量重建
    python tools/build_backdrops.py --measure    # 重建 + 指标表 + 验收判定
    python tools/build_backdrops.py --only cloud,falls
    python tools/build_backdrops.py --force-download   # 忽略本地缓存重下源图

说明：本脚本只写图片与文档，绝不改动 Kotlin / XML 源码。
"""

from __future__ import annotations

import argparse
import json
import math
import os
import statistics
import sys
import urllib.request
from pathlib import Path

from PIL import (Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter,
                 ImageOps)

try:                                        # 用于指标统计与快速拟合
    import numpy as np
except Exception:                           # pragma: no cover
    np = None


# ---------------------------------------------------------------- 常量 ----

PAPER_RGB = (0xF2, 0xEA, 0xDA)              # 宣纸色
INK_RGB = (0x0F, 0x15, 0x13)                # 夜墨色
CANVAS = (720, 1584)                        # 目标尺寸
WEBP_QUALITY = 78                           # 需求指定
WEBP_METHOD = 6                             # 最慢、压缩率最好的编码方法
MAX_BYTES = 150 * 1024                      # 单张上限（v2：结构变多，放宽到 150 KB）
MAX_TOTAL = 1100 * 1024                     # 总体积上限（v2）

# 白日：与宣纸混合后，再自顶向下压一层纸色渐变（让状态栏/文字区更干净）
DAY_GRAD_TOP, DAY_GRAD_BOTTOM = 0.35, 0.15
# 夜色：与夜墨混合后，自顶向下压一层更重的夜墨渐变
NIGHT_GRAD_TOP, NIGHT_GRAD_BOTTOM = 0.40, 0.14

# 底图先归一到一个「安全工作区间」，拟合时才不会因为大幅提亮而烧掉高光
BASE_MEAN, BASE_SPAN = 170.0, 150.0
BASE_GAIN_RANGE = (0.5, 1.0)                # 只收不放，避免放大噪点/裁切
# 仿射拟合的钳制，防止病态解把图压成纯色
GAIN_RANGE = (0.3, 3.0)
OFFSET_RANGE = (-110.0, 40.0)

# 四边渐隐暗角：向内 8% 的范围内线性平滑到 -6% 亮度，给画面一点「装裱」纵深
VIGNETTE_FRAC, VIGNETTE_STRENGTH = 0.08, 0.06

# 结构补偿：一次锐化不够时，再叠最多 N 次「中频」锐化，直到编码后的 Laplacian 过线。
# 半径用 4.0 而不是 2.0：WebP q78 会把半径 2 的高频几乎全部量化掉（实测加锐 2 次
# 编码后 lap4 只从 3.71 涨到 3.91），半径 4 的中频对比才扛得住压缩。
STRUCTURE_MAX_BOOST = 4
STRUCTURE_BOOST_RADIUS = 4.0
STRUCTURE_BOOST_PCT = 60
STRUCTURE_BOOST_THRESHOLD = 2

# 编码后仍不达标时的升级阶梯：(明暗幅度倍数, 补偿锐化次数)，从轻到重。
# 先拉幅度——淡墨原作拉开层次比堆锐化更耐看，也不会出浮雕光晕。
# 夜景单独一条：夜色亮度目标本来就窄（45–58），幅度拉太狠会把暗部压到 0
# 被截断，平均亮度反而被顶到上限外，所以夜梯几乎只靠补偿锐化。
ESCALATION = {
    'day': (
        (1.00, 0),
        (1.00, 1),
        (1.15, 1),
        (1.30, 1),
        (1.30, 2),
        (1.42, 2),
    ),
    'night': (
        (1.00, 0),
        (1.00, 1),
        (1.00, 2),
        (1.05, 2),
        (1.08, 3),
        (1.10, 3),
    ),
}

# 验收线（v2：画作权重提高后，均值收窄、结构指标改用 4 邻域归一化 Laplacian）
#   lap4     = 全分辨率 float 灰度的 4 邻域 Laplacian 方差（单位：级²）
#   lap4_std = 它的标准差（√lap4）；第一轮实测 1.5–2.6 正是这一列
ACCEPT = {
    'day': {'mean': (182.0, 192.0), 'std': 9.0, 'lap4': 3.2, 'lap4_std': 3.2},
    'night': {'mean': (45.0, 58.0), 'std': 7.0, 'lap4': 2.6, 'lap4_std': 2.6},
}

API_URL = 'https://openaccess-api.clevelandart.org/api/artworks/{cma_id}'
# CMA 的 API / CDN 对默认 UA 返回 403，必须显式声明
USER_AGENT = ('Lumo-Backdrops/1.0 (Cleveland Museum of Art Open Access; '
              'CC0 backgrounds)')

REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = Path(os.environ.get('LUMO_SOURCE_DIR', r'C:/tmp/cand'))
OUT_DIR = REPO_ROOT / 'app' / 'src' / 'main' / 'res' / 'drawable-nodpi'
DOCS_DIR = REPO_ROOT / 'docs'
SMOKE_DIR = DOCS_DIR / 'smoke'
CREDITS_PATH = DOCS_DIR / 'BACKGROUNDS-CREDITS.md'
CONTACT_PATH = SMOKE_DIR / 'backdrops-contact.png'
CHECK_PATH = SMOKE_DIR / 'backdrops-check.png'
# v2（第二轮）复核图：结构加强后的全量拼图 + 1:1 笔触裁切
CONTACT_V2_PATH = SMOKE_DIR / 'backdrops-contact-v2.png'
CROP_V2_PATH = SMOKE_DIR / 'backdrops-crop-v2.png'


# ------------------------------------------------------- 绫边检测参数 ----
DETECT = {
    'scale': 6,          # 先缩到 1/6 再统计，够稳且快
    'std_tol': 7.0,      # 绫边很平（std 2-6），画心一进来就 >12
    'mean_tol': 12.0,    # 同一侧允许的亮度漂移
    'backoff': 2,        # 越过边界后再向内多让 2 行/列（吃掉抗锯齿过渡）
    'safety': 0.004,     # 最终四边再内缩 0.4%，去掉最后一点描边
    'min_inset': 0.0,
    'max_inset': 0.22,   # 兜底：单边最多裁 22%
}


# --------------------------------------------------------------- 场景表 ----
# focus       : 0-1 取景中心。竖轴上部的题跋/留白不进画面，落点在中下部主体
# crop        : 手写相对坐标 (l, t, r, b)；None = 用自动检测
# blur        : 720 宽下的柔焦半径（4–6，保留皴法）
# contrast    : 混合前的 Contrast 增益（1.05–1.12）
# color/warm  : 白日降饱和 + 暖偏；night_color/cool 为夜色专用（更接近单色）
# art_w_*     : 画作权重（白日 0.74–0.79、夜色 0.58–0.64）
# *_mean/*_span : 拟合目标（平均亮度 / p5–p95 结构幅度）
SCENES: dict[str, dict] = {
    'cloud': {
        'cma_id': 137120,
        'accession': '1961.88',
        'title': 'Lonely Retreat Overlooking a Misty Valley',
        'artist': 'Sheng Maoye (Chinese, active early 1600s)',
        'date': '1630',
        'medium': 'Hanging scroll; ink and slight color on silk',
        'credit_line': 'Mr. and Mrs. William H. Marlatt Fund',
        'focus': (0.46, 0.52),
        'crop': None,
        'blur': 3.0,
        'unsharp': 62,
        'contrast': 1.06,
        'color': 0.78, 'warm': (3, 1, -4),
        'night_color': 0.52, 'cool': (-4, 0, 8),
        'art_w_day': 0.86, 'art_w_night': 0.66,
        'day_mean': 188.0, 'day_span': 88.0,
        'night_mean': 50.0, 'night_span': 66.0,
    },
    'stream': {
        'cma_id': 149628,
        'accession': '1979.28',
        'title': 'Looking for a Monastery in the Misty Mountains',
        'artist': 'Chen Chun (Chinese, 1483-1544)',
        'date': '1500-1544',
        'medium': 'Hanging scroll, ink on paper',
        'credit_line': 'John L. Severance Fund',
        'focus': (0.50, 0.55),
        'crop': None,
        'blur': 3.1,
        'unsharp': 58,
        'contrast': 1.06,
        'color': 0.82, 'warm': (3, 1, -4),
        'night_color': 0.52, 'cool': (-4, 0, 8),
        'art_w_day': 0.85, 'art_w_night': 0.65,
        'day_mean': 187.0, 'day_span': 86.0,
        'night_mean': 49.0, 'night_span': 64.0,
    },
    'snow_bamboo': {
        'cma_id': 543409,
        'accession': '2023.161',
        'title': 'Bamboo in Snow',
        'artist': 'Hong Fan (Chinese, active late 1700s-early 1800s)',
        'date': '1818',
        'medium': 'Hanging scroll; ink on paper',
        'credit_line': 'Severance and Greta Millikin Purchase Fund',
        'focus': (0.56, 0.52),
        'crop': None,
        'blur': 2.8,
        'unsharp': 68,
        'contrast': 1.06,
        'color': 0.80, 'warm': (3, 1, -4),
        'night_color': 0.50, 'cool': (-4, 0, 8),
        'art_w_day': 0.89, 'art_w_night': 0.7,
        'day_mean': 187.0, 'day_span': 90.0,
        'night_mean': 50.0, 'night_span': 64.0,
    },
    'wind_bamboo': {
        'cma_id': 130496,
        'accession': '1953.246',
        'title': 'Bamboo in the Wind',
        'artist': 'Puming (Xuechuang) (Chinese, active before 1274-after 1329)',
        'date': '1300s',
        'medium': 'Hanging scroll; ink on silk',
        'credit_line': 'John L. Severance Fund',
        'focus': (0.52, 0.52),
        'crop': None,
        'blur': 2.8,
        'unsharp': 68,
        'contrast': 1.05,
        'wb': 0.55,
        'color': 0.76, 'warm': (5, 2, -6),
        'night_color': 0.46, 'cool': (-3, 1, 9),
        'art_w_day': 0.89, 'art_w_night': 0.7,
        'day_mean': 186.0, 'day_span': 84.0,
        'night_mean': 49.0, 'night_span': 60.0,
    },
    'falls': {
        'cma_id': 146708,
        'accession': '1972.157',
        'title': 'Waterfall',
        'artist': 'Unknown (Chinese)',
        'date': '1271-1368',
        'medium': 'Hanging scroll, ink and slight color on silk',
        'credit_line': ('Gift of Mary B. Lee, C. Bingham Blossom, Dudley S. Blossom III, '
                        'Laurel B. Kovacik, and Elizabeth B. Blossom, in memory of '
                        'Elizabeth B. Blossom'),
        'focus': (0.50, 0.52),
        # 这卷上方裱了独立的题跋纸，自动检测会把白纸当画心留下，故手工指定画心。
        'crop': (186 / 1395, 915 / 3400, 1200 / 1395, 3262 / 3400),
        'blur': 2.6,
        'unsharp': 58,
        'contrast': 1.08,
        'wb': 0.88,
        'color': 0.70, 'warm': (3, 1, -4),
        'night_color': 0.46, 'cool': (-3, 1, 9),
        'art_w_day': 0.9, 'art_w_night': 0.72,
        'day_mean': 186.0, 'day_span': 86.0,
        'night_mean': 50.0, 'night_span': 62.0,
    },
    'winter': {
        'cma_id': 159727,
        'accession': '1997.102',
        'title': 'Old Trees by a Wintry Brook',
        'artist': 'Wen Zhengming (Chinese, 1470-1559)',
        'date': '1551',
        'medium': 'Hanging scroll, ink and light color on paper',
        'credit_line': 'Bequest of Mrs. A. Dean Perry',
        'focus': (0.50, 0.58),
        'crop': None,
        'blur': 3.0,
        'unsharp': 60,
        'contrast': 1.10,
        'color': 0.84, 'warm': (2, 1, -3),
        'night_color': 0.55, 'cool': (-4, 0, 8),
        'art_w_day': 0.87, 'art_w_night': 0.68,
        'day_mean': 189.0, 'day_span': 92.0,
        'night_mean': 52.0, 'night_span': 62.0,
    },
    'peaks': {
        'cma_id': 142641,
        'accession': '1966.366',
        'title': 'The Mountain of Five Cataracts',
        'artist': 'Chen Hongshou (Chinese, 1598/99-1652)',
        'date': 'c. 1630',
        'medium': 'Hanging scroll; ink on silk',
        'credit_line': 'John L. Severance Fund',
        'focus': (0.50, 0.54),
        'crop': None,
        'blur': 3.0,
        'unsharp': 62,
        'contrast': 1.05,
        'color': 0.76, 'warm': (5, 2, -6),
        'night_color': 0.46, 'cool': (-3, 1, 9),
        'art_w_day': 0.86, 'art_w_night': 0.66,
        'day_mean': 187.0, 'day_span': 84.0,
        'night_mean': 49.0, 'night_span': 60.0,
    },
    'qinglu': {
        'cma_id': 137125,
        'accession': '1961.90',
        'title': 'Xu Jingyang Moving His Family',
        'artist': 'Cui Zizhong (Chinese, 1574-1644)',
        'date': '1644',
        'medium': 'Hanging scroll; ink and color on silk',
        'credit_line': 'Mr. and Mrs. William H. Marlatt Fund',
        'focus': (0.50, 0.56),
        'crop': None,
        'blur': 3.0,
        'unsharp': 48,
        'contrast': 1.08,
        'wb': 0.88,
        'color': 0.60, 'warm': (4, 2, -5),
        'night_color': 0.40, 'cool': (-3, 1, 9),
        'art_w_day': 0.85, 'art_w_night': 0.65,
        'day_mean': 188.0, 'day_span': 88.0,
        'night_mean': 50.0, 'night_span': 64.0,
    },
}


# ------------------------------------------------------------ 小工具 ----

def _clamp(v, lo, hi):
    return max(lo, min(hi, v))


# ------------------------------------------------------------ 源图下载 ----

def _http_get(url: str, timeout: int = 60) -> bytes:
    req = urllib.request.Request(url, headers={
        'User-Agent': USER_AGENT,       # 缺这个头 API/CDN 直接 403
        'Accept': '*/*',
    })
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def source_path(cma_id: int) -> Path:
    return SOURCE_DIR / f'{cma_id}.jpg'


def ensure_source(scene: str, force: bool = False) -> Path:
    """返回本地源图路径；不存在则从 CMA Open Access 下载 print 分辨率原图。"""
    cma_id = SCENES[scene]['cma_id']
    dst = source_path(cma_id)
    if dst.exists() and not force:
        return dst

    SOURCE_DIR.mkdir(parents=True, exist_ok=True)
    api = API_URL.format(cma_id=cma_id)
    print(f'[dl] {scene}: 本地无 {dst}，改从 CMA 下载')
    payload = json.loads(_http_get(api).decode('utf-8'))
    data = payload.get('data') or {}
    url = ((data.get('images') or {}).get('print') or {}).get('url')
    if not url:
        raise RuntimeError(f'{scene}: API 未提供 print 图像 URL（{api}）')
    print(f'[dl] {scene}: GET {url}')
    blob = _http_get(url, timeout=180)
    if len(blob) < 10_000:
        raise RuntimeError(f'{scene}: 下载内容过小（{len(blob)}B），疑似被拒')
    tmp = dst.with_suffix('.part')
    tmp.write_bytes(blob)
    tmp.replace(dst)
    print(f'[dl] {scene}: {dst} ({len(blob)/1024:.0f} KB)')
    return dst


# --------------------------------------------------------- 装裱绫边检测 ----

def _line_stats(small: Image.Image, axis: int):
    """axis=0 逐列、axis=1 逐行，返回 [(均值, 标准差), ...]。"""
    w, h = small.size
    px = small.load()
    out = []
    if axis == 0:
        for x in range(w):
            vals = [px[x, y] for y in range(h)]
            out.append((sum(vals) / h, statistics.pstdev(vals)))
    else:
        for y in range(h):
            vals = [px[x, y] for x in range(w)]
            out.append((sum(vals) / w, statistics.pstdev(vals)))
    return out


def _walk_inward(prof, from_start: bool) -> int:
    """从一侧向内走，返回第一条「不像绫边」的线索引。"""
    n = len(prof)
    order = list(range(n)) if from_start else list(range(n - 1, -1, -1))
    head = order[:max(3, n // 150)]                     # 外缘参考带
    ref_mean = sum(prof[i][0] for i in head) / len(head)
    ref_std = sum(prof[i][1] for i in head) / len(head)
    std_tol = max(DETECT['std_tol'], ref_std * 2.0)
    for i in order:
        mean, std = prof[i]
        if std > std_tol or abs(mean - ref_mean) > DETECT['mean_tol']:
            return i
    return 0 if from_start else n - 1


def detect_panel(im: Image.Image):
    """自动找出绫边以内的画心，返回 (l, t, r, b) 相对坐标。"""
    w, h = im.size
    scale = DETECT['scale']
    small = im.resize((max(1, w // scale), max(1, h // scale)),
                      Image.LANCZOS).convert('L')
    sw, sh = small.size
    backoff = DETECT['backoff']

    left = _walk_inward(_line_stats(small, 0), True) + backoff
    right = _walk_inward(_line_stats(small, 0), False) - backoff
    top = _walk_inward(_line_stats(small, 1), True) + backoff
    bottom = _walk_inward(_line_stats(small, 1), False) - backoff

    lo, hi = DETECT['min_inset'], DETECT['max_inset']
    box = (
        _clamp(left / sw, lo, hi),
        _clamp(top / sh, lo, hi),
        _clamp(right / sw, 1 - hi, 1 - lo),
        _clamp(bottom / sh, 1 - hi, 1 - lo),
    )
    if box[2] <= box[0] or box[3] <= box[1]:            # 荒唐结果 → 退回整幅
        return (lo, lo, 1 - lo, 1 - lo)
    return box


def panel_box(scene: str, im: Image.Image):
    """优先用手写 crop；否则自动检测。最后统一再内缩一点 safety。"""
    box = SCENES[scene].get('crop') or detect_panel(im)
    s = DETECT['safety']
    return (box[0] + s, box[1] + s, box[2] - s, box[3] - s)


# ------------------------------------------------------------- 图像处理 ----

def cover_crop(im: Image.Image, size, focus) -> Image.Image:
    """等比放大铺满目标尺寸，再围绕 focus（0-1）裁切。"""
    tw, th = size
    w, h = im.size
    scale = max(tw / w, th / h)
    nw, nh = max(tw, math.ceil(w * scale)), max(th, math.ceil(h * scale))
    im = im.resize((nw, nh), Image.LANCZOS)
    fx, fy = focus
    left = int(round(fx * nw - tw / 2))
    top = int(round(fy * nh - th / 2))
    left = _clamp(left, 0, nw - tw)
    top = _clamp(top, 0, nh - th)
    return im.crop((left, top, left + tw, top + th))


def shift_channels(im: Image.Image, offsets) -> Image.Image:
    """整体做像素级加偏移（暖/冷偏），逐通道截断到 0-255。"""
    lut = []
    for off in offsets:
        lut.extend(min(255, max(0, v + off)) for v in range(256))
    return im.point(lut)


def apply_affine(im: Image.Image, gain: float, offset: float) -> Image.Image:
    """对 R/G/B 用同一条 LUT 做 v*gain+offset —— 只改明暗，不改色相。"""
    table = [int(_clamp(round(v * gain + offset), 0, 255)) for v in range(256)]
    return im.point(table * 3)


def _percentile(hist, pct: float) -> int:
    total = sum(hist)
    if total <= 0:
        return 255
    acc = 0
    aim = total * pct / 100.0
    for v, n in enumerate(hist):
        acc += n
        if acc >= aim:
            return v
    return 255


def paper_white_balance(im: Image.Image, target=(248, 246, 240),
                        pct: float = 96.0,
                        lim=(0.40, 3.6),
                        strength: float = 0.72,
                        knee: float = 96.0) -> Image.Image:
    """把「纸/绢的本色」拉成中性纸白（只动亮部，保护墨色）。

    古画的绢底往往整体偏黄褐（尤其 130496 的深褐绢、137125 的旧绢），直接混纸色
    会得到一张发黄的照片。逐通道把 96 分位（≈纸面）对齐到目标纸白，黄褐底就归一
    成干净纸色，墨色仍是墨色，朱印与石绿也还留着一点。

    `strength` < 1 时只走一部分；`knee` 让增益在暗部平滑回到 1.0（0 处为 0），
    否则逐通道放大 B 会把深褐绢上的墨一起拉成蓝灰——这是第一轮竹景发蓝的根因。
    """
    out = []
    for band, tgt in zip(im.split(), target):
        hi = max(1.0, float(_percentile(band.histogram(), pct)))
        g = _clamp(tgt / hi, *lim)
        g = 1.0 + strength * (g - 1.0)
        lut = []
        for v in range(256):
            d = min(1.0, v / knee) if knee > 0 else 1.0
            f = d * d * (3.0 - 2.0 * d)          # smoothstep：暗部不动，亮部全量
            lut.append(int(_clamp(round(v * (1.0 + (g - 1.0) * f)), 0, 255)))
        out.append(band.point(lut))
    return Image.merge('RGB', out)


_SOLID_CACHE: dict = {}
_GRAD_CACHE: dict = {}


def _solid(size, rgb) -> Image.Image:
    key = (size, rgb)
    if key not in _SOLID_CACHE:
        _SOLID_CACHE[key] = Image.new('RGB', size, rgb)
    return _SOLID_CACHE[key]


def vertical_ramp(size, top_alpha: float, bottom_alpha: float) -> Image.Image:
    """自上而下的 alpha 渐变蒙版（顶部重、底部轻）。"""
    key = (size, round(top_alpha, 4), round(bottom_alpha, 4))
    if key not in _GRAD_CACHE:
        w, h = size
        col = Image.new('L', (1, h))
        px = col.load()
        for y in range(h):
            t = y / max(1, h - 1)
            px[0, y] = int(round(255 * (top_alpha + (bottom_alpha - top_alpha) * t)))
        _GRAD_CACHE[key] = col.resize((w, h), Image.NEAREST)
    return _GRAD_CACHE[key]


_VIG_CACHE: dict = {}


def vignette(im: Image.Image, frac: float = VIGNETTE_FRAC,
             strength: float = VIGNETTE_STRENGTH) -> Image.Image:
    """四边向内 frac 的范围内平滑衰减到 (1-strength)：给画面一点纵深。"""
    key = (im.size, round(frac, 4), round(strength, 4))
    if key not in _VIG_CACHE:
        w, h = im.size
        if np is not None:                       # 向量化，1.1M 像素一次算完
            xs = np.minimum(np.arange(w), w - 1 - np.arange(w)) / max(1.0, w * frac)
            ys = np.minimum(np.arange(h), h - 1 - np.arange(h)) / max(1.0, h * frac)
            xs = np.clip(xs, 0.0, 1.0)
            ys = np.clip(ys, 0.0, 1.0)
            sx = xs * xs * (3.0 - 2.0 * xs)
            sy = ys * ys * (3.0 - 2.0 * ys)
            f = np.minimum(sy[:, None], sx[None, :])
            arr = (255.0 * (1.0 - strength * (1.0 - f))).astype(np.uint8)
            _VIG_CACHE[key] = Image.fromarray(arr, 'L')
            return _apply_vignette(im, _VIG_CACHE[key])
        mask = Image.new('L', (w, h))
        px = mask.load()
        bw, bh = max(1.0, w * frac), max(1.0, h * frac)
        fx = [1.0 - strength * (1.0 - (lambda d: d * d * (3.0 - 2.0 * d))(
            min(1.0, min(x, w - 1 - x) / bw))) for x in range(w)]
        for y in range(h):
            d = min(1.0, min(y, h - 1 - y) / bh)
            fy = 1.0 - strength * (1.0 - (d * d * (3.0 - 2.0 * d)))
            for x in range(w):
                px[x, y] = int(round(255 * min(fx[x], fy)))
        _VIG_CACHE[key] = mask
    return _apply_vignette(im, _VIG_CACHE[key])


def _apply_vignette(im: Image.Image, mask: Image.Image) -> Image.Image:
    if np is not None:
        a = np.asarray(im, dtype=np.float32) * (np.asarray(mask, dtype=np.float32)[:, :, None] / 255.0)
        return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGB')
    return ImageChops.multiply(im, Image.merge('RGB', (mask, mask, mask)))


def compose(art: Image.Image, cfg, kind: str, boost: int = 0) -> Image.Image:
    """降饱和 + 冷暖偏 → 按画作权重混色 → 叠垂直渐变 → 锐化 → 暗角。"""
    w = cfg['art_w_day'] if kind == 'day' else cfg['art_w_night']
    if kind == 'day':
        im = ImageEnhance.Color(art).enhance(cfg['color'])
        im = shift_channels(im, cfg['warm'])
        im = Image.blend(im, _solid(im.size, PAPER_RGB), 1.0 - w)
        out = Image.composite(_solid(im.size, PAPER_RGB), im,
                              vertical_ramp(im.size, DAY_GRAD_TOP, DAY_GRAD_BOTTOM))
    else:
        im = ImageEnhance.Color(art).enhance(cfg['night_color'])
        im = shift_channels(im, cfg['cool'])
        im = Image.blend(im, _solid(im.size, INK_RGB), 1.0 - w)
        out = Image.composite(_solid(im.size, INK_RGB), im,
                              vertical_ramp(im.size, NIGHT_GRAD_TOP, NIGHT_GRAD_BOTTOM))
    # 混色后再提一次结构：radius 2.0、强度 55–70%，把皴法与竹叶的笔锋找回来
    out = out.filter(ImageFilter.UnsharpMask(radius=2.0,
                                             percent=cfg.get('unsharp', 62),
                                             threshold=0))
    # 自适应结构补偿：淡墨卷子（misty）一次锐化仍到不了 Laplacian 验收线，
    # 就再叠补偿锐化——用 radius 4.0 的中频，才扛得住 WebP q78 的量化
    for _ in range(boost):
        out = out.filter(ImageFilter.UnsharpMask(radius=STRUCTURE_BOOST_RADIUS,
                                                 percent=STRUCTURE_BOOST_PCT,
                                                 threshold=STRUCTURE_BOOST_THRESHOLD))
    return vignette(out)


# --------------------------------------------------------------- 指标 ----

def tonal(im: Image.Image):
    """返回 (亮度均值, 结构幅度 p95-p5)。"""
    if np is not None:
        a = np.asarray(im.convert('L'), dtype=np.float32)
        p5, p95 = np.percentile(a, [5, 95])
        return float(a.mean()), float(p95 - p5)
    g = im.convert('L')
    data = sorted(g.getdata())
    n = len(data)
    return sum(data) / n, data[int(n * 0.95)] - data[int(n * 0.05)]


SMALL = (90, 198)          # 验收用降采样尺寸（720x1584 的 1/8）


def metrics(im: Image.Image) -> dict:
    """平均亮度（全分辨率）、90x198 灰度标准差、4 邻域归一化 Laplacian 方差。"""
    g = im.convert('L')
    small = g.resize(SMALL, Image.LANCZOS)
    if np is not None:
        a = np.asarray(g, dtype=np.float32)
        b = np.asarray(small, dtype=np.float32)
        # 4 邻域 Laplacian（float 灰度、全分辨率）：b[-2:] 用错位切片实现
        lap_full = (a[:-2, 1:-1] + a[2:, 1:-1] + a[1:-1, :-2] + a[1:-1, 2:]
                    - 4.0 * a[1:-1, 1:-1])
        lap4 = float(lap_full.var())
        lap = (b[:-2, 1:-1] + b[2:, 1:-1] + b[1:-1, :-2] + b[1:-1, 2:]
               - 4.0 * b[1:-1, 1:-1])
        return {'mean': float(a.mean()), 'std': float(b.std()),
                'lap': float(lap.var()), 'lap4': lap4,
                'lap4_std': math.sqrt(max(0.0, lap4))}
    w, h = g.size
    mean = sum(g.getdata()) / (w * h)
    vals = list(small.getdata())
    sw, sh = SMALL
    std = statistics.pstdev(vals)
    lap = []
    for y in range(1, sh - 1):
        row = vals[y * sw:(y + 1) * sw]
        up = vals[(y - 1) * sw:y * sw]
        dn = vals[(y + 1) * sw:(y + 2) * sw]
        for x in range(1, sw - 1):
            lap.append(up[x] + dn[x] + row[x - 1] + row[x + 1] - 4 * row[x])
    return {'mean': mean, 'std': std, 'lap': statistics.pvariance(lap),
            'lap4': 0.0, 'lap4_std': 0.0}


def check(kind: str, m: dict) -> bool:
    lim = ACCEPT[kind]
    return (lim['mean'][0] <= m['mean'] <= lim['mean'][1]
            and m['std'] >= lim['std']
            and m['lap4'] >= lim['lap4'] and m['lap4_std'] >= lim['lap4_std'])


# --------------------------------------------------------------- 构建 ----

def build_base(scene: str) -> Image.Image:
    """裁绫边 → 取景 cover → 柔焦 → 对比归一 → 摆进安全工作区间。"""
    cfg = SCENES[scene]
    path = ensure_source(scene)
    with Image.open(path) as raw:
        im = raw.convert('RGB')
    w, h = im.size
    l, t, r, b = panel_box(scene, im)
    im = im.crop((int(l * w), int(t * h), int(r * w), int(b * h)))
    im = cover_crop(im, CANVAS, cfg['focus'])
    im = im.filter(ImageFilter.GaussianBlur(radius=cfg['blur']))
    im = paper_white_balance(im, strength=cfg.get('wb', 0.72))      # 绢黄底 → 纸白
    im = ImageOps.autocontrast(im, cutoff=1, preserve_tone=True)   # 免得发闷
    im = ImageEnhance.Contrast(im).enhance(cfg['contrast'])

    # 归一到「均值 BASE_MEAN / 幅度 ≤ BASE_SPAN」，后面的仿射拟合才有余量
    m, s = tonal(im)
    gain = _clamp(BASE_SPAN / s, *BASE_GAIN_RANGE) if s > 1e-3 else 1.0
    return apply_affine(im, gain, BASE_MEAN - gain * m)


def fit_render(base: Image.Image, cfg, kind: str, max_iter: int = 6,
               boost: int = 0, span_scale: float = 1.0) -> Image.Image:
    """把结果精确压到 cfg 里那条「平均亮度 + 结构幅度」上。

    compose() 对底图的像素值近似仿射（混合常量色 + 渐变蒙版），所以先用
    一次 +8 的探针偏移量出斜率，解出 (gain, offset)，再迭代 2–3 次收敛。

    ``span_scale`` 放大目标幅度：淡墨原作（cloud/stream/peaks）靠「拉开层次」
    比靠「堆锐化」更能提升结构指标，且不会出现浮雕感光晕。
    """
    tgt_mean = cfg[f'{kind}_mean']
    tgt_span = cfg[f'{kind}_span'] * span_scale

    m0, s0 = tonal(compose(base, cfg, kind, boost))
    probe = apply_affine(base, 1.0, 8.0)
    m1, _ = tonal(compose(probe, cfg, kind, boost))
    slope = (m1 - m0) / 8.0
    if abs(slope) < 1e-3:
        slope = 0.30 if kind == 'day' else 0.25

    gain = _clamp(tgt_span / max(1e-3, s0), *GAIN_RANGE)
    offset = _clamp((tgt_mean - gain * m0) / slope, *OFFSET_RANGE)

    im = compose(apply_affine(base, gain, offset), cfg, kind, boost)
    # 迭代可能过冲（尤其夜景暗部被 LUT 压到 0 之后斜率变了），所以只认
    # 「离目标最近」的那一组参数，最后用它们重渲染，保证不会比中途更差。
    best = None
    for _ in range(max_iter):
        m, s = tonal(im)
        score = abs(m - tgt_mean) + 1.5 * abs(s - tgt_span)
        if best is None or score < best[0]:
            best = (score, gain, offset)
        if abs(m - tgt_mean) < 0.6 and abs(s - tgt_span) < 1.0:
            break
        gain = _clamp(gain * (1.0 + 0.85 * (tgt_span / max(1e-3, s) - 1.0)),
                      *GAIN_RANGE)
        offset = _clamp(offset + 0.85 * (tgt_mean - m) / slope, *OFFSET_RANGE)
        im = compose(apply_affine(base, gain, offset), cfg, kind, boost)

    _, bg, bo = best
    return compose(apply_affine(base, bg, bo), cfg, kind, boost)


def render_final(base: Image.Image, cfg, kind: str, path: Path):
    """拟合 → 存盘 → **解码回来**测量 → 不够就升级，直到编码后的文件过线。

    必须测编码后的文件：WebP q78 会把半径 2 的高频几乎全部量化掉，内存图的
    ``lap4_std`` 比落地文件高出一大截（cloud_day 11.58 vs 3.99），只在内存里
    验收会得出「数字很好、真机很糊」的假结论。

    升级阶梯先拉层次（``span_scale``）再叠锐化：淡墨原作靠拉开明暗层次提升
    Laplacian，不会像堆锐化那样出浮雕感光晕。
    """
    best = None
    for span_scale, boost in ESCALATION[kind]:
        im = fit_render(base, cfg, kind, boost=boost, span_scale=span_scale)
        nbytes = write_webp(im, path)
        with Image.open(path) as fp:
            decoded = fp.convert('RGB')
        m = metrics(decoded)
        if best is None:
            best = (span_scale, boost, decoded, nbytes, m)
        short = (m['lap4_std'] < ACCEPT[kind]['lap4_std']
                 or m['lap4'] < ACCEPT[kind]['lap4'])
        if not short:
            # 拼图/文档都基于真正落地的像素，避免「内存图漂亮、文件发灰」
            return decoded, nbytes, m, boost, span_scale
        best = (span_scale, boost, decoded, nbytes, m)
    span_scale, boost, decoded, nbytes, m = best
    return decoded, nbytes, m, boost, span_scale


# --------------------------------------------------------------- 输出 ----

def write_webp(im: Image.Image, path: Path) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    im.convert('RGB').save(path, 'WEBP', quality=WEBP_QUALITY, method=WEBP_METHOD)
    return path.stat().st_size


def build_contact_sheet(items, path: Path) -> None:
    """4 列 × 4 行 缩略拼图，标注 scene_day / scene_night，供人工复核。"""
    cols, rows = 4, 4
    tile_w = 250
    tile_h = int(tile_w * CANVAS[1] / CANVAS[0])          # 550
    gap, pad, cap, head = 12, 26, 26, 54
    width = pad * 2 + cols * tile_w + gap * (cols - 1)
    height = head + pad * 2 + rows * (tile_h + cap) + gap * (rows - 1)
    sheet = Image.new('RGB', (width, height), (30, 30, 32))
    draw = ImageDraw.Draw(sheet)
    draw.text((pad, 14), 'Lumo backdrops - 8 scenes x day/night - 720x1584 WebP q78',
              fill=(232, 232, 232))
    draw.text((pad, 32), 'row pairs: day | night  (review: soft / legible / clean)',
              fill=(150, 150, 150))

    for idx, (label, im, nbytes) in enumerate(items):
        r, c = divmod(idx, cols)
        x = pad + c * (tile_w + gap)
        y = head + pad + r * (tile_h + cap + gap)
        thumb = im.resize((tile_w, tile_h), Image.LANCZOS)
        sheet.paste(thumb, (x, y + cap))
        draw.text((x, y + 4), label, fill=(236, 236, 236))
        draw.text((x, y + 16), f'{nbytes/1024:.0f} KB', fill=(140, 140, 140))
        draw.rectangle([x - 1, y + cap - 1, x + tile_w, y + cap + tile_h],
                       outline=(72, 72, 76))

    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)
    print(f'[sheet] {path.relative_to(REPO_ROOT)}  {sheet.size[0]}x{sheet.size[1]}')


def build_check_sheet(path: Path, scenes=('cloud', 'stream', 'falls', 'winter')):
    """成品效果示意：日景 + 30% 纸色蒙版 + 白色玻璃卡片，用来判正文可读性。"""
    tile_w, tile_h = 360, 792                                # 720x1584 的 1/2
    gap, pad, head, cap = 16, 24, 58, 24
    n = len(scenes)
    width = pad * 2 + n * tile_w + gap * (n - 1)
    height = head + pad + tile_h + cap + pad
    sheet = Image.new('RGB', (width, height), (34, 34, 36))
    draw = ImageDraw.Draw(sheet)
    draw.text((pad, 14), 'Lumo backdrop readability check - day + 30% paper scrim',
              fill=(234, 234, 234))
    draw.text((pad, 32), 'white glass card 60x120 @ centre with body-text bars',
              fill=(152, 152, 152))

    paper = Image.new('RGB', (tile_w, tile_h), PAPER_RGB)
    for i, scene in enumerate(scenes):
        src = OUT_DIR / f'bg_{scene}_day.webp'
        with Image.open(src) as fp:
            im = fp.convert('RGB').resize((tile_w, tile_h), Image.LANCZOS)
        im = Image.blend(im, paper, 0.30)                     # 30% 纸色蒙版
        overlay = Image.new('RGBA', (tile_w, tile_h), (0, 0, 0, 0))
        od = ImageDraw.Draw(overlay)
        cw, ch = 60, 120
        cx, cy = (tile_w - cw) // 2, (tile_h - ch) // 2
        od.rounded_rectangle([cx, cy, cx + cw, cy + ch], radius=10,
                             fill=(255, 255, 255, 96),
                             outline=(255, 255, 255, 150), width=1)
        for k in range(3):                                    # 正文示意
            ty = cy + 22 + k * 14
            od.rounded_rectangle([cx + 10, ty, cx + cw - 10, ty + 3],
                                 radius=1, fill=(58, 58, 62, 170))
        im = Image.alpha_composite(im.convert('RGBA'), overlay).convert('RGB')
        x = pad + i * (tile_w + gap)
        y = head
        sheet.paste(im, (x, y))
        draw = ImageDraw.Draw(sheet)
        draw.text((x, y + tile_h + 8), f'{scene}_day  (+30% paper)',
                  fill=(210, 210, 210))

    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)
    print(f'[check] {path.relative_to(REPO_ROOT)}  {sheet.size[0]}x{sheet.size[1]}')


def build_crop_sheet(path: Path, scenes=('falls', 'cloud', 'wind_bamboo'),
                     size: int = 720) -> None:
    """1:1 中央裁切（不缩放），用来验笔触／皴法是否真的回来了。"""
    gap, pad, head, cap = 16, 24, 58, 26
    n = len(scenes)
    width = pad * 2 + n * size + gap * (n - 1)
    height = head + pad + size + cap + pad
    sheet = Image.new('RGB', (width, height), (28, 28, 30))
    draw = ImageDraw.Draw(sheet)
    draw.text((pad, 14), 'Lumo backdrop crop check - 1:1 centre 720x720 (no rescale)',
              fill=(234, 234, 234))
    draw.text((pad, 32), 'day variant - inspect brushwork / texture, not composition',
              fill=(152, 152, 152))
    for i, scene in enumerate(scenes):
        src = OUT_DIR / f'bg_{scene}_day.webp'
        with Image.open(src) as fp:
            im = fp.convert('RGB')
        w, h = im.size
        cx, cy = (w - size) // 2, (h - size) // 2
        im = im.crop((cx, cy, cx + size, cy + size))            # 1:1，不缩放
        x = pad + i * (size + gap)
        sheet.paste(im, (x, head + pad))
        draw = ImageDraw.Draw(sheet)
        draw.text((x, head + pad + size + 6), f'{scene}_day  720x720 @1:1',
                  fill=(212, 212, 212))
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)
    print(f'[crop] {path.relative_to(REPO_ROOT)}  {sheet.size[0]}x{sheet.size[1]}')


def write_credits(path: Path, built) -> None:
    """生成出处文档：逐场景列出作品、作者、编号、CC0、源 URL、处理步骤。"""
    L = []
    L.append('# 背景图出处与处理说明（BACKGROUNDS CREDITS）')
    L.append('')
    L.append('「语光 · Lumo」的青绿山水沉浸式背景，全部取自 **克利夫兰艺术博物馆'
             '（Cleveland Museum of Art）Open Access** 的中国立轴高清图。')
    L.append('')
    L.append('> **版权**：所选用作品在 CMA Open Access 下标记为 **CC0 1.0 通用'
             '（公有领域贡献）**，可用于任何用途、无需授权、无需署名。')
    L.append('> **用途声明**：本仓库中的 `bg_*.webp` **仅用于本应用内的背景显示**，'
             '不单独分发、不再许可、不用于任何素材转售；原作属**公有领域**'
             '（public domain），本仓库仅对裁切／柔焦／调色后的派生图主张无额外权利。')
    L.append('')
    L.append('生成脚本：[`tools/build_backdrops.py`](../tools/build_backdrops.py)'
             f'（Pillow，可复跑）。产物：8 场景 × 日/夜 = 16 张 '
             f'`720x1584` WebP（q{WEBP_QUALITY}，method {WEBP_METHOD}）。')
    L.append('')
    L.append('## 总览')
    L.append('')
    L.append('| 场景 | 文件 | 作品 | 作者 | 年代 | Accession | 许可 |')
    L.append('| --- | --- | --- | --- | --- | --- | --- |')
    for scene, cfg in SCENES.items():
        L.append(f"| `{scene}` | `bg_{scene}_day.webp` / `bg_{scene}_night.webp` | "
                 f"{cfg['title']} | {cfg['artist']} | {cfg['date']} | "
                 f"{cfg['accession']} | CC0 |")
    L.append('')
    L.append('## 处理流程（每一张都按此顺序）')
    L.append('')
    L.append('1. **解码**：`Image.open(src).convert(\'RGB\')`。源图为 CMA `print` '
             '分辨率（宽 1300–2100、高 3400 px，**含完整装裱**：上下绫边、题跋、轴头）。')
    L.append('2. **裁掉装裱绫边**：把源图缩到 1/6，逐列／逐行统计亮度均值与标准差。'
             '绫边很平（std ≈ 2–6 且亮度稳定），画心一进入纹样 std 立刻升到 13–60；'
             '据此从四边向内走到第一条「不像绫边」的线，再向内让 2 条线吃掉抗锯齿'
             '过渡，最后四边各内缩 0.4%。检测不理想的卷子改用脚本里手写的 `crop` '
             '相对坐标——目前只有 `falls`（上方裱有独立题跋纸，检测会把白纸当画心）。')
    L.append('3. **取景缩放**：按 `focus=(fx, fy)` 取景中心等比放大铺满 `720x1584`'
             '（LANCZOS），再裁到目标尺寸。竖轴上部的题跋与留白不进画面，落点放在'
             '中下部的主体山水（主峰／竹丛／水口）。')
    L.append('4. **柔焦**：`GaussianBlur(radius=2.6–3.1)`（720 宽下）——只压掉扫描噪点，'
             '皴法与竹叶的笔锋要留住。')
    L.append('5. **纸白平衡 + 自适应对比归一**：先逐通道把 96 分位（≈纸/绢本色）'
             '映射到接近中性的纸白 `(248, 246, 240)`（强度 `wb` 0.55–0.88，逐场景可调，'
             '只走一部分），陈旧绢本的黄褐底就被清掉；增益在暗部按 smoothstep 回到 1.0'
             '（`knee=96`），否则 B 通道被整体放大，深褐绢上的墨会变成蓝灰；再 '
             '`ImageOps.autocontrast(cutoff=1, preserve_tone=True)` 把原画明暗拉开'
             '（避免纸色发灰导致整幅发闷），`ImageEnhance.Contrast(1.05–1.10)` 略提；'
             f'随后把底图摆进「均值 {BASE_MEAN:.0f}／幅度 ≤{BASE_SPAN:.0f}」的安全工作区间。')
    L.append('6. **色调分级**：每场景单独给参——`ImageEnhance.Color(0.60–0.84)` 降饱和，'
             '再做 `(+2..+5, +1..+2, −4..−6)` 的暖偏通道偏移；夜色改用更低的'
             '`night_color`(0.40–0.55) 与 `(−3..−4, 0..1, +8..+9)` 冷偏。'
             '石色重的 `qinglu`、绢色偏褐的 `falls` 单独压得更低。')
    L.append('7. **按「画作权重」混色**：`Image.blend(art, paper, 1-w)`，'
             '白日 `w=0.85–0.90`（纸色 `#F2EADA`）、夜色 `w=0.65–0.72`（夜墨 `#0F1513`）。'
             '画作占九成左右，山形／竹影／水口都看得清，只是更淡、更柔、更暖。')
    L.append(f'8. **叠垂直渐变**：白日自顶向下纸色 alpha {DAY_GRAD_TOP:.2f} → '
             f'{DAY_GRAD_BOTTOM:.2f}；夜色夜墨 alpha {NIGHT_GRAD_TOP:.2f} → '
             f'{NIGHT_GRAD_BOTTOM:.2f}。顶部更重，让状态栏与标题区更干净。')
    L.append('9. **结构补偿**：混色后再走一次 `UnsharpMask(radius=2.0, '
             'percent=58–70)`；淡墨卷子（`cloud`/`stream`/`winter`）一次锐化仍到不了'
             '结构验收线，就按阶梯升级——先放大明暗幅度（至多 ×1.42），再加'
             f'`UnsharpMask(radius={STRUCTURE_BOOST_RADIUS:.0f}, '
             f'percent={STRUCTURE_BOOST_PCT})` 至多 {STRUCTURE_MAX_BOOST} 次。'
             '半径必须放大：q78 会把半径 2 的高频几乎全量化掉，补偿锐化等于白做。')
    L.append(f'10. **暗角**：四边向内 {VIGNETTE_FRAC*100:.0f}% 用 smoothstep 平滑衰减到 '
             f'-{VIGNETTE_STRENGTH*100:.0f}% 亮度，给画面一点「装裱」纵深，也让四角更安静。')
    L.append('11. **亮度/幅度对齐**：给每张图定目标「平均亮度 + 结构幅度（p5–p95）」'
             '——白日 平均 186–189 / 幅度 84–92，夜色 平均 49–55 / 幅度 60–66；'
             '用一条仿射 LUT（同一条作用于 R/G/B，不改色相）拟合到目标，'
             '迭代若干次并保留「离目标最近」的那一版（避免过冲）。')
    L.append(f'12. **导出**：`convert(\'RGB\').save(path, \'WEBP\', quality={WEBP_QUALITY}, '
             f'method={WEBP_METHOD})`，单张 ≤ {MAX_BYTES // 1024} KB。')
    L.append('')
    L.append('## 逐场景明细')
    L.append('')
    for scene, cfg in SCENES.items():
        api = API_URL.format(cma_id=cfg['cma_id'])
        print_url = (f'https://openaccess-cdn.clevelandart.org/'
                     f'{cfg["accession"]}/{cfg["accession"]}_print.jpg')
        L.append(f'### `{scene}` — {cfg["title"]}')
        L.append('')
        L.append(f'- **作品**：{cfg["title"]}')
        L.append(f'- **作者／年代**：{cfg["artist"]}；{cfg["date"]}')
        L.append(f'- **材质**：{cfg["medium"]}')
        L.append(f'- **馆藏编号（Accession）**：`{cfg["accession"]}`')
        L.append(f'- **来源**：Cleveland Museum of Art, Chinese Art；'
                 f'{cfg["credit_line"]}')
        L.append('- **许可**：CC0 1.0 Universal（Open Access，公有领域贡献）')
        L.append(f'- **API 源**：{api}')
        L.append(f'- **图像源（print）**：{print_url}')
        L.append(f'- **参数**：柔焦 {cfg["blur"]:.1f}px、对比 {cfg["contrast"]:.2f}；'
                 f'白日 画作权重 {cfg["art_w_day"]:.2f} / `color={cfg["color"]:.2f}` / '
                 f'目标 {cfg["day_mean"]:.0f}+{cfg["day_span"]:.0f}；'
                 f'夜色 画作权重 {cfg["art_w_night"]:.2f} / '
                 f'`color={cfg["night_color"]:.2f}` / '
                 f'目标 {cfg["night_mean"]:.0f}+{cfg["night_span"]:.0f}')
        L.append(f'- **取景**：focus `({cfg["focus"][0]:.2f}, {cfg["focus"][1]:.2f})`，'
                 f'画心 ' + ('手写 `crop`' if cfg.get('crop') else '自动检测'))
        if scene in built:
            d, n = built[scene]
            L.append(f'- **产物**：`bg_{scene}_day.webp`（{d/1024:.0f} KB）、'
                     f'`bg_{scene}_night.webp`（{n/1024:.0f} KB）')
        L.append('')
    L.append('## 人工复核提示')
    L.append('')
    L.append('- **v2 变更**：真机截图反馈「洗得太淡」后，画作权重 0.74→0.85–0.90'
             '（夜 0.58→0.65–0.72）、模糊 4–5.5→2.6–3.1、加 UnsharpMask 与 8% 暗角，'
             '结构指标 `lap4_std` 从 1.5–2.6 提到 3.4–4.3（夜 2.6–3.2）。')
    L.append('- **最容易「偏花」**：`qinglu`（石青石绿 + 朱红人物，`color` 压到 0.60、'
             '`unsharp` 只给 48、`wb` 0.88）与 `falls`（绢色偏暖褐、纹理细密）。'
             '真机上若觉得压字，优先把这两张的 `art_w_day` 各降 0.02。')
    L.append('- **结构最足**：`falls`（语音页专用，`art_w_day` 0.90 取上限、`blur` 2.6 取下限，'
             '页面本身还叠压暗层）、`wind_bamboo`（一丛竹横穿画面）、'
             '`snow_bamboo`（竹叶 + 雪坡，细节最密）。')
    L.append('- **最「偏淡」**：`stream`、`peaks`——原作本来就是淡墨，'
             '远看接近纸纹，作为正文底最安全；若觉得太空，把 `art_w_day` 各 +0.02。')
    L.append('- **题跋在画面里**：`winter` 左上、`cloud` 上部的题跋与留白仍在，属于原作'
             '自有内容，不是裁边残留。')
    L.append('')
    L.append('## 复习用图')
    L.append('')
    L.append('- 16 张缩略拼图（4 列 × 4 行，标注 `scene_day` / `scene_night`）：'
             '[`backdrops-contact.png`](smoke/backdrops-contact.png)')
    L.append('- v2 同格式拼图：[`backdrops-contact-v2.png`](smoke/backdrops-contact-v2.png)')
    L.append('- v2 笔触复核（`falls`/`cloud`/`wind_bamboo` 日景 1:1 中央裁 720×720，'
             '不缩放）：[`backdrops-crop-v2.png`](smoke/backdrops-crop-v2.png)')
    L.append('- 正文可读性示意（4 张日景 + 30% 纸色蒙版 + 白色玻璃卡片）：'
             '[`backdrops-check.png`](smoke/backdrops-check.png)')
    L.append('')
    L.append('## 重新生成')
    L.append('')
    L.append('```bash')
    L.append('python tools/build_backdrops.py           # 全量重建（幂等，覆盖生成）')
    L.append('python tools/build_backdrops.py --measure  # 附带指标表与验收判定')
    L.append('```')
    L.append('')
    L.append('源图缺失时脚本会自动从 CMA Open Access API 拉取 `print` 分辨率原图'
             '（**必须**带 `User-Agent`，否则 403），默认缓存于 `C:/tmp/cand/<cmaId>.jpg`。')
    L.append('')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('\n'.join(L) + '\n', encoding='utf-8')
    print(f'[doc] {path.relative_to(REPO_ROOT)}')


# --------------------------------------------------------------- 主流程 ----

def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description='Build Lumo backdrops from CMA CC0 scrolls.')
    ap.add_argument('--only', default=None, help='只处理指定场景，逗号分隔')
    ap.add_argument('--measure', action='store_true',
                    help='打印亮度/std/Laplacian 指标并逐张判定')
    ap.add_argument('--force-download', action='store_true', help='忽略本地源图缓存')
    ap.add_argument('--no-credits', action='store_true', help='不生成出处文档')
    ap.add_argument('--no-contact', action='store_true', help='不生成拼图')
    ap.add_argument('--no-check', action='store_true', help='不生成可读性示意图')
    args = ap.parse_args(argv)

    wanted = list(SCENES)
    if args.only:
        wanted = [s.strip() for s in args.only.split(',') if s.strip()]
        unknown = [s for s in wanted if s not in SCENES]
        if unknown:
            ap.error(f'未知场景: {", ".join(unknown)}')

    print(f'[cfg] 画布 {CANVAS[0]}x{CANVAS[1]}  webp q{WEBP_QUALITY} '
          f'method {WEBP_METHOD}  上限 {MAX_BYTES // 1024} KB/张')
    print(f'[cfg] 输出 {OUT_DIR}')
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    built: dict[str, list] = {}
    tiles = []
    total = 0
    oversized = []
    failed = []

    for scene in wanted:
        cfg = SCENES[scene]
        base = build_base(scene)
        for kind in ('day', 'night'):
            name = f'bg_{scene}_{kind}.webp'
            im, nbytes, m, boost, span_scale = render_final(
                base, cfg, kind, OUT_DIR / name)
            total += nbytes
            if nbytes > MAX_BYTES:
                oversized.append((name, nbytes))
            slot = built.setdefault(scene, [0, 0])
            slot[0 if kind == 'day' else 1] = nbytes
            tiles.append((f'{scene}_{kind}', im.copy(), nbytes))

            ok = check(kind, m)
            if not ok:
                failed.append((name, m))
            print(f'[img] {name:26s} {im.size[0]}x{im.size[1]} '
                  f'{nbytes/1024:6.1f} KB  luma={m["mean"]:6.1f} '
                  f'std={m["std"]:5.1f} lap4={m["lap4"]:6.2f} '
                  f'lap4s={m["lap4_std"]:5.2f} s{span_scale:.2f}/b{boost}  '
                  f'{"ok " if ok else "BAD"}')

    print(f'[sum] 共 {len(wanted)*2} 张，合计 {total/1024:.1f} KB '
          f'(平均 {total/max(1, len(wanted)*2)/1024:.1f} KB，'
          f'上限 {MAX_TOTAL/1024:.0f} KB)')

    rc = 0
    if oversized:
        for name, nbytes in oversized:
            print(f'[!!] {name} 超过单张上限：{nbytes/1024:.1f} KB > '
                  f'{MAX_BYTES//1024} KB', file=sys.stderr)
        rc = 2
    if total > MAX_TOTAL:
        print(f'[!!] 总体积超限：{total/1024:.1f} KB > {MAX_TOTAL/1024:.0f} KB',
              file=sys.stderr)
        rc = 2
    if failed:
        for name, m in failed:
            print(f'[!!] {name} 未达验收线：luma={m["mean"]:.1f} '
                  f'std={m["std"]:.1f} lap4={m["lap4"]:.2f} '
                  f'lap4s={m["lap4_std"]:.2f}', file=sys.stderr)
        rc = rc or 3

    if not args.no_credits:
        write_credits(CREDITS_PATH, {k: tuple(v) for k, v in built.items()})
    if not args.no_contact:
        build_contact_sheet(tiles, CONTACT_PATH)
        build_contact_sheet(tiles, CONTACT_V2_PATH)
        build_crop_sheet(CROP_V2_PATH)
    if not args.no_check:
        build_check_sheet(CHECK_PATH)

    print('[ok] 完成' if rc == 0 else f'[done] 有 {len(failed)} 张未过验收')
    return rc


if __name__ == '__main__':
    raise SystemExit(main())
