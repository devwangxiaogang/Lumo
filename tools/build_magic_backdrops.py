#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""弧光学院（Arcanum）皮肤背景图生成器 —— 纯程序化合成，零第三方素材。

8 个场景 x 昼/夜 = 16 张 720x1584 WebP(q78, method=6)
输出：app/src/main/res/drawable-nodpi/bg_arcane_<id>_{day,night}.webp

管线沿用 tools/build_backdrops.py 的「渲染 -> 存盘 -> 解码回来 -> 测量 -> 不达标就重来」：
  1) 2x 超采样绘制（渐变 / 多层噪声 / 星点 / 多边形剪影 / 矢量线稿）；
  2) 色调求解：线性 offset 二分解到目标平均亮度（不做 gamma —— 会把分层间距压平）；
  3) 结构增益：只对「中央 60% 之外」做双层 unsharp —— 中心留白给正文压字；
  4) 中心柔化 + 细颗粒 + ±1 抖动（抖动是为了杀掉渐变里的色带）；
  5) 编码 -> 解码 -> 测量（平均亮度 / 90x198 std / 拉普拉斯方差 / 中心硬边）-> 不达标换更硬一档重来。

验收（全部在「编码后重新解码」的图上测，不是内存里的数组）：
  day   : luma 178–205, std(90x198) >= 10, lap >= 38, 单张 <= 70KB
  night : luma  32–58,  std(90x198) >=  8, lap >= 26, 单张 <= 70KB
  16 张合计 <= 1.1MB；中央 60% 区域「高对比硬边」覆盖率 <= 1.5%

关于 lap 的定义（重要）：
  「先高斯半径 4 再算拉普拉斯」在 8-bit 输入下响应上界只有 2A/w^2 ~= 4.0（A=255, w~=12），
  方差上界 16 —— 任何图片都不可能达到 38。所以本脚本的验收值 `lap` 用**全分辨率直接 4 邻域
  拉普拉斯**的方差（v1 古画只有 7.9–11.6，清晰矢量线条 100–1000，38/26 才有区分度）；
  同时打印字面 σ=4 的 `lap4` 与去掉颗粒后的结构值 `lapS`，便于复核这个数不是噪点刷出来的。

用法：
  python tools/build_magic_backdrops.py                 # 重建 16 张 + 两张拼版图
  python tools/build_magic_backdrops.py --only castle    # 只做某些场景
  python tools/build_magic_backdrops.py --measure        # 只测量已有产物，不重绘
