#!/usr/bin/env python3
"""Builds the listening page for the 2.0 audio rework (see AUDIO_PLAN.md).

The page goes in audio-preview/ beside the main checkout and stays there:
its Arcade examples are mixed from Mojang's own sound files, found in the
local launcher's asset cache, so nothing this script writes may be committed
or shipped. In the mod the same recipes are played at run time from the
player's own game, which redistributes nothing.

  Arcade    read from the mod's own recipe files (assets/.../rd_sounds/arcade)
            and mixed the way the game mixes them, so the page plays what the
            mod plays.
  Grounded  synthesised by synth.js. The page carries the synthesiser itself
            and a slider per setting, so it is tuned by ear.

Run:  python3 tools/audio/make_previews.py     (numpy, ffmpeg)
"""

import base64
import json
import subprocess

import numpy as np

from common import EVENTS, HERE, SR, game_mix, preview_dir, recipe


def write_ogg(x: np.ndarray, path) -> None:
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
         "-c:a", "libvorbis", "-q:a", "6", str(path)],
        input=x.astype(np.float32).tobytes(), check=True)


def main() -> None:
    out = preview_dir()
    out.mkdir(exist_ok=True)
    uris = {}
    for name, event in EVENTS.items():
        x = game_mix(recipe("arcade", event))
        # The game adds layers and nothing else; only the level is matched.
        path = out / f"arcade_{name}.ogg"
        write_ogg(0.9 * x / np.max(np.abs(x)), path)
        uris[name] = "data:audio/ogg;base64," + base64.b64encode(path.read_bytes()).decode()
    page = (HERE / "tuner.html").read_text()
    assert page.count("/*__SYNTH__*/") == 1 and page.count('"__ARCADE__"') == 1
    page = page.replace("/*__SYNTH__*/", (HERE / "synth.js").read_text())
    page = page.replace('"__ARCADE__"', json.dumps(uris))
    (out / "index.html").write_text(page)
    print("wrote", out / "index.html", f"({len(page) // 1024} KB)")


if __name__ == "__main__":
    main()
