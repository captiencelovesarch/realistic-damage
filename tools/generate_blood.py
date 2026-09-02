#!/usr/bin/env python3
"""Synthesises the blood textures shipped in assets/realisticdamage/textures/.

Generated rather than drawn, for the same reason the injury sounds are
synthesised (see generate_sounds.py): nothing has to be licensed, and the look
is re-tunable by changing a number instead of by reopening an image editor.

Three families, all produced as white RGB with a shaped alpha channel so that
the renderer supplies the colour. That matters here — blood is drawn wet-bright
and re-drawn brown as it dries, from one texture, and a texture with the red
baked in could not do the second half.

  particle/blood_0..3   the falling drop. Small, bright core, soft edge.
  misc/blood_splat      2x2 atlas of marks left on a surface. Irregular main
                        blob, satellite droplets, a couple of directional
                        flicks — the things that make a splat read as thrown
                        rather than placed.
  misc/blood_lens       2x2 atlas of droplets on the camera. Teardrops with the
                        tail pointing up, because they are running down.

Run from the repo root:  python3 tools/generate_blood.py
Requires numpy and Pillow.
"""

import sys
from pathlib import Path

import numpy as np
from PIL import Image

OUT = Path("src/main/resources/assets/realisticdamage/textures")

# Deterministic: regenerating must not silently produce different textures.
SEED = 0x0B10D5


def _grid(size):
    """Coordinate grids in [0, 1] plus centred [-1, 1] axes."""
    axis = (np.arange(size) + 0.5) / size
    xs, ys = np.meshgrid(axis, axis)
    return xs, ys, xs * 2.0 - 1.0, ys * 2.0 - 1.0


def _blob(size, cx, cy, radius, wobble=0.0, rng=None, softness=0.06):
    """A filled circle with an optionally wobbly edge, as a soft alpha mask."""
    _, _, gx, gy = _grid(size)
    dx = gx - cx
    dy = gy - cy
    dist = np.hypot(dx, dy)
    edge = np.full_like(dist, radius)
    if wobble > 0.0 and rng is not None:
        angle = np.arctan2(dy, dx)
        # A few harmonics is all it takes: enough to look torn, not so much
        # that it turns into noise and stops reading as one mass.
        for harmonic in (2, 3, 5, 7):
            phase = rng.uniform(0.0, 2.0 * np.pi)
            amount = wobble * rng.uniform(0.35, 1.0) / harmonic
            edge = edge + radius * amount * np.sin(harmonic * angle + phase)
    # Smoothstep across the boundary gives an antialiased edge for free.
    t = np.clip((edge - dist) / max(softness, 1e-6), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def _streak(size, cx, cy, angle, length, width, taper_power=0.7):
    """An elongated flick, for the trailing spatter of a thrown drop.

    The taper exponent matters more than it looks: at 1.5 the width collapses to
    under a pixel within a third of the length, so the flick renders as a stub
    and the shape stops reading as motion. Below 1 it stays visible to the tip.
    """
    _, _, gx, gy = _grid(size)
    dx = gx - cx
    dy = gy - cy
    along = dx * np.cos(angle) + dy * np.sin(angle)
    across = -dx * np.sin(angle) + dy * np.cos(angle)
    # Taper: full width at the root, a point at the tip.
    frac = np.clip(along / max(length, 1e-6), 0.0, 1.0)
    taper = width * (1.0 - frac) ** taper_power
    inside = (along >= 0.0) & (along <= length)
    t = np.clip((taper - np.abs(across)) / (1.5 / size), 0.0, 1.0)
    return np.where(inside, t * t * (3.0 - 2.0 * t), 0.0)


def _write(mask, path, rgb=(255, 255, 255)):
    """Writes a white-RGB, shaped-alpha PNG. Colour is the renderer's job."""
    alpha = np.clip(mask, 0.0, 1.0)
    height, width = alpha.shape
    out = np.zeros((height, width, 4), dtype=np.uint8)
    out[..., 0] = rgb[0]
    out[..., 1] = rgb[1]
    out[..., 2] = rgb[2]
    out[..., 3] = (alpha * 255.0 + 0.5).astype(np.uint8)
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out, mode="RGBA").save(path)
    print(f"  {path.relative_to(Path('src/main/resources'))}  {width}x{height}")


