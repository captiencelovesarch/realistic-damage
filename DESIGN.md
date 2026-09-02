# Realistic Damage — Design (v1.4)

> "Minecraft, but damage is cooler." Vanilla hearts stay authoritative; this mod adds a
> physical layer: locational body trauma, deterministic injuries (Broken / Bleeding /
> Concussion / Burned), a hidden Blood meter, and a hidden Consciousness meter.
> Food remains the universal cure. No RNG in outcomes. Player-only. Nothing permanent.

Target: **Fabric, Minecraft 26.2, Fabric API 0.158.0+26.2, Java 25, Mojang mappings.**

## Four questions, four systems

| System | Question it answers | Storage |
|---|---|---|
| Vanilla HP | "Am I dying?" | vanilla (untouched) |
| Body condition ×6 | "How badly does my body work?" | attachment |
| Blood (0–100) | "Can I keep sustaining myself?" | attachment |
| Consciousness (0–100) | "Can I remain functional right now?" | attachment |

## Architecture

Server-authoritative. All state lives in a `RealisticDamageData` player attachment
(fabric-data-attachment-api), synced to the owning client via custom payloads on
login/respawn/meaningful-change plus a low-rate interpolation channel for Blood and
Consciousness. The client only presents.

Package layout (`dev.captience.realisticdamage`):

- `body/` — `BodyPart`, `BodyState`, `BodyPartData`, `RealisticDamageData`, `BleedWound`,
  `Vitals` (band math for Blood/Consciousness, shared server/client so the warnings and
  the inventory panel can never word the same state differently)
- `damage/` — `DamageCategory`, `DamageProfile`, `DamageProfiles`, `DamageClassifier`,
  `BodyHitResolver`, `EnvironmentalRouter`, `TraumaCalculator`, `DamageHooks`
- `systems/` — `BleedingSystem`, `ConsciousnessSystem`, `HealingSystem`,
  `ImpairmentSystem`, `TickDispatcher`
- `net/` — payload records + `RealisticDamageNetworking`
- `command/` — `RealisticDamageCommands` (debug/admin)
- `config/` — `Balance` (all tuning constants), `ServerConfig`
- `client/` (client source set) — `BodyHud`, `ConsciousnessEffects`, `ClientState`,
  accessibility config screen glue

Pure-math logic (state thresholds, wound stacking, impairment curves, trauma math) is
written MC-type-free where possible so it unit-tests without a Minecraft runtime.

## The damage pipeline

```
vanilla damage happens (armor/enchants/resistance/absorption all vanilla)
  └─ AFTER_DAMAGE hook: post-mitigation amount, source, blocked flag
       ├─ classify → DamageCategory + DamageProfile
       ├─ locate  → BodyHitResolver (projectile pos / melee ray / environmental route)
       ├─ trauma  → TraumaCalculator: condition damage, deterministic break check,
       │            concussion check, localized-armor modifier, secondary limb shock
       ├─ wounds  → BleedingSystem: deterministic wound creation by threshold
       └─ mind    → ConsciousnessSystem: head trauma / blunt / explosion / fall pressure
```

Body parts never soak vanilla damage. Bleed-out HP damage uses a tagged damage type that
the pipeline ignores (recursion safety, spec §51).

## Balance constants (authoritative copy lives in `config/Balance.java`)

Body condition max: HEAD 20, TORSO 20, ARM 13/13, LEG 16/16.
States by % of max: HEALTHY >75, HURT >50, INJURED >25, CRITICAL >0, DISABLED =0.

Trauma multipliers: GENERIC .75, SLASH 1.0, HEAVY_SLASH 1.15, PUNCTURE 1.10,
HEAVY_PUNCTURE 1.20, BLUNT 1.20, HEAVY_BLUNT 1.40, EXPLOSION 1.35, FALL 1.25,
FIRE .60, LAVA 1.0, SYSTEMIC .10.

Head/torso hits leak 10% of localized trauma to each limb. Systemic damage ≈10%
trauma-equivalent spread across regions.

