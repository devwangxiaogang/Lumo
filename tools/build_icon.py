"""生成「语光 / Lumo」的应用图标。

设计：宣纸底 + 一枚朱砂方印（印白「光」字，界格细边），与 App 内首页左上角的
「語光」方印同构；背景底部有一层极淡的墨色远山，避免纯纸色在浅色桌面上发飘。

输出：
  app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp
  app/src/main/res/mipmap-*/ic_launcher_round.webp
  app/src/main/res/drawable-*/ic_launcher_background.png   (adaptive 108dp 画布)
  app/src/main/res/drawable-*/ic_launcher_foreground.png
  app/src/main/res/drawable-*/ic_launcher_monochrome.png
  docs/screenshots/app-icon.png                            (512, README 用)

用法：python tools/build_icon.py            # 全部尺寸
      python tools/build_icon.py --preview  # 只出预览图到 C:/tmp
"""

from __future__ import annotations

import argparse
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

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


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true", help="只写预览图到 C:/tmp")
    args = ap.parse_args()

    if args.preview:
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


if __name__ == "__main__":
    main()
