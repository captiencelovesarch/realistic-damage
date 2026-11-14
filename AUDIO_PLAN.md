# Audio rework for 2.0

Every sound in the mod, redone as two complete sets: **Arcade** and **Grounded**.
Plan only. Nothing in the mod has changed yet. `audio-preview/index.html` is
the listening page: six events, each with its Arcade example and a live
Grounded synthesiser with sliders.

## Where audio stands today

- **16 sound files**, all synthesised by `tools/generate_sounds.py` from noise
  bursts and sine waves. That is why it sounds cheap: there is no real material
  in any of them.
- Only **4 events have their own sound in two styles**: bone break, limb
  destroyed, collapse, neck crack ("muted" and "visceral").
- **Everything else borrows a vanilla sound as is**: your heartbeat is the
  Warden's, ringing is a bell, retching is a burp plus a slime, a hit is the
  strong-attack sound, bodies landing use the fall sounds.
- **A lot is silent**: wounds opening, blood landing, arterial spurts,
  decapitation, a severed limb or head hitting the floor, a skull being crushed,
  whiplash, waking up.
- **The screams are TF2 Spy lines.** They are Valve's, so they are left out of
  the public jar. Everyone who downloads the mod hears no screams at all.

## The two sets

| | Arcade | Grounded |
|---|---|---|
| Your definition | Sounds like Minecraft. Not 8-bit, not too high quality. | Higher quality, kind of gruesome. |
| How it is made | **Minecraft's own sounds, layered and re-pitched** at run time, plus a few of our own lo-fi layers where vanilla has nothing. | **Synthesised**, and tuned by ear on a slider page until each one is right. |
| Shape | Short (under 1 s), dry, mono, one clear gesture. Wet is hinted (slime, mud, honey), never lingering. | Longer, wet tails, close-up. Cracks you can count. Blood you can hear land. |
| Your own body | Same sound, not positional. | **Stereo** version: your own injuries are inside your head, so they can be wide. Other people's stay mono and positional. |
| Rule | Never louder, brighter or longer than the vanilla sounds around it. | Never a bass drop (the mistake the 1.5 set made): the low end is a floor under the sound, not the sound. |

**Why Arcade is built from vanilla sounds:** nothing sounds more like Minecraft
than Minecraft. A turtle egg cracking over a bone block breaking is a bone
snap that already belongs in the game. The mod only *names* those sounds and
the player's own game plays them, so no Mojang audio is ever copied into the
jar. A resource pack that changes vanilla sounds changes ours with it, which is
right for this set.

**Why Grounded is synthesised:** your call (13 Nov 2026), after hearing both.
The first synthesised set was "weird as hell"; a set cut from real foley
recordings was "still off"; so it is synthesis, fine-tuned together. That also
keeps the old rule (injury sounds are synthesised, not sampled), keeps the jar
free of anyone else's audio, and means any sound can be changed by moving a
number.

What changed from the weird first set, measured against real recordings
(used as a ruler only, never shipped): real gore is **bright** (half the
energy of a snap or a tear is above 4 kHz), it is **noise** (crackle and grit,
nothing with a pitch), and a snap is **over in 15 ms**. The first set was dark,
slow, and built from pitched "bubbles". The synthesiser (`audio-preview/synth.js`)
is now built from short grains of noise, and bubbles are a slider that starts
at zero.

**How it is tuned:** I cannot hear, so you do the listening. The page has a
slider for everything (how many cracks, how sharp, how wet, how long...) and
replays on every change. "Copy all settings" gives a line of text to paste
back; those numbers become the shipped sound, rendered by the same code.

**Arcade examples: approved as they are (13 Nov 2026).** "The arcade version
should sound as they are in the page. They're fine." So these six recipes are
final, and they are the style every other Arcade recipe follows:

| Event | Vanilla sound file | Pitch | Volume | Delay (ticks) |
|---|---|---|---|---|
| Bone breaks | `mob/turtle/egg/egg_crack4` | 0.85 | 1.00 | 0 |
|  | `block/bone_block/break2` | 1.30 | 0.70 | 0 |
|  | `mob/goat/horn_break1` | 1.15 | 0.35 | 0 |
| Limb destroyed / severed | `mob/goat/horn_break2` | 0.80 | 0.90 | 0 |
|  | `block/bone_block/break1` | 0.90 | 0.80 | 0 |
|  | `block/mud/break4` | 0.80 | 0.80 | 0 |
|  | `mob/slime/small2` | 0.70 | 0.55 | 1 |
|  | `block/mud/break6` | 0.65 | 0.60 | 2 |
| Decapitation | `entity/player/attack/sweep4` | 1.30 | 0.80 | 0 |
|  | `item/trident/pierce2` | 0.90 | 0.80 | 1 |
|  | `block/bone_block/break4` | 1.10 | 0.60 | 1 |
|  | `block/mud/break4` | 0.70 | 0.80 | 1 |
|  | `mob/slime/small1` | 0.60 | 0.50 | 4 |
| Blood landing | `mob/slime/small5` | 1.50 | 0.30 | 0 |
|  | `block/honeyblock/break3` | 1.40 | 0.70 | 0 |
|  | `mob/slime/small3` | 1.70 | 0.22 | 7 |
|  | `block/honeyblock/break4` | 1.55 | 0.60 | 7 |
|  | `mob/slime/small5` | 1.35 | 0.26 | 12 |
|  | `block/honeyblock/break5` | 1.30 | 0.55 | 12 |
| Wound opens | `item/trident/pierce1` | 1.15 | 0.70 | 0 |
|  | `block/netherwart/break5` | 1.00 | 1.00 | 0 |
|  | `mob/slime/small3` | 0.90 | 0.40 | 0 |
| Collapse | `damage/fallbig` | 0.70 | 0.80 | 0 |
|  | `item/armor/equip_leather1` | 0.70 | 1.00 | 0 |
|  | `damage/fallsmall` | 0.60 | 0.90 | 5 |
|  | `step/cloth1` | 0.60 | 1.00 | 5 |
|  | `item/armor/equip_leather4` | 0.60 | 0.80 | 7 |

To sound exactly like the page, a recipe must play *that* file, not a random
one of its family (a vanilla sound event picks, say, any of four bone-block
breaks). So a recipe layer names a file (`minecraft:block/bone_block/break2`),
and the director plays it with a sound instance that carries its own
single-file event. That reads the file from the player's own game; nothing is
copied into the jar, and nothing is added to `sounds.json` or the registry.
**Confirmed in game** (phase 1): a client test plays every recipe through the
real sound engine and checks the files.

One thing the page hid: it set every example to the same peak level, and in
the game a lost limb's five layers add up to twice full scale. The limb and
decapitation recipes carry a `gain` (0.5 and 0.68) that brings the stack back
to full scale without touching the balance between layers.

**Tuned so far (13 Nov 2026).** Five of the six examples are tuned to your ear
and are now the synthesiser's defaults. `audio-preview/` is git-ignored, so the
numbers are kept here too:

```
"bone_break": {"seed":15,"params":{"crackGain":1.15,"crackCount":2,"crackSpread":35,"crackBright":7700,"crackHard":0.2,"creak":0.15,"thudGain":1.2,"thudDepth":75,"thudLen":0.06,"wetGain":0.7,"wetLen":0.4,"wetBright":1900,"wetDensity":0.35,"wetBubble":0,"width":0.3}}
"limb_lost": {"seed":9,"params":{"crackGain":0.1,"crackCount":1,"crackSpread":110,"crackBright":1500,"crackHard":0.05,"creak":0,"thudGain":0.55,"thudDepth":50,"thudLen":0.07,"tearGain":1.15,"tearLen":0.25,"tearRough":0.2,"wetGain":0.2,"wetLen":0.5,"wetBright":2400,"wetDensity":0.85,"wetBubble":0.05,"width":0.7}}
"decapitation": {"seed":6,"params":{"swishGain":0.35,"crackGain":0.8,"crackCount":3,"crackSpread":30,"crackBright":4000,"crackHard":0.6,"creak":0,"thudGain":0.4,"thudDepth":100,"thudLen":0.05,"wetGain":0.55,"wetLen":0.3,"wetBright":2600,"wetDensity":0.65,"wetBubble":0,"sprayGain":0.5,"sprayPulses":0,"sprayLen":0.3,"width":0.7}}
"blood_splat": {"seed":15,"params":{"thudGain":0,"thudDepth":50,"thudLen":0.01,"wetGain":0.75,"wetLen":0.2,"wetBright":2500,"wetDensity":0,"wetBubble":0.75,"width":0}}
"flesh_hit": {"seed":8,"params":{"thudGain":0.35,"thudDepth":115,"thudLen":0.03,"tearGain":0.4,"tearLen":0.1,"tearRough":0.4,"wetGain":0.8,"wetLen":0.2,"wetBright":3000,"wetDensity":0.65,"wetBubble":0,"crackGain":0.5,"crackCount":0,"crackSpread":30,"crackBright":4500,"crackHard":0.6,"creak":0,"width":0.5}}
```