Break thresholds (single qualifying heavy impact): ARM 5.5, LEG 6.5 condition damage.
Accumulated path: qualifying slash/puncture/blunt hits that leave the limb CRITICAL or
DISABLED set Broken. Broken clears when the limb re-enters HEALTHY.

Wound bleed rates (Blood/sec): MINOR .25, MODERATE .6, SEVERE 1.2, CRITICAL 2.0.
Stacking weights sorted desc: 100% / 50% / 25% / 12.5% each after (cap total).
Blood bands: ≥70 stable, 50–70 mild, 30–50 significant (HP ticks begin <30),
15–30 critical, <15 catastrophic; 0 = bleed-out (rapid HP+consciousness drain, death by
ordinary HP loss only).

Consciousness: baseline recovery ~2.5/s modified by head state, concussion, blood,
asphyxia. Head-state caps: HEALTHY 100, HURT ~95, INJURED ~82, CRITICAL ~55,
DISABLED ~25. Collapse at 0; wake at ≥20 with ≥2.5 s minimum unconscious time.
Symptom ladder thresholds: 75 vignette, 65 sensitivity, 55 attack recovery, 45
afterimages, 35 exposure, 32 stumbling, 30 ringing, 25 heartbeat, 22 microsleeps,
20 sprint-loss/sluggishness, 10 heavy everything, 8 feeble (no swing, no mining),
0 collapse. Sensitivity reduction capped 35%. Thresholds are read against
*effective* consciousness, so a concussion (−18) brings the whole ladder forward.

Arm penalty by state: 0 / 5% / 12% / 30% / 50% (smooth-lerped inside bands, +Broken adds
a flat strong penalty, total capped ~65%). Legs: worse leg weighted 70/30 vs better leg;
Disabled leg disables sprint; both-legs-wrecked floor keeps limp speed ≥ ~25% walk.

Totem floor: consciousness ≥60, blood ≥45, bleeding stopped, Disabled parts → 15% floor
(low CRITICAL); Broken/Concussion/Burned persist.

Sleep: consciousness →100 (subject to head cap), Blood +60, repair ~50% of missing
condition, minor wounds cleared.

Healing loop: every vanilla natural-regen event grants a body-repair budget (weighted
toward most-damaged regions), Blood restoration, and wound decay; eating grants an
immediate Blood bump scaled by nutrition+saturation; golden foods multiply strongly.
No regeneration of body condition while starving (hunger 0).

## Added in 1.1

Injury audio is synthesised (`tools/generate_sounds.py`) in two styles, muted and
visceral, and is **routed rather than played as a world sound**: the server sends an
`InjurySoundPayload` to the injured player and to everyone within
`INJURY_SOUND_RADIUS`, and each client plays its own configured style — full volume and
non-positional for your own body, 40% and positional for anybody else's. Style is
presentation, so the server never decides it.

Every damage type the mod inflicts carries the `realisticdamage:internal` tag, and the
recursion guards (spec §51) test that tag rather than listing keys, so a damage type
added later cannot be forgotten in one of two places. Core destruction split into
`skull_crushed` and `chest_crushed` so the two read as different deaths.

`unconsciousAggro` (ServerConfig) governs what notices a downed player. Default
`NEARBY` = prior attackers plus anything within 6 blocks with line of sight; `ALL` is
1.0's behaviour, `NONE` takes you off the menu.

Client presentation gained `lookInertiaIntensity` (split out of the sensitivity
control), `showNumericStats`, `injurySoundVolume` and `visceralInjurySounds`. The
accessibility screen is now an `OptionsSubScreen` with a scrolling, sectioned list.

## Added in 1.4

**Fading out has teeth.** The ladder above 1.4 was almost entirely sensory: at one point
of consciousness a player still swung, mined, placed and ate at close to full speed, then
collapsed between one tick and the next. Four changes, all deterministic:

