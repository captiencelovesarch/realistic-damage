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

"Sharper, brighter" is a claim with a number behind it, and until 1.6 the
visceral set failed it: measured, they were *darker* than the muted ones, with
50-73% of their weighted energy under 200 Hz against a vanilla damage palette
that runs 0.2-23% (only TNT is mostly sub-bass). Each one was mostly a single
pure sine gliding down an octave — a bass drop. The budget is now a rule rather
than an intention, and tools/test_sounds.py enforces it: no injury sound puts
more than a quarter of its K-weighted energy below 200 Hz, and no pitch glide
covers more than seven semitones.

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


# How far a struck body's partials droop over the whole sound. 0.72 is
# 12 * log2(1 / 0.72) = 5.7 semitones — enough to hear the mass settle, and
# under the seven or so where a downward glide stops being the pitch-droop of
# something heavy and starts being a portamento, i.e. a musical gesture.
BODY_DROOP = 0.72


def body(dur: float, f: float, decay: float,
         ratios=(1.0, 1.83, 3.11), tilt=(1.0, 0.45, 0.22)) -> np.ndarray:
    """A struck mass: inharmonic partials, each with its own decay.

    Unlike thump() this has weight without having a note, which is the whole
    point of it. thump() is one pure sine, and a pure sine sliding down an
    octave is an 808 — the visceral sounds measured 57-74% of their weighted
    energy in that one element and read as a bass drop with clicks on top.
    The ratios here are deliberately non-integer: 1 : 1.83 : 3.11 is nobody's
    harmonic series, so there is nothing in it to sing back.
    """
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for k, (ratio, gain) in enumerate(zip(ratios, tilt)):
        f_t = f * ratio * BODY_DROOP ** (t / dur)
        # The higher partials go first, at decay / (1 + 0.8k) — full, then 0.56
        # and 0.38 of it. Let them all ring equally and the stack settles into a
        # sustained chord, which is the pitched boom by another route.
        out += gain * np.sin(2 * np.pi * np.cumsum(f_t) / SR) * env(n, 0.0015, decay / (1 + 0.8 * k))
    return out / np.max(np.abs(out))


