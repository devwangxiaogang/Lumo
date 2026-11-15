"""生成「语光 / Lumo」的应用图标。

设计：宣纸底 + 一枚朱砂方印（印白「光」字，界格细边），与 App 内首页左上角的
「語光」方印同构；背景底部有一层极淡的墨色远山，避免纯纸色在浅色桌面上发飘。

输出（--skin lumo，默认，与历史产物逐字节一致）：
  app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp
  app/src/main/res/mipmap-*/ic_launcher_round.webp
  app/src/main/res/drawable-*/ic_launcher_background.png   (adaptive 108dp 画布)
  app/src/main/res/drawable-*/ic_launcher_foreground.png
  app/src/main/res/drawable-*/ic_launcher_monochrome.png
  docs/screenshots/app-icon.png                            (512, README 用)

输出（--skin arcane，第二套皮肤「弧光学院 / Arcanum」，全部用新文件名）：
  mipmap-*/ic_launcher_arcane.webp、ic_launcher_arcane_round.webp
  drawable-*/ic_launcher_arcane_{background,foreground,monochrome}.webp
  mipmap-anydpi-v26/ic_launcher_arcane.xml、ic_launcher_arcane_round.xml
  docs/screenshots/app-icon-arcane.png                 (512 预览)
  docs/screenshots/app-icon-arcane-states.png          (五形态对照)

用法：python tools/build_icon.py                     # lumo 全部尺寸
      python tools/build_icon.py --skin arcane       # arcane 全部尺寸
      python tools/build_icon.py --skin arcane --preview   # 只出预览到 C:/tmp
      python tools/build_icon.py --preview           # lumo 预览到 C:/tmp
"""

from __future__ import annotations

import argparse
import math
import random
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
FONT_PATH = Path("C:/Windows/Fonts/NotoSerifSC-VF.ttf")

# 与 ui/theme/AiTheme.kt 的 LumoPalette 保持一致
PAPER_TOP = (247, 240, 225)
PAPER_BOTTOM = (234, 223, 200)
CINNABAR = (192, 65, 47)
CINNABAR_DEEP = (163, 46, 36)
SEAL_INK = (255, 246, 233)
INK = (35, 42, 38)

# ---------------------------------------------------------------------------
# 第二套皮肤「弧光学院 / Arcanum」的调色板
# 与朱砂方印同一家族（「徽记 + 底」），换材质：午夜蓝夜空 + 金色八芒星 + 一圈细金环。
# 所有渐变都在 sRGB 空间逐通道插值（Pillow 默认行为），不做 linear-light 转换：
# sRGB 下插值才能保住金色的彩度，转 linear 再回来会让 #F0D793→#C9A24D 的中段发灰。
# ---------------------------------------------------------------------------
ARCANE_NIGHT_TOP = (14, 20, 40)          # #0E1428
ARCANE_NIGHT_BOTTOM = (27, 34, 66)       # #1B2242
ARCANE_GOLD_LIGHT = (240, 215, 147)      # #F0D793
ARCANE_GOLD_DEEP = (201, 162, 77)        # #C9A24D
ARCANE_GOLD_GLOW = (255, 246, 214)       # 星体左上角的一点高光（刻意偏暖，不发白）
ARCANE_GOLD_RING = (238, 196, 100)       # 细环专用：小尺寸下压窄渐变跨度，顶端不至于淡成灰白
ARCANE_GOLD_RING_DEEP = (206, 158, 58)
ARCANE_RING_HALO = 0.45                  # 细环外发的强度（抵消 WebP 4:2:0 抽色，见 arcane_emblem）
ARCANE_STARFIELD = (206, 222, 255)       # 背景星点：冷白，只做气氛
ARCANE_SEAL_RATIO = 0.70                 # 金环直径 / 画布边长（方形）
ARCANE_SEAL_RATIO_ROUND = 0.62           # 圆版整体收一圈，给圆形遮罩留呼吸
ARCANE_STATES_BG = (246, 243, 236)       # 对照图底色，与 app-icon-states.png 一致


def serif(size: int, weight: int = 620) -> ImageFont.FreeTypeFont:
    font = ImageFont.truetype(str(FONT_PATH), size)
    try:
        font.set_variation_by_axes([weight])
    except Exception:
        pass
    return font