def drop_sprites(size=8, count=4):
    """The falling drop: a bead, slightly taller than wide, with a soft edge.

    Four variants so a heavy bleed does not look like one sprite stamped
    repeatedly — at eight pixels the differences are barely articulable, which
    is exactly the level at which repetition stops being noticeable.
    """
    print("drop sprites:")
    for index in range(count):
        rng = np.random.default_rng(SEED + index)
        radius = 0.66 + 0.08 * rng.uniform(-1.0, 1.0)
        # Softness has to stay near one pixel here. At eight pixels across, a
        # soft edge is not a soft edge, it is the whole sprite: the first pass
        # used 0.30 and the drops came out as faint smudges you could not see
        # against terrain, which is the exact failure this rework is fixing.
        mask = _blob(size, 0.0, -0.05, radius, wobble=0.14, rng=rng, softness=1.6 / size)
        # Squash horizontally: a drop in flight is not a ball.
        _, _, gx, _ = _grid(size)
        mask = mask * np.clip(1.25 - 0.30 * np.abs(gx), 0.0, 1.0)
        _write(np.clip(mask, 0.0, 1.0), OUT / "particle" / f"blood_{index}.png")


def splat_sprites(cell=32):
    """Surface marks, picked per stain by a deterministic hash.

    Four shapes is enough because each is also rotated and scaled at draw time;
    what these have to supply is silhouette variety, not final variety.

    Written as individual particle textures rather than one atlas because stains
    are drawn as particles — it is the particle engine that gives them batching,
    frustum culling, lighting and lifetime for free, and it can only address
    sprites that live on the particle atlas.
    """
    print("splat sprites:")
    for index in range(4):
        rng = np.random.default_rng(SEED + 100 + index)

        # The body of the mark, built from two or three overlapping lobes rather
        # than one circle. A single wobbled disc still reads as a disc — it is
        # the asymmetry between lobes that makes it look like liquid that landed
        # somewhere rather than a decal that was placed.
        mask = np.zeros((cell, cell), dtype=np.float64)
        for lobe in range(int(rng.integers(2, 4))):
            angle = rng.uniform(0.0, 2.0 * np.pi)
            offset = 0.0 if lobe == 0 else rng.uniform(0.08, 0.22)
            mask = np.maximum(mask, _blob(
                cell, offset * np.cos(angle), offset * np.sin(angle),
                rng.uniform(0.30, 0.44), wobble=0.55, rng=rng, softness=1.5 / cell))

        # Flicks first, so satellites can sit on top of their tips the way real
        # spatter does — the bead is thrown further than the tail it came from.
        flicks = []
        for _ in range(int(rng.integers(2, 5))):
            angle = rng.uniform(0.0, 2.0 * np.pi)
            length = rng.uniform(0.34, 0.60)
            flicks.append((angle, length))
            mask = np.maximum(mask, _streak(
                cell, 0.26 * np.cos(angle), 0.26 * np.sin(angle),
                angle, length, rng.uniform(0.045, 0.085)))

        # Satellites: the droplets that break away on impact. Biased along the
        # flicks, kept inside the cell or they clip into the atlas neighbour.
        for _ in range(int(rng.integers(6, 12))):
            if flicks and rng.uniform() < 0.6:
                angle = flicks[int(rng.integers(0, len(flicks)))][0] + rng.uniform(-0.35, 0.35)
            else:
                angle = rng.uniform(0.0, 2.0 * np.pi)
            distance = rng.uniform(0.48, 0.84)
            cx, cy = distance * np.cos(angle), distance * np.sin(angle)
            if abs(cx) > 0.85 or abs(cy) > 0.85:
                continue
            mask = np.maximum(mask, _blob(
                cell, cx, cy, rng.uniform(0.055, 0.125), wobble=0.35, rng=rng, softness=1.4 / cell))

        _write(mask, OUT / "particle" / f"blood_splat_{index}.png")


