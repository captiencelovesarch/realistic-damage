#!/usr/bin/env python3
"""Draws the mod icon: a vanilla-style pixel heart, cracked down the middle and bleeding.

The heart is the one number the mod leaves in charge, and the crack and the drop are
everything it adds underneath, so the icon says what the mod is at the size Mod Menu
draws it. Painted on a 16x16 grid like a vanilla sprite and scaled up by whole pixels,
so it stays crisp at any size a launcher or Modrinth shows.

Requires Pillow. Output: src/main/resources/assets/realisticdamage/icon.png (128x128)
and, with --listing PATH, a 512x512 copy for the Modrinth project page.
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/realisticdamage/icon.png"

PALETTE = {
    ".": (0, 0, 0, 0),
    "K": (28, 6, 8, 255),       # outline
    "R": (200, 30, 36, 255),    # heart
    "H": (255, 112, 112, 255),  # light from the top left
    "W": (255, 236, 236, 255),  # glint
    "D": (122, 12, 18, 255),    # shade on the right
    "C": (28, 6, 8, 255),       # the crack, same ink as the outline
}

GRID = [
    "..KKKK....KKKK..",
    ".KHHRRK..KRRRRK.",
    "KHWHRRRKKRRRRRDK",
    "KHHRRRRRCRRRRRDK",
    "KHRRRRRCRRRRRRDK",
    "KRRRRRRRCCRRRRDK",
    ".KRRRRRRRCRRRDK.",
    "..KRRRRRCRRRDK..",
    "...KRRRRCRRDK...",
    "....KRRRRCDK....",
    ".....KRRCDK..K..",
    "......KRDK..KRK.",
    ".......KK..KRRRK",
    "...........KHRRK",
    "...........KRRDK",
    "............KKK.",
]


def render(size: int) -> Image.Image:
    assert all(len(row) == 16 for row in GRID) and len(GRID) == 16
    sprite = Image.new("RGBA", (16, 16))
    sprite.putdata([PALETTE[c] for row in GRID for c in row])
    return sprite.resize((size, size), Image.NEAREST)


def main() -> int:
    render(128).save(OUT)
    if len(sys.argv) == 3 and sys.argv[1] == "--listing":
        render(512).save(sys.argv[2])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
