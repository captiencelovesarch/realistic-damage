#!/usr/bin/env python3
"""Draws the field kit's art (2.0): the brewed effects' status icons, so far.

Painted on an 18x18 grid, the size of a vanilla mob_effect sprite, in the same
manner as tools/generate_icon.py: a palette and a hand-drawn grid per sprite,
scaled up by whole pixels only when shown. A couple of strokes that are easier
to place by rule than by hand (the heartbeat across the Adrenaline heart, the
arms of the Numbing frost) are drawn by code onto the grid before the outline
pass.

Nothing goes into the mod without a yes on the preview first:

    python3 tools/generate_fieldkit_art.py            # preview only
    python3 tools/generate_fieldkit_art.py --install  # write the textures too

The preview sheet is build/preview/fieldkit_effects.png: each icon at 8x beside
vanilla's own effect icons, on the inventory's effect panel, at 1x/2x/3x as the
HUD draws it, and the potion, splash, lingering and tipped-arrow colours each
effect will tint. Requires Pillow.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src/main/resources/assets/realisticdamage/textures/mob_effect"
PREVIEW = ROOT / "build/preview"

# The potion colour of each effect: what vanilla tints the bottle, the splash
# cloud and the arrow tip with. Kept here beside the icon so the two are
# judged together; FieldKitEffects.java carries the same numbers.
COLOURS = {
    "haemorrhage": 0x8A0F14,
    "sedation": 0x5B4F9E,
    "adrenaline": 0xF2711C,
    "numbing": 0x9BDDF0,
}

CLEAR = (0, 0, 0, 0)

# --- Haemorrhage (Weapons): a fat drop running over, and two more falling ---
HAEMORRHAGE_PALETTE = {
    ".": CLEAR,
    "K": (52, 4, 8, 255),
    "D": (124, 12, 18, 255),
    "R": (196, 24, 32, 255),
    "H": (236, 82, 70, 255),
    "W": (255, 186, 166, 255),
}
HAEMORRHAGE = [
    "..................",
    "........KK........",
    ".......KRDK.......",
    ".......KRDK.......",
    "......KHRRDK......",
    "......KHRRDK......",
    ".....KHRRRRDK.....",
    ".....KWHRRRDK.....",
    "....KHWRRRRRDK....",
    "....KHHRRRRRDK....",
    "....KHRRRRRRDK..K.",
    "....KRRRRRRRDK.KRK",
    "....KRRRRRRDDK.KDK",
    ".....KRRRRDDK...K.",
    "......KDDDDK......",
    "..K....KKKK.......",
    ".KRK..............",
    ".KDK..............",
]

# --- Sedation (Weapons): a heavy lid come down, and the Zs ---
SEDATION_PALETTE = {
    ".": CLEAR,
    "K": (31, 26, 51, 255),
    "P": (196, 186, 238, 255),
    "L": (146, 133, 205, 255),
    "M": (100, 88, 162, 255),
    "W": (240, 236, 250, 255),
    "E": (112, 98, 196, 255),
    "I": (31, 26, 51, 255),
    "Z": (236, 232, 255, 255),
    "S": (60, 52, 104, 255),
}
SEDATION = [
    "..................",
    "...........ZZZZ...",
    ".............Z....",
    "......ZZZ...Z.....",
    ".......Z...ZZZZ...",
    "......ZZZ.........",
    "......KKKKKK......",
    "....KKPPPPPPKK....",
    "...KPPLLLLLLPPK...",
    "..KPLLLLLLLLLLPK..",
    ".KMMMMMMMMMMMMMMK.",
    "..KWWEEIIIIEEWWK..",
    "...KWWEEEEEEWWK...",
    "....KKWWWWWWKK....",
    "......KKKKKK......",
    "..................",
    "..................",
    "..................",
]

# --- Adrenaline (Weapons): a heart with the beat spiking through it ---
ADRENALINE_PALETTE = {
    ".": CLEAR,
    "K": (58, 16, 0, 255),
    "D": (150, 42, 4, 255),
    "R": (222, 88, 28, 255),
    "H": (248, 146, 64, 255),
    "W": (255, 220, 160, 255),
    "Y": (255, 252, 214, 255),
}
ADRENALINE = [
    "..................",
    "..................",
    "...KKKK....KKKK...",
    "..KHHRRK..KRRRRK..",
    ".KHWHRRRKKRRRRRDK.",
    ".KHHRRRRRRRRRRRDK.",
    ".KHRRRRRRRRRRRRDK.",
    ".KRRRRRRRRRRRRRDK.",
    ".KRRRRRRRRRRRRDDK.",
    "..KRRRRRRRRRRDDK..",
    "...KRRRRRRRRDDK...",
    "....KRRRRRRDDK....",
    ".....KRRRRDDK.....",
    "......KRRDDK......",
    ".......KDDK.......",
    "........KK........",
    "..................",
    "..................",
]
# The trace: flat, a sharp spike up, a deeper one down, flat again.
ADRENALINE_TRACE = [(0, 9), (4, 9), (6, 4), (9, 14), (11, 9), (17, 9)]

# --- Numbing (Medicine): frost, the cold that takes the feeling away ---
NUMBING_PALETTE = {
    ".": CLEAR,
    "K": (16, 44, 62, 255),
    "B": (60, 140, 186, 255),
    "C": (150, 220, 246, 255),
    "W": (255, 255, 255, 255),
}


def numbing_grid():
    """An eight-armed frost crystal, drawn by rule: arms, then the V near each tip."""
    pixels = {}
    cx = cy = 8
    for k in range(-7, 8):
        pixels[(cx + k, cy)] = "C"
        pixels[(cx, cy + k)] = "C"
    for k in range(-5, 6):
        pixels[(cx + k, cy + k)] = "C"
        pixels[(cx + k, cy - k)] = "C"
    # The V on each straight arm, two pixels in from the tip.
    for sx, sy in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        tip = 5
        bx, by = cx + sx * tip, cy + sy * tip
        if sx == 0:
            pixels[(bx - 1, by + sy)] = "C"
            pixels[(bx + 1, by + sy)] = "C"
        else:
            pixels[(bx + sx, by - 1)] = "C"
            pixels[(bx + sx, by + 1)] = "C"
    # Shade by distance: white hub, light arms, deeper tips.
    for (x, y) in list(pixels):
        r = max(abs(x - cx), abs(y - cy))
        pixels[(x, y)] = "W" if r <= 1 else ("C" if r <= 4 else "B")
    grid = [["."] * 18 for _ in range(18)]
    for (x, y), c in pixels.items():
        if 0 <= x < 18 and 0 <= y < 18:
            grid[y][x] = c
    return outline(grid, "K")


def outline(grid, ink):
    """A one-pixel outline around every filled pixel, four-connected, like vanilla's."""
    out = [row[:] for row in grid]
    for y in range(18):
        for x in range(18):
            if grid[y][x] != ".":
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 18 and 0 <= ny < 18 and grid[ny][nx] not in (".", ink):
                    out[y][x] = ink
                    break
    return out