What your choices say about Grounded as a whole, to carry into the other
sounds: thumps deep and loud, few cracks rather than many (two for a break,
almost none for a lost limb, where the tear carries it), narrow stereo, and for
a blood drop a single round "bloop" rather than a spatter.

**Collapse is on its second design.** The first sounded "TOO realistic, like
someone falling on wood": its thump rang for four or five cycles with a 700 Hz
knock on top, which is a floorboard. The second is dead weight on ground that
gives: about one cycle of push that sags in pitch, no knock, a soft start,
several loose arrivals instead of two clean hits, a dull scuff and the breath
going out. You then moved three of its sliders (body hit tighter, 0.04; breath
up to 0.35; stereo out to 0.4), and those are its defaults now. No verdict yet
on whether it is right.

**Where the tools live.** `tools/audio/`: `synth.js` (the synthesiser, and
the tuned settings as its defaults), `tuner.html` and `make_previews.py` (the
slider page, written to the git-ignored `audio-preview/` beside the main
checkout), `build_grounded.py` (renders the Grounded files and recipes into
the mod, level-matched to Arcade). The page reads its Arcade examples from the
mod's own recipe files, so the page plays what the mod plays.

## How it works in the mod

One new client piece, the **sound director**, replaces the scattered
`playSound` calls.

- **Recipes, not files.** Each event in each set is a recipe: a list of layers
  (sound, pitch range, volume range, delay, chance). They live in the assets as
  small JSON files, so they can be retuned without touching code, and a
  resource pack can replace them.
- **Variation.** Every event has 3 to 5 variants and never plays the same one
  twice in a row. Pitch wobbles a little each time, like vanilla.
- **Intensity.** The server already knows how bad a hit was. A hairline
  fracture and a shattered femur use the same recipe at different strengths:
  the heavy layers only join in above a threshold.
- **Material.** Skeletons are dry (bone only, no wet layers), slimes squish,
  the undead are duller. This reuses the blood-profile tags the mobs already
  have.
- **Yours vs theirs.** Yours play non-positional and stay clear while the
  world is muffled (you hear your own bones through your skull). Theirs play
  at their position, quieter. This is how it works today and it stays.
- **Budget.** Blood can produce hundreds of drops. The director caps how many
  small sounds play at once and drops the quietest and farthest first.
- **One setting.** "Injury sounds: muted / visceral" becomes **"Sound set:
  Arcade / Grounded"**. The Arcade and Grounded preset buttons set it, and it
  can still be changed on its own. Per player, like every other effect.
- **Timing.** Minecraft can only delay a sound in whole ticks (0.05 s). Arcade
  recipes are written in ticks. Grounded keeps tight timing inside each file.
- **Test command.** `/rdmg sound <event> [arcade|grounded] [strength]` plays
  any sound on demand, so a playtest does not need a real broken leg.

The server side barely changes: it already sends "this event happened here" and
lets each client choose how it sounds. It gains more event ids and a strength
number.

## The catalogue

About 40 events, more once hit kinds and mob materials are counted. "Today" says what plays now.

