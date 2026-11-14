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


# --- Item sprites (16x16), phase 4 round 1: bleeding ---

ITEM_TEXTURES = ROOT / "src/main/resources/assets/realisticdamage/textures/item"

BANDAGE_PALETTE = {
    ".": CLEAR,
    "K": (74, 64, 56, 255),
    "W": (242, 238, 228, 255),
    "L": (210, 203, 189, 255),
    "D": (168, 158, 143, 255),
    "S": (122, 112, 100, 255),
}
BANDAGE = [
    "................",
    "....KKKKKK......",
    "...KWWWWWWK.....",
    "..KWWLLLLWWK....",
    ".KWWLDDDDLWWK...",
    ".KWLDKKKKDLWK...",
    ".KWLDKSSKDLWK...",
    ".KWLDKSSKDLWK...",
    ".KWLDKKKKDLWK...",
    ".KWWLDDDDLWWKK..",
    "..KWWLLLLWWWWWK.",
    "...KWWWWWWLLLLWK",
    "....KKKKKKKDDLWK",
    "...........KDLWK",
    "............KDWK",
    ".............KK.",
]

TOURNIQUET_PALETTE = {
    ".": CLEAR,
    "K": (42, 26, 16, 255),
    "B": (124, 76, 42, 255),
    "b": (164, 108, 62, 255),
    "d": (90, 54, 30, 255),
    "W": (150, 112, 62, 255),
    "w": (196, 156, 96, 255),
    "G": (176, 176, 182, 255),
    "g": (104, 104, 112, 255),
}
TOURNIQUET_STRAP = [
    "................",
    "................",
    "................",
    "....KKKKKKKK....",
    "..KKbbbbbbbbKK..",
    ".KbBKKKKKKKKBbK.",
    "KGGK........KBK.",
    "KGgK........KBK.",
    "KGGK........KBK.",
    ".KdBKKKKKKKKBdK.",
    "..KKddddddddKK..",
    "....KKKKKKKK....",
    "................",
    "................",
    "................",
    "................",
]

NEEDLE_PALETTE = {
    ".": CLEAR,
    "K": (36, 38, 46, 255),
    "S": (204, 208, 218, 255),
    "s": (132, 138, 152, 255),
    "H": (252, 252, 255, 255),
    "R": (196, 44, 44, 255),
    "r": (124, 22, 26, 255),
}

BLOOD_BAG_PALETTE = {
    ".": CLEAR,
    "K": (44, 12, 16, 255),
    "P": (228, 218, 222, 255),
    "p": (186, 172, 180, 255),
    "R": (152, 18, 26, 255),
    "r": (200, 44, 50, 255),
    "D": (98, 8, 14, 255),
    "T": (206, 196, 202, 255),
    "W": (246, 244, 240, 255),
}
BLOOD_BAG = [
    "......KKKK......",
    ".....KP..PK.....",
    "...KKKKKKKKKK...",
    "..KPPPPPPPPPPK..",
    "..KPprrrrrrrpK..",
    "..KPrRRRRRRRpK..",
    "..KPrRWWWWRRpK..",
    "..KPrRWWWWRRpK..",
    "..KPrRRRRRRRpK..",
    "..KPrRRRRRRRpK..",
    "..KPrRRRRRRDpK..",
    "..KPRRRRRRDDpK..",
    "..KppDDDDDDDpK..",
    "...KKKKKKKKKK...",
    "......KTTK......",
    ".......KK.......",
]


# --- phase 4, round 2: bones, head and senses ---

SPLINT_PALETTE = {
    ".": CLEAR,
    "K": (60, 44, 26, 255),
    "W": (146, 104, 58, 255),
    "h": (176, 132, 78, 255),
    "w": (108, 74, 40, 255),
    "S": (244, 244, 240, 255),
    "s": (190, 190, 186, 255),
}

