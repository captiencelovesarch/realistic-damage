"""Shared by the audio tools: where things are, and how the game would mix a recipe."""

import json
import subprocess
from pathlib import Path

import numpy as np

SR = 44100
TICK = 0.05
HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
ASSETS = REPO / "src/main/resources/assets/realisticdamage"
RECIPES = ASSETS / "rd_sounds"
VANILLA = Path.home() / ".local/share/PrismLauncher/assets"

# The tuner page's name for each sound -> the mod's event id (InjurySound.id()).
EVENTS = {
    "bone_break": "fracture",
    "limb_lost": "destruction",
    "decapitation": "decapitation",
    "blood_splat": "blood_splat",
    "flesh_hit": "wound_open",
    "collapse": "collapse",
}


def preview_dir() -> Path:
    """audio-preview/ beside the main checkout, whichever worktree this runs from.

    It holds audio mixed from Mojang's files, so it is git-ignored and there is
    exactly one of it.
    """
    common = subprocess.run(["git", "rev-parse", "--path-format=absolute", "--git-common-dir"],
                            cwd=HERE, capture_output=True, text=True, check=True).stdout.strip()
    return Path(common).parent / "audio-preview"


_index = None


def decode(path: Path) -> np.ndarray:
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


def sound_file(name: str) -> np.ndarray:
    """A recipe's "sound": namespace:path, mono, at 44.1 kHz."""
    global _index
    namespace, _, path = name.partition(":")
    if namespace == "minecraft":
        if _index is None:
            _index = json.loads((VANILLA / "indexes/34.json").read_text())["objects"]
        h = _index[f"minecraft/sounds/{path}.ogg"]["hash"]
        return decode(VANILLA / "objects" / h[:2] / h)
    if namespace == "realisticdamage":
        return decode(ASSETS / "sounds" / f"{path}.ogg")
    raise KeyError(name)


def pitched(x: np.ndarray, pitch: float) -> np.ndarray:
    """Minecraft's pitch is a playback-rate change, nothing cleverer."""
    n = int(len(x) / pitch)
    return np.interp(np.arange(n) * pitch, np.arange(len(x)), x)


def single(value) -> float:
    """A recipe number, or the middle of a [low, high] range."""
    return float(sum(value) / 2) if isinstance(value, list) else float(value)


def recipe(set_id: str, event: str) -> dict:
    return json.loads((RECIPES / set_id / f"{event}.json").read_text())


def game_mix(r: dict) -> np.ndarray:
    """A whole recipe as the game plays it at full strength: first variant, times the recipe's gain."""
    return mix(r["variants"][0]["layers"]) * float(r.get("gain", 1.0))


def mix(layers: list) -> np.ndarray:
    """What the game does with a recipe's layers: start each on its tick and add them. No limiter."""
    parts = [(pitched(sound_file(l["sound"]), single(l.get("pitch", 1.0))) * single(l.get("volume", 1.0)),
              int(l.get("delay", 0) * TICK * SR)) for l in layers]
    out = np.zeros(max(at + len(x) for x, at in parts) + 1)
    for x, at in parts:
        out[at: at + len(x)] += x
    return out


def _biquad(x, b, a):
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    b0, b1, b2 = (v / a[0] for v in b)
    a1, a2 = a[1] / a[0], a[2] / a[0]
    for i, xi in enumerate(x):
        yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, xi, y1, yi
        y[i] = yi
    return y


def loudness(x: np.ndarray) -> float:
    """How loud the ear takes a short sound to be, in dB: BS.1770's K-weighting
    (a high shelf and a low cut), then the loudest 400 ms. A ruler for matching
    one set to the other, not a broadcast meter."""
    # high shelf, +4 dB above ~1.7 kHz
    g, q, f0 = 3.99984385397, 0.7071752369554193, 1681.9744509555319
    k = np.tan(np.pi * f0 / SR)
    vh, vb = 10 ** (g / 20), (10 ** (g / 20)) ** 0.499666774155
    a0 = 1 + k / q + k * k
    x = _biquad(x, [(vh + vb * k / q + k * k) / a0, 2 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0],
                [1, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0])
    # low cut at 38 Hz
    q, f0 = 0.5003270373253953, 38.13547087613982
    k = np.tan(np.pi * f0 / SR)
    x = _biquad(x, [1, -2, 1], [1, 2 * (k * k - 1) / (1 + k / q + k * k), (1 - k / q + k * k) / (1 + k / q + k * k)])
    win = int(0.4 * SR)
    if len(x) < win:
        x = np.concatenate([x, np.zeros(win - len(x))])
    power = np.convolve(x * x, np.ones(win) / win, "valid")
    return -0.691 + 10 * np.log10(max(power.max(), 1e-12))
