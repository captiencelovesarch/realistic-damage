#!/usr/bin/env python3
"""Synthesises the injury sounds shipped in assets/realisticdamage/sounds/.

Every injury sound is generated rather than sampled, for the same reason the
tinnitus tone is: nothing has to be licensed, and the sound can be re-tuned by
changing a number instead of by finding a new recording.

Two styles of each event, chosen client-side (see ClientEffectsConfig):

  muted     sits inside Minecraft's own palette. Dry, low-fi, short. Never
            louder or wetter than the game around it.
  visceral  sells the injury. Sharper transients, more high end, wet tails,
            longer decays.

Run from the repo root:  python3 tools/generate_sounds.py
Requires numpy, scipy and ffmpeg.
"""

import subprocess
import sys
from pathlib import Path

import numpy as np
from scipy import signal

SR = 44100
OUT = Path("src/main/resources/assets/realisticdamage/sounds")

# Deterministic: regenerating must not silently produce a different sound.
SEED = 0x0DEFACED


def rng(salt: int) -> np.random.Generator:
    return np.random.default_rng(SEED + salt)


def env(n: int, attack: float, decay: float, curve: float = 3.0) -> np.ndarray:
    """Percussive envelope: near-instant attack, exponential decay."""
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-6), 0.0, 1.0)
    d = np.exp(-curve * np.clip((t - attack) / max(decay, 1e-6), 0.0, None))
    return a * d


def band(x: np.ndarray, lo: float, hi: float, order: int = 4) -> np.ndarray:
    nyq = SR / 2
    lo = max(lo, 20.0) / nyq
    hi = min(hi, nyq - 100.0) / nyq
    sos = signal.butter(order, [lo, hi], btype="band", output="sos")
    return signal.sosfilt(sos, x)


def lowpass(x: np.ndarray, cut: float, order: int = 4) -> np.ndarray:
    sos = signal.butter(order, cut / (SR / 2), btype="low", output="sos")
    return signal.sosfilt(sos, x)