def line(x0, y0, x1, y1):
    points = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        points.append((x0, y0))
        if x0 == x1 and y0 == y1:
            return points
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def sedation_grid():
    """The Zs get a shadow one pixel down and right, where it falls on nothing."""
    grid = [list(row) for row in SEDATION]
    for y in range(18):
        for x in range(18):
            if SEDATION[y][x] == "Z":
                nx, ny = x + 1, y + 1
                if nx < 18 and ny < 18 and grid[ny][nx] == ".":
                    grid[ny][nx] = "S"
    return grid


def adrenaline_grid():
    grid = [list(row) for row in ADRENALINE]
    trace = []
    for (a, b) in zip(ADRENALINE_TRACE, ADRENALINE_TRACE[1:]):
        trace += line(*a, *b)
    for (x, y) in trace:
        grid[y][x] = "Y"
    # Off the heart the trace needs ink either side of it, or it vanishes on a
    # pale panel; on the heart the red is the ink.
    for (x, y) in trace:
        if ADRENALINE[y][x] in ".":
            for dy in (-1, 1):
                if 0 <= y + dy < 18 and grid[y + dy][x] == ".":
                    grid[y + dy][x] = "K"
    return grid


def sprite(grid, palette):
    rows = ["".join(r) if not isinstance(r, str) else r for r in grid]
    assert len(rows) == 18 and all(len(r) == 18 for r in rows), [len(r) for r in rows]
    im = Image.new("RGBA", (18, 18))
    im.putdata([palette[c] for row in rows for c in row])
    return im


