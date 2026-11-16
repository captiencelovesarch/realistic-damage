# Audio rework: what to test

The sounds are in the combined jar `realistic-damage-2.0.0-alpha.4.jar`, in
the Prism instance **"Realistic Damage (dev)"**, together with the field kit
(Nov 14 2026). Everything below was written for `2.0.0-audio.1`, which was the
sounds alone, and applies unchanged.

The plan is [AUDIO_PLAN.md](AUDIO_PLAN.md). I cannot hear, so everything below
is checked by measurement and by tests, and only you can say if it sounds right.

## audio.1: the sound system (phase 1)

**What changed**

- **Sound Set: Arcade / Grounded.** On the settings page (K), under Sound,
  where "Injury Sound: Muted / Visceral" used to be. The Arcade and Grounded
  preset buttons set it too. If you had Visceral on, you start on Grounded.
- **Five sounds now come from the new system** whenever they happen in play:
  a bone breaking, a limb lost, collapsing, a neck snapping, and a
  decapitation. Decapitation was silent before.
- **Two more are in the jar but only play from the command** so far: blood
  landing and a wound opening. Hooking them to real bleeding comes later,
  because a bad bleed makes hundreds of drops and that needs tuning in play.
- **The neck snap is still the old sound** in both sets. It has no new design
  yet.

**The test command.** It plays one sound to you, so you don't need a real
broken leg. Everything after the name is optional, in this order:

```
/rdmg sound <name> [mine|arcade|grounded] [strength 0-1] [self|far] [flesh|dry]
```

Names: `fracture`, `destruction` (limb lost), `collapse`, `neck_crack`,
`decapitation`, `blood_splat`, `wound_open`.

**What to listen for**

1. **Arcade, each one.** Do they sound like they did on the page?
   ```
   /rdmg sound fracture arcade
   /rdmg sound destruction arcade
   /rdmg sound decapitation arcade
   /rdmg sound collapse arcade
   /rdmg sound wound_open arcade
   /rdmg sound blood_splat arcade
   ```
2. **Grounded, each one.** The same six with `grounded`. These are your tuned
   settings from the slider page. Collapse is the second design, with the
   sliders as you had them when I built the jar (tighter body hit, more
   breath, a bit wider).
3. **Loudness, Arcade against Grounded.** Play the same name in both sets, back
   to back. I matched the levels by measurement; switching sets should change
   the character, not the volume.
4. **Loudness, limb against bone.** In Arcade, a lost limb is five Minecraft
   sounds at once, which added up to twice full volume. I halved the stack so
   it doesn't overload. Is a lost limb still loud enough next to a bone break?
5. **Somebody else's.** Add `1 far` to hear it as if it happened six blocks in
   front of you. It should come from that spot and be much quieter:
   ```
   /rdmg sound destruction grounded 1 far
   ```
6. **A skeleton.** Add `1 self dry`. No wet layers: bone only.
   ```
   /rdmg sound destruction arcade 1 self dry
   /rdmg sound fracture grounded 1 self dry
   ```
   Grounded has no skeleton limb-loss sound of its own yet. It uses the dry
   bone break pitched down as a stand-in.
7. **In real play.** With the Sound Set on each setting:
   ```
   /rdmg break @s left_leg
   /rdmg destroy @s left_arm
   /rdmg set @s consciousness 0
   /rdmg behead @e[type=zombie,limit=1,sort=nearest]
   ```
   A real break still screams on top, if Pain Screams is on.

**Not in this jar**

- **Variety.** Each sound is one fixed take, exactly as you approved or tuned
  it, so it is identical every time. The system can rotate several takes and
  wobble the pitch like Minecraft does; that is switched off until you say
  how much you want.
- **Strength.** The command's strength number makes a sound quieter (down to
  60% at 0). Real injuries all play at full strength for now.
- **Everything else in the plan**: heartbeat, breathing, bleeding, falls,
  bodies, voices.