- **Microsleeps** below 22. A phase accumulator advances by `1/period` each tick and blacks
  the player out on wrap — period 160→60 ticks and duration 4→22 ticks as consciousness
  falls. Server-authoritative (the same action vetoes as unconsciousness) and synced as a
  snapshot flag. Invariant the tests enforce: duration must stay under half the period, or
  it stops being an episode and becomes unconsciousness with the lights flickering.
- **Sluggishness** re-anchored 0.22 @20 / 0.85 @0 (was 0.10/0.30), and no longer bounded by
  `ARM_PENALTY_TOTAL_CAP` — fading is not a limb injury and being filed under one is what
  let it be capped away. Below `SYMPTOM_FEEBLE` (8) attack and mining are refused outright.
- **Stumbling** rotates the client's movement vector up to 42° off the input, from two
  mismatched sines of the world clock. Client-side deliberately: a server-side nudge
  arrives as a rubber-band, and a control that fights you reads as a broken game rather
  than a failing body. `stumbleIntensity` is its own setting because it is the only effect
  in the mod that changes where the player physically ends up.
- **Eating** stalls up to 4 use-ticks per tick (a five-fold meal) but is *never* refused for
  weakness — only while actually absent. `advanceUseStall` returns a count rather than a
  flag, since one stall per tick caps the slowdown at exactly two-fold.

**Falls are keyed to height, not to the health bar.** Post-armour HP was the wrong input:
it is what vanilla decided the boots were worth, so diamond and Feather Falling IV deleted
the event rather than mitigating it. `LivingEntityFallMixin` records drop height, landing
surface and leading foot on the tick the ground arrives; `FallMath` turns those into
trauma, shares and knockdown. All heights are in blocks **beyond the safe fall distance**.

- Trauma = `heightOverSafe × 0.76 × surface`, then the ordinary FALL multiplier and the
  same localized armour reduction every other category gets.
- Secondary shares ramp rather than step: torso 0→30% over 9–16, head 0→22% over 16–30.
- Leading leg takes 50%→68% of the leg share as height rises, chosen from the walk cycle so
  it is reproducible rather than a coin flip.
- `LandingSurface` is a hand-written table, not a hardness lookup — hardness sorts glass
  beside wool and iron beside stone, which is a mineability ordering, not a give-underfoot
  one.
- **Knockdown** (`KNOCKDOWN_MIN_HEIGHT` 7 → `FULL` 22, 12–50 ticks) puts the player prone
  and unsteering, then a 20-tick unsteady tail. Cushioning applies to it too, so hay leaves
  you standing rather than merely hurting less.
- **Lethality compression**: damage above 10 counts for 40%. A 35-block drop becomes
  survivable from full health (~2 hearts, concussed, leg shattered); fifty blocks still
  kills. `CONSCIOUSNESS_IMPACT_SCALE` dropped 3.0→2.0 to compensate, because every other
  term in the consciousness sum rose at once.

Falls send `LandingPayload` instead of `ImpactPayload` — a landing comes from below and
throws the view down, and sharing the generic cue made every fall feel like being punched
by the floor.

## Added in 1.5

**Blood is a readout, not a decoration.** Bleeding used to be four redstone particles every
25 ticks, spawned with zero velocity at the centre of the wounded region. Three separate
failures in one function: severity was encoded only as a count of 1–4 so a scratch and a
severed artery looked identical, the particles had no gravity so nothing ever arrived
anywhere, and — because they spawned at the body's own regions — in first person they were
behind the camera and could not be seen at all. Four layers replace it, all driven from the
wound's real Blood/sec so what you see is what you are losing:

- **Drops** (`BloodDrop`) have gravity, drag and block collision. `Particle.move` reports
  collisions only by side effect, so the contacted face is recovered by comparing intended
  against actual movement. Severity travels in the *velocity* rather than in a custom
  `ParticleOptions`: velocity is already on the wire for every particle, so a spurt looks
  like a spurt on another client for free.
- **Stains** (`BloodStain`, `BloodStains`) are particles that ignore the camera and take a
  fixed rotation onto the surface normal instead. Bounded at 768 with oldest-first
  eviction, and a drop landing within 0.22 blocks of an existing mark *feeds* it rather
  than adding another — without which a stationary player spends the whole budget on one
  square of floor and erases the trail that led there. They dry red→brown, then fade.
