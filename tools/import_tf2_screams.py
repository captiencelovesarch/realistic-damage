#!/usr/bin/env python3
"""Imports a TF2 class's pain lines as the limb-break screams (1.7).

The one exception to "injury sounds are synthesised, not sampled" (DESIGN.md):
a synthesised scream is a kazoo, and the playtest asked for TF2's by name. So
these are imported from a TF2 install the player owns rather than shipped:
the output directory is gitignored, a clean checkout builds without screams
(the events stay registered and simply play nothing), and nothing of Valve's
ever enters version control.

Two lines per class, picked for what they are in TF2 itself:

  scream_sharp   <- vo/<class>_painsevere*.mp3       a short yell; a bone goes
  scream_severe  <- vo/<class>_paincrticialdeath*.mp3  (Valve's spelling) the
                                                      long scream; a limb is lost

Sources, first that works:
  --from DIR      a directory of already-extracted .mp3/.wav files
  TF2's VPK       tf/tf2_sound_vo_english_dir.vpk (needs `pip install vpk`)

Every clip is converted to mono Ogg Vorbis (Minecraft only positions mono),
loudness-matched to the mod's visceral destruction cue so a scream sits beside
the crack rather than drowning it, and given a short fade so a cut-off line
never ends in a click. sounds.json's two scream events are rewritten to list
exactly the files written.

Run from the repo root:
  python3 tools/import_tf2_screams.py --class spy --from ~/Projects/balaclava/build/raw/sounds
Requires ffmpeg on PATH.
"""

import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ASSETS = Path("src/main/resources/assets/realisticdamage")
OUT = ASSETS / "sounds" / "scream"
SOUNDS_JSON = ASSETS / "sounds.json"
TF2 = Path.home() / ".local/share/Steam/steamapps/common/Team Fortress 2/tf"
CLASSES = ("scout", "soldier", "pyro", "demoman", "heavy", "engineer", "medic", "sniper", "spy")
KINDS = {
    "scream_sharp": re.compile(r"_painsevere(\d+)\.(mp3|wav)$", re.I),
    "scream_severe": re.compile(r"_paincrticialdeath(\d+)\.(mp3|wav)$", re.I),
}
# Measured off destruction_visceral.ogg (ebur128): -21.5 LUFS integrated. A
# voice reads louder than a crack at the same loudness, so a touch under it.
TARGET_LUFS = -22.0


def from_directory(directory, cls):
    found = {kind: [] for kind in KINDS}
    for path in sorted(Path(directory).expanduser().iterdir()):
        name = path.name.lower()
        if not name.startswith(cls + "_"):
            continue
        for kind, pattern in KINDS.items():
            match = pattern.search(name)
            if match:
                found[kind].append((int(match.group(1)), path.read_bytes(), path.suffix.lower()))
    return found


def from_vpk(cls):
    try:
        import vpk
    except ImportError:
        return None
    pak_path = TF2 / "tf2_sound_vo_english_dir.vpk"
    if not pak_path.exists():
        return None

    class Pak(vpk.VPK):
        # The library mangles multi-underscore archive names
        # (tf2_sound_vo_english_dir.vpk -> tf2_sound_vo__000.vpk); same fix as hardhat's extract.py.
        def _make_vpkfile_path(self, metadata):
            return str(TF2 / pak_path.name.replace("_dir.vpk", f"_{metadata['archive_index']:03d}.vpk"))

    pak = Pak(str(pak_path))
    found = {kind: [] for kind in KINDS}
    for entry in pak:
        name = entry.lower()
        if not name.startswith(f"sound/vo/{cls}_"):
            continue
        for kind, pattern in KINDS.items():
            match = pattern.search(name)
            if match:
                found[kind].append((int(match.group(1)), pak[entry].read(), Path(name).suffix))
    return found


def convert(data, suffix, target):
    with tempfile.NamedTemporaryFile(suffix=suffix) as source:
        source.write(data)
        source.flush()
        duration = float(subprocess.check_output([
            "ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", source.name,
        ]).decode().strip())
        fade = min(0.08, duration * 0.1)
        subprocess.run([
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", source.name,
            "-af", f"loudnorm=I={TARGET_LUFS}:TP=-1.5:LRA=11,"
                   f"afade=t=out:st={max(0.0, duration - fade):.3f}:d={fade:.3f}",
            "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", str(target),
        ], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--class", dest="cls", default="spy", choices=CLASSES)
    parser.add_argument("--from", dest="source", help="directory of extracted TF2 voice files")
    args = parser.parse_args()
    if not ASSETS.exists():
        sys.exit(f"run from the repo root: {ASSETS} not found")
    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg is required")

    found = from_directory(args.source, args.cls) if args.source else from_vpk(args.cls)
    if found is None:
        sys.exit("no source: TF2's VPK is not installed (or `pip install vpk` is missing); pass --from DIR")
    for kind, clips in found.items():
        if not clips:
            sys.exit(f"no {kind} lines for {args.cls} in that source")

    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    events = json.loads(SOUNDS_JSON.read_text())
    for kind, clips in found.items():
        names = []
        for number, data, suffix in sorted(clips):
            stem = f"{args.cls}_{kind.removeprefix('scream_')}_{number:02d}"
            convert(data, suffix, OUT / f"{stem}.ogg")
            names.append(f"realisticdamage:scream/{stem}")
            print(f"  {kind}: {stem}.ogg")
        events[kind] = {"subtitle": "realisticdamage.subtitle.scream", "sounds": names}
    SOUNDS_JSON.write_text(json.dumps(events, indent="\t") + "\n")
    print(f"wrote {sum(len(c) for c in found.values())} clips for the {args.cls}; sounds.json updated")


if __name__ == "__main__":
    main()