def vertical_gradient(size: tuple[int, int], top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    w, h = size
    grad = Image.new("RGB", (1, h))
    px = grad.load()
    for y in range(h):
        t = y / max(h - 1, 1)
        px[0, y] = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return grad.resize((w, h), Image.BILINEAR)


def paper(size: tuple[int, int]) -> Image.Image:
    """宣纸底：竖直渐变 + 极淡墨山 + 细纸纹。"""
    w, h = size
    base = vertical_gradient(size, PAPER_TOP, PAPER_BOTTOM).convert("RGBA")

    # 纸纹：极轻的噪点，避免大片纯色在 OLED 上显脏
    noise = Image.effect_noise((w, h), 6).convert("L")
    noise_rgba = Image.new("RGBA", size)
    noise_rgba.putalpha(noise.point(lambda v: int(v * 0.10)))
    base = Image.alpha_composite(base, noise_rgba)
    return base


def seal(size: int, supersample: int = 4) -> Image.Image:
    """朱砂方印（透明底，含印白「光」字与界格细边）。"""
    supersample = max(supersample, math.ceil(512 / max(size, 1)))
    s = size * supersample
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    pad = s * 0.02
    radius = s * 0.085
    body = (round(pad), round(pad), round(s - pad), round(s - pad))
    # 印面：朱砂竖向微渐变，模拟印泥厚薄
    face = vertical_gradient((s, s), CINNABAR, CINNABAR_DEEP).convert("RGBA")
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle(body, radius=radius, fill=255)
    # 印泥毛边：把蒙版轻微抖动一圈
    mask = mask.filter(ImageFilter.GaussianBlur(s * 0.004))
    img.paste(face, (0, 0), mask)

    # 印面噪点：印泥不均匀
    grain = Image.effect_noise((s, s), 26).convert("L").point(lambda v: int(abs(v - 128) * 0.35))
    grain_rgba = Image.new("RGBA", (s, s), (255, 255, 255, 0))
    grain_rgba.putalpha(Image.composite(grain, Image.new("L", (s, s), 0), mask))
    img = Image.alpha_composite(img, grain_rgba)
    d = ImageDraw.Draw(img)

    # 界格：印面内收的一道印白细边
    inset = s * 0.105
    d.rounded_rectangle(
        (round(inset), round(inset), round(s - inset), round(s - inset)),
        radius=radius * 0.9,
        outline=(255, 246, 233, 120),
        width=max(1, round(s * 0.012)),
    )

    # 印白「光」字
    font_size = round(s * 0.62)
    font = serif(font_size, weight=640)
    glyph = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glyph)
    box = gd.textbbox((0, 0), "光", font=font)
    gw, gh = box[2] - box[0], box[3] - box[1]
    gd.text(
        ((s - gw) / 2 - box[0], (s - gh) / 2 - box[1] + s * 0.004),
        "光",
        font=font,
        fill=SEAL_INK + (255,),
    )
    img = Image.alpha_composite(img, glyph)

    return img.resize((size, size), Image.LANCZOS)