- **Bodies** (`BloodBody`, `BloodBodyLayer`) stain per region, so a cut left arm is a bloody
  left arm and a clean right one. Drawn per `ModelPart` with `submitModelPart`, because a
  single tinted pass over the model could only say "this player is bloody".
- **The lens** (`BloodLens`) is the unmissable one, and the only layer that reads bleed
  *rate* rather than blood *level* — so it says "you are losing blood right now", which
  nothing in the mod said before. Head wounds weight 1.0 to the camera, everything else
  0.35: a cut shin should not fog the screen like a cut scalp.

Emission moved entirely to the client (`BloodEmitter`), driven off the body snapshot that
was already synced to every client. It costs *less* on the wire than the packets it
replaced, follows the real rate instead of a fixed timer, and lets an arterial wound throw
its blood on the same beat the player is hearing. The server still sends one thing: the
directional spray when a blow opens a wound, because that is an event with a direction and
the snapshot carries neither. `FLAG_PRESSURE` joined the snapshot for the same reason —
with the client drawing, a compressed wound has to visibly stop pumping.

**The heart can falter.** `bpm = 55 + 110 × distress` with an envelope measured in
*fractions of a beat* had two consequences. The thump was 60 ms at rest and 20 ms at 165
bpm, so approaching death turned the heartbeat into a click track; and since distress was
one smooth scalar, the beat could race but never stumble. `Cardio` fixes both:

- Every decay is in **seconds**, so the shape of a beat cannot depend on how fast they
  arrive. This is the one property `CardioTest` asserts by simulation at two rates.
- **Ectopic beats** above 0.35 distress: one arrives early and weak, the heart pauses longer
  than usual, then the beat ending the pause lands at 1.65×. Chosen by hashing the beat
  index, so it is reproducible without being periodic, and never two in a row.
- **Three characters**, crossfaded by which cause dominates rather than switched: blood loss
  is fast and faint (up to 178, amplitude ×0.55), head trauma is *slow* and enormous (down
  to 38, envelope ×2.4), ordinary injury is the pounding one. The slow one was unreachable
  before and is the more frightening of the two.
- Rate rides the same 90-tick breathing clock the washout overlay uses, so the screen and
  the pulse breathe as one body.

**The screen says what happened, not only that something did.** `Vitals` now exposes the
three distress causes separately instead of collapsing them, and two things read them:

- Colour drain means **blood loss**; warping and ghosting mean the **head**. They used to
  share one `max()`, so bleeding out and being concussed looked identical and neither told
  you which to fix.
- `ImpactPayload` carries the damage category, folded by `ImpactFamily` from seventeen
  categories down to four physical events — cut (lens spatter + smear), pierce (iris snap),
  blow (camera ring + double vision), blast (whiteout + several seconds of ringing).
  Seventeen is the right resolution for deciding what a hit does to a body and far too fine
  for deciding what it does to a screen. Everything else keeps the plain flash, which is the
  honest answer for poison, drowning or falling.

Textures are synthesised by `tools/generate_blood.py`, white with a shaped alpha channel so
the renderer supplies the colour — a stain is drawn wet on the tick it lands and brown four
seconds later from the same sprite, and a texture with the red baked in could only do half
of that.

**Body blood is baked geometry, not re-submitted model parts.** In the current player
model the skin's overlay layer is a *child* of each base part — the hat sits +0.5 off the
head, jacket and sleeves +0.25 — and `submitModelPart` renders children. The first version
of `BloodBodyLayer` submitted `model.head` and friends directly, so the blood was painted
onto the inflated overlay cubes too, and the outermost copy is the one you see: a shell
hovering off the body. The layer now bakes its own overlay-free wide and slim meshes from
vanilla's cube definitions at +0.01, copies each live part's pose per frame, and submits
those — blood on the body, under the clothes.