def thump(dur: float, f_start: float, f_end: float, decay: float) -> np.ndarray:
    """A body-weight low end: a sine dropping in pitch as it decays."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f_start * (f_end / f_start) ** (t / dur)
    phase = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(phase) * env(n, 0.001, decay)


def crack(dur: float, lo: float, hi: float, decay: float, salt: int) -> np.ndarray:
    """One sharp transient: a filtered noise burst with a very fast decay."""
    n = int(dur * SR)
    return band(rng(salt).normal(0, 1, n), lo, hi) * env(n, 0.0004, decay, curve=5.0)


def place(base: np.ndarray, piece: np.ndarray, at: float, gain: float = 1.0) -> None:
    """Mixes a piece into base at a time offset, in place."""
    i = int(at * SR)
    end = min(len(base), i + len(piece))
    if end > i:
        base[i:end] += piece[: end - i] * gain


def norm(x: np.ndarray, peak: float) -> np.ndarray:
    m = np.max(np.abs(x))
    if m > 0:
        x = x / m
    # A short fade-out so the file never ends on a discontinuity (it clicks).
    tail = min(len(x), int(0.01 * SR))
    x[-tail:] *= np.linspace(1.0, 0.0, tail)
    return x * peak


# --------------------------------------------------------------------------
# Fracture: the moment a bone goes. Short, and it must cut through a fight.
# --------------------------------------------------------------------------

def fracture_muted() -> np.ndarray:
    out = np.zeros(int(0.30 * SR))
    # One dry woody snap, the way a dead branch goes underfoot. The low end is
    # kept short and quiet on purpose: let it ring and the snap turns into a
    # tuned boom, which is the one thing a fracture must not sound like.
    place(out, crack(0.17, 420, 4400, 0.032, 1), 0.0, 1.0)
    place(out, thump(0.10, 175, 95, 0.026), 0.001, 0.30)
    return norm(out, 0.72)


def fracture_visceral() -> np.ndarray:
    out = np.zeros(int(0.55 * SR))
    # Bone does not break in one clean event: it fails in a splintering
    # cluster. Irregular spacing is what stops it reading as a drum roll.
    for i, (at, gain) in enumerate([(0.0, 1.0), (0.011, 0.7), (0.026, 0.85),
                                    (0.045, 0.5), (0.071, 0.35)]):
        place(out, crack(0.13, 300, 5200, 0.016, 10 + i), at, gain)
    # The wet part: low, damp noise under the cracks.
    n = int(0.34 * SR)
    wet = lowpass(rng(20).normal(0, 1, n), 700) * env(n, 0.004, 0.10, curve=4.0)
    place(out, wet, 0.008, 0.55)
    place(out, thump(0.30, 190, 65, 0.08), 0.0, 0.65)
    return norm(out, 0.85)


# --------------------------------------------------------------------------
# Destruction: a limb is gone. The heaviest sound the mod makes.
# --------------------------------------------------------------------------

def destruction_muted() -> np.ndarray:
    out = np.zeros(int(0.55 * SR))
    # Weight, not pitch. A long pure sine here reads as a bass drop rather than
    # as something heavy landing, so the low end decays fast and most of the
    # body is noise.
    place(out, thump(0.34, 125, 48, 0.085), 0.0, 0.90)
    n = int(0.34 * SR)
    body = band(rng(30).normal(0, 1, n), 110, 1800) * env(n, 0.003, 0.085, curve=2.8)
    place(out, body, 0.0, 0.70)
    place(out, crack(0.10, 300, 2800, 0.018, 31), 0.0, 0.50)
    return norm(out, 0.80)


def destruction_visceral() -> np.ndarray:
    out = np.zeros(int(0.95 * SR))
    place(out, thump(0.70, 130, 38, 0.20), 0.0, 1.0)
    # Crunch: dense granular crackle, thinning out as it goes.
    r = rng(40)
    for i in range(26):
        at = float(r.uniform(0.0, 0.34))
        place(out, crack(0.08, 500, 7000, 0.010, 50 + i), at, 0.85 * (1.0 - at / 0.45))
    # Tearing tail: a noise band sliding downward, which is what gives it the
    # sense of something coming apart rather than just being hit.
    n = int(0.55 * SR)
    raw = r.normal(0, 1, n)
    tail = np.zeros(n)
    steps = 12
    for s in range(steps):
        a, b = s * n // steps, (s + 1) * n // steps
        top = 4200 * (1.0 - 0.72 * s / steps)
        tail[a:b] = band(raw, 260, top)[a:b]
    place(out, tail * env(n, 0.01, 0.24, curve=2.6), 0.05, 0.55)
    return norm(out, 0.92)


# --------------------------------------------------------------------------
# Collapse: you go down. Heard from inside your own head, so it is more breath
# than impact.
# --------------------------------------------------------------------------

def collapse_muted() -> np.ndarray:
    out = np.zeros(int(0.75 * SR))
    place(out, thump(0.45, 85, 40, 0.16), 0.0, 1.0)
    n = int(0.45 * SR)
    # Warm and throaty rather than bright: banded up at 2 kHz an exhale stops
    # being breath and becomes hiss.
    breath = band(rng(60).normal(0, 1, n), 240, 1300) * env(n, 0.09, 0.20, curve=2.2)
    place(out, breath, 0.10, 0.34)
    return norm(out, 0.62)


def collapse_visceral() -> np.ndarray:
    out = np.zeros(int(1.15 * SR))
    # Full weight going down, then the limbs following a moment later.
    place(out, thump(0.55, 95, 34, 0.18), 0.0, 1.0)
    place(out, thump(0.30, 70, 32, 0.10), 0.085, 0.42)
    n = int(0.34 * SR)
    place(out, lowpass(rng(70).normal(0, 1, n), 900) * env(n, 0.003, 0.09), 0.0, 0.35)
    # The air being knocked out: a long noisy exhale, shaped so it reads as a
    # throat rather than as hiss.
    n = int(0.70 * SR)
    ex = rng(71).normal(0, 1, n)
    # One wide band with a warm shelf under it. Stacking narrow bands to fake
    # vowel formants put a steady tone in the middle of the exhale — a whistle,
    # not a throat. Overlapping filter skirts are what built the resonance.
    voiced = band(ex, 240, 2600) + 0.40 * lowpass(ex, 620)
    # A slow tremble, so the breath is unsteady the way a winded one is.
    tremble = 1.0 + 0.18 * np.sin(2 * np.pi * 7.5 * np.arange(n) / SR)
    place(out, voiced * tremble * env(n, 0.10, 0.32, curve=2.0), 0.10, 0.52)
    return norm(out, 0.80)


SOUNDS = {
    "fracture_muted": fracture_muted,
    "fracture_visceral": fracture_visceral,
    "destruction_muted": destruction_muted,
    "destruction_visceral": destruction_visceral,
    "collapse_muted": collapse_muted,
    "collapse_visceral": collapse_visceral,
}


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in SOUNDS.items():
        audio = make()
        if np.max(np.abs(audio)) > 1.0:
            raise SystemExit(f"{name} clips")
        pcm = (np.clip(audio, -1.0, 1.0) * 32767).astype("<i2").tobytes()
        target = OUT / f"{name}.ogg"
        # Mono: Minecraft will not position or attenuate a stereo sound.
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error",
             "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "pipe:0",
             "-c:a", "libvorbis", "-q:a", "4", str(target)],
            input=pcm, check=True)
        print(f"{name:24s} {len(audio) / SR:.2f}s  {target.stat().st_size:6d} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