### Bones and limbs
| Event | Today | Arcade | Grounded |
|---|---|---|---|
| Bone breaks | synth | egg crack + bone block (preview) | creak, double crack, splinters, a little wet (preview) |
| Limb destroyed | synth | horn break + bone + mud + slime (preview) | crack, tear, long wet tail (preview) |
| Limb severed by a blade | same as destroyed | sweep + pierce + small squish | blade through, one clean crack, spurt |
| Limb torn off by a blast | same as destroyed | lost under the bang, bone + thump | ragged tear, debris, wet patter |
| Skull crushed | limb sound | bone block low + mud | dull crunch, short and final |
| Neck snaps | synth | one dry egg crack, nothing after | one deep crack, then silence |
| Decapitation | none | sweep, pierce, chop, small squish (preview) | blade, chop through bone, three fading spurts (preview) |
| Whiplash wrench | none | leash stretch + small crack | gristle creak, tiny pops |

### Wounds and blood
| Event | Today | Arcade | Grounded |
|---|---|---|---|
| Wound opens: cut | none | pierce + nether wart (preview) | wet slap, short tear (preview) |
| Wound opens: stab / arrow | none | trident pierce | puncture, dull thud |
| Wound opens: blunt | none | strong attack + cloth | meaty thud, no wet |
| Blood lands | none | honey + small slime, high (preview) | droplet with trailing spatter (preview) |
| Arterial spurt | none | squid squirt, short, on the heartbeat | pressured hiss and patter, on the heartbeat |
| Heavy bleed dripping | none | dripstone drips | thick drips, uneven |
| Bleeding stops | none | soft honey step | none (you just notice the quiet) |

### Your body
| Event | Today | Arcade | Grounded |
|---|---|---|---|
| Heartbeat (3 characters, skipped beats) | Warden's heartbeat | Warden's, re-pitched per character | recorded-style lub-dub, chest-deep, stereo |
| Breathing, gasp | synth | kept, made shorter and drier | recorded breaths, close-mic |
| Out of air | none | drown hurt, low | strained, closed-throat pulls |
| Retch | burp + slime | kept (it is funny and it works) | a real heave, wet at the end |
| Ringing / tinnitus | bell + synth tone | kept | kept, with the world's low-pass doing the work |
| Concussion wince | none | short high bell tick | sharp ring spike, then muffle |
| Adrenaline hits, then crashes | none | quick pulse rise | breath catch, heartbeat jumps forward in the mix |
| Fading out / microsleep | ducking only | soft downward "whump" | world drains away, heartbeat alone |
| Collapse | synth | two soft thumps + leather (preview) | soft knees, a loose heavy slump, scuff, breath out (preview, 2nd design) |
| Waking up | none | reverse of the fade | gasp in, the world rushes back |

### Hits, falls and bodies
| Event | Today | Arcade | Grounded |
|---|---|---|---|
| Hit taken, by kind (blunt, edged, pierce, burn, blast) | one vanilla sound | one recipe per kind | one recipe per kind |
| Hard landing, by height and surface | vanilla fall | fall + the block's own sound | body weight, joint crack on bad ones |
| Knocked down | none | fall small + cloth | air knocked out, scrape |
| Ragdoll body hits the ground | vanilla fall | kept, scaled by speed | flesh on the block's material |
| Limb flops, stumble steps, scuffs | block step | kept | heavier, dragging |
| Severed limb lands / head lands and rolls | none | small fall + squish / hollow knock | wet slap / skull knock, roll, settle |
| Mob versions (skeleton, slime, undead...) | none | dry, squishy or dull by material | same |

### Death
| Event | Today | Arcade | Grounded |
|---|---|---|---|
| Death sequence | heartbeat + ducking | readable: last beat, soft drop | heartbeat slows in the dark, last breath, true silence |
| Violent death (blast, long fall) | cut to black | one hard hit, cut | one hit, ringing, cut |
| Riding your own head | none | muffled thumps as it rolls | the same, with the body falling somewhere to one side |

### Voices
| Event | Today | Arcade | Grounded |
|---|---|---|---|
| Yell when a bone breaks | TF2 Spy (not shippable) | short Minecraft-style grunt | a real shout, cut short |
| Scream when a limb goes | TF2 Spy (not shippable) | longer grunt, pitched down | a real scream |
| Pain breath, coughing blood, death rattle | none | none (Arcade stays clean) | yes |