**Dying is a sequence, not a state change.** `active()` excludes dead players — rightly,
for gameplay effects — which meant death snapped every effect off between one frame and
the next and the death screen arrived looking like a menu. Now a client-side death fade
runs the ending, in one of two styles selected like the muted/visceral sound split
(`realisticDeath`, chosen by the Arcade and Grounded presets, overridable on its own):

- **Cinematic** (Arcade): five seconds, the red pulse, a dim capped well short of black.
  The dramatised version, described below.
- **Realistic** (Grounded): eight seconds, and the senses leave in the right order. No red
  cast — a tunnel that closes to *true black* at 55% of the fade — while the heart keeps
  beating in the dark until 75%, because hearing is the last sense to go. The ringing is
  gone by 85% and the world's audio ducks fully by 70%, so the final stretch before the
  respawn is genuinely nothing: black, and silent. The colour drain runs at double pace so
  it finishes before the blackout closes over it. The death screen's own text and buttons
  draw above all of it, so the way out never disappears with the world.

The cinematic sequence:

- The heart runs *down*: `Cardio.setMoribund` hands the rate over to a collapse from 140
  bpm through and below the living floor to 16, beats widening 9→11→14→17 ticks and each
  landing softer, silent from 60% of the fade — the last stretch of dying happens in
  silence, on purpose. Found and fixed in the process: the beat timing compared elapsed
  ticks against an interval re-derived every tick, so a heart slowing fast enough pushed
  the deadline away faster than the clock chased it and death was silent from the first
  frame. Beats are now a phase accumulator (`phase += 1/interval`), the same integrator
  the microsleep system uses, which can slow but never recede.
- The vignette closes in and loses its red as the heart stops; a flat dim settles late in
  the fade, capped well short of black because the death screen's buttons still have a
  job to do. Dying while already blacked out hands the frozen unconscious fade over to
  the death look rather than brightening on the death frame.
- The greyout chain finishes the colour drain, so the world behind the death screen ends
  grey instead of snapping back to vivid.
- Ringing swells to a peak 40% in and fades with everything else; the world's audio ducks
  toward quiet underneath. Lens blood stays where it landed, drying.
- Respawn resets all of it in one frame — a fresh body gets a fresh heart, and the fade
  must never leak into the next life.

**How hard the death happened decides how fast it ends.** Both styles played the same
wind-down whatever killed you, so a 50-block drop got the same slow heart-fade as bleeding
out in a corner. The server now scores the fatal blow into a violence figure (`DeathMath`)
— falls by their *height*, because 1.4's lethality compression deliberately shrinks
exactly the HP number a damage read would use; everything else by the raw blow against the
whole health bar, raw because armour softens the wound, not how abruptly the world stops.
Violence does two things on the client: compresses the fade (down to 15% of the style's
length — a 50-block landing or a charged-creeper blast is black inside a second) and
pre-ages the dying heart, so an overwhelming death has *no beats at all* — consciousness
ended before the heart did. Continuous rather than an "instant" flag, so two nearly
identical deaths cannot end in visibly different ways.

The wiring earns a note: Fabric's AFTER_DAMAGE is documented not to fire when the damage
kills, so the trauma pipeline never sees a killing blow — the scoring hangs off
ALLOW_DEATH instead, which fires with the fatal source and amount *before* the totem
check. The score is therefore stashed tick-stamped and only sent (one small
`DeathPayload`) from AFTER_DEATH, the first moment the death cannot be taken back: a stash
orphaned by a totem save can never colour a later, calmer death. Deaths the classifier
deliberately ignores — the void, `/kill`, starving — score nothing and keep the slow
ending, which is the right answer for deaths that are not blows.

## Deliberate non-goals

DESTROYED state beyond what 1.0 shipped, medical items, mob anatomy, rescue/carry
mechanics (1.2), numeric HUD stats — the inventory panel may show condition figures on
request, the HUD never does, and Blood/Consciousness are named by band and never
numbered anywhere. TACZ integration is still deferred, but DamageCategory +
BodyHitResolver accept externally-supplied classification/hit-points so an adapter can
slot in later.