SLING_PALETTE = {
    ".": CLEAR,
    "K": (70, 66, 74, 255),
    "W": (236, 234, 230, 255),
    "L": (204, 200, 196, 255),
    "D": (160, 156, 154, 255),
    "B": (90, 112, 160, 255),
    "b": (64, 82, 124, 255),
}
SLING = [
    "................",
    "......KKKK......",
    ".....KBbbBK.....",
    "....KBK..KBK....",
    "...KbK....KbK...",
    "...KbK....KbK...",
    "..KBK......KBK..",
    "..KbK......KbK..",
    ".KKKKKKKKKKKKKK.",
    ".KWWWWWWWWWWWLK.",
    "..KWLWWWWWWLDK..",
    "...KWLWWWWLDK...",
    "....KWLWWLDK....",
    ".....KWLLDK.....",
    "......KWDK......",
    ".......KK.......",
]

SALTS_PALETTE = {
    ".": CLEAR,
    "K": (40, 30, 24, 255),
    "C": (150, 104, 62, 255),
    "c": (112, 76, 44, 255),
    "G": (142, 92, 46, 255),
    "g": (102, 62, 30, 255),
    "H": (214, 168, 110, 255),
    "W": (244, 242, 236, 255),
    "w": (208, 204, 196, 255),
}
SALTS = [
    "................",
    "................",
    "................",
    "......KKKK......",
    "......KCCK......",
    "......KcCK......",
    ".....KKKKKK.....",
    ".....KgGGgK.....",
    "....KgGGGGgK....",
    "...KgHGGGGGgK...",
    "...KHWWWWWWgK...",
    "...KHWwWWWwgK...",
    "...KgWWWwWWgK...",
    "...KgwWWWWWgK...",
    "....KgggggggK...",
    ".....KKKKKKK....",
]

STETHOSCOPE_PALETTE = {
    ".": CLEAR,
    "K": (30, 30, 34, 255),
    "T": (64, 64, 72, 255),
    "t": (98, 98, 108, 255),
    "C": (226, 124, 82, 255),
    "c": (176, 86, 54, 255),
    "H": (250, 190, 150, 255),
    "M": (200, 202, 210, 255),
}
STETHOSCOPE = [
    "................",
    "..KK.......KK...",
    "..KMK.....KMK...",
    "...KTK...KTK....",
    "...KtK...KtK....",
    "....KTK.KTK.....",
    ".....KTKTK......",
    "......KTK.......",
    "......KtK.......",
    "......KTK.......",
    ".....KTK........",
    ".....KtK..KKKK..",
    ".....KTKKKcCCcK.",
    "......KTTTCHCCK.",
    "..........KcCcK.",
    "...........KKK..",
]

GOGGLES_PALETTE = {
    ".": CLEAR,
    "K": (34, 22, 16, 255),
    "L": (124, 76, 42, 255),
    "l": (90, 54, 30, 255),
    "T": (58, 44, 76, 255),
    "t": (96, 74, 124, 255),
    "H": (176, 156, 204, 255),
}
GOGGLES = [
    "................",
    "................",
    "................",
    "................",
    "...KKKK..KKKK...",
    "..KLLLLKKLLLLK..",
    ".KLHtTTLLHtTTLK.",
    "KlLtTTTLLtTTTLlK",
    "KlLTTTTLLTTTTLlK",
    ".KLTTTlKKTTTlLK.",
    "..KlllK..KlllK..",
    "...KKK....KKK...",
    "................",
    "................",
    "................",
    "................",
]


def splint_grid():
    """A wooden board corner to corner, bound on with two white wraps."""
    grid = blank16()
    board = line(2, 13, 11, 4)
    shades = "wWWh"
    for (x, y) in board:
        for k, c in enumerate(shades):
            grid[y][x + k] = c
    # The wraps: across the board and a pixel past it each side, two rows deep.
    for t in (2, 3, 6, 7):
        x, y = board[t]
        for k in range(0, 4):
            grid[y][x + k] = "s" if t in (3, 7) else "S"
    return outline16(grid, "K")

def blank16():
    return [["."] * 16 for _ in range(16)]


