# Modrinth listing — Realistic Damage

Everything to paste into Modrinth, in the order the site asks for it.

## 1. Create project (modrinth.com → "+" → New project)

| Field | Value |
|---|---|
| Name | `Realistic Damage` |
| URL | `realistic-damage` (free as of Oct 21 2026) |
| Visibility | Public (it goes into review first either way) |
| Summary | `Your body takes the damage too. Hit location, bleeding, broken and lost limbs, concussions and blacking out — while vanilla hearts stay in charge of life and death.` |

## 2. Settings → General

- **Icon:** `publish/icon-512.png`
- **Environment:** client and server, required on both (singleplayer just works; a server needs it, and every player joining it does too)

## 3. Settings → Tags

- **Categories:** Game Mechanics, Adventure, Mobs
- (Loader and Minecraft version come from the uploaded file.)

## 4. Settings → License

- `CC0-1.0`

## 5. Settings → Links

- **Donation:** your Ko-fi page (platform: Ko-fi) — see `KOFI.md`
- Source / issues / wiki / Discord: leave blank for now

## 6. Description (paste everything between the lines)

---

**Minecraft, but damage is cooler.**

Vanilla damage is a number going down. Realistic Damage adds a body underneath it — where you got hit, how badly that part of you works now, whether you're bleeding, and whether you can stay conscious — while the vanilla heart bar stays completely in charge of life and death.

> "I survived the fall, but both legs are wrecked."
> "I still had seven hearts, but those arrows made me black out."
> "The creeper didn't kill me, but I was concussed and could barely fight."

⚠️ **Early access.** This is an alpha of 1.7. It's played and tested, but expect rough edges, and report anything weird. Contains blood and dismemberment; every gore effect has its own slider and can be turned off.

## What it adds

**Six body regions.** Head, torso, both arms and both legs each wear down from Healthy to Disabled. A hurt arm mines and fights slower, hurt legs slow you down and eventually cost you your sprint.

**Real hit location.** Arrows use their actual impact point; melee traces the attacker's swing. What you hit is what you damage, left versus right included.

**Bleeding and consciousness.** Two hidden meters. Cuts and punctures bleed, and low blood pulls you toward blacking out. Head trauma can knock you out with most of your hearts intact.

**Going under is a slide, not a switch.** As consciousness fails, colour drains out of the world, the view warps and doubles, your aim goes slippery, audio dulls behind a ringing tone and a heartbeat, and you black out in short episodes that get longer the deeper you go.

**Falls land like falls.** A long drop shatters the leg you landed on and rattles your skull, even in good armour. Hay, leaves and snow genuinely cushion. You survive drops that used to kill you, concussed and limping, which is usually worse.

**Limbs can be lost.** Keep hitting a disabled limb, or stand too close to a creeper, and it's gone. A lost arm means one-handed everything, lost legs mean a permanent limp or a crawl. The stump shows on your model and in first person, and the limb lands in the world and bleeds out.

**Blood you can see.** Drops fall and mark what they hit, so you leave a trail and a pool. Bad wounds spurt in time with your pulse, blood soaks into the limb and dries there, and it gets on the camera.

**Mobs have bodies too.** Zombies, skeletons, pillagers and every other mob take regional trauma. They limp, bleed out in their own colour, lose limbs, stop shooting without a drawing arm, and collapse.

**Afflictions.** Drowning takes your mind before your hearts, blasts deafen you, concussions come in grades you play around, a limp arm loses its grip, and the last two hearts give you a burst of adrenaline, then a crash.

**Deaths that look like deaths.** The camera stays with your body as it falls, other players see a body thrown and tumbling instead of vanilla's tip-over, and blasts that kill take you apart.

**Food is the cure.** No bandages, no splints, no medical crafting. Eating, regeneration, sleep, golden apples and potions heal everything. The danger is whether you have enough food and time before things spiral.

**Rescue your friends.** Sneak-use a downed player to drag them out of danger, hold use to press a wound shut, and feed someone who can't use their own hands.

## Options

Press **K** (or use Mod Menu) for the settings screen. Every sensory effect has its own slider, and one button turns them all off. Two presets: **Arcade** (limb loss, readable effects) and **Grounded** (no limb loss, full blackouts, everything at full strength).

Server owners get `config/realisticdamage-server.json` to scale or switch off any system: bleeding, broken limbs, consciousness, limb loss, mob anatomy, long-term injuries, and more.

Something to practise on: `/summon minecraft:mannequin` has a full body that bleeds, breaks and loses limbs.

## Compatibility

- **Required:** Fabric API
- **Optional:** Mod Menu, Vivecraft (full VR support: per-eye effects, haptics, stumps in your hands)
- Install on both the client and the server.

## Known issues in this alpha

- The **Pain Screams** setting does nothing yet; scream audio isn't included in this build.

## Support the mod

Realistic Damage is free and always will be. If you're having fun with it, a coffee on Ko-fi keeps the updates coming. ☕

---

## 7. Gallery (upload from `publish/gallery/`, in order)

| File | Title | Description |
|---|---|---|
| 01-bleeding-out.png | Bleeding out | A wound you can see from across the field. |
| 02-blood-lands.png | Blood lands | Drops fall, splash and pool where they hit. |
| 03-colour-drains.png | Colour drains | Losing blood drains the colour out of the world. |
| 04-everything-goes-black.png | Everything goes black | Blacking out with hearts to spare. |
| 05-multiplayer.png | Trouble in multiplayer | Everyone can see who's hurt. |
| 06-leg-stump.png | Lost a leg | Limbs stay lost, on your model and in first person. |
| 07-lost-arm.png | Lost an arm | One-handed from here on. |
| 08-mobs-bleed.png | Mobs bleed too | Every mob has a body, and bleeds its own colour. |
| 09-body-panel.png | The body panel | Open your inventory to check the damage. |

Mark **01** as the featured image.

## 8. Versions → Upload a version

| Field | Value |
|---|---|
| File | `publish/realistic-damage-1.7.0-alpha.13.jar`: the clean build, with no TF2 audio. NOT the one in `build/libs`, which has the screams in it. |
| Version name | `1.7.0 alpha 13` |
| Version number | `1.7.0-alpha.13` |
| Release channel | **Alpha** |
| Loaders | Fabric |
| Game versions | 26.2 |
| Dependencies | Fabric API: **required**. Mod Menu: optional. Vivecraft: optional. |

**Changelog:**

```
First release on Modrinth: an early-access alpha of 1.7.

New since 1.6:
- Every mob has a body: limping, bleeding in its own colour, losing limbs, collapsing
- The death camera: stay with your body as it falls
- Deaths by kind: running deaths stumble, blasts take you apart, other players see bodies fly and land
- Afflictions: drowning takes your mind, blasts deafen, graded concussions, limp-arm grip loss, adrenaline
- Full Vivecraft support
- The vanilla mannequin is your practice dummy

Pain screams are not included in this build.
```
