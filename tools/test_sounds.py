#!/usr/bin/env python3
"""Numeric guards on the generated injury sounds.

A listening test cannot be automated, but the thing that was wrong with the
visceral set for five releases can be measured, and this is that measurement
written down. Every one of these assertions failed on the sounds as shipped in
1.5: each visceral sound was, perceptually, one pure sine gliding down an
octave, putting 50-73% of its weighted energy under 200 Hz — further into the
sub-bass than a vanilla big fall, and in the collapse's case three quarters of
the way to a TNT blast. The generator's own docstring called that set "sharper,
more high end". Numbers settle that argument; ears only reopen it.

This is a plain pytest, not a gametest: it imports the generator and touches no
Minecraft type, which is what earns it a place beside the generator rather than
in src/test. It is NOT part of the gradle build and does not run in CI — CI runs
`./gradlew build` and knows nothing about Python. Run it by hand after touching
the generator:

    cd /path/to/realistic-damage && python3 -m pytest tools/test_sounds.py -q

Requires numpy, scipy and pytest.
"""

import hashlib
import importlib.util
import math
from pathlib import Path

import numpy as np
import pytest
from scipy import signal

_spec = importlib.util.spec_from_file_location(
    "generate_sounds", Path(__file__).with_name("generate_sounds.py"))
gen = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(gen)

SR = gen.SR

# Vanilla's damage palette tops out at fallsmall's 23.3% of K-weighted energy
# below 200 Hz, with hit3 and fallbig at 15-16% and everything else far under.
# Only explode1 (91.7%) is mostly sub-bass, and an injury is not an explosion.
# A quarter is that ceiling with a little room above it.
LOW_SHARE_MAX = 0.25

# Above roughly a fifth, a downward glide stops reading as the pitch-droop of a
# struck body and starts reading as a portamento — a musical gesture, which is
# what made these sounds land outside the game rather than inside it.
MAX_GLIDE_SEMITONES = 7.0

# The peak each sound asks norm() for. Its actual peak must be this and no more:
# the generator's only clipping guard is an assert at render time, and a sound
# that quietly normalises to something else has had its balance changed.
REQUESTED_PEAK = {
    "fracture_muted": 0.72,
    "fracture_visceral": 0.85,
    "destruction_muted": 0.80,
    "destruction_visceral": 0.92,
    "collapse_muted": 0.62,
    "collapse_visceral": 0.80,
    "neck_crack_muted": 0.70,
    "neck_crack_visceral": 0.85,
}

# The three the 1.6 spectral budget applies to.
#
# The muted three are deliberately outside it and are excluded rather than
# exempted quietly: measured today they are 14.0%, 83.8% and 94.3% sub-200 Hz
# (fracture, destruction, collapse). The muted fracture passes comfortably; the
# other two do not, and would fail this file if it looped over all six the way
# the first draft of it did. They are a different design — dry, low-fi, short
# and quiet, sitting under the game rather than cutting through it — they are
# not what the playtest was about, and they are pinned exactly by GOLDEN below,
# so nothing can drift here unnoticed. If they are ever re-tuned, the honest
# move is to bring them inside this budget and delete this list, not to raise
# LOW_SHARE_MAX.
VISCERAL = ["fracture_visceral", "destruction_visceral", "collapse_visceral", "neck_crack_visceral"]

# sha256 of each rendered buffer, quantised to the int16 that actually ships.
# Quantising rather than hashing the raw float64 is deliberate: a scipy upgrade
# can move a filter coefficient in the last bits without moving the sound, and a
# golden test that cries wolf gets deleted. Refresh these ONLY alongside a
# deliberate change to the generator, and listen to the result before you do.
GOLDEN = {
    "fracture_muted": "96eff596103931385dcbcb34bb191f274bc19d5a01b030a62edb249bb2c7af36",
    "fracture_visceral": "67d2a59d56f9f27dc07dc3261d4647a9686abb7f69ac7f6de939f8bfcdae76b9",
    "destruction_muted": "e993769e7b587450332c319f01b79b67a55a6ade672bcbc2f3d57c9286a87417",
    "destruction_visceral": "34125aa07db2c32f8f7cc3e00dce08fd6e0e6df4cd1b9ab7e2069484edf5381f",
    "collapse_muted": "cab17d86e18150e3b36d3f2e207a985282bc1eaff6bd920d6b323e828411c5e2",
    "collapse_visceral": "171c500b0ff9ae934382014fbeeef28e4aa4c6332a26147695eba37f303bba4a",
    "neck_crack_muted": "c20ea1baf8a27d38c4877e00abac988eed93b2c028d8f06b4482096266608d09",
    "neck_crack_visceral": "83097a84414ac189eb5cde2206767eca5e6cf9ba905c343b8a1aceef0a0bd65e",
}

