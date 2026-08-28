# Realistic Damage: roadmap to 1.5

Where 1.0 landed and what comes next. Each release has one theme, because
shipping a coherent idea beats shipping a pile of options.

Ordering principle: fix what exists before adding what doesn't, and never add a
system that undermines "food is the cure".

## Shipped in 1.0

Six body regions with real hit location, deterministic injuries (Broken,
Bleeding, Concussion, Burned), hidden Blood and Consciousness, limb destruction
with permanent consequences, food-powered recovery, and a sensory layer that
does genuine colour drain, world warping, screen blur and low-pass audio rather
than imitating them with overlays.

Limb destruction was originally scoped for a later version and got pulled into
1.0 during playtesting. That leaves the "permanence" ground partly covered
already, which is why 1.1 to 1.5 below lean toward depth over new damage types.

## 1.1: Sharpening

The unglamorous release. Everything here is a known rough edge, several of them
found in play rather than in tests.

- **Verify the inventory hover panel.** The gametest harness cannot position a
  cursor, so the hover path shipped untested. It needs a manual pass, and
  ideally a client gametest that fakes a mouse position.
- **Split "Look Slowdown"** into separate sensitivity and inertia sliders. They
  are one control today, so wanting sloppy aim without slow aim is impossible.
- **Optional numeric stats** in the inventory panel, off by default. The
  no-numbers rule serves the HUD well; on a dedicated detail screen it mostly
  gets in the way, and `/rdmg info` being admin-only is an awkward workaround.
- **Aggro tuning.** Unconscious players currently attract any hostile with line
  of sight. The gentler reading, only holding mobs that were already attacking,
  should at least be a config option.
- **Sound pass.** Fracture, destruction and collapse all borrow vanilla sounds.
  Purpose-made audio would carry far more weight than any of the visuals still
  left to polish.
- **Death messages** for every mod-caused death, and a pass over injury
  messages for tone consistency.

## 1.2: Rescue

The single most requested thing the original design deferred, and the one that
changes the multiplayer game most.

- **Drag and carry** unconscious players. Someone has to be able to haul you out
  of the fire.
- **Feed and treat teammates**: apply food or a golden apple to someone who is
  unconscious and cannot use their own hands.
- **Shared visibility**: optionally see a teammate's body state when close, so
  triage is possible without voice chat.
- **Bleeding pressure**: hold-to-apply pressure on a wound to slow it, using no
  item at all. Keeps the no-medical-items rule intact while giving a companion
  something to actually do.

Risk to watch: this is the first feature that makes being downed *survivable by
design*, so bleed-out and consciousness timings will need retuning alongside it.

## 1.3: Consequence

Deepen what injuries mean rather than adding more of them.

- **Long-term injuries**: an optional hardcore mode where the worst injuries
  heal to a permanently reduced ceiling rather than to full.
- **Explosive dismemberment**: TNT and creepers able to destroy limbs directly
  at extreme trauma, instead of only finishing off an already-Disabled one.
- **Scarring**: purely cosmetic marks on the body panel for regions that have
  been destroyed or broken before, so your character carries a history.
- **Injury-aware mob AI**: hostiles preferring targets that are visibly limping
  or bleeding. Predators pick off the wounded.

## 1.4: Anatomy for everyone

Mobs get bodies. Deliberately late, because it doubles the surface area of
every system and needs 1.0 to 1.3 to be stable first.

- **Mob body regions**, starting with humanoids where the existing hitbox
  geometry transfers directly.
- **Headshots on mobs** that impair rather than simply damaging, mirroring the
  player rules so the mod stays symmetric.
- **Crippling**: hobbled mobs move slower, disarmed ones stop using weapons.
- **Performance work**: per-entity state at mob scale needs pooling and a much
  cheaper tick than the per-player path gets away with.

## 1.5: Interoperability

Make the mod a platform rather than an island.

- **Public API** for other mods to classify damage, supply exact impact points,
  and read body state, built on the existing `DamageCategory` and
  `BodyHitResolver` seams that were designed for this from the start.
- **TACZ and firearm mods**: ballistic damage as heavy puncture with exact hit
  coordinates, which the pipeline already accepts.
- **Data-driven profiles**: damage classification from datapacks so servers can
  retune weapon behaviour without touching code.
- **Compatibility passes** against the big combat and armour mods.

## Deliberately not planned

A medical crafting tree, a wound-management screen, surgery, infection,
splints, or anything else that answers an injury with an item rather than with
food and time. The spec was right the first time: the danger should be whether
you have enough food and enough time, not whether you packed the right supply.
