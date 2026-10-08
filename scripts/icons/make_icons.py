#!/usr/bin/env python3
"""Generates every Audimmory icon from icon.svg's ingredients.

The mark is Grimmory's book logo (grimmory-tools/grimmory, assets/logo.svg,
AGPL-3.0) wearing headphones. The book geometry is copied verbatim into
grimmory-book.svg; the headphones and the layouts are drawn here.

Gaps between the headphones and the book are real transparent cut-outs (an SVG
mask), not dark strokes; the same mask trims the book's top corners to the
headband's curve. Cut-outs rather than strokes mean the single-colour variants
(themed launcher icon, notification icon) keep the separation after Android
tints them.

Usage: scripts/icons/make_icons.py   (needs rsvg-convert and ImageMagick)
Writes into app/src/{main,debug}/res and fastlane/metadata/android/en-US/images.
"""

import os
import re
import subprocess
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
MAIN = os.path.join(ROOT, "app/src/main/res")
DEBUG = os.path.join(ROOT, "app/src/debug/res")
FASTLANE = os.path.join(ROOT, "fastlane/metadata/android/en-US/images")

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

# Book units: the Grimmory book is 814 x 1024.
BAND = "M-40 520 V430 A447 447 0 0 1 854 430 V520"
CUPS = [(-115, 435), (799, 435)]  # inner cup origins; cups are 130 x 270
CONTENT = (-150, -55, 964, 1024)  # x0, y0, x1, y1 of book + headphones


def book_paths(fill=None):
    svg = open(os.path.join(HERE, "grimmory-book.svg")).read()
    body = svg[svg.index(">") + 1 : svg.rindex("</svg>")]
    if fill:
        body = re.sub(r'fill="[^"]*"', f'fill="{fill}"', body)
        body = re.sub(r"<defs>.*?</defs>", "", body, flags=re.S)
    return body


def mark(mono, gap):
    """Book + headphones in book units. gap = cut-out width around the headphones."""
    band_w = 78
    cut = band_w + 2 * gap
    cups_cut = "".join(
        f'<rect x="{x - gap}" y="{y - gap}" width="{130 + 2 * gap}" height="{270 + 2 * gap}" '
        f'rx="{45 + gap}" fill="black"/>'
        for x, y in CUPS
    )
    band_fill = "#FFFFFF" if mono else "url(#band)"
    cup_fill = "#FFFFFF" if mono else "url(#cup)"
    cups = "".join(
        f'<rect x="{x}" y="{y}" width="130" height="270" rx="45" fill="{cup_fill}"/>' for x, y in CUPS
    )
    return f"""
  <mask id="cut" maskUnits="userSpaceOnUse" x="-400" y="-400" width="1700" height="1800">
    <rect x="-400" y="-400" width="1700" height="1800" fill="white"/>
    <path d="{BAND}" fill="none" stroke="black" stroke-width="{cut}" stroke-linecap="round"/>
    <path d="M-400 -400 H1300 V430 H854 A447 447 0 0 0 -40 430 H-400 Z" fill="black"/>
    {cups_cut}
  </mask>
  <g mask="url(#cut)">{book_paths("#FFFFFF" if mono else None)}</g>
  <path d="{BAND}" fill="none" stroke="{band_fill}" stroke-width="{band_w}" stroke-linecap="round"/>
  {cups}"""


DEFS = """<defs>
  <linearGradient id="bg" x1="0" y1="0" x2="1024" y2="1024" gradientUnits="userSpaceOnUse">
    <stop stop-color="#2A1B54"/><stop offset="1" stop-color="#15121F"/>
  </linearGradient>
  <linearGradient id="band" x1="0" y1="-20" x2="0" y2="520" gradientUnits="userSpaceOnUse">
    <stop stop-color="#F5F3FF"/><stop offset="1" stop-color="#C4B5FD"/>
  </linearGradient>
  <linearGradient id="cup" x1="0" y1="435" x2="0" y2="705" gradientUnits="userSpaceOnUse">
    <stop stop-color="#F5F3FF"/><stop offset="1" stop-color="#A78BFA"/>
  </linearGradient>
</defs>"""


