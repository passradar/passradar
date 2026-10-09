#!/usr/bin/env python3
"""Draws the PNG launcher icons for Android 5.0–7.1 (newer versions use the
adaptive vector icon in res/drawable) and the 512 px store icon.

The shapes match res/drawable/ic_launcher_foreground.xml: coordinates are on
its 108×108 canvas, of which the middle 72×72 is what launchers show.

Usage:  python tools/make_icons.py   (needs Pillow: pip install Pillow)
"""
import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
STORE_ICON = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "icon.png"

BRAND = (0x00, 0x6A, 0x60, 255)
WHITE = (255, 255, 255, 255)
AMBER = (0xFF, 0xC1, 0x07, 255)
DARK = (0x00, 0x33, 0x2E, 255)
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SUPERSAMPLE = 8


def render(size, round_shape):
    full = size * SUPERSAMPLE
    image = Image.new("RGBA", (full, full), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    pad = full / 24  # 2 dp of a 48 dp icon
    inner = full - 2 * pad
    if round_shape:
        draw.ellipse([pad, pad, full - pad, full - pad], fill=BRAND)
    else:
        draw.rounded_rectangle([pad, pad, full - pad, full - pad], radius=inner * 0.2, fill=BRAND)

    scale = inner / 72.0

    def p(x, y):
        return pad + (x - 18) * scale, pad + (y - 18) * scale

    def circle(cx, cy, r, **kwargs):
        x, y = p(cx, cy)
        draw.ellipse([x - r * scale, y - r * scale, x + r * scale, y + r * scale], **kwargs)

    # Passport with globe emblem and two text lines
    draw.rounded_rectangle([p(34, 30), p(68, 78)], radius=4 * scale, fill=WHITE)
    circle(51, 47, 9.25, fill=BRAND)
    circle(51, 47, 6.75, fill=WHITE)
    draw.ellipse([p(46.25, 37.75), p(55.75, 56.25)], fill=BRAND)  # meridian
    draw.ellipse([p(48.75, 40.25), p(53.25, 53.75)], fill=WHITE)
    draw.rectangle([p(43, 45.75), p(59, 48.25)], fill=BRAND)
    draw.rectangle([p(41, 62), p(61, 64.5)], fill=BRAND)
    draw.rectangle([p(44, 68), p(58, 70.5)], fill=BRAND)

    # Bell badge with a ring in the background colour
    circle(70, 36, 14.25, fill=BRAND)
    circle(70, 36, 11.75, fill=AMBER)
    draw.ellipse([p(63.5, 27.5), p(76.5, 40.5)], fill=DARK)
    draw.rectangle([p(63.5, 34), p(76.5, 41)], fill=DARK)
    draw.polygon([p(63.5, 41), p(76.5, 41), p(78.5, 43), p(61.5, 43)], fill=DARK)
    circle(70, 45.5, 2.2, fill=DARK)
    circle(70, 26.8, 1.6, fill=DARK)

    return image.resize((size, size), Image.LANCZOS)


def main():
    for density, size in SIZES.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        render(size, round_shape=False).save(folder / "ic_launcher.png", optimize=True)
        render(size, round_shape=True).save(folder / "ic_launcher_round.png", optimize=True)
    STORE_ICON.parent.mkdir(parents=True, exist_ok=True)
    render(512, round_shape=False).save(STORE_ICON, optimize=True)
    print("Icons written")


if __name__ == "__main__":
    main()
