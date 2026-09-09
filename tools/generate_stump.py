#!/usr/bin/env python3
"""Synthesises the cut face drawn on a stump and on the shoulder end of a severed limb.

A 16x16 texture tiled from one 4x4 cell, because the cap cube's faces each sample a
4x4 region of it and which region depends on the face: painting every cell the same
way means the pattern is right whichever face the renderer happens to show. The cell is
dark flesh with a paler ring one pixel in and a bone-white centre, i.e. what a cross
section actually looks like at sixteen pixels to the block.

Requires numpy and Pillow. Output: src/main/resources/assets/realisticdamage/textures/misc/stump_end.png
"""
from pathlib import Path

import numpy as np
from PIL import Image

FLESH = (96, 12, 14, 255)
RING = (150, 30, 32, 255)
BONE = (228, 214, 190, 255)

cell = np.zeros((4, 4, 4), dtype=np.uint8)
cell[:, :] = FLESH
cell[1:3, 1:3] = RING
cell[1, 1] = BONE
cell[2, 2] = BONE
texture = np.tile(cell, (4, 4, 1))
out = Path(__file__).resolve().parents[1] / "src/main/resources/assets/realisticdamage/textures/misc/stump_end.png"
Image.fromarray(texture, "RGBA").save(out)
print("wrote", out)