def icon_svg(fraction, background=None, mono=False, gap=36, rotate=False):
    """A 1024 x 1024 icon whose content is `fraction` of the canvas wide.

    background: None (transparent), "square", "rounded" or "circle".
    """
    x0, y0, x1, y1 = CONTENT
    scale = fraction * 1024 / max(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    tx, ty = 512 - cx * scale, 512 - cy * scale
    bg = {
        None: "",
        "square": '<rect width="1024" height="1024" fill="url(#bg)"/>',
        "rounded": '<rect x="16" y="16" width="992" height="992" rx="224" fill="url(#bg)"/>',
        "circle": '<circle cx="512" cy="512" r="496" fill="url(#bg)"/>',
    }[background]
    turn = ' transform="rotate(180 512 512)"' if rotate else ""
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
{DEFS}{bg}
<g{turn}><g transform="translate({tx:.2f} {ty:.2f}) scale({scale:.5f})">{mark(mono, gap)}</g></g>
</svg>"""


def render(svg, out, size):
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as f:
        f.write(svg)
    subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), f.name, "-o", out], check=True)
    os.unlink(f.name)


def per_density(svg, res, kind, name, dp):
    for density, factor in DENSITIES.items():
        render(svg, os.path.join(res, f"{kind}-{density}", name), round(dp * factor))


def launcher(res, rotate):
    # Adaptive icon: full-bleed artwork used as the background layer; the
    # content stays inside the 66dp safe zone of the 108dp canvas.
    per_density(icon_svg(0.56, "square", rotate=rotate), res, "mipmap", "ic_launcher_foreground.png", 108)
    per_density(icon_svg(0.56, None, mono=True, rotate=rotate), res, "drawable", "ic_launcher_monochrome.png", 108)
    # Legacy (pre-API 26) icons carry their own shape.
    per_density(icon_svg(0.72, "rounded", rotate=rotate), res, "mipmap", "ic_launcher.png", 48)
    per_density(icon_svg(0.68, "circle", rotate=rotate), res, "mipmap", "ic_launcher_round.png", 48)


def feature_graphic_svg():
    """1024 x 500 store banner: mark, JetBrains Mono wordmark and tagline.

    The wordmark needs JetBrains Mono installed for fontconfig (copy
    app/src/main/res/font/*.ttf to ~/.fonts).
    """
    x0, y0, x1, y1 = CONTENT
    scale = 300 / max(x1 - x0, y1 - y0)
    tx, ty = 70 - x0 * scale, 250 - (y0 + y1) / 2 * scale
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
{DEFS}
<rect width="1024" height="500" fill="url(#bg)"/>
<g transform="translate({tx:.2f} {ty:.2f}) scale({scale:.5f})">{mark(False, 36)}</g>
<text x="440" y="215" font-family="JetBrains Mono" font-weight="bold" font-size="76" fill="#FFFFFF">Audimmory</text>
<text x="442" y="268" font-family="JetBrains Mono" font-size="25" letter-spacing="3" fill="#FDBA74">AUDIOBOOKS FOR GRIMMORY</text>
<text x="442" y="318" font-family="sans-serif" font-size="26" fill="#C4B5FD">Offline-first. MP3 and M4B. Self-hosted.</text>
</svg>"""


def main():
    launcher(MAIN, rotate=False)
    launcher(DEBUG, rotate=True)
    # In-app brand icon (app bars, login), 40dp.
    per_density(icon_svg(0.72, "rounded"), MAIN, "drawable", "ic_brand.png", 40)
    # Status-bar / media notification icon: white silhouette on transparent,
    # 24dp with wider gaps so they survive the tiny size.
    per_density(icon_svg(0.92, None, mono=True, gap=60), MAIN, "drawable", "ic_stat_audimmory.png", 24)
    # Store listing.
    render(icon_svg(0.72, "rounded"), os.path.join(FASTLANE, "icon.png"), 512)
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as f:
        f.write(feature_graphic_svg())
    out = os.path.join(FASTLANE, "featureGraphic.png")
    subprocess.run(["rsvg-convert", f.name, "-o", out], check=True)
    # The store wants an opaque banner.
    subprocess.run(["convert", out, "-background", "#15121F", "-alpha", "remove", "-alpha", "off", out], check=True)
    os.unlink(f.name)
    with open(os.path.join(HERE, "icon.svg"), "w") as f:
        f.write(icon_svg(0.72, "rounded"))


if __name__ == "__main__":
    main()
