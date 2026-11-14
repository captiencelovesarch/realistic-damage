#!/usr/bin/env python3
"""Renders the Grounded sound set into the mod (see AUDIO_PLAN.md).

synth.js makes each sound with the settings tuned on the slider page (its
defaults). This script turns those into the mod's files:

  sounds/grounded/<event>.ogg       stereo, for your own body
  sounds/grounded/<event>_far.ogg   mono, for somebody else's: the game can
                                    only place a mono sound in the world
  rd_sounds/grounded/<event>.json   the recipe that plays them

Each sound's level is matched to the Arcade recipe for the same event, so
switching sets changes the character and not the volume. A body with no blood
in it (a skeleton) gets the same sound rendered without its wet layers.

Run:  python3 tools/audio/build_grounded.py     (node, numpy, ffmpeg)
"""

import json
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

from common import ASSETS, EVENTS, HERE, RECIPES, SR, game_mix, loudness, recipe

# What "dry" switches off in the synthesiser, per sound that has a dry version.
DRY = {
    "bone_break": {"wetGain": 0},
    "decapitation": {"wetGain": 0},
}
PEAK = 0.98


def render(settings: dict) -> dict:
    with tempfile.TemporaryDirectory() as tmp:
        file = Path(tmp) / "settings.json"
        file.write_text(json.dumps(settings))
        subprocess.run(["node", str(HERE / "synth.js"), "wav", tmp, str(file)], check=True, capture_output=True)
        out = {}
        for name in EVENTS:
            with wave.open(str(Path(tmp) / f"grounded_{name}.wav")) as w:
                assert w.getframerate() == SR and w.getnchannels() == 2
                out[name] = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).reshape(-1, 2) / 32768.0
        return out


def write_ogg(x: np.ndarray, path: Path) -> None:
    channels = 1 if x.ndim == 1 else x.shape[1]
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", str(channels), "-i", "-",
         "-c:a", "libvorbis", "-q:a", "6", "-map_metadata", "-1", str(path)],
        input=x.astype(np.float32).tobytes(), check=True)


def layer(file: str, **more) -> dict:
    return {"sound": f"realisticdamage:grounded/{file}", "far": f"realisticdamage:grounded/{file}_far", **more}


def write_recipe(event: str, variants: list, dry: list | None, small: bool) -> None:
    def block(vs):
        rows = []
        for v in vs:
            layers = ",\n".join("\t\t\t{ " + ", ".join(f'"{k}": {json.dumps(val)}' for k, val in l.items()) + " }"
                                for l in v)
            rows.append('\t\t{ "layers": [\n' + layers + "\n\t\t] }")
        return "[\n" + ",\n".join(rows) + "\n\t]"
    body = [f'\t"subtitle": "realisticdamage.subtitle.{event}"', f'\t"variants": {block(variants)}']
    if dry:
        body.append(f'\t"dry": {block(dry)}')
    if small:
        body.append('\t"small": true')
    path = RECIPES / "grounded" / f"{event}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("{\n" + ",\n".join(body) + "\n}\n")
    json.loads(path.read_text())


def main() -> None:
    sounds = ASSETS / "sounds/grounded"
    sounds.mkdir(parents=True, exist_ok=True)
    wet = render({})
    dry = render({name: {"params": params} for name, params in DRY.items()})
    print(f"{'event':13s} {'arcade':>8s} {'grounded':>9s} {'gain':>7s} {'peak':>5s}  note")
    for name, event in EVENTS.items():
        arcade = game_mix(recipe("arcade", event))
        x = wet[name]
        target, have = loudness(arcade), loudness(x.mean(axis=1))
        gain = 10 ** ((target - have) / 20)
        note = ""
        if gain * np.abs(x).max() > PEAK:
            gain = PEAK / np.abs(x).max()
            note = f"cannot go louder without clipping: {target - have - 20 * np.log10(gain):.1f} dB under Arcade"
        write_ogg(x * gain, sounds / f"{event}.ogg")
        write_ogg(x.mean(axis=1) * gain, sounds / f"{event}_far.ogg")
        dry_variants = None
        if name in DRY:
            d = dry[name]
            write_ogg(d * gain, sounds / f"{event}_dry.ogg")
            write_ogg(d.mean(axis=1) * gain, sounds / f"{event}_dry_far.ogg")
            dry_variants = [[layer(f"{event}_dry")]]
        elif event == "destruction":
            # STAND-IN: the tuned limb sound is nearly all tearing flesh, and a
            # skeleton has none. Until a dry one is designed and listened to,
            # its limb goes with the dry fracture, pitched down.
            dry_variants = [[layer("fracture_dry", pitch=0.8)]]
        write_recipe(event, [[layer(event)]], dry_variants, small=(event == "blood_splat"))
        print(f"{event:13s} {target:8.1f} {have:9.1f} {20 * np.log10(gain):+7.1f} {gain * np.abs(x).max():5.2f}  "
              f"arcade mix peak {np.abs(arcade).max():.2f}  {note}")


if __name__ == "__main__":
    main()
