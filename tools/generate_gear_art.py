#!/usr/bin/env python3
"""Draws the field kit's gear as it is worn (2.0, items.7): one 64x64 texture
holding every material the worn gear is built from, in the same colours as the
item sprites (tools/generate_fieldkit_art.py), so a bandage on an arm and the
bandage in the hotbar are the same cloth.

Each material is a rectangle big enough for a cube's box UV: the gear cubes in
GearMeshes.java point their texture offsets at these rectangles, so the
numbers below and the REGION table there must agree.

    python3 tools/generate_gear_art.py            # preview only
    python3 tools/generate_gear_art.py --install  # write the texture too

Nothing goes into the mod without a yes on the in-game preview first.
Requires Pillow.
"""
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/realisticdamage/textures/entity/field_kit_gear.png"
PREVIEW = ROOT / "build/preview/field_kit_gear.png"

# name: (u, v, width, height). Mirrored in GearMeshes.REGION.
REGIONS = {
    "gauze": (0, 0, 32, 12),
    "gauze_soaked": (32, 0, 32, 12),
    "leather": (0, 12, 32, 12),
    "cloth": (32, 12, 32, 12),
    "wood": (0, 24, 8, 16),
    "string": (8, 24, 24, 8),
    "metal": (32, 24, 16, 8),
    "tube": (48, 24, 16, 8),
    "copper": (8, 32, 8, 8),
    "goggle_strap": (0, 40, 32, 12),
    "lens": (32, 40, 12, 6),
}

GAUZE = [(242, 238, 228), (226, 220, 206), (210, 203, 189)]
LEATHER = [(124, 76, 42), (104, 64, 34), (90, 54, 30)]
CLOTH = [(236, 234, 230), (220, 217, 212), (204, 200, 196)]
WOOD = [(176, 132, 78), (146, 104, 58), (108, 74, 40)]
STRING = [(244, 244, 240), (220, 220, 214), (190, 190, 186)]
METAL = [(208, 208, 214), (176, 176, 182), (140, 140, 148)]
TUBE = [(76, 76, 84), (64, 64, 72), (52, 52, 58)]
COPPER = [(226, 124, 82), (196, 104, 66), (176, 86, 54)]
STRAP = [(116, 72, 40), (98, 60, 32), (84, 50, 28)]


def fill(img, region, shades, rng, pattern=None):
    u, v, w, h = region
    for y in range(h):
        for x in range(w):
            shade = shades[0]
            roll = rng.random()
            if roll < 0.18:
                shade = shades[1]
            elif roll < 0.24:
                shade = shades[2]
            if pattern:
                shade = pattern(x, y, shade, shades) or shade
            img.putpixel((u + x, v + y), shade + (255,))


def gauze_weave(x, y, shade, shades):
    # A wrap's edges: every third row a fold line, darker.
    if y % 3 == 2:
        return shades[1]
    return None


def soaked(img, region, rng):
    u, v, w, h = region
    for y in range(h):
        for x in range(w):
            r, g, b, a = img.getpixel((u + x, v + y))
            # A stain heaviest in the middle of the wrap, browner at the rim.
            cx = abs((x % 16) - 8) / 8.0
            cy = abs(y - h / 2.0) / (h / 2.0)
            soak = max(0.0, 1.0 - (cx * 0.7 + cy * 0.9)) + rng.uniform(-0.15, 0.15)
            soak = max(0.0, min(1.0, soak))
            red = (150, 22, 26)
            img.putpixel((u + x, v + y), (int(r + (red[0] - r) * soak), int(g + (red[1] - g) * soak),
                                          int(b + (red[2] - b) * soak), 255))


def wood_grain(x, y, shade, shades):
    if x % 4 == 3:
        return shades[2]
    if (y + x) % 5 == 0:
        return shades[1]
    return None


def string_twist(x, y, shade, shades):
    return shades[1] if (x + y) % 3 == 0 else None


def leather_stitch(x, y, shade, shades):
    # Stitches along both edges of a strap.
    if y in (0,) and x % 2 == 0:
        return (176, 140, 96)
    return None


def lens(img, region):
    u, v, w, h = region
    rim = (60, 44, 30, 255)
    glass = (96, 74, 124, 170)
    deep = (58, 44, 76, 190)
    shine = (176, 156, 204, 210)
    for y in range(h):
        for x in range(w):
            img.putpixel((u + x, v + y), rim)
    # The lens cube is 3.2 x 3.2 x 1.2: its front face is u+1..u+4, v+1..v+4
    # (box UV, depth 1). Glass inside a one-pixel rim, a highlight up-left.
    for y in range(1, 5):
        for x in range(1, 5):
            inner = 1 < x < 4 and 1 < y < 4
            if inner:
                img.putpixel((u + x, v + y), glass if (x + y) % 3 else deep)
    img.putpixel((u + 2, v + 2), shine)


def build():
    rng = random.Random(1207)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    fill(img, REGIONS["gauze"], GAUZE, rng, gauze_weave)
    fill(img, REGIONS["gauze_soaked"], GAUZE, rng, gauze_weave)
    soaked(img, REGIONS["gauze_soaked"], rng)
    fill(img, REGIONS["leather"], LEATHER, rng, leather_stitch)
    fill(img, REGIONS["cloth"], CLOTH, rng, gauze_weave)
    fill(img, REGIONS["wood"], WOOD, rng, wood_grain)
    fill(img, REGIONS["string"], STRING, rng, string_twist)
    fill(img, REGIONS["metal"], METAL, rng)
    fill(img, REGIONS["tube"], TUBE, rng)
    fill(img, REGIONS["copper"], COPPER, rng)
    fill(img, REGIONS["goggle_strap"], STRAP, rng, leather_stitch)
    lens(img, REGIONS["lens"])
    return img


def main():
    img = build()
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    img.resize((512, 512), Image.NEAREST).save(PREVIEW)
    print("preview:", PREVIEW)
    if "--install" in sys.argv:
        OUT.parent.mkdir(parents=True, exist_ok=True)
        img.save(OUT)
        print("installed:", OUT)


if __name__ == "__main__":
    main()