def lens_atlas(cell=32):
    """2x2 atlas of droplets clinging to the camera.

    Teardrops with the tail pointing up: they are running down the lens, and a
    drop's tail trails behind its direction of travel. Getting that backwards
    reads instantly as wrong even though nobody can say why.
    """
    print("lens atlas:")
    atlas = np.zeros((cell * 2, cell * 2), dtype=np.float64)
    for index in range(4):
        rng = np.random.default_rng(SEED + 200 + index)
        # A smaller head and a much longer tail than the first pass, which put
        # most of the mass in the head and produced blobs with a nub on top.
        # What sells a running droplet is the length of what it left behind.
        head_r = rng.uniform(0.20, 0.27)
        head_y = rng.uniform(0.46, 0.60)
        mask = _blob(cell, 0.0, head_y, head_r, wobble=0.25, rng=rng, softness=1.5 / cell)
        # Tail upward: rows increase downward, so the trail sits at lower y.
        tail = rng.uniform(0.95, 1.35)
        mask = np.maximum(mask, _streak(
            cell, 0.0, head_y, -np.pi / 2.0, tail, head_r * 0.62, taper_power=0.55))
        # Beads left behind along the run, on every variant — a droplet that has
        # travelled always sheds some of itself on the way.
        for _ in range(int(rng.integers(2, 5))):
            along = rng.uniform(0.25, 0.95)
            mask = np.maximum(mask, _blob(
                cell, rng.uniform(-0.09, 0.09), head_y - tail * along,
                rng.uniform(0.035, 0.085), softness=1.4 / cell))
        row, col = divmod(index, 2)
        atlas[row * cell:(row + 1) * cell, col * cell:(col + 1) * cell] = mask
    _write(atlas, OUT / "misc" / "blood_lens.png")


def body_overlay(size=64):
    """The stain layer drawn over a player model, in the vanilla skin layout.

    Deliberately *uniform in density* rather than composed as a picture of a
    body. The six regions this mod tracks occupy six unrelated rectangles of the
    skin UV, so anything authored around the image as a whole — the first pass
    used a single top-to-bottom gradient — lands differently on every limb, and
    a leg ends up drenched while an arm stays clean for no reason the player can
    see. An even field of splotches and downward runs samples plausibly wherever
    a region happens to fall, and the per-region alpha carries the actual "how
    bloody is this part" signal.
    """
    print("body overlay:")
    rng = np.random.default_rng(SEED + 300)
    mask = np.zeros((size, size), dtype=np.float64)
    rows = np.arange(size)[:, None]
    cols = np.arange(size)[None, :]

    # Splotches: where blood has soaked in and sat.
    for _ in range(70):
        cx = rng.uniform(0.0, size)
        cy = rng.uniform(0.0, size)
        radius = rng.uniform(1.2, 4.5)
        edge = radius * (1.0 + 0.35 * np.sin(
            rng.uniform(0.0, 6.28) + 3.0 * np.arctan2(rows - cy, cols - cx)))
        dist = np.hypot(rows - cy, cols - cx)
        blob = np.clip((edge - dist) / 1.4, 0.0, 1.0)
        mask = np.maximum(mask, blob * rng.uniform(0.55, 1.0))

    # Runs: gravity's contribution. Narrow at the top where they started,
    # widening to a bead at the bottom where they stopped, because that is what
    # a trickle drying on a surface actually looks like.
    for _ in range(55):
        cx = rng.uniform(0.0, size)
        top = rng.uniform(0.0, size)
        length = rng.uniform(4.0, 18.0)
        width = rng.uniform(0.5, 1.5)
        strength = rng.uniform(0.35, 0.85)
        extent = np.clip((rows - top) / length, 0.0, 1.0)
        swell = width * (0.55 + 0.75 * extent)
        run = np.clip((swell - np.abs(cols - cx)) / 1.2, 0.0, 1.0)
        run = run * np.where((rows >= top) & (rows <= top + length), 1.0, 0.0)
        mask = np.maximum(mask, run * strength)
        # The bead that hangs at the end of the run.
        dist = np.hypot(rows - (top + length), cols - cx)
        mask = np.maximum(mask, np.clip((width * 1.5 - dist) / 1.2, 0.0, 1.0) * strength)

    _write(np.clip(mask, 0.0, 1.0), OUT / "misc" / "blood_body.png")


def main():
    if not OUT.parent.exists():
        sys.exit(f"run from the repo root: {OUT.parent} not found")
    drop_sprites()
    splat_sprites()
    lens_atlas()
    body_overlay()
    print("done")


if __name__ == "__main__":
    main()