NAMES = list(gen.SOUNDS)


def kweighted(x: np.ndarray) -> np.ndarray:
    """ITU-R BS.1770 K-weighting: a high shelf, then a high pass.

    Weighted rather than raw because the ear is markedly less sensitive down
    there, and an unweighted budget would condemn a low end that nobody can
    actually hear as loud. The coefficients are the standard's 48 kHz design
    re-derived at SR by the same bilinear transform the standard uses.
    """
    f0, gain_db, q = 1681.974450955533, 3.999843853973347, 0.7071752369554196
    k = math.tan(math.pi * f0 / SR)
    vh = 10 ** (gain_db / 20)
    vb = vh ** 0.4996667741545416
    denom = 1 + k / q + k * k
    b = np.array([vh + vb * k / q + k * k, 2 * (k * k - vh), vh - vb * k / q + k * k]) / denom
    a = np.array([1.0, 2 * (k * k - 1) / denom, (1 - k / q + k * k) / denom])
    shelved = signal.lfilter(b, a, x)

    f0, q = 38.13547087602444, 0.5003270373238773
    k = math.tan(math.pi * f0 / SR)
    denom = 1 + k / q + k * k
    b = np.array([1.0, -2.0, 1.0])
    a = np.array([1.0, 2 * (k * k - 1) / denom, (1 - k / q + k * k) / denom])
    return signal.lfilter(b, a, shelved)


def low_share(x: np.ndarray) -> float:
    """Fraction of K-weighted energy below 200 Hz."""
    spectrum = np.abs(np.fft.rfft(kweighted(x))) ** 2
    freqs = np.fft.rfftfreq(len(x), 1 / SR)
    return float(spectrum[freqs < 200.0].sum() / spectrum.sum())


@pytest.mark.parametrize("name", VISCERAL)
def test_visceral_sounds_stay_inside_the_vanilla_low_end(name):
    """The regression that actually shipped, and the one this file exists for."""
    share = low_share(gen.SOUNDS[name]())
    assert share < LOW_SHARE_MAX, (
        f"{name} puts {share:.1%} of its weighted energy under 200 Hz; vanilla's "
        f"whole damage palette fits under {LOW_SHARE_MAX:.0%} and only TNT does not")


@pytest.mark.parametrize("name", NAMES)
def test_nothing_clips_and_the_peak_is_the_one_asked_for(name):
    audio = gen.SOUNDS[name]()
    peak = float(np.max(np.abs(audio)))
    assert peak < 1.0, f"{name} clips at {peak}"
    assert peak == pytest.approx(REQUESTED_PEAK[name], abs=1e-6), (
        f"{name} normalised to {peak:.4f}, not the {REQUESTED_PEAK[name]} it asked for")


def test_a_struck_body_droops_less_than_a_fifth():
    """body() is where every visceral low end now comes from, and its droop is a
    module constant, so one assertion covers every call site there will ever be.
    """
    semitones = 12.0 * math.log2(1.0 / gen.BODY_DROOP)
    assert semitones <= MAX_GLIDE_SEMITONES, (
        f"BODY_DROOP {gen.BODY_DROOP} glides {semitones:.1f} semitones")


def test_no_visceral_sound_is_built_on_a_gliding_sine():
    """thump() is a single pure sine with a definite pitch, and the visceral set
    was 57-74% of it. The muted set may still use it — short, quiet and under
    everything else, it is a thud there rather than the sound itself — but a
    visceral function reaching for it again is the exact regression, and it would
    slip past the spectral budget above if the glide were kept shallow enough.
    """
    import ast
    source = Path(__file__).with_name("generate_sounds.py").read_text()
    for node in ast.parse(source).body:
        if not isinstance(node, ast.FunctionDef) or not node.name.endswith("_visceral"):
            continue
        called = {c.func.id for c in ast.walk(node)
                  if isinstance(c, ast.Call) and isinstance(c.func, ast.Name)}
        assert "thump" not in called, f"{node.name} is back on a gliding sine"
        assert "body" in called, f"{node.name} lost its low end entirely"


@pytest.mark.parametrize("name", NAMES)
def test_regeneration_does_not_silently_change_the_sound(name):
    """The generator's docstring promises this ("regenerating must not silently
    produce a different sound"); until now nothing enforced it. Hashed at int16,
    which is exactly what gets encoded into the .ogg.
    """
    audio = (np.clip(gen.SOUNDS[name](), -1.0, 1.0) * 32767).astype("<i2")
    digest = hashlib.sha256(audio.tobytes()).hexdigest()
    assert digest == GOLDEN[name], (
        f"{name} rendered differently: {digest}. If that was intentional, listen "
        f"to it and then update GOLDEN.")