### Field kit
Bandages, tourniquets, splints, brews, special arrows, traps and the rest are
being built in the field-kit session. They get recipes in both sets through
the same director once this lands on `2.0` and is merged over there. They are
not in this plan's phases so the two sessions do not edit the same files.

## Phases

Each phase ends with a jar, a short checklist in `AUDIO_TESTING.md`, and your
verdict before the next one starts.

0. **Direction check (now).** Listen to the twelve previews. Is Arcade the
   right kind of Minecraft? **Yes, approved as heard.** Is Grounded heading the
   right way? Five of six tuned; collapse (second design) awaits your ears.
1. **The director.** Recipes, variants, strength, material, the budget, the
   "Sound set" setting, the test command. The four existing events move over
   first, so the first jar already has new Arcade bone breaks in game.
   **Built 13 Nov 2026 as `2.0.0-audio.1`** on branch `claude/2-0-audio`;
   checklist in [AUDIO_TESTING.md](AUDIO_TESTING.md). Decapitation got its
   sound too (it was silent). What is built but idle: variants and pitch
   wobble (every recipe ships one fixed take, as approved), strength (the
   server sends 1.0 for everything), the budget (only blood drops are small,
   and they are not hooked to bleeding yet).
2. **Arcade, complete.** Every catalogue event as a recipe. Needs no new sound
   files, so it is fast and adds almost nothing to the jar.
3. **Grounded, complete.** The synthesiser grows from the six tuned examples
   to every catalogue event, each tuned on the slider page: bones, limbs,
   wounds and blood first.
4. **Your body.** Heartbeat, breathing, fading, waking, death. The continuous
   sounds, and the ones that most change how the mod feels.
5. **Bodies and the world.** Ragdolls, severed pieces, mob materials.
6. **Voices.**
7. **Mix pass.** Loudness matched against vanilla, what ducks under what,
   subtitles for every sound, VR check, docs and the Modrinth page.

## Rules

- **No Mojang audio in the jar.** Arcade recipes name vanilla sounds; they are
  never copied. The preview folder contains mixed Mojang audio, so it is
  git-ignored and stays on this machine.
- **No Valve audio in the jar.** Same as today.
- **Nobody else's audio in the jar.** Grounded is synthesised. The CC0
  recordings in `audio-preview/sources/` are a measuring stick only.
- **Measured, then listened to.** A script checks every file (loudness, peak,
  low-end share, length) so a bad export cannot slip in. It replaces
  `tools/test_sounds.py`. Numbers catch mistakes; only your ears say if it is
  good.
- **Grounded stays optional per player**, and the subtitles stay neutral
  ("Bone breaks"), so nobody gets gore they did not choose.

## Open decisions (yours)

1. ~~Where do Grounded's sounds come from?~~ **Decided: synthesised**, tuned
   by ear on the slider page.
2. **Whose voice screams?** The public jar has none today. Your own voice
   (recorded once, then processed into variants), CC0 voice recordings, or AI.
   Arcade could use a short grunt in the spirit of the old Minecraft "oof".
3. ~~Does Arcade get any wet sounds at all?~~ **Decided: yes, a hint**, as in
   the approved previews (slime, mud and honey).
4. **Keep the old synthesised set** as a third option, or delete it?
5. **Default set** for new players: Arcade (my pick, it is the safer first
   impression) or Grounded.

## What might not work

- **Layered vanilla sounds can smear.** Three sounds starting in the same tick
  can read as a clatter instead of one crack. The cure is fewer layers and one
  clear leader per recipe; the previews already lean that way.
- **Blood sounds can become noise.** A bad bleed makes a lot of drops. If the
  budget is not tight, it turns into rain. Expect tuning here.
- **Synthesis has a ceiling.** Some sounds (voices, a believable heartbeat)
  may never sound right synthesised. If one will not come good after tuning,
  it is flagged for you rather than shipped weird.
- **Tuning 40 sounds by slider takes your time.** The six examples set the
  style; the rest start from those settings, so most should need a nudge, not
  a rebuild.
- **I cannot hear.** Every sound is built from measurements and from knowing
  what the source material is. You are the only listening test, which is why
  every phase stops for one.