def legacy_icon(px: int, round_icon: bool) -> Image.Image:
    """老 launcher 用的整图：宣纸底 + 居中印章，四周留 paper margin。"""
    canvas = paper((px, px))
    # 圆形图标留白更多，方形图标可撑满一点
    seal_ratio = 0.66 if round_icon else 0.70
    s = round(px * seal_ratio)
    art = seal(s, supersample=4)
    offset = ((px - s) // 2, (px - s) // 2)
    canvas.alpha_composite(art, offset)

    # 蒙版：方形留 22% 圆角，圆形直接裁圆
    mask = Image.new("L", (px, px), 0)
    md = ImageDraw.Draw(mask)
    if round_icon:
        md.ellipse((0, 0, px - 1, px - 1), fill=255)
    else:
        md.rounded_rectangle((0, 0, px - 1, px - 1), radius=round(px * 0.225), fill=255)
    out = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    out.paste(canvas, (0, 0), mask)
    return out


def adaptive_layers(dp: int, px: int) -> tuple[Image.Image, Image.Image, Image.Image]:
    """adaptive icon 三件套（108dp 画布）：background / foreground / monochrome。"""
    bg = paper((px, px))
    fg = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    mono = Image.new("RGBA", (px, px), (0, 0, 0, 0))

    # 安全区：以 108dp 画布中心为圆心，可见区约 72dp；印章取 58dp，圆角吸收裁切。
    seal_dp = 60.0
    s = round(px * seal_dp / dp)
    art = seal(s, supersample=4)
    off = ((px - s) // 2, (px - s) // 2)
    fg.alpha_composite(art, off)

    # 单色层：只有印面轮廓 + 字，颜色交给系统（用白，alpha 作为蒙版）
    md = ImageDraw.Draw(mono)
    pad = s * 0.02
    md.rounded_rectangle(
        (off[0] + pad, off[1] + pad, off[0] + s - pad, off[1] + s - pad),
        radius=s * 0.085,
        outline=(255, 255, 255, 255),
        width=max(2, round(s * 0.055)),
    )
    font = serif(round(s * 0.62), weight=640)
    glyph = Image.new("RGBA", mono.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glyph)
    box = gd.textbbox((0, 0), "光", font=font)
    gw, gh = box[2] - box[0], box[3] - box[1]
    gd.text(
        (off[0] + (s - gw) / 2 - box[0], off[1] + (s - gh) / 2 - box[1]),
        "光",
        font=font,
        fill=(255, 255, 255, 255),
    )
    mono = Image.alpha_composite(mono, glyph)

    return bg.convert("WEBP_LOSSLESS" if False else "RGBA"), fg, mono


def save_webp(img: Image.Image, path: Path, quality: int = 92) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGBA").save(path, "WEBP", quality=quality, method=6)


def save_layer(img: Image.Image, path: Path) -> None:
    """adaptive 层：渐变+噪点用 PNG 会到 88KB，WebP q92 只有十几 KB。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGBA").save(path, "WEBP", quality=92, method=6, exact=True)


def save_layer_arcane(img: Image.Image, path: Path) -> None:
    """arcane 的 adaptive 层：同样 q92 / method=6，但不传 exact=True。

    exact=True 时 libwebp 会把透明像素里的 RGB 原样编进去。我们的徽记画在透明底上，
    于是编码器要在「金色 ↔ 透明黑」的硬边界上硬扛整条轮廓，fg 432px 要多花 2.1KB、
    mono 要多花 5.6KB（22 张层合起来能省 20 多 KB）。exact=False 让 libwebp 按邻近色
    清理整片透明区，编码代价降下来；顺带也消除了非预乘解码器在缩放时从覆盖边缘渗出的黑边。
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGBA").save(path, "WEBP", quality=92, method=6)


# ===========================================================================
# 「弧光学院 / Arcanum」—— 第二套皮肤
# 下面所有东西只在 --skin arcane 下执行，lumo 的代码路径完全不经过这里。
# ===========================================================================


def arcane_stroke(vis: float) -> float:
    """金环线宽。512 基准 1.5px；小尺寸按 1 设备像素下限钳制做光学补偿 ——
    1.5px 直接按比例缩到 48px 只剩 0.14px，整圈会蒸发掉。
    下限给到 1.0 而不是 0.8：0.8px 的线只盖住像素的六成，再经 WebP 4:2:0 色度抽样，
    金环会被抽成一条发灰的虚线；1.0px 刚好占满一整列像素，48px 下是干净的细环。"""
    return max(1.5 * vis / 512.0, 1.0)


def arcane_dot_size(vis: float) -> float:
    """环外 8 颗小星点直径。512 基准 3px，小尺寸钳到 ≥1.0px。"""
    return max(3.0 * vis / 512.0, 1.0)


def arcane_proportions(vis: float) -> tuple[float, float, float, float]:
    """(星半径 / 环半径, 短芒比, 谷半径比, 芒尖切钝比)。

    512 基准一套值；尺寸越小，谷半径抬起来、短芒变长、切钝量变大 —— 芒会明显变粗。
    48px 时谷半径 0.385 → 0.440，芒的根部宽了近四成，缩略图才不会糊成一团金块。
    这是唯一按尺寸变化的地方，其余几何（环径、环线宽、星点位置）全部等比。"""
    small = 1.0 - min(max((vis - 48.0) / (192.0 - 48.0), 0.0), 1.0)  # 48→1, ≥192→0
    return (
        0.695,
        0.625 + 0.035 * small,
        0.385 + 0.055 * small,
        0.115 + 0.030 * small,
    )


def _polar(cx: float, cy: float, ang: float, r: float) -> tuple[float, float]:
    return (cx + r * math.cos(ang), cy + r * math.sin(ang))


def arcane_star_points(
    cx: float, cy: float, r_long: float, r_short: float, r_valley: float, blunt: float, steps: int = 7
) -> list[tuple[float, float]]:
    """八芒星轮廓：4 条长芒朝正方向、4 条短芒朝对角，谷点保持尖角。

    芒尖不做多边形直尖，而是在顶角两侧各退 blunt 处取两点，用一条二次贝塞尔
    （控制点就是原顶点）连过去 —— 尖头因此被切开一小块并变圆，即「星尖略钝」。
    直接对尖锐顶点做圆弧倒角会把芒吃掉近三成，所以这里用切钝而不是内切圆角。"""
    verts: list[tuple[float, float]] = []
    for i in range(16):
        ang = -math.pi / 2 + i * math.pi / 8.0   # 从正上方开始，每 22.5° 一个顶点
        if i % 4 == 0:
            r = r_long
        elif i % 4 == 2:
            r = r_short
        else:
            r = r_valley
        verts.append((ang, r))

    pts: list[tuple[float, float]] = []
    n = len(verts)
    for i, (ang, r) in enumerate(verts):
        tip = _polar(cx, cy, ang, r)
        if i % 2 == 1:                      # 谷点：内凹，保持尖
            pts.append(tip)
            continue
        pa = _polar(cx, cy, verts[(i - 1) % n][0], verts[(i - 1) % n][1])
        pb = _polar(cx, cy, verts[(i + 1) % n][0], verts[(i + 1) % n][1])
        d = blunt
        p1 = (tip[0] + (pa[0] - tip[0]) * d / max(math.dist(tip, pa), 1e-6),
              tip[1] + (pa[1] - tip[1]) * d / max(math.dist(tip, pa), 1e-6))
        p2 = (tip[0] + (pb[0] - tip[0]) * d / max(math.dist(tip, pb), 1e-6),
              tip[1] + (pb[1] - tip[1]) * d / max(math.dist(tip, pb), 1e-6))
        for s in range(steps + 1):
            t = s / steps
            u = 1.0 - t
            pts.append((
                u * u * p1[0] + 2 * u * t * tip[0] + t * t * p2[0],
                u * u * p1[1] + 2 * u * t * tip[1] + t * t * p2[1],
            ))
    return pts


def _gradient_band(size: tuple[int, int], y0: float, y1: float,
                   top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    """把一条竖直渐变贴到 [y0, y1] 的条带里，条带外沿用端点色填满（只为不留黑边）。"""
    w, h = size
    a = max(0, min(h - 2, int(round(y0))))
    b = max(a + 1, min(h - 1, int(round(y1))))
    band = vertical_gradient((w, b - a + 1), top, bottom)
    img = Image.new("RGB", (w, h), top)
    img.paste(band, (0, a))
    if a > 0:
        img.paste(Image.new("RGB", (w, a), top), (0, 0))
    if b + 1 < h:
        img.paste(Image.new("RGB", (w, h - b - 1), bottom), (0, b + 1))
    return img


def arcane_emblem(size: int, seal_ratio: float, *, vis: float | None = None,
                  mono: bool = False, dots: bool = True) -> Image.Image:
    """透明底的徽记：金环 + 八芒星（+ 环外 8 颗小星点），居中画在 size×size 画布上。

    size      画布像素
    seal_ratio 金环直径 / 画布边长
    vis        「可见图标」的像素尺寸，仅用于小尺寸光学补偿（默认等于 size）
    mono      True 时输出纯白实心轮廓，交给系统着色（不含渐变、不含底、不含星点）
    """
    vis_px = float(size if vis is None else vis)
    ss = 4 if size >= 128 else max(4, math.ceil(256 / size))
    s = size * ss
    c = s / 2.0

    star_ratio, short_ratio, valley_ratio, blunt_ratio = arcane_proportions(vis_px)
    ring_r = seal_ratio * s / 2.0
    star_r = ring_r * star_ratio
    stroke = arcane_stroke(vis_px) * ss
    dot_d = arcane_dot_size(vis_px) * ss

    # --- 蒙版 -------------------------------------------------------------
    star_mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(star_mask).polygon(
        arcane_star_points(c, c, star_r, star_r * short_ratio, star_r * valley_ratio, star_r * blunt_ratio),
        fill=255,
    )

    outer = Image.new("L", (s, s), 0)
    rr = ring_r + stroke / 2.0
    ImageDraw.Draw(outer).ellipse((c - rr, c - rr, c + rr, c + rr), fill=255)
    inner = Image.new("L", (s, s), 0)
    ri = max(ring_r - stroke / 2.0, 0.0)
    if ri > 0:
        ImageDraw.Draw(inner).ellipse((c - ri, c - ri, c + ri, c + ri), fill=255)
    ring_mask = ImageChops.subtract(outer, inner)

    dot_mask = Image.new("L", (s, s), 0)
    if dots:
        dd = ImageDraw.Draw(dot_mask)
        # 512 基准空隙 11.3px；小尺寸下星点有 1px 下限，空隙也跟着放宽，
        # 否则 48px 时星点会贴到环上，把细环糊成一串珠子
        dot_orbit = ring_r + max(0.022 * vis_px * ss, 2.0 * dot_d) + dot_d / 2.0
        for k in range(8):                  # 8 颗等距，落在 45° 的整数倍上
            ang = -math.pi / 2 + k * math.pi / 4.0
            x, y = _polar(c, c, ang, dot_orbit)
            dd.ellipse((x - dot_d / 2.0, y - dot_d / 2.0, x + dot_d / 2.0, y + dot_d / 2.0), fill=255)

    # --- 上色 -------------------------------------------------------------
    if mono:
        solid = Image.new("RGBA", (s, s), (255, 255, 255, 255))
        level = ImageChops.lighter(star_mask, ring_mask)
        img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        img.paste(solid, (0, 0), level)
        return img.resize((size, size), Image.LANCZOS)

    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))

    # 星体：竖向线性渐变（上亮下深，模拟自上打光）
    star_fill = _gradient_band((s, s), c - star_r, c + star_r, ARCANE_GOLD_LIGHT, ARCANE_GOLD_DEEP).convert("RGBA")
    # 左上一点暖高光，避免大芒显平；只在星体上生效
    glow = Image.new("L", (s, s), 0)
    gr = star_r * 0.60
    gx, gy = c - star_r * 0.30, c - star_r * 0.34
    ImageDraw.Draw(glow).ellipse((gx - gr, gy - gr, gx + gr, gy + gr), fill=104)
    glow = ImageChops.multiply(glow.filter(ImageFilter.GaussianBlur(star_r * 0.45)), star_mask)
    star_fill = Image.composite(Image.new("RGBA", (s, s), ARCANE_GOLD_GLOW + (255,)), star_fill, glow)
    img.paste(star_fill, (0, 0), star_mask)

    # 金环：跟着星体同样的竖向渐变走一遍，比星体略深半档。
    # 但环窄到 1 设备像素时（48~288px 的绝大多数产物都是这样），「上亮下深」的大跨度渐变
    # 会让顶端那条弧淡到接近白、经 4:2:0 色度抽样后整圈发灰；细环改用压窄的一档金，
    # 环在任何方向都还是金色。
    if stroke < 1.2 * ss:
        ring_top, ring_bottom = ARCANE_GOLD_RING, ARCANE_GOLD_RING_DEEP
        # 1px 的金线在 WebP 4:2:0 下会掉色：色度块只有 2x2 一个样本，细线的色度被两侧的
        # 午夜蓝平均掉，解码出来是一圈浅灰。沿着环补一圈很淡的金晕，给色度块留金色可用 ——
        # 视觉上像一圈极弱的外发光，正好贴「弧光」的主题。
        halo = ring_mask.filter(ImageFilter.GaussianBlur(max(stroke * 1.7, 1.0)))
        halo = halo.point(lambda v: int(v * ARCANE_RING_HALO))
        img.paste(Image.new("RGBA", (s, s), ring_top + (255,)), (0, 0), halo)
    else:
        ring_top, ring_bottom = ARCANE_GOLD_LIGHT, ARCANE_GOLD_DEEP
    ring_fill = _gradient_band((s, s), c - ring_r, c + ring_r, ring_top, ring_bottom).convert("RGBA")
    img.paste(ring_fill, (0, 0), ring_mask)

    # 星点：实心，色比星体浅一档
    if dots:
        img.paste(Image.new("RGBA", (s, s), (240, 224, 176, 255)), (0, 0), dot_mask)

    return img.resize((size, size), Image.LANCZOS)


def _arcane_starfield(size: tuple[int, int]) -> Image.Image:
    """极细、低对比的星点层：数量随画布线性增长，48px 下只剩几个亚像素点，不会抢主体。"""
    w, h = size
    rng = random.Random(20261116)
    n = max(8, round(62 * (w / 512.0) ** 1.15))
    alpha = Image.new("L", size, 0)
    d = ImageDraw.Draw(alpha)
    scale = w / 512.0
    for _ in range(n):
        x, y = rng.uniform(0, w), rng.uniform(0, h)
        r = rng.uniform(0.45, 1.30) * scale
        d.ellipse((x - r, y - r, x + r, y + r), fill=round(rng.uniform(16, 58)))
    alpha = alpha.filter(ImageFilter.GaussianBlur(max(0.35, 0.5 * scale)))
    layer = Image.new("RGBA", size, ARCANE_STARFIELD + (0,))
    layer.putalpha(alpha)
    return layer


def _arcane_vignette(size: tuple[int, int], depth: float = 0.08) -> Image.Image:
    """四角 8% 暗角：低分辨率算完再双三次放大（曲线本身是光滑的，放大看不出来）。"""
    n = 96
    v = Image.new("L", (n, n))
    p = v.load()
    mid = (n - 1) / 2.0
    far = math.hypot(mid, mid)
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - mid, y - mid) / far
            p[x, y] = round(255 * (1.0 - depth * d ** 2.2))
    return v.resize(size, Image.BICUBIC)