def icons():
    return {
        "haemorrhage": sprite(HAEMORRHAGE, HAEMORRHAGE_PALETTE),
        "sedation": sprite(sedation_grid(), SEDATION_PALETTE),
        "adrenaline": sprite(adrenaline_grid(), ADRENALINE_PALETTE),
        "numbing": sprite(numbing_grid(), NUMBING_PALETTE),
    }


def tint(image, rgb):
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    px = image.convert("RGBA").load()
    out = Image.new("RGBA", image.size)
    po = out.load()
    for y in range(image.size[1]):
        for x in range(image.size[0]):
            pr, pg, pb, pa = px[x, y]
            po[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
    return out


def vanilla(path):
    """Vanilla art, read from the loom cache's client jar, for comparison only."""
    import glob
    import io
    import zipfile
    jars = sorted(glob.glob(str(ROOT / ".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-*/26.2/*-26.2.jar")))
    jars += sorted(glob.glob(str(Path.home() / "Projects/realistic-damage/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-*/26.2/*-26.2.jar")))
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            if path in z.namelist():
                return Image.open(io.BytesIO(z.read(path))).convert("RGBA")
    return None


def label(draw, xy, text, fill=(32, 32, 32, 255)):
    draw.text(xy, text, fill=fill)


def preview(sprites):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    names = list(sprites)
    scale = 8
    cell = 18 * scale
    pad = 24
    W = pad + 4 * (cell + pad) + 260
    H = 1100
    sheet = Image.new("RGBA", (W, H), (198, 198, 198, 255))
    d = ImageDraw.Draw(sheet)
    label(d, (pad, 6), "Realistic Damage 2.0, field kit: the four brewed effects (8x, then as the game draws them)")

    # Row 1: ours at 8x with names and their switch.
    switch = {"haemorrhage": "Weapons", "sedation": "Weapons", "adrenaline": "Weapons", "numbing": "Medicine"}
    for i, n in enumerate(names):
        x = pad + i * (cell + pad)
        sheet.alpha_composite(sprites[n].resize((cell, cell), Image.NEAREST), (x, 30))
        label(d, (x, 30 + cell + 4), f"{n.capitalize()}  ({switch[n]})")

    # Row 2: vanilla neighbours at 8x for style.
    y2 = 30 + cell + 30
    label(d, (pad, y2 - 2), "vanilla, for comparison: poison, regeneration, instant damage, nausea, slowness, weakness")
    for i, n in enumerate(["poison", "regeneration", "instant_damage", "nausea", "slowness", "weakness"]):
        im = vanilla(f"assets/minecraft/textures/mob_effect/{n}.png")
        if im is not None:
            sheet.alpha_composite(im.resize((cell * 2 // 3, cell * 2 // 3), Image.NEAREST),
                                  (pad + i * (cell * 2 // 3 + 16), y2 + 14))

    # Row 3: the inventory effect panel at 2x and 3x, ours mixed in with vanilla's.
    y3 = y2 + 14 + cell * 2 // 3 + 30
    label(d, (pad, y3 - 2), "in the inventory's effect list and on the HUD, at GUI scale 2 and 3")
    bg = vanilla("assets/minecraft/textures/gui/sprites/container/inventory/effect_background.png")
    hud_bg = vanilla("assets/minecraft/textures/gui/sprites/hud/effect_background.png")
    row = [sprites[n] for n in names] + [vanilla(f"assets/minecraft/textures/mob_effect/{n}.png")
                                         for n in ("regeneration", "poison")]
    for gi, gs in enumerate((2, 3)):
        yy = y3 + 14 + gi * (34 * gs + 16)
        for i, ic in enumerate(row):
            if ic is None:
                continue
            x = pad + i * (32 * gs + 6)
            if bg is not None:
                panel = bg.resize((bg.size[0] * gs, bg.size[1] * gs), Image.NEAREST)
                sheet.alpha_composite(panel.crop((0, 0, 32 * gs, 32 * gs)), (x, yy))
            sheet.alpha_composite(ic.resize((18 * gs, 18 * gs), Image.NEAREST), (x + 7 * gs, yy + 7 * gs))
    yh = y3 + 14 + 2 * (34 * 3 + 16)
    for gi, gs in enumerate((2, 3)):
        for i, ic in enumerate(row):
            if ic is None:
                continue
            x = pad + 520 + gi * 330 + i * (24 * gs + 2) // 2 * (2 if gs == 2 else 1)
            x = pad + i * (24 * gs + 4) + gi * 430
            if hud_bg is not None:
                sheet.alpha_composite(hud_bg.resize((24 * gs, 24 * gs), Image.NEAREST), (x, yh))
            sheet.alpha_composite(ic.resize((18 * gs, 18 * gs), Image.NEAREST), (x + 3 * gs, yh + 3 * gs))

    # Row 4: the bottles and the arrow, tinted with each colour, at 6x.
    y4 = yh + 24 * 3 + 30
    label(d, (pad, y4 - 2), "the potion colours: drinkable, splash, lingering bottle and tipped arrow (6x)")
    bottle = vanilla("assets/minecraft/textures/item/potion.png")
    overlay = vanilla("assets/minecraft/textures/item/potion_overlay.png")
    splash = vanilla("assets/minecraft/textures/item/splash_potion.png")
    lingering = vanilla("assets/minecraft/textures/item/lingering_potion.png")
    arrow_base = vanilla("assets/minecraft/textures/item/tipped_arrow_base.png")
    arrow_head = vanilla("assets/minecraft/textures/item/tipped_arrow_head.png")
    s = 6
    for i, n in enumerate(names):
        x0 = pad + i * (4 * 16 * s // 2 + 40)
        label(d, (x0, y4 + 12), n.capitalize())
        items = []
        for body in (bottle, splash, lingering):
            if body is not None and overlay is not None:
                im = Image.new("RGBA", (16, 16))
                im.alpha_composite(tint(overlay, COLOURS[n]))
                im.alpha_composite(body)
                items.append(im)
        if arrow_base is not None and arrow_head is not None:
            im = Image.new("RGBA", (16, 16))
            im.alpha_composite(arrow_base)
            im.alpha_composite(tint(arrow_head, COLOURS[n]))
            items.append(im)
        for j, im in enumerate(items):
            col, rw = j % 2, j // 2
            sheet.alpha_composite(im.resize((16 * s // 2 * 2 // 2 * 1, 16 * s // 2), Image.NEAREST)
                                  if False else im.resize((16 * s // 2, 16 * s // 2), Image.NEAREST),
                                  (x0 + col * (16 * s // 2 + 8), y4 + 30 + rw * (16 * s // 2 + 8)))
    sheet = sheet.crop((0, 0, W, y4 + 30 + 2 * (16 * s // 2 + 8) + 10))
    out = PREVIEW / "fieldkit_effects.png"
    sheet.save(out)
    return out


def main():
    sprites = icons()
    out = preview(sprites)
    print("preview:", out)
    if "--install" in sys.argv:
        TEXTURES.mkdir(parents=True, exist_ok=True)
        for n, im in sprites.items():
            im.save(TEXTURES / f"{n}.png")
            print("wrote", TEXTURES / f"{n}.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())