def moving_band(x: np.ndarray, lo: float, top_start: float, top_end: float,
                chunks: int = 48) -> np.ndarray:
    """A bandpass whose top cutoff walks geometrically while the sound plays.

    The same chunked technique as destruction's tearing tail, which was measured
    for stepping and has none: all the chunks filter one shared noise buffer, so
    neighbouring slices differ only by a little filter skirt and the crossovers
    sit inside the ordinary sample-to-sample variation of the noise itself.
    Forty-eight chunks over a 0.7 s exhale is a step every 15 ms.
    """
    n = len(x)
    out = np.zeros(n)
    for s in range(chunks):
        a, b = s * n // chunks, (s + 1) * n // chunks
        top = top_start * (top_end / top_start) ** (s / max(chunks - 1, 1))
        out[a:b] = band(x, lo, top)[a:b]
    return out


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
    # 0.40 s rather than 0.55: nothing was audible past 183 ms of it anyway.
    out = np.zeros(int(0.40 * SR))
    # Bone does not break in one clean event: it fails in a splintering
    # cluster. Irregular spacing is what stops it reading as a drum roll — and
    # so, since 1.6, does giving each splinter its own voice. Five bursts
    # through one identical (300, 5200, 0.016) filter is one sample retriggered
    # five times, at constant brightness and constant decay; these run
    # 3800-7000 Hz with decays from 10 to 20 ms, so the cluster has bright
    # shards and dull ones in it.
    tops = [6200, 4600, 7000, 5000, 3800]
    decays = [0.020, 0.012, 0.017, 0.010, 0.014]
    for i, (at, gain) in enumerate([(0.0, 1.0), (0.011, 0.7), (0.026, 0.85),
                                    (0.045, 0.5), (0.071, 0.35)]):
        place(out, crack(0.13, 340, tops[i], decays[i], 10 + i), at, gain)
    # The wet part: low, damp noise under the cracks.
    n = int(0.34 * SR)
    wet = lowpass(rng(20).normal(0, 1, n), 700) * env(n, 0.004, 0.10, curve=4.0)
    place(out, wet, 0.008, 0.55)
    # The low end is a thud underneath the snap, not the snap itself. It was
    # thump(0.30, 190, 65) at 0.65 — an 11.5-semitone sine glide holding 60% of
    # the weighted energy, which is exactly the "tuned boom" the muted variant
    # above is written to avoid. The same guard, applied here at last: an
    # inharmonic body, a third of the length, and half the gain.
    place(out, body(0.14, 190, 0.030), 0.0, 0.32)
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
    # 0.75 s rather than 0.95, now the low end no longer rings for 461 ms.
    out = np.zeros(int(0.75 * SR))
    # Was thump(0.70, 130, 38) at full gain: a 14-semitone sine drop carrying
    # 57% of the weighted energy. That is a bass drop, and this is the sound
    # explosive dismemberment will reuse and play often, so it has to be weight
    # rather than pitch.
    place(out, body(0.30, 125, 0.075), 0.0, 0.62)
    # Crunch: dense granular crackle, thinning out as it goes — in character as
    # well as in volume. Every grain used to share one (500, 7000, 0.010)
    # filter, so 26 of them were one sound retriggered; drawing each grain's
    # brightness and decay from a seeded generator, and darkening the draw as
    # the crunch runs on, is what makes it a limb coming apart instead.
    r = rng(40)
    ats = [float(r.uniform(0.0, 0.34)) for _ in range(26)]
    v = rng(91)
    for i, at in enumerate(ats):
        # 1 - 0.35 * at / 0.34 walks the top cutoff down to 65% of its draw
        # across the 0.34 s the grains are scattered over.
        top = float(v.uniform(4200, 10000)) * (1.0 - 0.35 * at / 0.34)
        decay = float(v.uniform(0.006, 0.018))
        place(out, crack(0.08, 420, max(top, 900), decay, 50 + i), at,
              0.85 * (1.0 - at / 0.45))
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
    out = np.zeros(int(1.00 * SR))
    # Full weight going down, then the limbs following a moment later. Two pure
    # sines gliding 95 -> 44 Hz held three quarters of this sound's weighted
    # energy and 73% of it lived under 200 Hz — further into the sub-bass than
    # any vanilla sound but a TNT blast. Bodies are inharmonic; explosions are
    # not what this is.
    place(out, body(0.30, 105, 0.070), 0.0, 0.70)
    place(out, body(0.18, 84, 0.045), 0.085, 0.28)
    n = int(0.34 * SR)
    place(out, lowpass(rng(70).normal(0, 1, n), 900) * env(n, 0.003, 0.09), 0.0, 0.35)
    # The air being knocked out: a long noisy exhale, shaped so it reads as a
    # throat rather than as hiss.
    n = int(0.70 * SR)
    ex = rng(71).normal(0, 1, n)
    # A band that closes as the breath runs out, over a warm shelf. It used to
    # be one fixed band(240, 2600), which left 0.4% of the sound above 4 kHz and
    # pinned its spectral centroid at an unmoving 1900 Hz for 630 ms — a noise
    # generator behind a volume fader, not a throat. Walking the top cutoff
    # 5600 -> 1400 Hz puts real air in the front of the breath and closes it off
    # at the end. Stacking narrow bands to fake vowel formants was tried and put
    # a steady tone in the middle of the exhale: a whistle. This is one moving
    # band, so there are no overlapping skirts to build a resonance.
    voiced = moving_band(ex, 260, 5600, 1400) + 0.40 * lowpass(ex, 620)
    # A slow tremble, so the breath is unsteady the way a winded one is. It was
    # a 7.5 Hz sine, which measured 4.5x above the exhale's own envelope
    # variation and is perfectly periodic — an LFO, i.e. the one thing a
    # struggling breath is not. Low-passed noise at 6 Hz wavers on roughly the
    # same timescale without ever repeating. Seeded from SEED like every other
    # noise here, so the file is still identical every time it is generated.
    waver = lowpass(rng(72).normal(0, 1, n), 6.0)
    waver = waver / (np.max(np.abs(waver)) + 1e-9)
    place(out, voiced * (1.0 + 0.22 * waver) * env(n, 0.10, 0.32, curve=2.0), 0.10, 0.52)
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