def arcane_night(size: tuple[int, int]) -> Image.Image:
    """底：午夜蓝竖渐变 + 细星点 + 8% 暗角。"""
    base = vertical_gradient(size, ARCANE_NIGHT_TOP, ARCANE_NIGHT_BOTTOM).convert("RGBA")
    base = Image.alpha_composite(base, _arcane_starfield(size))
    vig = _arcane_vignette(size)
    rgb = ImageChops.multiply(base.convert("RGB"), vig.convert("RGB"))   # sRGB 逐通道压暗，色相不变
    return rgb.convert("RGBA")


def legacy_icon_arcane(px: int, round_icon: bool) -> Image.Image:
    """老 launcher 的整图：夜空底 + 居中徽记；方形留 22.5% 圆角，圆形裁圆。"""
    canvas = arcane_night((px, px))
    ratio = ARCANE_SEAL_RATIO_ROUND if round_icon else ARCANE_SEAL_RATIO
    canvas.alpha_composite(arcane_emblem(px, ratio, vis=px), (0, 0))

    mask = Image.new("L", (px, px), 0)
    md = ImageDraw.Draw(mask)
    if round_icon:
        md.ellipse((0, 0, px - 1, px - 1), fill=255)
    else:
        md.rounded_rectangle((0, 0, px - 1, px - 1), radius=round(px * 0.225), fill=255)
    out = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    out.paste(canvas, (0, 0), mask)
    return out