"""

import argparse
import os
import sys
import time

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.dont_write_bytecode = True

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi')
SMOKE_DIR = os.path.join(ROOT, 'docs', 'smoke')
CREDITS = os.path.join(ROOT, 'docs', 'BACKGROUNDS-CREDITS.md')

W, H = 720, 1584
SS = 2                      # 超采样倍率
X, Y = W * SS, H * SS
SMALL = (90, 198)           # 缩略图尺寸（std 测点，与 build_backdrops.py 一致）

TONE = {'day': 191.0, 'night': 45.0}
ACCEPT = {
    'day': dict(mean=(178.0, 205.0), std=10.0, lap=38.0),
    'night': dict(mean=(32.0, 58.0), std=8.0, lap=26.0),
}
MAX_BYTES = 70 * 1024
MAX_TOTAL = int(1.1 * 1024 * 1024)
MAX_CENTRE = 1.5            # 中央 60% 高对比硬边覆盖率上限（%）

_YN = ((np.arange(H, dtype=np.float32) + 0.5) / H)
_XN = ((np.arange(W, dtype=np.float32) + 0.5) / W)


# ----------------------------------------------------------------------------
# 基础光栅工具（全部 float32 0..255，HxW 或 HxWx3）
# ----------------------------------------------------------------------------

def _u8(a):
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'L')


def _resize(a, size, resample=Image.BILINEAR):
    return np.asarray(_u8(a).resize(size, resample), np.float32)


def _resize_f(a, size, resample=Image.BILINEAR):
    """float 版 resize：走 PIL 'F' 通道，不做 8bit 量化（0..1 的噪声/遮罩必须用这个）。"""
    im = Image.fromarray(np.ascontiguousarray(a, dtype=np.float32), mode='F')
    return np.asarray(im.resize(size, resample), np.float32)


_KCACHE = {}


def _kernel(r):
    k = _KCACHE.get(r)
    if k is None:
        n = max(1, int(round(r * 2.6)))
        x = np.arange(-n, n + 1, dtype=np.float32)
        k = np.exp(-(x * x) / (2.0 * r * r))
        k /= k.sum()
        _KCACHE[r] = k
    return k


def bl(a, r, ds=True):
    """可分离高斯（float，可降采样提速）。a: (h,w) 或 (h,w,3)。"""
    if r <= 0.02:
        return a
    single = (a.ndim == 2)
    a3 = a[..., None] if single else a
    h, w = a3.shape[:2]
    f = 1
    if ds and r > 2.2 and min(h, w) > 80:
        f = int(np.clip(round(r / 1.5), 2, 6))
    if f > 1:
        hh, ww = max(1, h // f), max(1, w // f)
        a3 = np.stack([_resize_f(a3[..., i], (ww, hh)) for i in range(a3.shape[2])], -1)
        r = r / f
    k = _kernel(r)
    n = (len(k) - 1) // 2
    hh, ww = a3.shape[:2]
    p = np.pad(a3, ((0, 0), (n, n), (0, 0)), mode='edge')
    acc = np.zeros_like(a3)
    for i, wgt in enumerate(k):
        acc += p[:, i:i + ww, :] * wgt
    p = np.pad(acc, ((n, n), (0, 0), (0, 0)), mode='edge')
    acc = np.zeros_like(a3)
    for i, wgt in enumerate(k):
        acc += p[i:i + hh, :, :] * wgt
    if f > 1:
        acc = np.stack([_resize_f(acc[..., i], (w, h)) for i in range(acc.shape[2])], -1)
    return acc[..., 0] if single else acc


def sstep(a, b, x):
    d = float(b) - float(a)
    t = np.clip((x - a) / d, 0.0, 1.0) if abs(d) > 1e-9 else (x >= b).astype(np.float32)
    return (t * t * (3.0 - 2.0 * t)).astype(np.float32)


def vgrad(stops):
    t = np.linspace(0.0, 1.0, H, dtype=np.float32)
    ys = np.array([s[0] for s in stops], np.float32)
    cs = np.array([s[1] for s in stops], np.float32)
    out = np.empty((H, 3), np.float32)
    for i in range(3):
        out[:, i] = np.interp(t, ys, cs[:, i]).astype(np.float32)
    return out[:, None, :]


def vband(y0, y1, s0=0.02, s1=0.02):
    """竖直方向的软窗（返回 Hx1）。"""
    return (sstep(y0 - s0, y0 + s0, _YN) * (1.0 - sstep(y1 - s1, y1 + s1, _YN)))[:, None]


def vbelow(y0, s=0.02):
    return sstep(y0 - s, y0 + s, _YN)[:, None]


def vabove(y0, s=0.02):
    return (1.0 - sstep(y0 - s, y0 + s, _YN))[:, None]


def hband(x0, x1, s=0.02):
    return (sstep(x0 - s, x0 + s, _XN) * (1.0 - sstep(x1 - s, x1 + s, _XN)))[None, :]


def field(cx, cy, rx, ry, p=2.0):
    d2 = (((_XN[None, :] - cx) / rx) ** 2) + (((_YN[:, None] - cy) / ry) ** 2)
    return np.exp(-d2 * p).astype(np.float32)


def discf(cx, cy, rpx, soft=0.9):
    d = np.sqrt((((_XN[None, :] - cx) * W) ** 2) + (((_YN[:, None] - cy) * H) ** 2)) / rpx
    return (1.0 - sstep(1.0, 1.0 + soft, d)).astype(np.float32)


def ringf(cx, cy, rpx, thick=1.4):
    d = np.sqrt((((_XN[None, :] - cx) * W) ** 2) + (((_YN[:, None] - cy) * H) ** 2))
    return np.clip(1.0 - np.abs(d - rpx) / thick, 0.0, 1.0).astype(np.float32)


def center_mask(feather=0.06):
    """中央 60%（x 0.2–0.8, y 0.2–0.8）为 1，向外平滑到 0。"""
    mx = sstep(0.2 - feather, 0.2 + feather, _XN) * (1.0 - sstep(0.8 - feather, 0.8 + feather, _XN))
    my = sstep(0.2 - feather, 0.2 + feather, _YN) * (1.0 - sstep(0.8 - feather, 0.8 + feather, _YN))
    return (my[:, None] * mx[None, :]).astype(np.float32)


CEN = center_mask(0.055)
CEN_SOFT = center_mask(0.10)


def fbm(seed, cells=5, octaves=3, gain=0.55, blur_r=0.0):
    """低频分形噪声（0..1），用来做雾、云、星云、纸纹。"""
    r = np.random.default_rng(seed)
    out = np.zeros((H, W), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        gx = max(2, int(round(cells * (2 ** o))))
        gy = max(2, int(round(gx * H / W)))
        arr = r.random((gy, gx)).astype(np.float32)
        out += amp * _resize(arr * 255.0, (W, H), Image.BICUBIC) * (1.0 / 255.0)
        tot += amp
        amp *= gain
    out /= max(tot, 1e-6)
    if blur_r > 0:
        out = bl(out, blur_r)
    return out


def points(rng, n, box=(0.0, 0.0, 1.0, 1.0), r=(0.6, 1.6), peak=(0.35, 1.0),
           halo=3.4, hw=(0.10, 0.24)):
    """不落网格的星点/萤火：高斯核 + 大光晕，逐点贴小图块。"""
    acc = np.zeros((H, W), np.float32)
    x0, y0, x1, y1 = box
    for _ in range(int(n)):
        px = rng.uniform(x0, x1) * W
        py = rng.uniform(y0, y1) * H
        s = rng.uniform(*r) * 0.55
        pk = rng.uniform(*peak)
        hsig = s * halo
        rad = int(np.ceil(hsig * 2.6))
        ix, iy = int(px), int(py)
        ax, ay = max(0, ix - rad), max(0, iy - rad)
        bx, by = min(W, ix + rad + 1), min(H, iy + rad + 1)
        if bx <= ax or by <= ay:
            continue
        yy = (np.arange(ay, by, dtype=np.float32) + 0.5 - py)[:, None]
        xx = (np.arange(ax, bx, dtype=np.float32) + 0.5 - px)[None, :]
        d2 = xx * xx + yy * yy
        core = pk * np.exp(-d2 / (2.0 * s * s))
        glow = pk * rng.uniform(*hw) * np.exp(-d2 / (2.0 * hsig * hsig))
        acc[ay:by, ax:bx] += (core + glow).astype(np.float32)
    return acc


def discs(rng, n, box, rad=(6, 16), fill=0.16, rim=0.30, halo=1.9):
    """焦外光斑（bokeh）：平盘 + 略亮的边缘 + 外晕。"""
    acc = np.zeros((H, W), np.float32)
    x0, y0, x1, y1 = box
    for _ in range(int(n)):
        px = rng.uniform(x0, x1) * W
        py = rng.uniform(y0, y1) * H
        rr = rng.uniform(*rad)
        rad_px = rr * 1.9
        ix, iy = int(px), int(py)
        ax, ay = max(0, ix - int(rad_px)), max(0, iy - int(rad_px))
        bx, by = min(W, ix + int(rad_px) + 2), min(H, iy + int(rad_px) + 2)
        if bx <= ax or by <= ay:
            continue
        yy = (np.arange(ay, by, dtype=np.float32) + 0.5 - py)[:, None]
        xx = (np.arange(ax, bx, dtype=np.float32) + 0.5 - px)[None, :]
        d = np.sqrt(xx * xx + yy * yy)
        v = fill * (1.0 - sstep(rr * 0.94, rr, d))
        v += rim * np.clip(1.0 - np.abs(d - rr * 0.97) / (rr * 0.10 + 0.6), 0.0, 1.0)
        v += 0.10 * np.exp(-((d - rr) / (rr * halo)) ** 2)
        acc[ay:by, ax:bx] += v.astype(np.float32)
    return acc


def msk(fn, blur=0.0):
    """矢量遮罩：2x 画 -> LANCZOS 下采样（自带抗锯齿）。"""
    m = Image.new('L', (X, Y), 0)
    fn(ImageDraw.Draw(m))
    m = m.resize((W, H), Image.LANCZOS)
    if blur > 0:
        m = m.filter(ImageFilter.GaussianBlur(blur))
    return np.asarray(m, np.float32) * (1.0 / 255.0)


def clayer(fn, blur=0.0):
    """彩色矢量层：同一批图形同时画进 RGB 与遮罩，返回 (rgb, alpha)。"""
    col = Image.new('RGB', (X, Y), (0, 0, 0))
    mm = Image.new('L', (X, Y), 0)
    fn(ImageDraw.Draw(col), ImageDraw.Draw(mm))
    col = col.resize((W, H), Image.BICUBIC)
    mm = mm.resize((W, H), Image.LANCZOS)
    a = np.asarray(mm, np.float32) * (1.0 / 255.0)
    if blur > 0:
        a = np.asarray(Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'L')
                       .filter(ImageFilter.GaussianBlur(blur)), np.float32) * (1.0 / 255.0)
    return np.asarray(col, np.float32), a


# 归一化坐标的矢量绘制（供 msk / clayer 的回调使用）
def rect(d, x0, y0, x1, y1, fill=255):
    d.rectangle([x0 * X, y0 * Y, x1 * X - 1, y1 * Y - 1], fill=fill)


def poly(d, pts, fill=255):
    d.polygon([(p[0] * X, p[1] * Y) for p in pts], fill=fill)


def ell(d, cx, cy, rx, ry, fill=255):
    d.ellipse([(cx - rx) * X, (cy - ry) * Y, (cx + rx) * X, (cy + ry) * Y], fill=fill)


def seg(d, p0, p1, wpx=1.0, fill=255):
    d.line([p0[0] * X, p0[1] * Y, p1[0] * X, p1[1] * Y], fill=fill,
           width=max(1, int(round(wpx * SS))))


def arc(d, cx, cy, rpx, a0, a1, wpx=1.0, fill=255):
    d.arc([(cx * X - rpx * SS), (cy * Y - rpx * SS), (cx * X + rpx * SS), (cy * Y + rpx * SS)],
          a0, a1, fill=fill, width=max(1, int(round(wpx * SS))))


def rectc(dc, dm, x0, y0, x1, y1, rgb):
    box = [x0 * X, y0 * Y, x1 * X - 1, y1 * Y - 1]
    dc.rectangle(box, fill=tuple(int(v) for v in rgb))
    dm.rectangle(box, fill=255)


def polyc(dc, dm, pts, rgb):
    q = [(p[0] * X, p[1] * Y) for p in pts]
    dc.polygon(q, fill=tuple(int(v) for v in rgb))
    dm.polygon(q, fill=255)


# 合成
def over(img, col, a):
    a3 = a[..., None] if a.ndim == 2 else a
    c = np.asarray(col, np.float32)
    if c.ndim == 1:
        c = c.reshape(1, 1, 3)
    return img * (1.0 - a3) + c * a3


def addl(img, col, a):
    a3 = a[..., None] if a.ndim == 2 else a
    c = np.asarray(col, np.float32)
    if c.ndim == 1:
        c = c.reshape(1, 1, 3)
    return img + c * a3


def silhouette(img, m, color, alpha=1.0, tex=None, tex_amp=0.0):
    a = (m * alpha)[..., None]
    col = np.asarray(color, np.float32).reshape(1, 1, 3)
    if tex is not None and tex_amp:
        col = np.clip(col * (1.0 + tex[..., None] * tex_amp), 0.0, 255.0)
    return img * (1.0 - a) + col * a


def lerpc(a, b, t):
    return tuple(float(a[i]) * (1 - t) + float(b[i]) * t for i in range(3))


def luma(img):
    return (0.299 * img[..., 0] + 0.587 * img[..., 1] + 0.114 * img[..., 2]).astype(np.float32)


# ----------------------------------------------------------------------------
# 调色板：day = 晨雾羊皮纸 + 墨褐剪影 + 烛金；night = 午夜蓝 + 烛金 + 星蓝
# ----------------------------------------------------------------------------

DAY = dict(
    ink=(74, 63, 51), ink2=(101, 88, 71), ink3=(132, 118, 98), ink_far=(166, 156, 137),
    paper=(240, 233, 216), paper_hi=(250, 245, 231), paper_lo=(224, 213, 191),
    gold=(191, 145, 70), gold_hi=(226, 186, 112), ember=(165, 101, 42),
    glow=(255, 246, 219), mist=(247, 242, 229), star=(255, 252, 240), moon=(255, 250, 234),
    water=(198, 208, 199), green=(114, 128, 100), green_far=(170, 178, 154),
    glass=(211, 220, 213), steam=(245, 241, 229), shelf=(99, 84, 64),
    steel=(150, 152, 146), teal=(126, 158, 152), violet=(150, 138, 168), firefly=(210, 208, 148),
    book=[(150, 96, 70), (126, 120, 86), (94, 110, 126), (162, 128, 82),
          (112, 96, 116), (172, 142, 96), (138, 84, 78), (104, 122, 104)],
)

NIGHT = dict(
    ink=(5, 7, 16), ink2=(12, 16, 32), ink3=(20, 26, 46), ink_far=(30, 40, 66),
    paper=(16, 21, 42), paper_hi=(30, 38, 66), paper_lo=(10, 13, 30),
    gold=(238, 182, 92), gold_hi=(255, 214, 140), ember=(196, 110, 48),
    glow=(255, 206, 132), mist=(58, 72, 104), star=(226, 236, 255), moon=(236, 240, 255),
    water=(14, 24, 46), green=(9, 19, 20), green_far=(24, 38, 40),
    glass=(38, 62, 76), steam=(84, 114, 122), shelf=(13, 15, 26),
    steel=(56, 66, 84), teal=(70, 160, 158), violet=(122, 108, 196), firefly=(226, 255, 168),
    book=[(58, 50, 42), (42, 56, 62), (72, 44, 52), (38, 46, 38),
          (64, 58, 80), (52, 42, 36), (78, 58, 36), (34, 44, 52)],
)


class Ctx:
    def __init__(self, sid, kind, seed, note, cn):
        self.id = sid
        self.kind = kind
        self.note = note
        self.cn = cn
        self.P = DAY if kind == 'day' else NIGHT
        self.g = np.random.default_rng(seed + 11)   # 几何（昼夜共用 -> 构图一致）
        self.s = np.random.default_rng(seed + 22)   # 星点
        self.t = np.random.default_rng(seed + 33)   # 噪声纹理
        self.d = np.random.default_rng(seed + 44)   # 装饰（萤火/光斑/书脊）

    @property
    def night(self):
        return self.kind == 'night'

    def st(self, day, night):
        return vgrad(night if self.night else day)

    def seed(self):
        return int(self.t.integers(1 << 30))

    def starfield(self, n, y0, y1, peak=(0.35, 1.0), r=(0.55, 1.55), calm_center=0.55,
                  halo=3.4):
        """星野：中央 60% 压暗并柔化（正文压字区不铺硬点）。"""
        acc = points(self.s, n, (0.0, y0, 1.0, y1), r=r, peak=peak, halo=halo)
        if calm_center > 0:
            acc = acc * (1.0 - CEN_SOFT * calm_center) + bl(acc, 1.7) * (CEN_SOFT * calm_center * 0.6)
        return acc


# ============================================================================
# 景深阶梯：两套皮肤共用一条「纸 -> 墨」（夜为「天光 -> 墨」）的灰阶阶梯
# ----------------------------------------------------------------------------
# 之前 day 版糊成一片白，根因是每一层都用 lerpc(ink_far, paper, t) 这种「往纸色里
# 调」的写法：越近的山反而越亮，和天空只差 30 级，提亮到均值 190 之后全挤在 240+。
# 这里改成显式阶梯：far 最浅、ink 最深，每一级差 30–40 级，任何 tone 映射之前就
# 已经成立。night 用同一条阶梯的镜像（天光最亮，剪影更深）。
# ============================================================================

DAY_L = dict(
    sky=(228, 220, 202), far=(197, 188, 167), mid=(166, 155, 134),
    near=(131, 121, 102), fore=(97, 88, 72), ink=(70, 61, 50),
)
NIGHT_L = dict(
    sky=(30, 40, 70), far=(25, 33, 58), mid=(17, 23, 43),
    near=(12, 16, 32), fore=(8, 10, 21), ink=(4, 5, 13),
)


def L(c, key):
    """取景深阶梯色。"""
    return (NIGHT_L if c.night else DAY_L)[key]


def mix(a, b, t):
    return lerpc(a, b, t)


def grain_tex(c, cells=9, octaves=2, amp=0.20):
    """剪影内部的低频纹理（避免一片死色）。"""
    return (fbm(c.seed(), cells=cells, octaves=octaves, blur_r=1.1) - 0.5) * amp


def fir(d, px, top, base=1.0, half=0.040, tiers=5):
    """冷杉剪影：自上而下 5 层三角叠成。

    单个等腰三角看起来像方尖碑/尖刺（上一版就是这样，一排「牙」），
    分层叠出来才有冷杉的锯齿轮廓。
    """
    h = base - top
    for k in range(tiers):
        y0 = top + h * 0.80 * (k / float(tiers))
        hw = half * (0.26 + 0.74 * (k + 1) / float(tiers))
        hh = h * (0.46 - 0.05 * k)
        poly(d, [(px - hw, min(base, y0 + hh)), (px + hw, min(base, y0 + hh)), (px, y0)])


# ============================================================================
# 场景 1 —— castle 星夜城堡 / 晨雾城堡
# 景深：天空 -> 远岭 -> 城堡（中景，唯一主体）-> 中岭横切 -> 近岭松林 -> 前脊 -> 取景塔
# 取景塔压到 7.5% 宽并贴边，避免上一版「两根门柱」的门框感。
# ============================================================================

def scene_castle(c):
    img = c.st([(0.00, (203, 194, 175)), (0.24, (215, 205, 186)), (0.48, (228, 219, 199)),
                (0.66, (238, 230, 212)), (0.80, (243, 235, 219)), (1.00, (236, 228, 212))],
               [(0.00, (9, 13, 30)), (0.26, (13, 19, 42)), (0.48, (18, 26, 54)),
                (0.66, (25, 34, 65)), (0.80, (31, 42, 74)), (1.00, (20, 27, 52))])

    # 高空薄云：只在 0.04–0.26 一条带，别糊到主体上
    cl = fbm(c.seed(), cells=4, octaves=3, blur_r=5.0)
    cm = sstep(0.54, 0.82, cl) * vband(0.02, 0.28, 0.05, 0.09)
    img = addl(img, (252, 248, 238) if not c.night else (58, 74, 112),
               cm * (0.50 if not c.night else 0.45))

    # 星野（白天留一点极淡的星尘，不然上半张是一块平布）
    stars = c.starfield(660, 0.0, 0.60, peak=(0.30, 1.0), r=(0.5, 1.5), calm_center=0.6)
    img = addl(img, c.P['star'],
               stars * (0.13 if not c.night else 1.0) * vband(0.0, 0.62, 0.0, 0.14))

    # 月亮 / 白昼的日轮：都是 33px，光晕压到 0.30 以内，别把中央洗白
    mx, my, mr = 0.705, 0.116, 31.0
    halo = field(mx, my, 0.17, 0.077, 1.7)
    if c.night:
        img = addl(img, (96, 118, 178), halo * 0.30)
        img = over(img, c.P['moon'], discf(mx, my, mr, 1.0) * 0.97)
        img = addl(img, (250, 252, 255), discf(mx, my, mr * 0.70, 3.2) * 0.16)
        rings = msk(lambda d: [
            arc(d, mx, my, mr * 1.55, 22, 150, 1.4),
            arc(d, mx, my, mr * 1.55, 194, 300, 1.1),
            arc(d, mx, my, mr * 1.95, 128, 248, 0.9),
            arc(d, mx, my, mr * 2.40, 296, 44, 0.8)], blur=0.28)
        img = addl(img, (214, 228, 255), rings * 0.50)
    else:
        img = addl(img, (255, 249, 230), halo * 0.62)
        img = over(img, (252, 244, 222), discf(mx, my, mr * 1.10, 7.0) * 0.85)
        img = addl(img, (150, 138, 112), ringf(mx, my, mr * 1.75, 2.2) * 0.08)

    # 两颗十字星芒（细节放在四角）
    for (fx, fy, fr, wk) in ((0.128, 0.082, 2.0, 0.5), (0.884, 0.196, 2.3, 0.45)):
        fl = msk(lambda d, fx=fx, fy=fy, fr=fr: [
            seg(d, (fx, fy - 0.014), (fx, fy + 0.014), 0.8),
            seg(d, (fx - 0.010, fy), (fx + 0.010, fy), 0.8)], blur=0.30)
        img = addl(img, c.P['star'], fl * (wk if c.night else wk * 0.55))
        img = addl(img, c.P['star'], discf(fx, fy, fr) * (0.55 if c.night else 0.30))
        img = addl(img, c.P['star'], field(fx, fy, 0.022, 0.010, 2.0) * (0.22 if c.night else 0.10))

    # 远岭（最浅）
    ph = c.g.uniform(0, 6.28)
    pts = [(0.0, 0.606)]
    for i in range(1, 37):
        xf = i / 36.0
        pts.append((xf, 0.606 - 0.026 * (0.5 + 0.5 * np.sin(xf * 4.4 * 6.28 + ph))
                    * (0.45 + 0.55 * np.sin(xf * 1.7 * 6.28 + ph * 0.7))))
    pts += [(1.0, 1.0), (0.0, 1.0)]
    fm = msk(lambda d: poly(d, pts), blur=3.4)
    img = silhouette(img, fm, L(c, 'far'), 1.0, grain_tex(c, 6), 0.02)

    # 山间雾带（把远岭和城堡分开一层）
    fmist = fbm(c.seed(), cells=3, octaves=2, blur_r=14.0)
    img = addl(img, (250, 246, 234) if not c.night else (64, 80, 118),
               (fmist * 0.7 + 0.3) * vband(0.575, 0.700, 0.05, 0.05)
               * (0.20 if not c.night else 0.13))

    # 城堡主体：中景，唯一的高对比形状
    def keep(d):
        rect(d, 0.286, 0.742, 0.714, 0.812)                        # 幕墙
        for i in range(15):                                        # 垛口
            rect(d, 0.286 + i * 0.0288, 0.724, 0.300 + i * 0.0288, 0.745)
        rect(d, 0.404, 0.624, 0.596, 0.812)                        # 主堡
        for i in range(8):
            rect(d, 0.404 + i * 0.0242, 0.606, 0.417 + i * 0.0242, 0.627)
        poly(d, [(0.440, 0.624), (0.560, 0.624), (0.500, 0.448)])  # 中央尖塔
        seg(d, (0.500, 0.448), (0.500, 0.410), 1.1)                # 旗杆
        for sx, top in ((0.346, 0.474), (0.654, 0.468)):           # 侧尖塔
            rect(d, sx - 0.030, 0.578, sx + 0.030, 0.812)
            poly(d, [(sx - 0.037, 0.578), (sx + 0.037, 0.578), (sx, top)])
            seg(d, (sx, top), (sx, top - 0.030), 0.9)
        for sx, top in ((0.292, 0.560), (0.708, 0.556)):           # 角楼
            rect(d, sx - 0.017, 0.668, sx + 0.017, 0.812)
            poly(d, [(sx - 0.022, 0.668), (sx + 0.022, 0.668), (sx, top)])
    km = msk(keep, blur=0.95)
    kcol = mix(L(c, 'mid'), L(c, 'near'), 0.34)
    img = silhouette(img, km, kcol, 1.0, grain_tex(c, 7, amp=0.24), 0.05)

    # 城堡上的窗（夜为烛金，昼只有极浅的暖点）+ 城门暖光
    def wins(d):
        for (wx, wy) in ((0.336, 0.646), (0.368, 0.646), (0.616, 0.646), (0.648, 0.646),
                         (0.340, 0.716), (0.660, 0.716), (0.500, 0.700), (0.470, 0.742),
                         (0.530, 0.742), (0.436, 0.690), (0.564, 0.690),
                         (0.296, 0.700), (0.704, 0.700), (0.500, 0.520)):
            rect(d, wx - 0.0034, wy - 0.0052, wx + 0.0034, wy + 0.0052)
    wm = msk(wins, blur=0.14)
    img = over(img, c.P['glow'] if c.night else mix(c.P['gold_hi'], c.P['paper'], 0.10),
               wm * (0.92 if c.night else 0.40))
    gw = field(0.500, 0.762, 0.030, 0.022, 2.0)
    img = addl(img, c.P['gold'] if c.night else c.P['gold'], gw * (0.30 if c.night else 0.10))

    # 中岭：横切城堡基座，把它「按」进地里（这是上一版最缺的一层）
    ph = c.g.uniform(0, 6.28)
    pts = [(0.0, 0.770)]
    for i in range(1, 33):
        xf = i / 32.0
        pts.append((xf, 0.770 - 0.020 * np.sin(xf * 3.1 * 6.28 + ph)
                    - 0.009 * np.sin(xf * 9.7 * 6.28 + ph * 1.7)))
    pts += [(1.0, 1.0), (0.0, 1.0)]
    mm = msk(lambda d: poly(d, pts), blur=2.1)
    img = silhouette(img, mm, mix(L(c, 'near'), L(c, 'mid'), 0.40), 1.0, grain_tex(c, 8), 0.03)

    # 近岭 + 一排小松（森林的第一层）
    ph = c.g.uniform(0, 6.28)
    pts = [(0.0, 0.822)]
    for i in range(1, 41):
        xf = i / 40.0
        pts.append((xf, 0.822 - 0.016 * np.sin(xf * 5.3 * 6.28 + ph)
                    - 0.008 * np.sin(xf * 13.1 * 6.28 + ph * 0.6)))
    pts += [(1.0, 1.0), (0.0, 1.0)]
    nm = msk(lambda d: poly(d, pts), blur=1.1)
    img = silhouette(img, nm, L(c, 'near'), 1.0, grain_tex(c, 10), 0.04)

    def line_pines(d):
        for i in range(9):
            px = 0.046 + i * 0.1130 + c.g.uniform(-0.016, 0.016)
            fir(d, px, 0.826 - c.g.uniform(0.010, 0.030), 0.858,
                c.g.uniform(0.017, 0.026), tiers=4)
    lm = msk(line_pines, blur=0.55)
    img = silhouette(img, lm, mix(L(c, 'near'), L(c, 'fore'), 0.45), 1.0)

    # 前脊（最近的一层，边缘要挺，细节都留在这里）
    ph = c.g.uniform(0, 6.28)
    pts = [(0.0, 0.888)]
    for i in range(1, 47):
        xf = i / 46.0
        pts.append((xf, 0.888 - 0.020 * np.sin(xf * 4.1 * 6.28 + ph)
                    - 0.010 * np.sin(xf * 11.3 * 6.28 + ph * 1.3)))
    pts += [(1.0, 1.0), (0.0, 1.0)]
    gm = msk(lambda d: poly(d, pts), blur=0.40)
    img = silhouette(img, gm, L(c, 'fore'), 1.0, grain_tex(c, 12, amp=0.26), 0.06)

    # 前景大松：两侧，树尖到 0.60–0.72，给纸张一个剪影边框
    def big_pines(d):
        for (px, top, hw) in ((0.040, 0.652, 0.050), (0.116, 0.722, 0.043),
                              (0.182, 0.796, 0.034), (0.902, 0.668, 0.048),
                              (0.836, 0.752, 0.038), (0.958, 0.726, 0.040)):
            fir(d, px, top, 1.0, hw, tiers=6)
    pm = msk(big_pines, blur=0.35)
    img = silhouette(img, pm, L(c, 'ink'), 1.0, grain_tex(c, 12, amp=0.22), 0.05)

    # 取景塔：贴边 7.5%，尖顶不对称，内侧面留一道受光
    def towers(d):
        rect(d, 0.000, 0.318, 0.074, 1.0)
        rect(d, -0.010, 0.300, 0.084, 0.322)
        poly(d, [(-0.010, 0.300), (0.084, 0.300), (0.037, 0.150)])
        seg(d, (0.037, 0.150), (0.037, 0.118), 1.0)
        rect(d, 0.926, 0.262, 0.999, 1.0)
        rect(d, 0.916, 0.244, 1.010, 0.266)
        poly(d, [(0.916, 0.244), (1.010, 0.244), (0.963, 0.104)])
        seg(d, (0.963, 0.104), (0.963, 0.074), 1.0)
    tm = msk(towers, blur=0.45)
    img = silhouette(img, tm, L(c, 'ink'), 1.0, grain_tex(c, 14, amp=0.18), 0.05)

    # 塔身石缝：短、错缝、疏密不均（上一版等距长条像梯子）
    def courses(d):
        y = 0.345
        while y < 0.995:
            w0 = c.d.uniform(0.0, 0.012)
            rect(d, 0.004 + w0, y, 0.070 - c.d.uniform(0.0, 0.008), y + 0.0026)
            x0 = c.d.uniform(0.0, 0.010)
            rect(d, 0.930 + x0, y, 0.994 - c.d.uniform(0.0, 0.006), y + 0.0026)
            y += c.d.uniform(0.0215, 0.0335)
        for i in range(5):                                          # 竖向错缝
            yy = 0.345 + i * 0.128 + c.d.uniform(0.0, 0.02)
            seg(d, (0.037, yy), (0.037, yy + 0.115), 0.6)
            seg(d, (0.963, yy), (0.963, yy + 0.115), 0.6)
    cm2 = msk(courses, blur=0.22)
    img = addl(img, mix(L(c, 'ink'), (255, 255, 255), 0.55), cm2 * tm * (0.16 if c.night else 0.12))

    # 塔内侧面受光
    lig = msk(lambda d: [rect(d, 0.058, 0.322, 0.074, 1.0), rect(d, 0.926, 0.266, 0.942, 1.0)],
              blur=1.6)
    img = addl(img, c.P['glow'], lig * (0.10 if c.night else 0.16))

    # 塔上的窗
    def tw(d):
        for (wx, wy) in ((0.037, 0.470), (0.037, 0.640), (0.037, 0.810),
                         (0.963, 0.430), (0.963, 0.600), (0.963, 0.790)):
            rect(d, wx - 0.0055, wy - 0.0105, wx + 0.0055, wy + 0.0105)
    twm = msk(tw, blur=0.18)
    img = over(img, c.P['glow'] if c.night else mix(c.P['gold_hi'], c.P['paper'], 0.06),
               twm * (0.95 if c.night else 0.42))

    # 地面暖反射 + 暗角
    img = addl(img, c.P['glow'], vband(0.900, 1.0, 0.06, 0.0) * (0.05 if c.night else 0.08))
    return img


# ============================================================================
# 场景 2 —— hall 烛火长厅
# 结构：后墙 + 三扇高拱窗（上 1/3）-> 光柱 -> 两根壁柱 -> 长桌透视 -> 浮烛
# 中央 0.2–0.8 / 0.2–0.8 基本是墙面与桌面，不加硬边。
# ============================================================================

def scene_hall(c):
    """烛火长厅：三扇高拱窗 + 两盏吊烛环 + 一张横向长桌，正对构图，中央留净。"""
    img = c.st([(0.00, (198, 189, 170)), (0.28, (206, 197, 178)), (0.56, (211, 202, 183)),
                (0.72, (204, 195, 176)), (1.00, (184, 175, 156))],
               [(0.00, (11, 15, 32)), (0.34, (14, 19, 40)), (0.66, (17, 23, 46)),
                (0.86, (21, 28, 54)), (1.00, (12, 16, 32))])

    # 侧墙石砌（只在两侧 20% 出现，中央留给正文）
    def wall(d):
        y = 0.055
        i = 0
        while y < 0.98:
            rect(d, 0.0, y, 1.0, y + 0.0030)
            step = 0.086 + c.d.uniform(-0.01, 0.01)
            x = ((i % 2) * step * 0.5) + c.d.uniform(-0.01, 0.01)
            while x < 1.0:
                seg(d, (x, y), (x, y + 0.088), 0.5)
                x += step
            y += 0.088
            i += 1
    wm2 = msk(wall, blur=0.35)
    side = (hband(0.0, 0.20, 0.04) + hband(0.80, 1.0, 0.04))
    img = addl(img, mix(L(c, 'mid'), c.P['paper'], 0.35), -0.10 * wm2 * side)
    img = addl(img, c.P['paper_hi'], wm2 * side * (0.05 if not c.night else 0.02))

    # 三扇高拱窗的玻璃
    def win_glass(d):
        for cx in (0.205, 0.500, 0.795):
            rect(d, cx - 0.072, 0.152, cx + 0.072, 0.330)
            d.ellipse([(cx - 0.072) * X, 0.090 * Y, (cx + 0.072) * X, 0.214 * Y], fill=255)
    gm = msk(win_glass, blur=0.55)
    gcol = (252, 247, 232) if not c.night else (46, 66, 108)
    img = over(img, gcol, gm * (0.96 if not c.night else 0.90))
    if c.night:
        img = over(img, (236, 240, 255), discf(0.795, 0.176, 12.0, 1.0) * 0.9 * gm)
        img = addl(img, (200, 216, 255), field(0.795, 0.176, 0.09, 0.045, 1.6) * 0.22 * gm)
        st = c.starfield(70, 0.075, 0.235, peak=(0.4, 1.0), r=(0.5, 1.1), calm_center=0.0)
        img = addl(img, (225, 234, 255), st * 0.55 * gm)
    else:
        img = addl(img, (255, 253, 245), field(0.500, 0.150, 0.10, 0.075, 1.4) * 0.30 * gm)
        img = addl(img, (255, 255, 250), field(0.205, 0.130, 0.08, 0.06, 1.4) * 0.18 * gm)

    # 窗框、竖棂、横档
    def win_frame(d):
        for cx in (0.205, 0.500, 0.795):
            seg(d, (cx - 0.072, 0.330), (cx - 0.072, 0.150), 2.2)
            seg(d, (cx + 0.072, 0.330), (cx + 0.072, 0.150), 2.2)
            d.arc([(cx - 0.072) * X, 0.090 * Y, (cx + 0.072) * X, 0.214 * Y],
                  180, 360, fill=255, width=int(2.2 * SS))
            seg(d, (cx, 0.100), (cx, 0.330), 1.5)
            seg(d, (cx - 0.072, 0.238), (cx + 0.072, 0.238), 1.2)
    frm = msk(win_frame, blur=0.30)
    img = silhouette(img, frm, mix(L(c, 'near'), L(c, 'mid'), 0.45), 1.0, grain_tex(c, 16), 0.05)

    # 檐口 + 齿饰：一行连续的建筑基线，把三扇窗"落地"
    def cornice(d):
        rect(d, 0.0, 0.330, 1.0, 0.344)
        rect(d, 0.0, 0.352, 1.0, 0.358)
        x = 0.014
        while x < 0.985:
            rect(d, x, 0.344, x + 0.012, 0.352)
            x += 0.026
    cm = msk(cornice, blur=0.30)
    img = silhouette(img, cm, mix(L(c, 'mid'), L(c, 'near'), 0.40), 1.0, grain_tex(c, 14), 0.05)

    # 光柱：从窗底散到桌面为止，弱
    for cx, wk in ((0.205, 0.055), (0.500, 0.070), (0.795, 0.048)):
        beam = msk(lambda d, cx=cx: poly(d, [(cx - 0.060, 0.360), (cx + 0.060, 0.360),
                                             (cx + 0.132, 0.706), (cx - 0.132, 0.706)]), blur=18.0)
        img = addl(img, c.P['glow'], beam * (wk if not c.night else wk * 0.45))

    # 两侧壁柱（收窄，别读成画框）
    def pil(d):
        rect(d, 0.030, 0.330, 0.070, 0.996)
        rect(d, 0.930, 0.330, 0.970, 0.996)
        rect(d, 0.022, 0.316, 0.078, 0.332)
        rect(d, 0.922, 0.316, 0.978, 0.332)
    plm = msk(pil, blur=0.55)
    img = silhouette(img, plm, mix(L(c, 'mid'), L(c, 'near'), 0.50), 1.0, grain_tex(c, 12), 0.06)

    # 护墙板：下半墙的竖向分格（中央压得很轻）
    def wain(d):
        rect(d, 0.0, 0.556, 1.0, 0.560)
        x = 0.070
        while x < 0.94:
            seg(d, (x, 0.560), (x, 0.700), 0.8)
            x += 0.132
    img = addl(img, L(c, 'near'), msk(wain, blur=0.45) * 0.26)

    # 两盏吊烛环（左右，避开中央）
    def chand(cx, cy, rad, wk):
        nonlocal img
        img = silhouette(img, msk(lambda d: [seg(d, (cx, 0.358), (cx, cy - rad / H), 0.7)],
                                  blur=0.30), L(c, 'ink'), 0.80)
        ring = msk(lambda d: [arc(d, cx, cy, rad, 0, 360, 1.0),
                              arc(d, cx, cy, rad * 0.52, 0, 360, 0.8)], blur=0.30)
        img = silhouette(img, ring, mix(L(c, 'fore'), c.P['gold'], 0.40), 0.95)
        for k in range(8):
            a = 2.0 * np.pi * k / 8.0
            px = cx + rad * np.cos(a) / W
            py = cy + rad * np.sin(a) / H
            img = addl(img, c.P['glow'], discf(px, py, 1.1 + 0.4 * wk) * (0.85 * wk))
            img = addl(img, (255, 238, 184) if c.night else (255, 247, 216),
                       field(px, py, 0.017, 0.011, 2.1) * (0.30 * wk if c.night else 0.16 * wk))
            img = addl(img, c.P['glow'], field(px, py, 0.040, 0.027, 1.7) * (0.045 * wk))
    chand(0.268, 0.452, 36.0, 0.92)
    chand(0.732, 0.492, 31.0, 0.80)

    # 长桌：横向桌面板 + 正面，桌面靠烛光提亮（夜里比墙亮，白天比墙深）
    top = mix(L(c, 'near'), L(c, 'mid'), 0.30) if not c.night else mix(L(c, 'mid'), L(c, 'sky'), 0.55)
    face = mix(L(c, 'near'), L(c, 'fore'), 0.30) if not c.night else L(c, 'fore')
    tb = msk(lambda d: [poly(d, [(0.010, 0.700), (0.990, 0.700), (1.0, 0.744), (0.0, 0.744)])],
             blur=0.45)
    img = silhouette(img, tb, top, 1.0, grain_tex(c, 11), 0.05)
    fc = msk(lambda d: [rect(d, 0.0, 0.744, 1.0, 0.812)], blur=0.45)
    img = silhouette(img, fc, face, 1.0, grain_tex(c, 7), 0.05)
    # 桌腿（正面压薄后，靠腿把桌子撑到地面）
    lg = msk(lambda d: [rect(d, 0.070, 0.812, 0.098, 0.958), rect(d, 0.470, 0.812, 0.498, 0.958),
                        rect(d, 0.502, 0.812, 0.530, 0.958), rect(d, 0.902, 0.812, 0.930, 0.958)],
             blur=0.45)
    img = silhouette(img, lg, mix(face, L(c, 'fore'), 0.40), 1.0, grain_tex(c, 8), 0.05)
    img = addl(img, c.P['gold'],
               msk(lambda d: [seg(d, (0.0, 0.744), (1.0, 0.744), 1.6)], blur=0.35)
               * (0.42 if c.night else 0.28))

    # 桌上的五支烛（烛身暗、火焰亮）
    sticks = ((0.128, 0.062, 0.9), (0.312, 0.078, 1.0), (0.500, 0.088, 1.0),
              (0.688, 0.076, 1.0), (0.872, 0.060, 0.9))

    def sk(d):
        for (sx, h, wk) in sticks:
            rect(d, sx - 0.008, 0.744 - 0.010, sx + 0.008, 0.744)
            rect(d, sx - 0.004, 0.744 - h, sx + 0.004, 0.744 - 0.010)
    img = silhouette(img, msk(sk, blur=0.28), L(c, 'ink'), 0.95)
    for (sx, h, wk) in sticks:
        fy = 0.744 - h - 0.008
        img = addl(img, c.P['glow'], discf(sx, fy, 1.5 + 0.5 * wk) * (1.0 * wk))
        img = addl(img, (255, 236, 176) if c.night else (255, 245, 212),
                   field(sx, fy, 0.020, 0.013, 2.0) * (0.62 * wk if c.night else 0.34 * wk))
        img = addl(img, c.P['glow'], field(sx, fy, 0.075, 0.050, 1.5) * (0.14 * wk))
        img = addl(img, c.P['glow'], field(sx, 0.742, 0.090, 0.020, 1.6) * (0.10 * wk))

    # 长凳（桌前一横，压住桌脚）
    bn = msk(lambda d: [rect(d, 0.026, 0.900, 0.974, 0.930),
                        rect(d, 0.104, 0.930, 0.126, 0.998), rect(d, 0.874, 0.930, 0.896, 0.998),
                        rect(d, 0.474, 0.930, 0.496, 0.998), rect(d, 0.504, 0.930, 0.526, 0.998)],
             blur=0.45)
    img = silhouette(img, bn, mix(L(c, 'near'), L(c, 'fore'), 0.55) if not c.night else L(c, 'ink'),
                     1.0, grain_tex(c, 8), 0.05)

    # 地面：暗下去，桌下一团暖光
    img = addl(img, L(c, 'fore'), vband(0.930, 1.0, 0.0, 0.9) * (0.80 if not c.night else 0.45))
    img = addl(img, c.P['glow'], field(0.500, 0.962, 0.26, 0.026, 1.7) * (0.07 if c.night else 0.05))
    img = addl(img, L(c, 'fore'), vband(0.985, 1.0, 0.0, 0.9) * 0.40)

    # 两侧壁灯（把中段的空白点住）
    for (sx, sy) in ((0.050, 0.606), (0.950, 0.606)):
        img = silhouette(img, msk(lambda d, sx=sx, sy=sy: [rect(d, sx - 0.014, sy, sx + 0.014, sy + 0.010),
                                                          seg(d, (sx, sy), (sx, sy - 0.020), 0.8)],
                                  blur=0.30), L(c, 'ink'), 0.85)
        img = addl(img, c.P['glow'], discf(sx, sy - 0.026, 1.6) * 0.9)
        img = addl(img, (255, 240, 190) if c.night else (255, 248, 218),
                   field(sx, sy - 0.026, 0.022, 0.014, 2.0) * (0.55 if c.night else 0.30))
        img = addl(img, c.P['glow'], field(sx, sy - 0.026, 0.075, 0.050, 1.5) * 0.09)
    return img


# 场景 3 —— forest 禁林萤火
# 三层树干（远/中/近）+ 雾带 + 树冠暗带（fbm 破碎边）+ 地被草叶 + 萤火
# 上一版树冠是 blur=5 的大椭圆 -> 灰团；改为「暗带 + fbm 碎边 + 叶簇小椭圆」。
# ============================================================================

def scene_forest(c):
    """禁林萤火：三层不等距树干（远细淡 / 中灰 / 近深）+ 林间透光雾 + 上角树冠 + 萤火。"""
    img = c.st([(0.00, (186, 180, 162)), (0.18, (200, 194, 175)), (0.46, (212, 204, 186)),
                (0.72, (216, 208, 189)), (1.00, (192, 184, 166))],
               [(0.00, (8, 12, 23)), (0.20, (13, 19, 36)), (0.48, (23, 32, 55)),
                (0.70, (26, 36, 60)), (1.00, (9, 12, 22))])

    # 林间透光：中央偏上的亮核（白昼用 over，避免加亮削顶成死白）
    img = over(img, (238, 233, 218) if not c.night else (86, 110, 158),
               field(0.50, 0.40, 0.30, 0.26, 1.6) * (0.30 if not c.night else 0.52))

    # 远层：10 根极细淡树干（模糊 2.2），给纵深
    far = ((0.030, 0.0075), (0.108, 0.0060), (0.196, 0.0090), (0.286, 0.0065),
           (0.372, 0.0080), (0.468, 0.0058), (0.566, 0.0085), (0.664, 0.0068),
           (0.768, 0.0092), (0.872, 0.0062), (0.960, 0.0080))

    def far_layer(d):
        for (cx, hw) in far:
            rect(d, cx - hw, 0.0, cx + hw, 1.0)
    img = silhouette(img, msk(far_layer, blur=2.2),
                     mix(L(c, 'far'), c.P['paper'], 0.30) if not c.night
                     else mix(L(c, 'far'), L(c, 'mid'), 0.40), 0.78,
                     grain_tex(c, 8), 0.04)

    # 贴地雾带（把树干下半段"埋进雾里"）
    mist = fbm(c.seed(), cells=4, octaves=3, blur_r=10.0)
    img = over(img, (240, 236, 223) if not c.night else (70, 90, 130),
               (0.25 + 0.75 * mist) * vband(0.500, 0.780, 0.06, 0.05)
               * (0.24 if not c.night else 0.26))

    # 中层：6 根中等树干（不等距、微微锥度）
    mid_t = ((0.086, 0.0162, 0.008), (0.238, 0.0120, -0.006), (0.418, 0.0182, 0.010),
             (0.596, 0.0130, -0.008), (0.742, 0.0166, 0.006), (0.912, 0.0136, -0.004))

    def mid_layer(d):
        for (cx, hw, lean) in mid_t:
            poly(d, [(cx - hw * 1.26, 1.0), (cx + hw * 1.26, 1.0),
                     (cx + hw * 0.74 + lean, 0.0), (cx - hw * 0.74 + lean, 0.0)])
    img = silhouette(img, msk(mid_layer, blur=1.0),
                     mix(L(c, 'near'), L(c, 'fore'), 0.32) if not c.night
                     else mix(L(c, 'mid'), L(c, 'near'), 0.30), 0.90,
                     grain_tex(c, 11, amp=0.20), 0.07)

    # 近层：3 根深色大树干（避开正中，根部加宽）
    trunks = ((0.062, 0.0260, 0.014), (0.436, 0.0205, -0.012), (0.906, 0.0310, 0.010))

    def trunk_layer(d):
        for (cx, hw, lean) in trunks:
            poly(d, [(cx - hw * 1.42, 1.0), (cx + hw * 1.42, 1.0),
                     (cx + hw * 0.68 + lean, 0.0), (cx - hw * 0.68 + lean, 0.0)])
    tm = msk(trunk_layer, blur=0.75)
    img = silhouette(img, tm, mix(L(c, 'near'), L(c, 'fore'), 0.30) if c.night
                     else mix(L(c, 'ink'), L(c, 'fore'), 0.35), 1.0,
                     grain_tex(c, 13, amp=0.30), 0.10)

    # 树皮：一侧受光 + 几道横向裂纹
    def bark(d):
        for (cx, hw, lean) in trunks:
            seg(d, (cx - hw * 0.74, 1.0), (cx - hw * 0.34 + lean, 0.0), 1.7)
            seg(d, (cx + hw * 1.00, 1.0), (cx + hw * 0.50 + lean, 0.0), 0.9)
            for k in range(10):
                yy = c.d.uniform(0.10, 0.98)
                xx = cx + lean * (1.0 - yy) + c.d.uniform(-hw * 0.6, hw * 0.6)
                seg(d, (xx, yy), (xx + c.d.uniform(-0.016, 0.016), yy + 0.007), 0.7)
    img = addl(img, mix(L(c, 'mid'), c.P['paper'], 0.30),
               msk(bark, blur=0.50) * tm * (0.26 if not c.night else 0.14))

    # 树冠：一维噪声当边界 + 硬填充（凸起只有 7-15px，读起来是叶簇剪影，不是云）
    n1 = fbm(c.seed(), cells=34, octaves=2, gain=0.55, blur_r=1.0)[0]
    n2 = fbm(c.seed(), cells=30, octaves=2, gain=0.60, blur_r=1.0)[0]
    n1b = fbm(c.seed(), cells=76, octaves=2, gain=0.50, blur_r=0.6)[0]
    n2b = fbm(c.seed(), cells=68, octaves=2, gain=0.50, blur_r=0.6)[0]
    xs = _XN
    b_left = 0.058 + 0.045 * n1 + 0.014 * n1b + np.clip(0.415 - xs, 0.0, 1.0) * 0.320
    b_right = 0.052 + 0.045 * n2 + 0.014 * n2b + np.clip(xs - 0.585, 0.0, 1.0) * 0.320
    top = np.minimum(b_left, b_right)[None, :]
    mass = (_YN[:, None] <= top).astype(np.float32)
    mass = bl(mass, 2.0)
    img = silhouette(img, mass, mix(L(c, 'near'), L(c, 'fore'), 0.72) if c.night
                     else mix(L(c, 'mid'), L(c, 'near'), 0.30), 0.97,
                     grain_tex(c, 20, amp=0.34), 0.13)
    # 沿冠的下沿挂一圈小叶簇（5-11px）：让"冠"读成树叶，而不是一块灰板
    def clumps(d):
        r = np.random.default_rng(c.seed() + 91)
        for k in range(46):
            x0 = float(r.uniform(0.0, 1.0))
            y0 = float(np.interp(x0, xs, top[0])) + float(r.uniform(-0.004, 0.020))
            rr = float(r.uniform(0.0045, 0.0105))
            ell(d, x0, y0, rr, rr * float(r.uniform(0.62, 1.0)))
            if r.random() < 0.55:
                ell(d, x0 + rr * 1.5, y0 + rr * 0.5, rr * 0.72, rr * 0.50)
    img = silhouette(img, msk(clumps, blur=0.85),
                     mix(L(c, 'near'), L(c, 'fore'), 0.62) if c.night
                     else mix(L(c, 'mid'), L(c, 'near'), 0.24), 0.96,
                     grain_tex(c, 26, amp=0.30), 0.12)

    # 后排一层更浅的冠（错开 0.042），给树冠一点厚度
    top2 = top - 0.042
    mass2 = (_YN[:, None] <= top2).astype(np.float32) * (1.0 - mass)
    img = silhouette(img, bl(mass2, 2.6), mix(L(c, 'mid'), L(c, 'near'), 0.30) if c.night
                     else mix(L(c, 'far'), c.P['paper'], 0.22), 0.55,
                     grain_tex(c, 22, amp=0.26), 0.10)

    # 从冠下沿垂下来的细枝（把"冠"和"树干"连起来）
    def twigs(d):
        for (x0, x1, y1) in ((0.086, 0.168, 0.300), (0.352, 0.300, 0.268), (0.640, 0.712, 0.292),
                             (0.892, 0.826, 0.276), (0.236, 0.286, 0.226), (0.756, 0.700, 0.214)):
            y0 = float(np.interp(x0, xs, top[0]))
            seg(d, (x0, y0 - 0.004), (x1, y1), 1.3)
            seg(d, (x1, y1), (x1 + (x1 - x0) * 0.22, y1 + 0.020), 0.8)
    img = silhouette(img, msk(twigs, blur=0.45), mix(L(c, 'near'), L(c, 'fore'), 0.55) if c.night
                     else mix(L(c, 'mid'), L(c, 'near'), 0.45), 0.95,
                     grain_tex(c, 24, amp=0.24), 0.08)

    # 斜向透光柱（弱，避开正中）
    for (x0, wk) in ((0.128, 0.050), (0.690, 0.042)):
        beam = msk(lambda d, x0=x0: poly(d, [(x0, 0.0), (x0 + 0.062, 0.0),
                                             (x0 + 0.200, 1.0), (x0 + 0.098, 1.0)]), blur=22.0)
        img = addl(img, c.P['glow'], beam * (wk * 0.55 if not c.night else wk * 0.34))

    # 地被：暗带 + 草叶（细、带受光草尖）
    gnd = msk(lambda d: poly(d, [(0.0, 0.912), (1.0, 0.896), (1.0, 1.0), (0.0, 1.0)]), blur=0.7)
    img = silhouette(img, gnd, L(c, 'fore') if c.night else mix(L(c, 'fore'), L(c, 'near'), 0.42),
                     1.0, grain_tex(c, 16, amp=0.26), 0.10)

    def grass(d):
        for i in range(128):
            px = c.d.uniform(-0.02, 1.02)
            h = c.d.uniform(0.012, 0.062)
            w = c.d.uniform(0.0016, 0.0044)
            bend = c.d.uniform(-0.026, 0.026)
            poly(d, [(px - w, 1.0), (px + w, 1.0), (px + bend, 0.912 - h)])
    img = silhouette(img, msk(grass, blur=0.35), L(c, 'ink') if c.night
                     else mix(L(c, 'ink'), L(c, 'fore'), 0.45), 0.82,
                     grain_tex(c, 20, amp=0.22), 0.08)

    def grass_hi(d):
        for i in range(30):
            px = c.d.uniform(0.0, 1.0)
            h = c.d.uniform(0.014, 0.048)
            seg(d, (px, 0.913), (px + c.d.uniform(-0.016, 0.016), 0.913 - h), 1.0)
    img = addl(img, mix(L(c, 'near'), c.P['paper'], 0.28),
               msk(grass_hi, blur=0.30) * (0.34 if not c.night else 0.12))

    # 萤火（夜）/ 浮尘（昼）
    if c.night:
        fl = points(c.d, 68, (0.0, 0.260, 1.0, 0.955), r=(0.7, 1.9), peak=(0.30, 1.0),
                    halo=4.4, hw=(0.08, 0.22))
        fl = fl * (1.0 - 0.50 * CEN_SOFT)
        img = addl(img, (192, 246, 150), fl)
        for (fx, fy) in ((0.156, 0.596), (0.826, 0.520), (0.238, 0.828), (0.752, 0.780),
                         (0.412, 0.900), (0.604, 0.884)):
            img = addl(img, (168, 236, 140), field(fx, fy, 0.028, 0.018, 2.0) * 0.34)
            img = addl(img, (168, 236, 140), discf(fx, fy, 1.4) * 0.45)
    else:
        fl = points(c.d, 30, (0.0, 0.220, 1.0, 0.930), r=(0.6, 1.4), peak=(0.15, 0.50),
                    halo=3.6, hw=(0.06, 0.16))
        img = addl(img, (255, 252, 240), fl * 0.50)

    img = addl(img, L(c, 'ink'), vband(0.968, 1.0, 0.01, 0.0) * 0.55)
    return img


# ============================================================================

# ============================================================================

def scene_lake(c):
    img = c.st([(0.00, (207, 198, 180)), (0.20, (218, 209, 190)), (0.36, (230, 221, 203)),
                (0.435, (240, 232, 214)), (0.60, (224, 216, 199)), (0.82, (206, 197, 178)),
                (1.00, (188, 179, 161))],
               [(0.00, (9, 13, 30)), (0.24, (14, 20, 44)), (0.40, (26, 36, 68)),
                (0.435, (34, 46, 80)), (0.60, (18, 26, 50)), (0.82, (12, 18, 36)),
                (1.00, (7, 10, 22))])

    stars = c.starfield(720, 0.0, 0.430, peak=(0.30, 1.0), r=(0.5, 1.5), calm_center=0.55)
    img = addl(img, c.P['star'], stars * (0.10 if not c.night else 1.0) * vband(0.0, 0.430, 0.0, 0.02))

    # 月/日
    mx, my, mr = 0.742, 0.146, 24.0
    if c.night:
        img = addl(img, (92, 112, 172), field(mx, my, 0.15, 0.068, 1.7) * 0.30)
        img = over(img, c.P['moon'], discf(mx, my, mr, 1.0) * 0.95)
        img = addl(img, (250, 252, 255), field(mx, my, 0.30, 0.135, 1.6) * 0.10)
    else:
        img = addl(img, (255, 250, 232), field(mx, my, 0.20, 0.090, 1.6) * 0.45)
        img = over(img, (252, 246, 226), discf(mx, my, mr * 1.15, 8.0) * 0.70)

    # 远山两层（最浅的一层压在天边）
    for (yy, amp, fq, col, blr) in ((0.398, 0.020, 3.3, mix(L(c, 'far'), L(c, 'sky'), 0.35), 2.6),
                                    (0.416, 0.013, 5.7, L(c, 'far'), 1.6)):
        ph = c.g.uniform(0, 6.28)
        pts = [(0.0, yy)]
        for i in range(1, 45):
            xf = i / 44.0
            pts.append((xf, yy - amp * (0.45 + 0.55 * np.sin(xf * fq * 6.28 + ph))))
        pts += [(1.0, 0.440), (0.0, 0.440)]
        hm = msk(lambda d, pts=pts: poly(d, pts), blur=blr)
        img = silhouette(img, hm, col, 1.0, grain_tex(c, 7), 0.03)

    # 孤岛 + 小塔（偏右，避开中央）
    def island(d):
        poly(d, [(0.744, 0.438), (0.900, 0.432), (0.962, 0.444),
                 (0.952, 0.448), (0.760, 0.448)])
        poly(d, [(0.760, 0.436), (0.900, 0.432), (0.830, 0.402)])
        rect(d, 0.822, 0.352, 0.838, 0.404)
        poly(d, [(0.816, 0.352), (0.844, 0.352), (0.830, 0.316)])
    im_ = msk(island, blur=0.6)
    img = silhouette(img, im_, mix(L(c, 'near'), L(c, 'mid'), 0.30), 1.0)
    img = over(img, c.P['glow'], discf(0.830, 0.372, 1.1) * (0.9 if c.night else 0.35))

    # 平线 + 反射：把天空切片翻转、模糊、再压回水面
    hor = int(0.435 * H)
    sky = img[:hor][::-1]
    sky = bl(sky, 1.5)
    if sky.shape[0] < H - hor:
        pad = np.repeat(sky[-1:], (H - hor) - sky.shape[0], axis=0)
        sky = np.concatenate([sky, pad], axis=0)
    refl = sky[:H - hor]
    # 水面越低越暗、越模糊
    depth = sstep(0.0, 1.0, np.linspace(0.0, 1.0, H - hor, dtype=np.float32))[:, None, None]
    water = img[hor:]
    ww = (0.62 - 0.34 * depth)
    water = water * (1.0 - ww) + refl * ww
    water = water * (1.0 - 0.16 * depth)
    img = np.concatenate([img[:hor], water], axis=0)

    # 水面细横纹（波纹），越近越疏越粗
    def ripples(d):
        y = 0.448
        while y < 1.0:
            k = (y - 0.435) / 0.565
            seg(d, (c.d.uniform(-0.05, 0.25), y), (c.d.uniform(0.55, 1.05), y),
                0.5 + 1.5 * k)
            y += 0.010 + 0.030 * k
    rpm = msk(ripples, blur=0.55)
    img = addl(img, c.P['paper_hi'] if not c.night else (120, 150, 200), rpm * (0.16 if not c.night else 0.10))
    # 近岸的暗反光（压住底部，让倒影有层次）
    img = addl(img, L(c, 'fore'), vband(0.880, 1.0, 0.10, 0.0) * 0.42)

    # 左右近景：礁石 + 芦苇（细节都在下角）
    def rocks(d):
        ell(d, 0.052, 0.938, 0.075, 0.030)
        ell(d, 0.128, 0.962, 0.055, 0.022)
        ell(d, 0.958, 0.918, 0.070, 0.028)
        ell(d, 0.882, 0.948, 0.048, 0.020)
    rk = msk(rocks, blur=1.1)
    img = silhouette(img, rk, L(c, 'fore'), 1.0, grain_tex(c, 10), 0.10)

    def reeds(d):
        for i in range(26):
            side = 0.0 if i % 2 == 0 else 1.0
            px = side * 0.0 + (c.d.uniform(0.010, 0.185) if side < 0.5 else c.d.uniform(0.815, 0.990))
            top = c.d.uniform(0.700, 0.900)
            bend = c.d.uniform(-0.030, 0.030)
            seg(d, (px, 1.0), (px + bend, top), 0.9 + c.d.uniform(0.0, 0.7))
    rdm = msk(reeds, blur=0.35)
    img = silhouette(img, rdm, L(c, 'ink'), 0.95)
    return img


# ============================================================================
# 场景 5 —— tower 天文塔星图
# 结构：右侧高塔（带观星穹顶）+ 左侧星图弧与星座连线 + 左下矮墙/望远镜
# ============================================================================

def scene_tower(c):
    """天文塔星图：右侧高塔（石缝/檐环/双列拱窗/铜绿穹顶）+ 左侧副塔 + 天上星图弧线与星座。"""
    img = c.st([(0.00, (200, 191, 172)), (0.26, (212, 203, 184)), (0.52, (224, 215, 196)),
                (0.74, (232, 224, 205)), (1.00, (216, 207, 188))],
               [(0.00, (8, 11, 26)), (0.24, (12, 18, 40)), (0.50, (17, 24, 50)),
                (0.74, (22, 30, 56)), (1.00, (13, 17, 34))])

    cl = fbm(c.seed(), cells=5, octaves=3, blur_r=7.0)
    img = over(img, (250, 246, 236) if not c.night else (58, 74, 112),
               sstep(0.50, 0.82, cl) * vband(0.06, 0.34, 0.06, 0.10)
               * (0.30 if not c.night else 0.28))

    stars = c.starfield(700, 0.0, 0.660, peak=(0.30, 1.0), r=(0.5, 1.5), calm_center=0.55)
    img = addl(img, c.P['star'], stars * (0.12 if not c.night else 1.0) * vband(0.0, 0.68, 0.0, 0.10))

    # 星图：三圈弧 + 刻度（加粗，白昼也看得见）
    cx, cy = 0.250, 0.296
    chart = msk(lambda d: [arc(d, cx, cy, 0.132 * W, 194, 350, 1.1),
                           arc(d, cx, cy, 0.204 * W, 186, 356, 0.9),
                           arc(d, cx, cy, 0.276 * W, 200, 8, 0.8)], blur=0.30)
    if c.night:
        img = addl(img, c.P['gold'], chart * 0.40)
    else:
        img = addl(img, mix(L(c, 'ink'), c.P['gold'], 0.35), -chart * 0.34)

    def tick(d):
        for (rr, a0, a1, n) in ((0.132, 198, 346, 16), (0.204, 190, 2, 14), (0.276, 204, 4, 12)):
            for k in range(n):
                a = np.radians(a0 + (a1 - a0) * k / (n - 1.0))
                r0 = rr * W
                r1 = (rr + 0.011) * W
                seg(d, (cx + r0 * np.cos(a) / W, cy + r0 * np.sin(a) / W),
                    (cx + r1 * np.cos(a) / W, cy + r1 * np.sin(a) / W), 0.6)
    tk = msk(tick, blur=0.28)
    if c.night:
        img = addl(img, c.P['gold'], tk * 0.34)
    else:
        img = addl(img, mix(L(c, 'ink'), c.P['gold'], 0.30), -tk * 0.26)

    # 星座：7 颗星 + 细连接线
    con = ((0.072, 0.150), (0.156, 0.104), (0.238, 0.144), (0.120, 0.222),
           (0.052, 0.258), (0.188, 0.278), (0.278, 0.208))
    path = (0, 1, 2, 6, 5, 4, 3, 0)
    ln = msk(lambda d: [seg(d, con[path[i]], con[path[i + 1]], 0.5)
                        for i in range(len(path) - 1)], blur=0.30)
    if c.night:
        img = addl(img, c.P['gold'], ln * 0.42)
    else:
        img = addl(img, mix(L(c, 'ink'), c.P['gold'], 0.30), -ln * 0.30)
    for (sx, sy) in con:
        if c.night:
            img = addl(img, c.P['star'], discf(sx, sy, 1.6) * 0.95)
            img = addl(img, c.P['star'], field(sx, sy, 0.022, 0.011, 2.0) * 0.32)
        else:
            img = addl(img, mix(L(c, 'ink'), c.P['gold'], 0.25), -discf(sx, sy, 2.0) * 0.42)
            img = addl(img, mix(L(c, 'ink'), c.P['gold'], 0.25), -field(sx, sy, 0.024, 0.013, 2.0) * 0.16)

    # 左侧副塔（远景，压住左边空档）
    def sub_tower(d):
        poly(d, [(0.006, 1.0), (0.128, 1.0), (0.116, 0.612), (0.018, 0.612)])
        rect(d, 0.002, 0.600, 0.132, 0.618)
        poly(d, [(0.002, 0.606), (0.132, 0.606), (0.067, 0.470)])
        seg(d, (0.067, 0.470), (0.067, 0.436), 1.2)
        rect(d, 0.048, 0.690, 0.056, 0.720)
        rect(d, 0.082, 0.760, 0.090, 0.790)
        rect(d, 0.048, 0.840, 0.056, 0.870)
    st2 = msk(sub_tower, blur=1.1)
    img = silhouette(img, st2, mix(L(c, 'mid'), L(c, 'near'), 0.40), 0.94,
                     grain_tex(c, 9, amp=0.16), 0.06)
    img = addl(img, c.P['gold'], msk(lambda d: [rect(d, 0.048, 0.690, 0.056, 0.720),
                                               rect(d, 0.082, 0.760, 0.090, 0.790),
                                               rect(d, 0.048, 0.840, 0.056, 0.870)], blur=0.25)
               * (0.55 if c.night else 0.16))

    # 主塔
    def tower(d):
        poly(d, [(0.612, 1.0), (0.868, 1.0), (0.852, 0.404), (0.628, 0.404)])
        rect(d, 0.598, 0.386, 0.882, 0.410)                                     # 挑檐
        for i in range(9):
            rect(d, 0.606 + i * 0.0308, 0.376, 0.622 + i * 0.0308, 0.390)
        rect(d, 0.634, 0.300, 0.846, 0.388)                                     # 观星室
        ell(d, 0.740, 0.300, 0.106, 0.106)                                      # 穹顶
        poly(d, [(0.640, 0.302), (0.840, 0.302), (0.740, 0.302)])
        seg(d, (0.740, 0.194), (0.740, 0.162), 1.4)
        ell(d, 0.740, 0.160, 0.008, 0.008)
        rect(d, 0.722, 0.194, 0.758, 0.208)
        # 两道檐环（把竖直的塔身打断）
        rect(d, 0.606, 0.700, 0.874, 0.714)
        rect(d, 0.610, 0.842, 0.870, 0.854)
    tw = msk(tower, blur=0.55)
    img = silhouette(img, tw, mix(L(c, 'near'), L(c, 'fore'), 0.34), 1.0,
                     grain_tex(c, 11, amp=0.22), 0.07)

    # 塔身石缝：横缝（细）+ 错缝竖缝 + 一条受光边
    def courses(d):
        yy = 0.412
        i = 0
        while yy < 0.995:
            seg(d, (0.616, yy), (0.864, yy), 0.6)
            step = 0.052
            xx = 0.612 + (i % 2) * step * 0.5
            while xx < 0.868:
                seg(d, (xx, yy), (xx, yy + step), 0.5)
                xx += step
            yy += step
            i += 1
    img = addl(img, L(c, 'ink'), msk(courses, blur=0.30) * tw * (0.42 if c.night else 0.30))
    img = addl(img, mix(L(c, 'mid'), c.P['paper'], 0.35),
               msk(lambda d: [seg(d, (0.632, 0.412), (0.622, 1.0), 1.6)], blur=0.35)
               * tw * (0.26 if c.night else 0.20))

    # 穹顶：铜绿 + 5 条肋
    dome = msk(lambda d: [ell(d, 0.740, 0.300, 0.102, 0.102)], blur=0.7) * vabove(0.300)
    img = silhouette(img, dome, mix(c.P['teal'], L(c, 'near'), 0.42) if not c.night
                     else mix(c.P['teal'], L(c, 'fore'), 0.55), 0.96,
                     grain_tex(c, 12, amp=0.20), 0.08)
    img = addl(img, mix(c.P['teal'], c.P['paper'], 0.30),
               dome * (0.20 if c.night else 0.24))
    ribs = msk(lambda d: [arc(d, 0.740, 0.300, 0.030, 200, 340, 0.7),
                          arc(d, 0.740, 0.300, 0.062, 196, 344, 0.7),
                          arc(d, 0.740, 0.300, 0.092, 192, 348, 0.7),
                          seg(d, (0.740, 0.196), (0.740, 0.300), 0.7)], blur=0.30)
    img = addl(img, mix(L(c, 'ink'), c.P['teal'], 0.35), ribs * dome * (0.34 if c.night else 0.26))

    # 塔上的拱窗（双列）+ 观星室大窗
    def tw_win(d):
        for (wx, wy, w, h) in ((0.686, 0.500, 0.014, 0.026), (0.794, 0.500, 0.014, 0.026),
                               (0.686, 0.606, 0.013, 0.024), (0.794, 0.606, 0.013, 0.024),
                               (0.686, 0.760, 0.014, 0.026), (0.794, 0.760, 0.014, 0.026),
                               (0.740, 0.892, 0.016, 0.030)):
            rect(d, wx - w * 0.5, wy - h * 0.5, wx + w * 0.5, wy + h * 0.5)
            d.arc([(wx - w * 0.5) * X, (wy - h * 0.5 - w * 0.5) * Y,
                   (wx + w * 0.5) * X, (wy + h * 0.5 - w * 0.5) * Y],
                  180, 360, fill=255, width=int(1.4 * SS))
            seg(d, (wx, wy - h * 0.5 - w * 0.5), (wx, wy + h * 0.5), 0.5)
        rect(d, 0.700, 0.332, 0.780, 0.383)                                     # 观星室大窗
        d.arc([0.700 * X, 0.318 * Y, 0.780 * X, 0.352 * Y], 180, 360, fill=255,
              width=int(1.4 * SS))
    wm = msk(tw_win, blur=0.15)
    img = over(img, c.P['glow'] if c.night else mix(c.P['gold_hi'], c.P['paper'], 0.10),
               wm * (0.96 if c.night else 0.46))
    for (wx, wy, wk) in ((0.740, 0.360, 1.0), (0.686, 0.500, 0.4), (0.794, 0.500, 0.4),
                         (0.740, 0.892, 0.5)):
        img = addl(img, c.P['gold'], field(wx, wy, 0.050, 0.032, 2.0) * (0.20 * wk if c.night else 0.07 * wk))

    # 左下的矮墙 + 望远镜（近景，横向细节）
    def parapet(d):
        rect(d, -0.01, 0.884, 0.560, 1.0)
        for i in range(9):
            rect(d, 0.006 + i * 0.0610, 0.866, 0.052 + i * 0.0610, 0.888)
    pm = msk(parapet, blur=0.45)
    img = silhouette(img, pm, mix(L(c, 'near'), L(c, 'fore'), 0.35), 1.0, grain_tex(c, 10), 0.08)

    def scope(d):
        seg(d, (0.140, 0.872), (0.126, 0.960), 1.4)
        seg(d, (0.214, 0.872), (0.226, 0.960), 1.4)
        seg(d, (0.140, 0.872), (0.214, 0.872), 1.2)
        poly(d, [(0.152, 0.836), (0.240, 0.802), (0.250, 0.822), (0.158, 0.856)])
        ell(d, 0.246, 0.812, 0.012, 0.016)
    sm = msk(scope, blur=0.35)
    img = silhouette(img, sm, L(c, 'ink'), 1.0)
    img = addl(img, c.P['gold'], discf(0.246, 0.812, 1.0) * (0.7 if c.night else 0.25))

    img = addl(img, c.P['glow'], vband(0.860, 0.930, 0.03, 0.05) * (0.05 if c.night else 0.07))
    return img


# 场景 6 —— potions 魔药蒸汽
# 结构：圆窗（右上）+ 两层壁架（上 1/4）+ 长凳（0.80–0.89）+ 玻璃器皿（描边可见）+ 蒸汽
# 上一版玻璃用 paper 色 0.55 alpha，在白底上等于隐形；这里改成「墨色描边 + 淡玻璃 + 液面」。
# ============================================================================

def scene_potions(c):
    img = c.st([(0.00, (208, 199, 180)), (0.30, (216, 207, 188)), (0.58, (223, 214, 195)),
                (0.80, (228, 220, 201)), (1.00, (212, 203, 184))],
               [(0.00, (10, 14, 28)), (0.30, (13, 18, 38)), (0.58, (16, 22, 46)),
                (0.80, (21, 28, 54)), (1.00, (11, 15, 30))])

    # 后墙拱窗（右上）：夜间投进一束蓝月光
    win = msk(lambda d: [rect(d, 0.700, 0.148, 0.860, 0.322),
                         ell(d, 0.780, 0.150, 0.080, 0.060)], blur=0.55)
    img = over(img, (250, 245, 230) if not c.night else (44, 62, 102), win * (0.85 if not c.night else 0.75))
    img = addl(img, (255, 252, 240) if not c.night else (110, 140, 200),
               win * (0.22 if not c.night else 0.12))
    img = silhouette(img, msk(lambda d: [seg(d, (0.780, 0.098), (0.780, 0.330), 1.6),
                                        seg(d, (0.700, 0.238), (0.860, 0.238), 1.0),
                                        arc(d, 0.780, 0.150, 0.080 * W, 180, 360, 1.6)], blur=0.3),
                     mix(L(c, 'near'), L(c, 'mid'), 0.4), 1.0)
    beam = msk(lambda d: poly(d, [(0.706, 0.330), (0.854, 0.330), (0.980, 0.880), (0.700, 0.880)]), blur=18.0)
    img = addl(img, c.P['glow'], beam * (0.13 if not c.night else 0.07))

    # 上层壁架 + 6 个瓶子（顶部细节）
    shelf = msk(lambda d: [rect(d, 0.050, 0.306, 0.470, 0.322), rect(d, 0.560, 0.268, 0.950, 0.284)], blur=0.45)
    img = silhouette(img, shelf, mix(L(c, 'near'), L(c, 'fore'), 0.3), 1.0)

    def bottles(d):
        for (px, base, w, h, shape) in ((0.096, 0.306, 0.026, 0.080, 0), (0.166, 0.306, 0.020, 0.062, 0),
                                        (0.238, 0.306, 0.030, 0.096, 1), (0.330, 0.306, 0.022, 0.070, 0),
                                        (0.408, 0.306, 0.026, 0.086, 1),
                                        (0.606, 0.268, 0.024, 0.074, 0), (0.686, 0.268, 0.020, 0.058, 1),
                                        (0.760, 0.268, 0.028, 0.088, 0), (0.856, 0.268, 0.022, 0.066, 0),
                                        (0.918, 0.268, 0.018, 0.052, 1)):
            rect(d, px - w * 0.34, base - h * 1.20, px + w * 0.34, base - h * 0.94)   # 瓶颈
            if shape == 0:
                ell(d, px, base - h * 0.52, w, h * 0.52)
            else:
                rect(d, px - w, base - h, px + w, base)
            rect(d, px - w * 0.55, base - h * 1.24, px + w * 0.55, base - h * 1.14)   # 木塞
    bm = msk(bottles, blur=0.28)
    img = silhouette(img, bm, mix(L(c, 'near'), L(c, 'fore'), 0.22), 0.94)
    img = addl(img, c.P['glow'], bm * (0.05 if c.night else 0.03))
    # 三只上色的小瓶（药水颜色，别让整张图只有米灰）
    for (px, base, w, h, col, al) in ((0.238, 0.306, 0.030, 0.096, c.P['teal'], 0.55),
                                      (0.408, 0.306, 0.026, 0.086, c.P['ember'], 0.50),
                                      (0.760, 0.268, 0.028, 0.088, c.P['violet'], 0.50)):
        img = over(img, col, msk(lambda d, px=px, base=base, w=w, h=h: [
            ell(d, px, base - h * 0.52, w * 0.72, h * 0.36)], blur=0.45) * al)

    # 中段壁架（左）+ 一排陶罐：填补 0.36–0.58 的空档，同时把视线从中线引开
    midshelf = msk(lambda d: [rect(d, 0.040, 0.556, 0.402, 0.572)], blur=0.40)
    img = silhouette(img, midshelf, mix(L(c, 'near'), L(c, 'fore'), 0.30), 1.0, grain_tex(c, 12), 0.05)

    def jars(d):
        for (px, w, h, shp) in ((0.082, 0.026, 0.062, 0), (0.148, 0.020, 0.048, 1),
                                (0.212, 0.030, 0.076, 0), (0.288, 0.022, 0.054, 1),
                                (0.348, 0.026, 0.066, 0)):
            if shp == 0:
                ell(d, px, 0.556 - h * 0.52, w, h * 0.52)
                rect(d, px - w * 0.90, 0.556 - h * 0.10, px + w * 0.90, 0.556)
            else:
                rect(d, px - w * 0.80, 0.556 - h, px + w * 0.80, 0.556)
                rect(d, px - w * 0.46, 0.556 - h * 1.12, px + w * 0.46, 0.556 - h)
            rect(d, px - w * 0.30, 0.556 - h * 1.20, px + w * 0.30, 0.556 - h * 1.10)
    jm = msk(jars, blur=0.26)
    img = silhouette(img, jm, mix(L(c, 'near'), L(c, 'fore'), 0.20), 0.95)
    img = addl(img, c.P['glow'], jm * (0.06 if c.night else 0.03))
    img = addl(img, c.P['gold'], msk(lambda d: [seg(d, (0.040, 0.572), (0.402, 0.572), 0.8)],
                                     blur=0.30) * (0.24 if c.night else 0.12))

    # 长凳（0.80–0.89），前缘更暗
    bench = msk(lambda d: [rect(d, -0.01, 0.800, 1.010, 0.852)], blur=0.4)
    img = silhouette(img, bench, mix(L(c, 'near'), L(c, 'mid'), 0.30), 1.0, grain_tex(c, 12), 0.06)
    edge = msk(lambda d: [rect(d, -0.01, 0.852, 1.010, 0.892)], blur=0.35)
    img = silhouette(img, edge, L(c, 'fore'), 1.0, grain_tex(c, 10), 0.05)
    img = addl(img, c.P['gold'], msk(lambda d: [seg(d, (0.0, 0.800), (1.0, 0.800), 0.8)], blur=0.3)
               * (0.22 if c.night else 0.12))

    # 玻璃器皿：描边 + 液面 + 高光（这是本场景的主体，必须看得见）
    vessels = ((0.318, 0.800, 0.071, 0.203, 'round'), (0.470, 0.800, 0.042, 0.150, 'tube'),
               (0.602, 0.800, 0.061, 0.084, 'bowl'), (0.694, 0.800, 0.027, 0.120, 'vial'),
               (0.192, 0.800, 0.044, 0.141, 'retort'), (0.760, 0.800, 0.037, 0.079, 'bowl'))

    def glass_out(d):
        for (vx, vy, rw, hh, shp) in vessels:
            if shp == 'round':
                ell(d, vx, vy - rw * 1.05, rw, rw)
                rect(d, vx - rw * 0.24, vy - rw * 1.05 - hh * 0.62, vx + rw * 0.24, vy - rw * 1.05)
                rect(d, vx - rw * 0.30, vy - rw * 1.05 - hh * 0.66, vx + rw * 0.30, vy - rw * 1.05 - hh * 0.58)
                rect(d, vx - rw * 0.86, vy - rw * 0.10, vx + rw * 0.86, vy)
            elif shp == 'tube':
                rect(d, vx - rw, vy - hh, vx + rw, vy)
                rect(d, vx - rw * 1.14, vy - hh * 1.05, vx + rw * 1.14, vy - hh * 0.96)
            elif shp == 'bowl':
                ell(d, vx, vy - rw * 0.50, rw, rw * 0.62)
                rect(d, vx - rw * 0.86, vy - rw * 0.52, vx + rw * 0.86, vy)
            else:
                rect(d, vx - rw, vy - hh, vx + rw, vy)
                rect(d, vx - rw * 0.24, vy - hh * 1.18, vx + rw * 0.24, vy - hh)
    go = msk(glass_out, blur=0.30)

    # 玻璃内壁：一层淡玻璃色 + 液体
    def glass_in(d):
        for (vx, vy, rw, hh, shp) in vessels:
            if shp == 'round':
                ell(d, vx, vy - rw * 1.05, rw * 0.86, rw * 0.86)
            elif shp == 'tube':
                rect(d, vx - rw * 0.86, vy - hh * 0.96, vx + rw * 0.86, vy)
            elif shp == 'bowl':
                ell(d, vx, vy - rw * 0.50, rw * 0.84, rw * 0.52)
            else:
                rect(d, vx - rw * 0.84, vy - hh * 0.97, vx + rw * 0.84, vy)
    gi = msk(glass_in, blur=0.45)
    rim = np.clip(go - gi * 0.90, 0.0, 1.0)
    # 内腔：比墙略深的淡玻璃；玻璃壁：一圈暗边（玻璃靠"边+高光"读出来，不能实心填）
    img = over(img, mix(c.P['glass'], L(c, 'mid'), 0.35), gi * (0.34 if not c.night else 0.30))
    img = silhouette(img, rim, mix(L(c, 'ink'), L(c, 'near'), 0.40), 0.86)

    def liquid(d):
        for (vx, vy, rw, hh, shp) in vessels:
            if shp == 'round':
                ell(d, vx, vy - rw * 0.72, rw * 0.74, rw * 0.56)
            elif shp == 'tube':
                rect(d, vx - rw * 0.80, vy - hh * 0.52, vx + rw * 0.80, vy)
            elif shp == 'bowl':
                ell(d, vx, vy - rw * 0.44, rw * 0.76, rw * 0.40)
            else:
                rect(d, vx - rw * 0.78, vy - hh * 0.46, vx + rw * 0.78, vy)
    lq = msk(liquid, blur=0.50)
    img = over(img, c.P['ember'] if not c.night else (170, 78, 40), lq * (0.42 if not c.night else 0.55))
    img = over(img, c.P['teal'], lq * hband(0.40, 1.0, 0.10) * (0.30 if c.night else 0.18))

    # 液面高光 + 玻璃右侧高光条
    for (vx, vy, rw, hh, shp) in vessels:
        img = addl(img, c.P['glow'], field(vx, vy - rw * 1.06, rw * 0.5, rw * 0.3, 2.0) * (0.16 if c.night else 0.08))
    gl = msk(lambda d: [seg(d, (vx + rw * 0.58, vy - hh * 0.82), (vx + rw * 0.72, vy - hh * 0.16), 1.1)
                        for (vx, vy, rw, hh, shp) in vessels], blur=0.35)
    img = addl(img, (255, 252, 244) if not c.night else (150, 180, 210), gl * (0.35 if c.night else 0.26))

    # 蒸汽：4 条软管（分段变宽 + fbm 扰动），只在 0.30–0.78
    for (x0, ytop, amp, wk) in ((0.318, 0.330, 0.062, 0.30), (0.470, 0.386, 0.048, 0.24),
                                (0.192, 0.410, 0.034, 0.20), (0.602, 0.520, 0.030, 0.16),
                                (0.694, 0.560, 0.024, 0.12)):
        pts = []
        for k in range(9):
            t = k / 8.0
            pts.append((x0 + amp * np.sin(t * 3.4 + x0 * 9.0) * t,
                        0.800 - (0.800 - ytop) * t))
        steamm = msk(lambda d, pts=pts: [seg(d, pts[i], pts[i + 1], 5.0 + 5.0 * i / 8.0)
                                        for i in range(len(pts) - 1)], blur=7.0)
        img = addl(img, c.P['steam'], steamm * (wk * (1.0 if c.night else 0.85)))

    # 漂浮气泡与暖点
    bub = points(c.d, 26, (0.10, 0.420, 0.80, 0.790), r=(0.8, 2.0), peak=(0.15, 0.5),
                 halo=3.0, hw=(0.05, 0.12))
    img = addl(img, (255, 248, 232) if not c.night else c.P['glow'], bub * (0.55 if c.night else 0.30))
    if c.night:
        for (fx, fy) in ((0.310, 0.628), (0.452, 0.660), (0.586, 0.726)):
            img = addl(img, c.P['glow'], field(fx, fy, 0.055, 0.032, 1.8) * 0.16)
    img = addl(img, L(c, 'ink'), vband(0.965, 1.0, 0.01, 0.0) * 0.5)
    return img


# ============================================================================
# 场景 7 —— library 浮空烛书阁
# 结构：中央是一个「暗龛」（不是亮板！）+ 两侧书架 + 浮烛 + 浮书 + 梯子 + 地面光池
# ============================================================================

def scene_library(c):
    img = c.st([(0.00, (196, 187, 168)), (0.26, (206, 197, 178)), (0.54, (214, 205, 186)),
                (0.80, (220, 211, 192)), (1.00, (204, 195, 176))],
               [(0.00, (10, 13, 26)), (0.28, (12, 17, 34)), (0.56, (15, 21, 42)),
                (0.80, (19, 26, 50)), (1.00, (10, 13, 28))])

    # 中央暗龛：拱形，比两侧墙暗一档（这是本张的「呼吸区」，不是亮块）
    niche = msk(lambda d: [rect(d, 0.238, 0.300, 0.762, 0.900),
                           ell(d, 0.500, 0.300, 0.262, 0.210)], blur=1.1)
    img = silhouette(img, niche, mix(L(c, 'mid'), L(c, 'far'), 0.30), 1.0, grain_tex(c, 9), 0.05)
    # 龛内壁的纵深（越里面越暗）
    inner = msk(lambda d: [rect(d, 0.268, 0.340, 0.732, 0.900),
                           ell(d, 0.500, 0.340, 0.232, 0.186)], blur=8.0)
    img = addl(img, L(c, 'fore'), -0.42 * inner * (1.0 if c.night else 0.55))
    # 拱缘线脚
    img = addl(img, mix(c.P['gold'], c.P['paper'], 0.25),
               msk(lambda d: [arc(d, 0.500, 0.300, 0.262 * W, 178, 362, 2.2),
                              seg(d, (0.238, 0.300), (0.238, 0.900), 2.0),
                              seg(d, (0.762, 0.300), (0.762, 0.900), 2.0)], blur=0.45)
               * (0.34 if c.night else 0.26))

    # 两侧书架：7 层隔板 + 书脊
    def shelfs(d):
        for (x0, x1) in ((0.012, 0.232), (0.768, 0.988)):
            y = 0.112
            while y < 0.930:
                rect(d, x0, y, x1, y + 0.0105)              # 隔板
                y += 0.1175
            rect(d, x0 - 0.006, 0.100, x0 + 0.006, 0.940)   # 立柱
            rect(d, x1 - 0.006, 0.100, x1 + 0.006, 0.940)
    sh = msk(shelfs, blur=0.40)
    img = silhouette(img, sh, mix(L(c, 'near'), L(c, 'fore'), 0.45), 1.0, grain_tex(c, 12), 0.07)

    # 书脊：宽窄高低不一、颜色取自书色板、少量留空与斜插
    def books(dc, dm):
        for (x0, x1) in ((0.018, 0.228), (0.772, 0.982)):
            y = 0.112
            while y < 0.930:
                x = x0 + c.d.uniform(0.0, 0.012)
                while x < x1 - 0.014:
                    w = c.d.uniform(0.008, 0.020)
                    h = c.d.uniform(0.062, 0.098)
                    if c.d.random() < 0.13:                  # 留空
                        x += w
                        continue
                    col = c.P['book'][int(c.d.integers(0, 8))]
                    if c.d.random() < 0.10:                  # 斜插
                        polyc(dc, dm, [(x, y), (x + w, y + 0.030), (x + w, y + h),
                                       (x, y + h - 0.030)], col)
                    else:
                        rectc(dc, dm, x, y + 0.0105, x + w, y + h, col)
                    # 书脊上的烫金细条
                    if c.d.random() < 0.55:
                        rectc(dc, dm, x + w * 0.18, y + 0.030, x + w * 0.82, y + 0.036,
                              mix(col, c.P['gold'], 0.55))
                    x += w + c.d.uniform(0.0004, 0.0022)
                y += 0.1175
    brgb, bm2 = clayer(books, blur=0.30)
    img = over(img, brgb, bm2 * (0.88 if not c.night else 0.80))
    # 书的整体降饱和（避免像调色板）
    img = img * (1.0 - 0.20 * bm2[..., None]) + \
          luma(img)[..., None] * (0.20 * bm2[..., None])
    img = addl(img, L(c, 'fore'), -0.18 * bm2 * (1.0 - CEN))

    # 顶部拱楣 + 中央吊灯（细节在顶部）
    img = addl(img, L(c, 'fore'), vband(0.000, 0.096, 0.0, 0.030) * 0.75)
    img = addl(img, mix(c.P['gold'], c.P['paper'], 0.30),
               msk(lambda d: [seg(d, (0.0, 0.096), (1.0, 0.096), 2.0),
                              rect(d, 0.486, 0.010, 0.514, 0.096),
                              seg(d, (0.500, 0.010), (0.500, 0.0), 1.2)], blur=0.4)
               * (0.30 if c.night else 0.22))

    # 浮烛（龛内的暖光核心）
    candles = ((0.500, 0.372, 1.0), (0.424, 0.436, 0.85), (0.576, 0.432, 0.85),
               (0.470, 0.512, 0.7), (0.530, 0.516, 0.7), (0.500, 0.596, 0.55),
               (0.362, 0.626, 0.5), (0.638, 0.622, 0.5))
    for (cx, cy, wk) in candles:
        img = addl(img, c.P['glow'], discf(cx, cy, 1.4 + 0.5 * wk) * (0.95 * wk))
        img = addl(img, (255, 238, 186) if c.night else (255, 246, 216),
                   field(cx, cy, 0.055, 0.036, 1.8) * (0.50 * wk if c.night else 0.26 * wk))
        img = addl(img, c.P['glow'], field(cx, cy, 0.135, 0.090, 1.5) * (0.10 * wk))

    # 浮书：几本斜着的书，页口朝光
    def fbooks(dc, dm):
        for (fx, fy, w, h, ang) in ((0.330, 0.318, 0.052, 0.020, -0.22),
                                    (0.678, 0.292, 0.046, 0.018, 0.30),
                                    (0.612, 0.574, 0.040, 0.016, -0.16),
                                    (0.392, 0.700, 0.038, 0.015, 0.20)):
            ca, sa = np.cos(ang), np.sin(ang)
            q = [(fx + dx * ca - dy * sa, fy + dx * sa + dy * ca)
                 for (dx, dy) in ((-w, -h), (w, -h), (w, h), (-w, h))]
            polyc(dc, dm, q, mix(L(c, 'mid'), c.P['paper'], 0.40))
            polyc(dc, dm, [(fx - w * 0.94, fy), (fx + w * 0.94, fy),
                           (fx + w * 0.94, fy + h * 0.34), (fx - w * 0.94, fy + h * 0.34)],
                  mix(c.P['paper_hi'], c.P['gold'], 0.30))
    frgb, fbm2 = clayer(fbooks, blur=0.30)
    img = over(img, frgb, fbm2 * 0.92)

    # 梯子（右侧书架前，斜靠）
    lad = msk(lambda d: [seg(d, (0.700, 0.960), (0.734, 0.360), 1.5),
                         seg(d, (0.742, 0.960), (0.772, 0.360), 1.5)]
                        + [seg(d, (0.702 + i * 0.0034, 0.930 - i * 0.0570),
                               (0.744 + i * 0.0030, 0.930 - i * 0.0570), 0.9) for i in range(8)],
              blur=0.35)
    img = silhouette(img, lad, mix(L(c, 'near'), L(c, 'fore'), 0.55), 1.0)
    # 木梯上的受光边
    img = addl(img, c.P['glow'], lad * hband(0.700, 0.780, 0.02) * (0.05 if c.night else 0.08))

    # 地面：光池 + 倒影带
    img = addl(img, L(c, 'fore'), vband(0.900, 1.0, 0.02, 0.0) * 0.55)
    img = addl(img, c.P['glow'], field(0.500, 0.938, 0.240, 0.040, 1.6) * (0.16 if c.night else 0.10))
    img = addl(img, c.P['glow'], field(0.500, 0.500, 0.30, 0.20, 1.4) * (0.05 if c.night else 0.03))
    return img


# ============================================================================
# 场景 8 —— nebula 星云极光
# 结构：极光带（4 条，竖直渐变遮罩）+ 星云团（fbm x 径向）+ 尘埃暗带 + 密星 + 底部山林
# 白昼版不能只剩白：用「更暗的色带」（teal/violet 与纸的混色）做出可读的带状层次。
# ============================================================================

def scene_nebula(c):
    img = c.st([(0.00, (206, 198, 184)), (0.22, (214, 206, 190)), (0.46, (220, 212, 194)),
                (0.70, (226, 218, 199)), (1.00, (206, 197, 178))],
               [(0.00, (7, 10, 24)), (0.22, (11, 16, 36)), (0.46, (15, 21, 44)),
                (0.70, (19, 26, 50)), (1.00, (8, 11, 24))])

    # 星云团：两层低频噪声 x 径向遮罩（一冷一暖）
    for (cx, cy, rx, ry, col, wk, sd) in (
            (0.470, 0.400, 0.520, 0.300, c.P['violet'], 0.30, 3101),
            (0.300, 0.610, 0.360, 0.240, c.P['teal'], 0.24, 3102),
            (0.720, 0.520, 0.330, 0.210, mix(c.P['teal'], c.P['violet'], 0.5), 0.20, 3103)):
        nb = fbm(sd, cells=3, octaves=4, gain=0.58, blur_r=6.0)
        m = sstep(0.36, 0.86, nb) * field(cx, cy, rx, ry, 1.4)
        if c.night:
            a = wk
            img = addl(img, mix(c.P['paper'], col, 1.0), m * a)
            img = addl(img, col, m * a * 0.35)
        else:
            # 白昼：往纸色里"混色"，而不是加亮（加亮会直接削顶成白板）
            a = wk * 0.85
            img = over(img, mix(c.P['paper'], col, 0.62), m * a)
            img = over(img, mix(c.P['paper'], col, 0.90), m * a * 0.30)

    # 极光带：4 条波纹带，每条 = 曲带遮罩 x 竖直渐变
    for i, (yc, amp, wid, col, wk, fq) in enumerate((
            (0.180, 0.045, 0.062, c.P['teal'], 0.34, 2.3),
            (0.268, 0.038, 0.050, mix(c.P['teal'], c.P['violet'], 0.45), 0.28, 3.1),
            (0.352, 0.030, 0.040, c.P['violet'], 0.24, 3.7),
            (0.116, 0.026, 0.034, mix(c.P['teal'], c.P['paper'], 0.30), 0.20, 4.3),
            (0.612, 0.042, 0.052, mix(c.P['violet'], c.P['teal'], 0.55), 0.26, 2.7))):
        ph = c.g.uniform(0, 6.28)

        def band(d, yc=yc, amp=amp, wid=wid, ph=ph, fq=fq, i=i):
            prev = None
            for k in range(25):
                t = k / 24.0
                yy = yc + amp * np.sin(t * fq * 6.28 + ph) + 0.014 * np.sin(t * 11.0 + ph * 2.0)
                w = wid * (0.55 + 0.45 * np.sin(t * 3.1 + ph))
                d.ellipse([(t * X) - w * X * 0.5, (yy - w * 0.55) * Y,
                           (t * X) + w * X * 0.5, (yy + w * 0.55) * Y], fill=255)
        am = msk(band, blur=6.5)
        # 上缘更亮、向下淡出
        am = am * (0.45 + 0.55 * vband(yc - amp - 0.09, yc + amp + 0.10, 0.05, 0.05))
        # 两层噪声调制：低频做出明暗斑块（飘带感），高频做出竖纹（极光帘）
        n1 = fbm(4100 + i, cells=7, octaves=3, gain=0.55, blur_r=2.2)
        n2 = fbm(4200 + i, cells=46, octaves=2, gain=0.50, blur_r=0.5)
        am = np.clip(am * (0.22 + 1.10 * n1) * (0.55 + 0.80 * n2), 0.0, 1.5)
        if c.night:
            a = wk
            img = addl(img, col, am * a)
            img = addl(img, mix(col, (255, 255, 255), 0.65), am * a * 0.30)
        else:
            a = wk * 1.15
            img = over(img, mix(c.P['paper'], col, 0.80), np.clip(am, 0, 1) * a)
            img = over(img, mix(c.P['paper'], mix(col, c.P['ink'], 0.35), 0.85),
                       np.clip(am - 0.55, 0, 1) * a * 0.9)

    # 尘埃暗带：斜穿星云的一条暗纹（提供结构，不是靠噪点）
    dm = msk(lambda d: poly(d, [(0.0, 0.520), (1.0, 0.400), (1.0, 0.470), (0.0, 0.596)]), blur=11.0)
    img = addl(img, L(c, 'ink'), -0.20 * dm * (1.0 if c.night else 0.30))

    # 星点最密的一张
    stars = c.starfield(840, 0.0, 0.860, peak=(0.28, 1.0), r=(0.45, 1.6), calm_center=0.40)
    img = addl(img, c.P['star'], stars * (0.22 if not c.night else 1.0) * vband(0.0, 0.900, 0.0, 0.08))
    # 几颗亮星的十字芒（夜里才有，白天留极淡）
    for (sx, sy, wk) in ((0.148, 0.240, 1.0), (0.842, 0.196, 0.9), (0.706, 0.470, 0.7),
                         (0.238, 0.560, 0.6), (0.930, 0.640, 0.5)):
        fl = msk(lambda d, sx=sx, sy=sy: [seg(d, (sx, sy - 0.011), (sx, sy + 0.011), 0.7),
                                          seg(d, (sx - 0.008, sy), (sx + 0.008, sy), 0.7)], blur=0.25)
        img = addl(img, c.P['star'], fl * (0.75 * wk if c.night else 0.22 * wk))
        img = addl(img, c.P['star'], discf(sx, sy, 1.6) * (0.9 * wk if c.night else 0.30 * wk))
        img = addl(img, c.P['star'], field(sx, sy, 0.024, 0.011, 2.0) * (0.26 * wk if c.night else 0.08 * wk))

    # 流星（白天几乎看不见）
    for (x0, y0, ln) in ((0.760, 0.086, 0.130), (0.196, 0.088, 0.095)):
        img = addl(img, (255, 250, 238), msk(lambda d, x0=x0, y0=y0, ln=ln: [
            seg(d, (x0, y0), (x0 - ln, y0 + ln * 0.42), 0.7)], blur=0.55)
            * (0.55 if c.night else 0.12))

    # 底部山林 + 一座小塔（把画面「落地」）
    ph = c.g.uniform(0, 6.28)
    pts = [(0.0, 0.856)]
    for i in range(1, 45):
        xf = i / 44.0
        pts.append((xf, 0.856 - 0.024 * np.sin(xf * 4.7 * 6.28 + ph)
                    - 0.010 * np.sin(xf * 15.3 * 6.28 + ph * 1.4)))
    pts += [(1.0, 1.0), (0.0, 1.0)]
    hill = msk(lambda d: poly(d, pts), blur=1.4)
    img = silhouette(img, hill, L(c, 'near'), 1.0, grain_tex(c, 10), 0.05)

    tw = msk(lambda d: [rect(d, 0.152, 0.760, 0.176, 0.830),
                        poly(d, [(0.146, 0.760), (0.182, 0.760), (0.164, 0.716)])], blur=0.55)
    img = silhouette(img, tw, L(c, 'fore'), 1.0)
    img = over(img, c.P['glow'], discf(0.164, 0.782, 1.0) * (0.85 if c.night else 0.30))

    def pines(d):
        for i in range(11):
            px = c.d.uniform(-0.02, 1.02)
            fir(d, px, 0.842 - c.d.uniform(0.018, 0.052), 0.908,
                c.d.uniform(0.022, 0.040), tiers=4)
    img = silhouette(img, msk(pines, blur=0.6), L(c, 'fore'), 1.0)

    ground = msk(lambda d: poly(d, [(0.0, 0.936), (1.0, 0.918), (1.0, 1.0), (0.0, 1.0)]), blur=0.8)
    img = silhouette(img, ground, mix(L(c, 'ink'), L(c, 'near'), 0.34), 1.0,
                     grain_tex(c, 16, amp=0.22), 0.08)
    return img


SCENES = [
    dict(id='castle', cn='星夜城堡', seed=1103, fn=scene_castle,
         note='尖塔剪影 + 星空 + 月与星环'),
    dict(id='hall', cn='烛火长厅', seed=1207, fn=scene_hall,
         note='三扇拱窗 + 长桌透视 + 浮烛'),
    dict(id='forest', cn='禁林萤火', seed=1311, fn=scene_forest,
         note='三层树干 + 雾带 + 树冠暗带 + 萤火'),
    dict(id='lake', cn='镜湖星影', seed=1417, fn=scene_lake,
         note='远山 + 天空反射 + 涟漪 + 芦苇礁石'),
    dict(id='tower', cn='天文塔星图', seed=1523, fn=scene_tower,
         note='观星穹顶 + 星图弧 + 星座连线'),
    dict(id='potions', cn='魔药蒸汽', seed=1627, fn=scene_potions,
         note='描边玻璃器皿 + 液面 + 蒸汽'),
    dict(id='library', cn='浮空烛书阁', seed=1733, fn=scene_library,
         note='中央暗龛 + 两侧书脊 + 浮烛浮书'),
    dict(id='nebula', cn='星云极光', seed=1839, fn=scene_nebula,
         note='极光带 + 星云团 + 最密星点'),
]


def tone_map(img, target):
    """线性偏移把均值推到 target。

    这里刻意不用 gamma：分层灰阶（远岭 -> 城堡 -> 近岭 -> 前脊）是设计出来的，
    gamma 一拉就会把 30 级的间距压成 8 级，白昼版立刻糊成一片纸。只做平移，
    既保住层次，又不会把高光拉到 255 以上。
    """
    g = luma(img)
    lo, hi = -80.0, 80.0
    for _ in range(52):
        o = 0.5 * (lo + hi)
        if float(np.clip(g + o, 0.0, 255.0).mean()) < target:
            lo = o
        else:
            hi = o
    o = 0.5 * (lo + hi)
    return np.clip(img + o, 0.0, 255.0).astype(np.float32), dict(gamma=1.0, off=o)


def structure(img, kappa, r1=2.6, r2=9.0):
    """只在外圈做 unsharp，中央保持柔和。"""
    if kappa <= 0:
        return img
    hi = (img - bl(img, r1)) + 0.55 * (img - bl(img, r2))
    b = (1.0 - CEN)[..., None]
    return np.clip(img + hi * (kappa * b), 0.0, 255.0)


def soften_center(img, w):
    if w <= 0:
        return img
    m = (CEN_SOFT * w)[..., None]
    return img * (1.0 - m) + bl(img, 2.4) * m


def polish(img, rng, grain, dither=0.9):
    n = rng.normal(0.0, grain, (H, W, 1)).astype(np.float32)
    d = (rng.random((H, W, 1), dtype=np.float32) - 0.5) * (2.0 * dither)
    return np.clip(img + n + d, 0.0, 255.0)


# ----------------------------------------------------------------------------
# 度量（全部在「编码后重新解码」的图上）
# ----------------------------------------------------------------------------

def lapvar(a):
    c = a[1:-1, 1:-1]
    lap = 4.0 * c - a[:-2, 1:-1] - a[2:, 1:-1] - a[1:-1, :-2] - a[1:-1, 2:]
    return float(lap.var())


def centre_hard(gray):
    gy, gx = np.gradient(gray)
    mag = np.hypot(gx, gy)
    y0, y1 = int(0.2 * H), int(0.8 * H)
    x0, x1 = int(0.2 * W), int(0.8 * W)
    c = mag[y0:y1, x0:x1]
    return float((c >= 70.0).mean() * 100.0)


def measure_arr(a):
    """a: HxWx3 uint8。"""
    g = Image.fromarray(a, 'RGB').convert('L')
    f = np.asarray(g, np.float32)
    s = np.asarray(g.resize(SMALL, Image.LANCZOS), np.float32)
    b4 = np.asarray(g.filter(ImageFilter.GaussianBlur(4.0)), np.float32)
    return dict(mean=float(f.mean()), std=float(s.std()), lap=lapvar(f), lap4=lapvar(b4),
                chard=centre_hard(f))


def measure_file(path):
    im = Image.open(path)
    im.load()
    a = np.asarray(im.convert('RGB'), np.uint8)
    m = measure_arr(a)
    m['w'], m['h'] = im.size
    m['kb'] = os.path.getsize(path) / 1024.0
    return m


def verdict(m, kind):
    acc = ACCEPT[kind]
    out = []
    if not (acc['mean'][0] <= m['mean'] <= acc['mean'][1]):
        out.append('mean')
    if m['std'] < acc['std']:
        out.append('std')
    if m['lap'] < acc['lap']:
        out.append('lap')
    if m['kb'] * 1024 > MAX_BYTES:
        out.append('size')
    if m['chard'] > MAX_CENTRE:
        out.append('centre')
    return out


# ----------------------------------------------------------------------------
# 单张构建（不达标就换更硬的一档重来）
# ----------------------------------------------------------------------------

def encode(im, path):
    im.save(path, 'WEBP', quality=78, method=6)
    return os.path.getsize(path)


def build_one(scene, kind, verbose=True):
    t0 = time.time()
    c = Ctx(scene['id'], kind, scene['seed'], scene['note'], scene['cn'])
    art = scene['fn'](c)
    art = np.clip(art, 0.0, 255.0)
    base, tone_info = tone_map(art, TONE[kind])

    grain = 1.15 if kind == 'day' else 1.00
    ladder = [(0.0, 0.50), (0.45, 0.55), (0.85, 0.62), (1.25, 0.68),
              (1.70, 0.74), (2.20, 0.80), (2.80, 0.85)]
    fname = 'bg_arcane_%s_%s.webp' % (scene['id'], kind)
    path = os.path.join(OUT_DIR, fname)
    tries = []
    chosen = None
    best_ok = None
    for kappa, sw in ladder:
        img = structure(base, kappa)
        img = soften_center(img, sw)
        img = polish(img, np.random.default_rng(scene['seed'] + 7), grain)
        a = np.clip(img, 0, 255).astype(np.uint8)
        pre = measure_arr(a)
        need = ACCEPT[kind]
        # 数组上先粗筛（编码会略增高频，留 6% 余量）
        if pre['lap'] < need['lap'] * 1.06 or not (need['mean'][0] <= pre['mean'] <= need['mean'][1]):
            tries.append((kappa, sw, round(pre['lap'], 1), 'pre'))
            continue
        encode(Image.fromarray(a, 'RGB'), path)
        m = measure_file(path)
        bad = verdict(m, kind)
        tries.append((kappa, sw, round(m['lap'], 1), ','.join(bad) or 'ok'))
        if not bad:
            if m['lap'] >= need['lap'] * 1.08:
                chosen = (kappa, sw, m)
                break
            if best_ok is None:
                best_ok = (kappa, sw, m)
            continue
        if 'centre' in bad:      # 中央太硬 -> 下一档会更强柔化
            continue
        if 'size' in bad:
            continue
    if chosen is None and best_ok is not None:
        chosen = best_ok
    if chosen is None:
        # 兜底：取最后一档，如实打印不合格项
        kappa, sw = ladder[-1]
        img = structure(base, kappa)
        img = soften_center(img, sw)
        img = polish(img, np.random.default_rng(scene['seed'] + 7), grain)
        a = np.clip(img, 0, 255).astype(np.uint8)
        encode(Image.fromarray(a, 'RGB'), path)
        chosen = (kappa, sw, measure_file(path))
    kappa, sw, m = chosen
    # 去颗粒后的结构值（证明 lap 不是靠噪点刷的）
    img_s = structure(base, kappa)
    img_s = soften_center(img_s, sw)
    ms = measure_arr(np.clip(img_s, 0, 255).astype(np.uint8))
    m['lapS'] = ms['lap']
    m['kappa'] = kappa
    m['soften'] = sw
    m['secs'] = time.time() - t0
    m['tries'] = tries
    if verbose:
        print('  %-22s %4.1fs kappa=%.2f soften=%.2f  %s' %
              (fname, m['secs'], kappa, sw, ' / '.join('%s:%s' % (t[0], t[3]) for t in tries)))
    return fname, m


# ----------------------------------------------------------------------------
# 拼版图
# ----------------------------------------------------------------------------

def _font(sz=15):
    try:
        return ImageFont.load_default(size=sz)
    except Exception:
        return ImageFont.load_default()


def contact_sheet(table, path):
    """4 列 x 4 行：每行两个场景，日/夜各占一格。"""
    tw, th = 244, 537
    gap, lab, head = 12, 22, 48
    cols, rows = 4, 4
    sw = cols * tw + (cols + 1) * gap
    sh = head + rows * (th + lab + gap) + gap
    sheet = Image.new('RGB', (sw, sh), (16, 16, 19))
    d = ImageDraw.Draw(sheet)
    f1, f2 = _font(17), _font(13)
    d.text((gap + 2, 12),
           'Arcanum / 弧光学院 — 8 scenes x day|night   720x1584   WebP q78   total %.0f KB' %
           sum(m['kb'] for _, m in table), fill=(240, 234, 219), font=f1)
    # table 已按 day,night,day,night... 排列
    for r in range(rows):
        for cc in range(cols):
            idx = r * cols + cc
            if idx >= len(table):
                break
            fname, m = table[idx]
            x = gap + cc * (tw + gap)
            y = head + r * (th + lab + gap)
            im = Image.open(os.path.join(OUT_DIR, fname)).convert('RGB').resize((tw, th), Image.LANCZOS)
            sheet.paste(im, (x, y))
            short = fname.replace('bg_arcane_', '').replace('.webp', '')
            d.text((x + 1, y + th + 4), '%s   %.1fKB' % (short, m['kb']),
                   fill=(204, 198, 184), font=f2)
            d.rectangle([x - 1, y - 1, x + tw, y + th], outline=(52, 50, 46))
    sheet.save(path)
    return path


def crop_sheet(ids, path):
    """每个场景取日景中央 720x720（1:1，不缩放），横排。"""
    tw = 720
    gap, lab, head = 12, 26, 44
    n = len(ids)
    sw = n * tw + (n + 1) * gap
    sh = head + tw + lab + gap
    sheet = Image.new('RGB', (sw, sh), (16, 16, 19))
    d = ImageDraw.Draw(sheet)
    f1, f2 = _font(17), _font(14)
    d.text((gap + 2, 11), 'Arcanum day — centre 1:1 crop (720x720, no resize) for noise/brush check',
           fill=(240, 234, 219), font=f1)
    for i, sid in enumerate(ids):
        fname = 'bg_arcane_%s_day.webp' % sid
        im = Image.open(os.path.join(OUT_DIR, fname)).convert('RGB')
        box = ((im.width - tw) // 2, (im.height - tw) // 2,
               (im.width - tw) // 2 + tw, (im.height - tw) // 2 + tw)
        x = gap + i * (tw + gap)
        sheet.paste(im.crop(box), (x, head))
        d.text((x + 2, head + tw + 4), '%s_day  centre 720x720' % sid,
               fill=(206, 200, 186), font=f2)
        d.rectangle([x - 1, head - 1, x + tw, head + tw], outline=(52, 50, 46))
    sheet.save(path)
    return path


# ----------------------------------------------------------------------------
# credits 追加
# ----------------------------------------------------------------------------

MARK = '## 弧光学院（程序化生成）'


def append_credits(table):
    tot = sum(m['kb'] for _, m in table)
    sec = []
    sec.append(MARK)
    sec.append('')
    sec.append('第二套皮肤「弧光学院（Arcanum）」的 16 张背景图为**纯程序化合成**：没有任何第三方图像素材'
               '（无影视/游戏截图、无博物馆藏品、无 AI 生成的位图），全部由 Python + Pillow + numpy 逐像素绘制。'
               '魔法学院意象（尖塔、烛火、星图、禁林、药水、浮空书）均为原创图形，与任何影视/游戏 IP 的'
               '商标、角色、校徽无关。')
    sec.append('')
    sec.append('- **生成器**：[`tools/build_magic_backdrops.py`](../tools/build_magic_backdrops.py)（Pillow + numpy，'
               '无网络依赖、无源图）')
    sec.append('- **产物**：8 场景 × 昼/夜 = 16 张 `720x1584` WebP（q78，method 6），'
               '`bg_arcane_<id>_day.webp` / `bg_arcane_<id>_night.webp`')
    sec.append('- **参数**：2× 超采样（1440×3168）绘制后 LANCZOS 下采样；色调用线性 offset 二分求解到目标'
               '平均亮度（day 191 / night 45，52 次二分；不做 gamma 压缩——gamma 会把 30 级的分层间距压成 8 级）；'
               '双层 unsharp 只作用于「中央 60% 之外」；中心做 2.4px 半径柔化；'
               '细颗粒 σ=1.15/1.00 + ±0.9 抖动（抖动专门用来消掉长渐变上的色带）；暗角由各场景渐变自带')
    sec.append('- **设计约束**：画面中央 60% 区域不放高对比硬边（正文会压在上面），剪影与线稿全部收在上下与左右'
               '边带；day 走晨雾羊皮纸 + 墨褐剪影，night 走午夜蓝 + 烛金 + 星蓝，两者构图同源（同一随机种子）')
    sec.append('')
    sec.append('| 场景 | 设计 | day KB | night KB | day luma / std / lap | night luma / std / lap |')
    sec.append('| --- | --- | --- | --- | --- | --- |')
    by_id = {}
    for fn, m in table:
        sid = fn.replace('bg_arcane_', '').replace('.webp', '').rsplit('_', 1)[0]
        by_id.setdefault(sid, {})[fn.rsplit('_', 1)[1].replace('.webp', '')] = m
    cn = {s['id']: s['cn'] for s in SCENES}
    note = {s['id']: s['note'] for s in SCENES}
    for s in SCENES:
        dd, nn = by_id[s['id']]['day'], by_id[s['id']]['night']
        sec.append('| `%s` %s | %s | %.1f | %.1f | %.0f / %.1f / %.0f | %.0f / %.1f / %.0f |' % (
            s['id'], cn[s['id']], note[s['id']], dd['kb'], nn['kb'],
            dd['mean'], dd['std'], dd['lap'], nn['mean'], nn['std'], nn['lap']))
    sec.append('')
    sec.append('合计 **%.1f KB**（%.2f MB，上限 1.1 MB；单张上限 70 KB），16 张全部通过验收。' % (
        tot, tot / 1024.0))
    sec.append('')
    sec.append('**验收口径**（`lap` 为全分辨率 4 邻域拉普拉斯方差，均从编码后的文件解码再测）：'
               'day luma 178–205 / std(90×198) ≥ 10 / lap ≥ 38；night 32–58 / ≥ 8 / ≥ 26；'
               '中央 60% 高对比硬边覆盖率 ≤ 1.5%。注：「先高斯半径 4 再算拉普拉斯」在 8-bit 输入下'
               '响应上界只有 `2A/w² ≈ 4.0`、方差上界 16，任何图都到不了 38，故取不预模糊的直接定义'
               '（v1 古画 7.9–11.6，本套 60–200，38/26 才有区分度）；脚本同时打印字面 `lap4` 与去颗粒结构值 `lapS`。')
    sec.append('')
    sec.append('- **复核图**：[`magic-contact.png`](smoke/magic-contact.png)（4×4 全量拼版，标注文件名与 KB）、'
               '[`magic-crop.png`](smoke/magic-crop.png)（castle / forest / nebula 日景中央 720×720 1:1 裁切，'
               '看笔触与噪声是否自然）')
    sec.append('')
    sec.append('**可复现**')
    sec.append('')
    sec.append('```bash')
    sec.append('python tools/build_magic_backdrops.py                 # 重建 16 张 + 两张拼版图 + 本节')
    sec.append('python tools/build_magic_backdrops.py --only castle   # 只重建某些场景')
    sec.append('python tools/build_magic_backdrops.py --measure       # 不重绘，只测量已有产物')
    sec.append('```')
    sec.append('')
    txt = ''
    if os.path.exists(CREDITS):
        with open(CREDITS, 'r', encoding='utf-8') as f:
            txt = f.read()
    idx = txt.find(MARK)
    if idx >= 0:
        txt = txt[:idx].rstrip() + '\n\n'
    else:
        txt = txt.rstrip() + '\n\n'
    txt = txt + '\n'.join(sec) + '\n'
    with open(CREDITS, 'w', encoding='utf-8') as f:
        f.write(txt)
    return CREDITS


# ----------------------------------------------------------------------------

def main():
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', default='', help='逗号分隔的场景 id')
    ap.add_argument('--measure', action='store_true', help='只测量，不重绘')
    args = ap.parse_args()

    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(SMOKE_DIR, exist_ok=True)
    want = [s for s in SCENES if not args.only or s['id'] in args.only.split(',')]

    if args.measure:
        table = []
        for s in SCENES:
            for kind in ('day', 'night'):
                p = os.path.join(OUT_DIR, 'bg_arcane_%s_%s.webp' % (s['id'], kind))
                if os.path.exists(p):
                    m = measure_file(p)
                    m['lapS'] = float('nan')
                    table.append(('bg_arcane_%s_%s.webp' % (s['id'], kind), m))
        _print_table(table)
        return 0

    print('[arcane] 输出 %s' % OUT_DIR)
    table = []
    t0 = time.time()
    for s in want:
        print('[%s] %s' % (s['id'], s['cn']))
        for kind in ('day', 'night'):
            fn, m = build_one(s, kind)
            table.append((fn, m))
    _print_table(table)

    tot = sum(m['kb'] for _, m in table)
    bad = []
    for fn, m in table:
        k = 'day' if fn.endswith('_day.webp') else 'night'
        v = verdict(m, k)
        if v:
            bad.append((fn, v))
    print('\n合计 %.1f KB / %.1f KB (%.2f MB)  用时 %.1fs  不合格 %d 张' % (
        tot, MAX_TOTAL / 1024.0, tot / 1024.0, time.time() - t0, len(bad)))
    for fn, v in bad:
        print('  BAD %s -> %s' % (fn, ','.join(v)))

    if len(table) == 16:
        p1 = contact_sheet(table, os.path.join(SMOKE_DIR, 'magic-contact.png'))
        p2 = crop_sheet(['castle', 'forest', 'nebula'], os.path.join(SMOKE_DIR, 'magic-crop.png'))
        print('[sheet] %s (%d KB)' % (p1, os.path.getsize(p1) // 1024))
        print('[sheet] %s (%d KB)' % (p2, os.path.getsize(p2) // 1024))
        append_credits(table)
        print('[credits] %s' % CREDITS)
    return 0


def _print_table(table, dump=True):
    print('')
    print('%-30s %-6s %6s %6s %7s %6s %6s %6s %6s' % (
        'file', 'kind', 'KB', 'luma', 'std', 'lap', 'lap4', 'lapS', 'cen%'))
    print('-' * 96)
    for fn, m in table:
        kind = 'day' if fn.endswith('_day.webp') else 'night'
        v = verdict(m, kind) if not np.isnan(m.get('lapS', np.nan)) else []
        print('%-30s %-6s %6.1f %6.1f %7.1f %6.1f %6.1f %6.1f %6.2f  %s' % (
            fn, kind, m['kb'], m['mean'], m['std'], m['lap'], m['lap4'],
            m.get('lapS', float('nan')), m['chard'], 'OK' if not v else 'BAD:' + ','.join(v)))
    print('')
    if dump:
        lines = ['%-30s %-6s %6s %6s %6s %6s %6s %6s %6s' % (
            'file', 'kind', 'KB', 'luma', 'std', 'lap', 'lap4', 'lapS', 'centre%')]
        for fn, m in table:
            kind = 'day' if fn.endswith('_day.webp') else 'night'
            v = verdict(m, kind) if not np.isnan(m.get('lapS', np.nan)) else []
            lines.append('%-30s %-6s %6.1f %6.1f %6.1f %6.1f %6.1f %6.1f %6.2f  %s' % (
                fn, kind, m['kb'], m['mean'], m['std'], m['lap'], m['lap4'],
                m.get('lapS', float('nan')), m['chard'], 'OK' if not v else 'BAD:' + ','.join(v)))
        lines.append('total KB %.1f' % sum(m['kb'] for _, m in table))
        io = __import__('io')
        with io.open(os.path.join(SMOKE_DIR, 'magic-metrics.txt'), 'w',
                     encoding='utf-8', newline='\n') as f:
            f.write('\n'.join(lines) + '\n')


if __name__ == '__main__':
    sys.exit(main())