def outline16(grid, ink):
    out = [row[:] for row in grid]
    for y in range(16):
        for x in range(16):
            if grid[y][x] != ".":
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and grid[ny][nx] not in (".", ink):
                    out[y][x] = ink
                    break
    return out


def tourniquet_grid():
    """The strap with its buckle, and the windlass stick through it corner to corner."""
    grid = [list(r) for r in TOURNIQUET_STRAP]
    stick = line(2, 14, 13, 1)
    for (x, y) in stick:
        grid[y][x] = "W"
        if x + 1 < 16:
            grid[y][x + 1] = "w"
    # Ink the stick where it crosses empty space, so it reads over the strap.
    for (x, y) in stick:
        for dx, dy in ((-1, 0), (0, 1), (2, 0), (1, -1), (0, -1), (-1, 1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and grid[ny][nx] == ".":
                grid[ny][nx] = "K"
    return grid


def needle_grid():
    """A steel needle point-down to the left, its eye up to the right, and a red thread looping off it."""
    grid = blank16()
    shaft = line(3, 13, 12, 4)
    for (x, y) in shaft:
        grid[y][x] = "S"
    for (x, y) in shaft[len(shaft) // 2:]:
        if y - 1 >= 0 and grid[y - 1][x] == ".":
            grid[y - 1][x] = "H"
    # The eye: a widened head with a hole.
    for (x, y) in ((12, 3), (13, 3), (13, 2), (12, 2), (14, 2), (13, 1)):
        grid[y][x] = "S"
    grid[2][13] = "."
    grid[3][13] = "s"
    # The thread, from the eye around in a loop and away.
    thread = (line(14, 3, 15, 5) + line(15, 5, 14, 7) + line(14, 7, 15, 9) + line(15, 9, 14, 11)
              + line(14, 11, 15, 13))
    for (x, y) in thread:
        if grid[y][x] == ".":
            grid[y][x] = "R"
    for (x, y) in thread[1::3]:
        if grid[y][x] == "R":
            grid[y][x] = "r"
    # Two pixels in from the right, so the thread doesn't ride the slot's edge.
    grid = [row[2:] + [".", "."] for row in grid]
    return outline16(grid, "K")


def soft_outline(rows, palette, ink="K", shade=0.62):
    """
    Vanilla items don't outline in black: an edge is a darker shade of whatever
    it borders (leather's edge is dark leather, paper's is grey paper). So every
    ink pixel takes the colour of its darkest filled neighbour, darkened.
    """
    def fill(x, y):
        if 0 <= x < 16 and 0 <= y < 16:
            c = rows[y][x]
            if c not in (".", ink):
                return palette[c]
        return None
    out = []
    for y in range(16):
        for x in range(16):
            c = rows[y][x]
            if c != ink:
                out.append(palette[c])
                continue
            around = [fill(x + dx, y + dy) for dx, dy in
                      ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1))]
            around = [a for a in around if a is not None]
            if not around:
                out.append(palette[ink])
                continue
            darkest = min(around, key=lambda a: a[0] * 0.3 + a[1] * 0.59 + a[2] * 0.11)
            out.append((int(darkest[0] * shade), int(darkest[1] * shade), int(darkest[2] * shade), 255))
    return out


def item_sprites():
    def make(grid, palette):
        rows = ["".join(r) if not isinstance(r, str) else r for r in grid]
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
        im = Image.new("RGBA", (16, 16))
        im.putdata(soft_outline(rows, palette))
        return im
    return {
        "bandage": make(BANDAGE, BANDAGE_PALETTE),
        "tourniquet": make(tourniquet_grid(), TOURNIQUET_PALETTE),
        "needle_and_thread": make(needle_grid(), NEEDLE_PALETTE),
        "blood_bag": make(BLOOD_BAG, BLOOD_BAG_PALETTE),
    }


def round2_sprites():
    def make(grid, palette):
        rows = ["".join(r) if not isinstance(r, str) else r for r in grid]
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
        im = Image.new("RGBA", (16, 16))
        im.putdata(soft_outline(rows, palette))
        return im
    return {
        "splint": make(splint_grid(), SPLINT_PALETTE),
        "sling": make(SLING, SLING_PALETTE),
        "smelling_salts": make(SALTS, SALTS_PALETTE),
        "stethoscope": make(STETHOSCOPE, STETHOSCOPE_PALETTE),
        "tinted_goggles": make(GOGGLES, GOGGLES_PALETTE),
    }


def slot(size):
    """A vanilla-looking inventory slot, size x size."""
    im = Image.new("RGBA", (size, size), (139, 139, 139, 255))
    d = ImageDraw.Draw(im)
    d.line([(0, 0), (size - 1, 0)], fill=(55, 55, 55, 255))
    d.line([(0, 0), (0, size - 1)], fill=(55, 55, 55, 255))
    d.line([(0, size - 1), (size - 1, size - 1)], fill=(255, 255, 255, 255))
    d.line([(size - 1, 0), (size - 1, size - 1)], fill=(255, 255, 255, 255))
    return im


def preview_items(sprites, title, out_name, neighbours):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    names = list(sprites)
    big = 16 * 8
    pad = 24
    W = max(pad + len(names) * (big + pad), 760)
    sheet = Image.new("RGBA", (W, 640), (198, 198, 198, 255))
    d = ImageDraw.Draw(sheet)
    label(d, (pad, 6), title)
    for i, n in enumerate(names):
        x = pad + i * (big + pad)
        sheet.alpha_composite(sprites[n].resize((big, big), Image.NEAREST), (x, 28))
        label(d, (x, 28 + big + 4), n.replace("_", " "))
    y2 = 28 + big + 30
    label(d, (pad, y2), "vanilla neighbours, same scale: " + ", ".join(n.split("/")[-1] for n in neighbours))
    for i, path in enumerate(neighbours):
        im = vanilla(f"assets/minecraft/textures/item/{path}.png")
        if im is not None:
            sheet.alpha_composite(im.resize((big * 2 // 3, big * 2 // 3), Image.NEAREST),
                                  (pad + i * (big * 2 // 3 + 16), y2 + 16))
    y3 = y2 + 16 + big * 2 // 3 + 24
    label(d, (pad, y3), "in inventory slots at GUI scale 2 and 3, ours then the neighbours")
    row = [sprites[n] for n in names] + [vanilla(f"assets/minecraft/textures/item/{p}.png") for p in neighbours]
    for gi, gs in enumerate((2, 3)):
        yy = y3 + 16 + gi * (18 * 3 + 12)
        for i, ic in enumerate(row):
            if ic is None:
                continue
            x = pad + i * (18 * gs + 2)
            sheet.alpha_composite(slot(18 * gs), (x, yy))
            sheet.alpha_composite(ic.resize((16 * gs, 16 * gs), Image.NEAREST), (x + gs, yy + gs))
    sheet = sheet.crop((0, 0, W, y3 + 16 + 2 * (18 * 3 + 12) + 8))
    out = PREVIEW / out_name
    sheet.save(out)
    return out



# --- phase 4, round 3: food and drink ---
# Spirits and the iron tonic are brewed, so they are vanilla's potion bottle in
# their own colour, like the phase 3 potions; coffee and the blood sausages are
# items of their own. Each drink's effect gets an icon.

ROUND3_COLOURS = {
    "caffeinated": 0x6B4226,
    "tipsy": 0xD9962B,
    "iron_tonic": 0xA8432F,
}

# Vanilla's bottle (glass and cork as in potion.png / honey_bottle.png), filled with coffee.
COFFEE_PALETTE = {
    ".": CLEAR,
    "A": (212, 109, 73, 255), "B": (169, 71, 37, 255), "D": (204, 175, 165, 255), "F": (151, 55, 22, 255),
    "C": (212, 229, 247, 255), "E": (179, 207, 236, 255), "G": (93, 143, 194, 255), "L": (139, 173, 208, 255),
    "c": (196, 146, 92, 255),   # crema
    "m": (120, 76, 44, 255),
    "M": (88, 52, 28, 255),
    "N": (64, 36, 18, 255),
}
COFFEE = [
    "................",
    "................",
    ".......AAB......",
    "......CDDDE.....",
    "......EBBFE.....",
    ".......E.G......",
    ".......E.G......",
    "......C...G.....",
    ".....CcEcccG....",
    "....CmEMMMMNG...",
    "....CmEMMMNNG...",
    "....EmMMMMLNG...",
    "....GMMMMMLNG...",
    ".....GMNNLNG....",
    "......GGGGG.....",
    "................",
]


def coffee_sprite():
    im = Image.new("RGBA", (16, 16))
    im.putdata([COFFEE_PALETTE[c] for row in COFFEE for c in row])
    return im


SAUSAGE_SPANS = {2: (10, 12), 3: (9, 13), 4: (8, 13), 5: (7, 12), 6: (6, 11), 7: (5, 10),
         8: (4, 9), 9: (3, 8), 10: (2, 7), 11: (2, 6), 12: (3, 5)}

def sausage_grid():
    grid = blank16()
    for y, (l, r) in SAUSAGE_SPANS.items():
        for x in range(l, r + 1):
            i, n = x - l, r - l + 1
            grid[y][x] = "H" if i == 0 or (i == 1 and n >= 5) else ("D" if i >= n - 2 and n >= 4 else ("d" if i == n - 1 else "R"))
    for (x, y) in ((8, 5), (6, 7), (9, 7), (5, 9)):
        grid[y][x] = "F"
    # The ties: each end pinched into string, a tail beyond it.
    for (x, y) in ((11, 2), (12, 2)):
        grid[y][x] = "T"
    grid[2][10] = "."
    grid[2][13] = "t"
    for (x, y) in ((3, 12), (4, 12)):
        grid[y][x] = "T"
    grid[12][5] = "."
    grid[12][2] = "t"
    return outline16(grid, "K")

RAW_SAUSAGE = {".": CLEAR, "K": (60, 12, 26, 255), "H": (164, 58, 82, 255), "R": (120, 26, 48, 255),
       "D": (90, 18, 36, 255), "d": (72, 14, 30, 255), "F": (236, 196, 196, 255),
       "T": (226, 218, 196, 255), "t": (196, 186, 162, 255)}
COOKED_SAUSAGE = {".": CLEAR, "K": (30, 16, 14, 255), "H": (108, 62, 52, 255), "R": (64, 34, 30, 255),
          "D": (46, 24, 22, 255), "d": (36, 18, 16, 255), "F": (222, 206, 176, 255),
          "T": (214, 196, 160, 255), "t": (176, 156, 120, 255)}


def blank18():
    return [["."] * 18 for _ in range(18)]


CAFFEINE_PALETTE = {".": CLEAR, "K": (40, 20, 8, 255), "H": (168, 112, 66, 255), "B": (122, 76, 40, 255),
                    "D": (86, 50, 24, 255), "C": (44, 22, 10, 255), "W": (214, 168, 116, 255),
                    "Y": (255, 236, 170, 255), "y": (214, 170, 80, 255)}

CAFFEINE = [
    "..................",
    "..............y...",
    "..............y...",
    "............yyYyy.",
    ".......KKKKK..y...",
    ".....KKHHHHBK.y...",
    "....KHWWHHHBBK....",
    "...KHWHHHHBBCBK...",
    "..KHHHHHHBBCBBK...",
    "..KHHHHHHBCBBDK...",
    ".KHHHHHBCCBBDK....",
    ".KHHHHCCBBBDDK....",
    ".KHHHCBBBBDDK.....",
    "..KHCBBBBDDK......",
    "...KBBBBDKK.......",
    "....KKKKK.........",
    "..................",
    "..................",
]

def caffeine_grid():
    """A roasted coffee bean, its crease down the middle; and a spark: wide awake."""
    return [list(r) for r in CAFFEINE]

TIPSY_PALETTE = {".": CLEAR, "K": (66, 36, 8, 255), "C": (176, 104, 60, 255), "c": (132, 72, 38, 255),
                 "G": (214, 230, 236, 255), "A": (232, 160, 48, 255), "a": (196, 118, 26, 255),
                 "H": (255, 214, 130, 255),
                 "k": (150, 120, 84, 255), "g": (226, 214, 190, 255), "p": (238, 206, 150, 255)}

# A squat bottle of spirits, corked.
BOTTLE = [
    "..KKK..",
    "..KcK..",
    "..KCK..",
    "..KGK..",
    ".KGGGK.",
    "KGGGGGK",
    "KHAAAaK",
    "KHAAAaK",
    "KHAAAaK",
    "KAAAaaK",
    "KAAAaaK",
    ".KKKKK.",
]
GHOST = {"K": "k", "c": "k", "C": "p", "G": "g", "H": "g", "A": "p", "a": "p"}

def tipsy_grid():
    """The bottle and, a little up and to the right, its double: the room isn't quite steady."""
    grid = blank18()
    def put(ox, oy, ghost):
        for j, row in enumerate(BOTTLE):
            for i, c in enumerate(row):
                if c != ".":
                    grid[oy + j][ox + i] = GHOST[c] if ghost else c
    put(9, 1, True)
    put(4, 5, False)
    return grid

TONIC_PALETTE = {".": CLEAR, "K": (60, 8, 10, 255), "D": (150, 20, 26, 255), "R": (210, 40, 44, 255),
                 "H": (246, 110, 100, 255), "W": (255, 206, 196, 255),
                 "k": (44, 46, 52, 255), "I": (224, 226, 232, 255), "i": (160, 164, 174, 255)}
DROP = [
    "....KK....",
    "...KRDK...",
    "...KRDK...",
    "..KHRRDK..",
    "..KHRRDK..",
    ".KHRRRRDK.",
    ".KWHRRRDK.",
    "KHWRRRRRDK",
    "KHHRRRRRDK",
    "KHRRRRRRDK",
    "KRRRRRRRDK",
    ".KRRRRRDK.",
    "..KDDDDK..",
    "...KKKK...",
]
ARROW = [
    "...k...",
    "..kIk..",
    ".kIIik.",
    "kIIIiik",
    "kkIikkk",
    "..kIk..",
    "..kIk..",
    "..kik..",
    "..kik..",
    "..kkk..",
]

def tonic_grid():
    """A drop of blood, and the iron's arrow up beside it: Blood coming back."""
    grid = blank18()
    for j, row in enumerate(DROP):
        for i, c in enumerate(row):
            if c != ".":
                grid[3 + j][1 + i] = c
    for j, row in enumerate(ARROW):
        for i, c in enumerate(row):
            if c != "." and grid[1 + j][11 + i] == ".":
                grid[1 + j][11 + i] = c
    return grid


def round3_items():
    def make(grid, palette):
        rows = ["".join(r) for r in grid]
        im = Image.new("RGBA", (16, 16))
        im.putdata(soft_outline(rows, palette))
        return im
    return {
        "coffee": coffee_sprite(),
        "raw_blood_sausage": make(sausage_grid(), RAW_SAUSAGE),
        "blood_sausage": make(sausage_grid(), COOKED_SAUSAGE),
    }


def round3_icons():
    return {
        "caffeinated": sprite(caffeine_grid(), CAFFEINE_PALETTE),
        "tipsy": sprite(tipsy_grid(), TIPSY_PALETTE),
        "iron_tonic": sprite(tonic_grid(), TONIC_PALETTE),
    }


def preview_round3(items, effects):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    big = 16 * 8
    pad = 24
    W = 980
    sheet = Image.new("RGBA", (W, 1000), (198, 198, 198, 255))
    d = ImageDraw.Draw(sheet)
    label(d, (pad, 6), "Realistic Damage 2.0, field kit: phase 4 round 3, food and drink (Medicine) - 8x")
    # Row 1: the items, then the effect icons, at 8x.
    x = pad
    for n, im in items.items():
        sheet.alpha_composite(im.resize((big, big), Image.NEAREST), (x, 28))
        label(d, (x, 28 + big + 4), n.replace("_", " "))
        x += big + pad
    x += pad
    for n, im in effects.items():
        sheet.alpha_composite(im.resize((18 * 7, 18 * 7), Image.NEAREST), (x, 28))
        label(d, (x, 28 + big + 4), "effect: " + n.replace("_", " "))
        x += 18 * 7 + pad
    # Row 2: the items in slots beside vanilla's.
    y2 = 28 + big + 34
    neighbours = ["honey_bottle", "potion", "cocoa_beans", "porkchop", "cooked_porkchop", "cooked_beef"]
    label(d, (pad, y2), "in inventory slots at GUI scale 2 and 3, ours then " + ", ".join(neighbours))
    row = list(items.values()) + [vanilla(f"assets/minecraft/textures/item/{p}.png") for p in neighbours]
    for gi, gs in enumerate((2, 3)):
        yy = y2 + 16 + gi * (18 * 2 + 12)
        for i, ic in enumerate(row):
            if ic is None:
                continue
            xx = pad + i * (18 * gs + 2)
            sheet.alpha_composite(slot(18 * gs), (xx, yy))
            sheet.alpha_composite(ic.resize((16 * gs, 16 * gs), Image.NEAREST), (xx + gs, yy + gs))
    # Row 3: the effect icons in the inventory's effect list and on the HUD.
    y3 = y2 + 16 + 18 * 2 + 12 + 18 * 3 + 30
    label(d, (pad, y3), "effects in the inventory's list and on the HUD at GUI scale 2 and 3, beside regeneration, nausea, haste")
    bg = vanilla("assets/minecraft/textures/gui/sprites/container/inventory/effect_background.png")
    hud_bg = vanilla("assets/minecraft/textures/gui/sprites/hud/effect_background.png")
    erow = list(effects.values()) + [vanilla(f"assets/minecraft/textures/mob_effect/{n}.png")
                                     for n in ("regeneration", "nausea", "haste")]
    for gi, gs in enumerate((2, 3)):
        yy = y3 + 16
        for i, ic in enumerate(erow):
            if ic is None:
                continue
            xx = pad + gi * 300 + i * (32 * gs // 2 + 4) if gs == 2 else pad + 300 + i * (32 * gs // 2 + 4)
            xx = pad + i * (32 * gs + 4) if gi == 0 else pad + i * (32 * gs + 4)
            yy = y3 + 16 + gi * (32 * 2 + 10)
            if bg is not None:
                panel = bg.resize((bg.size[0] * gs, bg.size[1] * gs), Image.NEAREST)
                sheet.alpha_composite(panel.crop((0, 0, 32 * gs, 32 * gs)), (xx, yy))
            sheet.alpha_composite(ic.resize((18 * gs, 18 * gs), Image.NEAREST), (xx + 7 * gs, yy + 7 * gs))
    yh = y3 + 16 + 32 * 2 + 10 + 32 * 3 + 16
    for gi, gs in enumerate((2, 3)):
        for i, ic in enumerate(erow):
            if ic is None:
                continue
            xx = pad + i * (24 * gs + 4) + gi * 460
            if hud_bg is not None:
                sheet.alpha_composite(hud_bg.resize((24 * gs, 24 * gs), Image.NEAREST), (xx, yh))
            sheet.alpha_composite(ic.resize((18 * gs, 18 * gs), Image.NEAREST), (xx + 3 * gs, yh + 3 * gs))
    # Row 4: the two brewed drinks' bottles and arrow in their colours.
    y4 = yh + 24 * 3 + 30
    label(d, (pad, y4), "brewed: spirits (tipsy) and iron tonic - drinkable, splash, lingering, tipped arrow (6x)")
    bottle = vanilla("assets/minecraft/textures/item/potion.png")
    overlay = vanilla("assets/minecraft/textures/item/potion_overlay.png")
    splash = vanilla("assets/minecraft/textures/item/splash_potion.png")
    lingering = vanilla("assets/minecraft/textures/item/lingering_potion.png")
    arrow_base = vanilla("assets/minecraft/textures/item/tipped_arrow_base.png")
    arrow_head = vanilla("assets/minecraft/textures/item/tipped_arrow_head.png")
    s = 6
    for k, (n, title) in enumerate((("tipsy", "Spirits"), ("iron_tonic", "Iron Tonic"))):
        x0 = pad + k * 440
        label(d, (x0, y4 + 14), title)
        forms = []
        for body in (bottle, splash, lingering):
            if body is not None and overlay is not None:
                im = Image.new("RGBA", (16, 16))
                im.alpha_composite(tint(overlay, ROUND3_COLOURS[n]))
                im.alpha_composite(body)
                forms.append(im)
        if arrow_base is not None and arrow_head is not None:
            im = Image.new("RGBA", (16, 16))
            im.alpha_composite(arrow_base)
            im.alpha_composite(tint(arrow_head, ROUND3_COLOURS[n]))
            forms.append(im)
        for j, im in enumerate(forms):
            sheet.alpha_composite(im.resize((16 * s, 16 * s), Image.NEAREST), (x0 + j * (16 * s + 6), y4 + 32))
    sheet = sheet.crop((0, 0, W, y4 + 32 + 16 * s + 12))
    out = PREVIEW / "fieldkit_food_drink.png"
    sheet.save(out)
    return out


def main():
    sprites = icons()
    out = preview(sprites)
    print("preview:", out)
    things = item_sprites()
    out = preview_items(things, "Realistic Damage 2.0, field kit: phase 4 round 1, bleeding (Medicine) - 8x, then in slots",
                        "fieldkit_items_bleeding.png",
                        ["paper", "string", "leather", "iron_nugget", "glass_bottle", "flint_and_steel"])
    print("preview:", out)
    round2 = round2_sprites()
    out = preview_items(round2, "Realistic Damage 2.0, field kit: phase 4 round 2, bones and senses (Medicine) - 8x, then in slots",
                        "fieldkit_items_bones_senses.png",
                        ["stick", "string", "white_wool", "bone_meal", "copper_ingot", "leather"])
    print("preview:", out)
    round3 = round3_items()
    round3_effects = round3_icons()
    out = preview_round3(round3, round3_effects)
    print("preview:", out)
    if "--install-round3" in sys.argv:
        ITEM_TEXTURES.mkdir(parents=True, exist_ok=True)
        for n, im in round3.items():
            im.save(ITEM_TEXTURES / f"{n}.png")
            print("wrote", ITEM_TEXTURES / f"{n}.png")
        TEXTURES.mkdir(parents=True, exist_ok=True)
        for n, im in round3_effects.items():
            im.save(TEXTURES / f"{n}.png")
            print("wrote", TEXTURES / f"{n}.png")
    if "--install-round2" in sys.argv:
        ITEM_TEXTURES.mkdir(parents=True, exist_ok=True)
        for n, im in round2.items():
            im.save(ITEM_TEXTURES / f"{n}.png")
            print("wrote", ITEM_TEXTURES / f"{n}.png")
    if "--install-items" in sys.argv:
        ITEM_TEXTURES.mkdir(parents=True, exist_ok=True)
        for n, im in things.items():
            im.save(ITEM_TEXTURES / f"{n}.png")
            print("wrote", ITEM_TEXTURES / f"{n}.png")
    if "--install" in sys.argv:
        TEXTURES.mkdir(parents=True, exist_ok=True)
        for n, im in sprites.items():
            im.save(TEXTURES / f"{n}.png")
            print("wrote", TEXTURES / f"{n}.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())