def adaptive_layers_arcane(dp: int, px: int) -> tuple[Image.Image, Image.Image, Image.Image]:
    """adaptive 三件套（108dp 画布）。可见区只有中央 72dp，可见尺寸 = px * 72/108，
    徽记按可见尺寸的 70% 放，星点外沿约 76% —— 落在 72dp 安全区内且有余量。"""
    bg = arcane_night((px, px))
    vis = px * 72.0 / dp
    ratio = ARCANE_SEAL_RATIO * 72.0 / dp
    fg = arcane_emblem(px, ratio, vis=vis)
    mono = arcane_emblem(px, ratio, vis=vis, mono=True, dots=False)
    return bg, fg, mono


ARCANE_ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_arcane_background" />
    <foreground android:drawable="@drawable/ic_launcher_arcane_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_arcane_monochrome" />
</adaptive-icon>"""


def write_adaptive_xml(stem: str) -> Path:
    path = RES / f"mipmap-anydpi-v26/{stem}.xml"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(ARCANE_ADAPTIVE_XML, encoding="utf-8", newline="\n")
    return path


def _mono_plate(size: int, plate: tuple[int, int, int], ink: tuple[int, int, int]) -> Image.Image:
    """对照图用：把单色层染成 ink 色，铺在一块圆形 plate 上（模拟系统给单色层的着色）。"""
    disc = Image.new("RGBA", (size, size), plate + (255,))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(disc, (0, 0), mask)
    tinted = Image.new("RGBA", (size, size), ink + (255,))
    tinted.putalpha(arcane_emblem(size, ARCANE_SEAL_RATIO, vis=size, mono=True, dots=False).getchannel("A"))
    out.alpha_composite(tinted)
    return out


def arcane_states_sheet(path: Path) -> None:
    """五形态对照图：960×240，五格换行 = 方形 / 圆形 / 单色(浅底) / 单色(深底) / 48px 放大。"""
    W, H = 960, 240
    cell, gap_y, small = 144, 40, 96
    sheet = Image.new("RGBA", (W, H), ARCANE_STATES_BG + (255,))
    sheet.alpha_composite(legacy_icon_arcane(cell, round_icon=False), (40, gap_y))
    sheet.alpha_composite(legacy_icon_arcane(cell, round_icon=True), (240, gap_y))
    sheet.alpha_composite(_mono_plate(cell, (242, 234, 218), (43, 51, 82)), (440, gap_y))     # 浅色桌面
    sheet.alpha_composite(_mono_plate(cell, (16, 21, 39), (233, 223, 199)), (640, gap_y))      # 深色桌面
    # 最后一格是真正的 48px 产物 ×2 最近邻放大 —— 不重画一遍大图，看的就是 48px 的真实观感。
    # 竖直方向与上面四格中心对齐（参考图里第五格就是 64 = 40 + (144-96)/2）。
    sheet.alpha_composite(legacy_icon_arcane(48, round_icon=False).resize((small, small), Image.NEAREST),
                          (840, gap_y + (cell - small) // 2))
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(path, "PNG", optimize=True)


def _arcane_preview(out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    for px in (512, 192, 144, 96, 72, 48):
        legacy_icon_arcane(px, round_icon=False).save(out / f"square-{px}.png")
        legacy_icon_arcane(px, round_icon=True).save(out / f"round-{px}.png")
    bg, fg, mono = adaptive_layers_arcane(108, 432)
    bg.save(out / "adaptive-bg.png")
    fg.save(out / "adaptive-fg.png")
    mono.save(out / "adaptive-mono.png")
    shot = Image.new("RGBA", (432, 432), (30, 32, 34, 255))
    shot.alpha_composite(bg, (0, 0))
    shot.alpha_composite(fg, (0, 0))
    mask = Image.new("L", (432, 432), 0)
    ImageDraw.Draw(mask).ellipse((36, 36, 396, 396), fill=255)
    plate = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    plate.paste(shot, (0, 0), mask)
    plate.save(out / "preview-circle.png")
    # 48px 放大 8 倍，用来看缩略图有没有糊成一团
    legacy_icon_arcane(48, round_icon=False).resize((384, 384), Image.NEAREST).save(out / "zoom48.png")
    arcane_states_sheet(out / "states.png")
    print("preview ->", out)


def _build_lumo(preview: bool) -> None:
    """原 main() 的主体，逐字保留。--skin lumo 必须与历史产物逐字节一致。"""
    if preview:
        out = Path("C:/tmp/icon")
        out.mkdir(parents=True, exist_ok=True)
        for px in (512, 192, 144, 96, 72, 48):
            legacy_icon(px, round_icon=False).save(out / f"square-{px}.png")
            legacy_icon(px, round_icon=True).save(out / f"round-{px}.png")
        bg, fg, mono = adaptive_layers(108, 432)
        bg.save(out / "adaptive-bg.png")
        fg.save(out / "adaptive-fg.png")
        mono.save(out / "adaptive-mono.png")
        # 桌面合成预览：圆形蒙版
        shot = Image.new("RGBA", (432, 432), (30, 32, 34, 255))
        shot.alpha_composite(bg, (0, 0))
        shot.alpha_composite(fg, (0, 0))
        mask = Image.new("L", (432, 432), 0)
        ImageDraw.Draw(mask).ellipse((36, 36, 396, 396), fill=255)
        plate = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
        plate.paste(shot, (0, 0), mask)
        plate.save(out / "preview-circle.png")
        print("preview ->", out)
        return

    densities = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
    for name, factor in densities.items():
        px = round(48 * factor)
        save_webp(legacy_icon(px, round_icon=False), RES / f"mipmap-{name}/ic_launcher.webp")
        save_webp(legacy_icon(px, round_icon=True), RES / f"mipmap-{name}/ic_launcher_round.webp")

        layer = round(108 * factor)
        bg, fg, mono = adaptive_layers(108, layer)
        save_layer(bg, RES / f"drawable-{name}/ic_launcher_background.webp")
        save_layer(fg, RES / f"drawable-{name}/ic_launcher_foreground.webp")
        save_layer(mono, RES / f"drawable-{name}/ic_launcher_monochrome.webp")
        print(f"{name}: icon {px}px, adaptive layer {layer}px")

    for old in RES.glob("drawable-*/ic_launcher_*.png"):
        old.unlink()
    # 矢量版的三件套已被位图替代，删掉避免同资源名冲突
    for stale in ("ic_launcher_background.xml", "ic_launcher_foreground.xml", "ic_launcher_monochrome.xml"):
        path = RES / "drawable" / stale
        if path.exists():
            path.unlink()
            print("removed", path.relative_to(ROOT))

    icon = legacy_icon(512, round_icon=False)
    icon.save(ROOT / "docs/screenshots/app-icon.png", "PNG", optimize=True)
    print("docs/screenshots/app-icon.png")


def _build_arcane(preview: bool) -> None:
    if preview:
        _arcane_preview(Path("C:/tmp/icon-arcane"))
        return

    densities = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
    written: list[Path] = []
    rows: list[tuple[str, int, Path]] = []
    for name, factor in densities.items():
        px = round(48 * factor)
        for stem, rnd in (("ic_launcher_arcane", False), ("ic_launcher_arcane_round", True)):
            path = RES / f"mipmap-{name}/{stem}.webp"
            save_webp(legacy_icon_arcane(px, round_icon=rnd), path)
            written.append(path)
            rows.append((f"mipmap-{name}/{stem}", px, path))

        layer = round(108 * factor)
        bg, fg, mono = adaptive_layers_arcane(108, layer)
        for suffix, img in (("background", bg), ("foreground", fg), ("monochrome", mono)):
            path = RES / f"drawable-{name}/ic_launcher_arcane_{suffix}.webp"
            save_layer_arcane(img, path)
            written.append(path)
            rows.append((f"drawable-{name}/ic_launcher_arcane_{suffix}", layer, path))
        print(f"{name}: icon {px}px, adaptive layer {layer}px")

    for stem in ("ic_launcher_arcane", "ic_launcher_arcane_round"):
        written.append(write_adaptive_xml(stem))

    icon_path = ROOT / "docs/screenshots/app-icon-arcane.png"
    legacy_icon_arcane(512, round_icon=False).save(icon_path, "PNG", optimize=True)
    written.append(icon_path)
    states_path = ROOT / "docs/screenshots/app-icon-arcane-states.png"
    arcane_states_sheet(states_path)
    written.append(states_path)

    total = sum(p.stat().st_size for p in written)
    width = max(len(name) for name, _, _ in rows)
    for name, px, path in rows:
        print(f"  {name:<{width}}  {px:>3}px  {path.stat().st_size / 1024:6.2f} KB")
    print(f"arcane total: {total / 1024:.2f} KB over {len(written)} files")
    print("docs/screenshots/app-icon-arcane.png / app-icon-arcane-states.png")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--skin", choices=("lumo", "arcane"), default="lumo",
                    help="图标皮肤：lumo（默认，宣纸朱砂印）/ arcane（弧光学院，午夜蓝金芒星）")
    ap.add_argument("--preview", action="store_true", help="只写预览图到 C:/tmp")
    args = ap.parse_args()

    if args.skin == "arcane":
        _build_arcane(args.preview)
    else:
        _build_lumo(args.preview)


if __name__ == "__main__":
    main()

