# Realistic Damage — Design (v1.7, in progress)

> "Minecraft, but damage is cooler." Vanilla hearts stay authoritative; this mod adds a
> physical layer: locational body trauma, deterministic injuries (Broken / Bleeding /
> Concussion / Burned), a hidden Blood meter, and a hidden Consciousness meter.
> Food remains the universal cure. No RNG in outcomes. Player-only. Nothing permanent
> unless a server deliberately enables the long-term-injury rule.

Target: **Fabric, Minecraft 26.2, Fabric API 0.158.0+26.2, Java 25, Mojang mappings.**

## Four questions, four systems

| System | Question it answers | Storage |
|---|---|---|
| Vanilla HP | "Am I dying?" | vanilla (untouched) |
| Body condition ×6 | "How badly does my body work?" | attachment |
| Blood (0–100) | "Can I keep sustaining myself?" | attachment |
| Consciousness (0–100) | "Can I remain functional right now?" | attachment |

## Architecture

Server-authoritative. Mutable state lives in a persistent `RealisticDamageData` player
attachment (fabric-data-attachment-api) that is never synced. A separate, immutable and
quantized `BodySnapshot` attachment is equality-deduplicated and synced to every tracking
client; remote clients need it for blood, scars, poses and triage. One-shot impact, landing,
sound, death and rescue events use small custom payloads. The client only presents.

Package layout (`dev.captience.realisticdamage`):

- `body/` — `BodyPart`, `BodyState`, `BodyPartData`, `RealisticDamageData`, `BleedWound`,
  `Vitals` (band math for Blood/Consciousness, shared server/client so the warnings and
  the inventory panel can never word the same state differently)
- `damage/` — `DamageCategory`, `DamageProfile`, `DamageProfiles`, `DamageClassifier`,
  `BodyHitResolver`, `EnvironmentalRouter`, `TraumaCalculator`, `DamageHooks`
- `systems/` — `BleedingSystem`, `ConsciousnessSystem`, `HealingSystem`,
  `ImpairmentSystem`, `TickDispatcher`
- `net/` — the versioned `BodySnapshot` wire record and one-shot payload records
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

**The one sampled exception (1.7): screams.** A synthesised scream is a kazoo, and the
playtest asked for TF2's by name, so a player's yell on a fracture and scream on a lost
limb are TF2 pain lines — imported from the player's own TF2 install by
`tools/import_tf2_screams.py` into a gitignored directory, never committed. A clean
checkout builds without them; the events stay registered and simply play nothing.
Players only, never the unconscious or the dead, never a skull, one voice at a time.

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
of that. (Since 1.7 the particle sprites are a hair off white, 255/251/248: Complementary
Reimagined treats a *grayscale* particle tinted red as redstone dust and draws it at full
emission. `BloodPalette.particle` divides the cast back out.)

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

## Added in 1.6

**The worst injury can become the best a region ever gets again.** Long-term injuries are
a server rule, off by default, expressed as three bands rather than a hidden condition
number. A newly Broken limb, any region newly driven to Disabled, or a newly Destroyed limb
costs that region one rung per damage event. Limbs may descend to Critical; head and torso
stop at Hurt, because a head pinned any lower would make the consciousness symptom ladder
permanent rather than making the injury meaningful.

The ceiling belongs to `BodyPartData`, beside the condition it bounds. `fraction()` still
uses the part's original maximum — a capped leg at its new best is still a visibly and
mechanically injured leg — while `heal`, `setCondition` and `missing` use the effective
maximum so every existing recovery route stops naturally instead of spending forever on a
missing budget. Persistence restores the ceiling before condition, the snapshot packs two
bits per region under a new versioned attachment id, and the HUD and inventory panel draw
the state boundary, never its underlying value. Sleep and totems cannot move it. Death
starts a fresh body at each ceiling while the mode is enabled; switching the mode off stops
new rungs and lets death clear the old ones. `/rdmg ceiling` is the explicit admin escape.

**Explosions are judged as explosions, not as the health bar's remainder.** A
`ServerExplosion` mixin captures centre, radius-derived damage and exposure for the victim;
the ordinary routing and the direct-destruction check consume the same nearest-region share.
Only the nearest limb is eligible, and only when its post-local-armour blast trauma reaches
45% of the limb's original condition bar. That threshold leaves a survivable window for TNT
and creepers without letting a wither-sized remainder take four limbs at once. The feature
is nested under both destruction toggles, so Grounded remains a complete refusal of the
Destroyed state.

A qualifying blast goes through `DestructionLogic.destroy`, not a parallel route. It opens
a fixed Critical stump wound, records the scar, hides the limb, applies the hand or movement
penalty, plays the destruction sound and leaves the same internal-tagged core-death path in
charge. Losing the limb now also emits 36 deterministic drops — 1.8 times an ordinary
Critical opening — in a wider, downward-biased cone. That is the 1.6 severance mark; a
flying limb is deferred to the entity work in 1.7.

**History is state, but only presentation reads it.** Every part stores one of `NONE`,
`BROKEN`, `DESTROYED`, raised inside the setters that own those events so commands and future
damage paths cannot forget. It persists, syncs to every tracking client and follows a player
through death even when long-term injuries are off. The body panel names the rung; an
overlay-free `BloodStainLayer` draws it on the actual skin rather than on the inflated hat,
jacket and sleeves. A lost limb has nowhere to render in that life, so its Destroyed mark is
visible on the next body. Healing, impairment and prey scoring never read scars. The client
has a dedicated scar toggle; the sensory panic button leaves informational body markings
alone, as it does the body diagram and injury icons.

**A limp is one definition on both sides of the network.** `LimpMath` turns destroyed,
broken and condition band into ordered rungs. The humanoid-model hook carries an injured arm
and turns out a ruined leg while scaling the step vanilla already calculated, which keeps
armour on the same pose and avoids a second walk formula drifting from Mojang's. A lost limb
is not posed — its absence is the louder cue.

`PreyMath` reads those same rungs. At `Mob.setTarget`, hostiles may substitute a candidate
whose visible injury score clears the current target plus a switching margin: down, missing
leg, fracture, banded limp, then running wound. Revenge supplies a stronger hold; equal
candidates break by distance and then entity id. Scars, Blood, Consciousness and MINOR
wounds are excluded. Scores happen before range checks and line of sight happens last, so a
server of healthy players pays no raycasts. Brain-driven hostiles that bypass
`Mob.setTarget` remain outside the rule rather than being half-supported.

**Wall blood moves, without becoming world state.** A bleeding region within the local
player's reconciled contact envelope transfers a bounded share of its visible flow to the
actual collision face. A large, wet vertical mark advances a deterministic phase and sheds
a physics drop from its lower edge, paying radius each time and slowing as it dries. Both
paths reuse the bounded stain field and disappear with the client particles. Persistent
per-face block blood would be a different storage system and remains deliberately deferred.

## Added in 1.6.1

Four playtest bugs and one presentation system, all client-side. Nothing here touches the
server, the save, or the wire.

**Death starts from where the ladder left off.** The dying pass drew only its own look, and
its own look begins at nothing — vignette at zero, no washout, no ghost — while the living
pass had drawn a full ladder one frame earlier. A player at consciousness five therefore
saw the screen *clear* on the frame they died and then dim again from scratch. Only the
unconscious blackout was carried over, and that on a linear crossfade against the death
fade, which for the cinematic ending (dim capped at 110/255) still meant a blacked-out
player watched the world come back before it went. `ConsciousnessEffects` now captures the
last living frame's vignette, washout and blackout on the first dying frame and holds them
for the whole sequence as a floor; the death look draws only the part of its vignette that
exceeds the floor, and its darkness is the greater of the two. The one rule: nothing on the
death screen may be lighter than the frame before it. The afterimage and the camera pose
freeze with it. Verified by `DeathClientGameTest`, which samples the three readouts across
a whole low-consciousness death and fails on any drop.

**Afterimages are afterimages.** What shipped under that name from 1.0 was vanilla's nausea
texture, tinted, scaled up a few percent and pulsed. It contained no previous frame — the
HUD has no access to one — so it was a vignette with a heartbeat, and the report that
afterimages "still don't work" was correct. `Afterimage` is a post chain on the world
render target with a *persistent* internal target (the 26.2 chain format allows one):
each frame it writes `mix(now, history, keep)` to both the screen and the history, so what
you saw a moment ago trails behind what you see now and decays exponentially. Anything
that moves leaves itself behind — you, a mob, your own hand — which is what seeing double
when dazed actually looks like. Vanilla bakes a pass's uniforms once from the JSON, so the
mod swaps its own writable buffer into the pass's uniform map (`PostPassAccessor`) and
rewrites the retention every frame, raised to the power of the real frame delta so the trail
lasts the same fraction of a second at any frame rate. It runs before the greyout so the
history holds full-colour frames, reseeds itself after being off or after a resize rather
than blending a stale frame in, and is gated on `framesProcessed` in the HUD test rather
than on the strength alone. The concussion floor, the 45→18 curve and the post-blow
doubling all feed it, behind the one Afterimages slider.

**The arm you are looking down at bleeds.** `BloodBodyLayer` is a layer on the player
renderer; the first-person hand is not drawn through those layers — `ItemInHandRenderer`
asks `AvatarRenderer.renderHand` for the bare arm part and nothing else. Everyone else saw
your bloody arm; you saw a clean sleeve. `FirstPersonBlood` injects at the tail of that
method and paints the same blood and scar shells from the same baked meshes through the
same `paint` call the layer uses, so the two views cannot disagree.

**Blood disperses in water.** A drop had gravity, drag and block collision and nothing else,
so water was air to it: it fell through, stopped on the pool floor and laid a stain there.
Now the tick checks the fluid first. Lava burns the drop off with no mark; water turns it
into a cloud — nearly all of its speed gone at once, a slow sink, growing fast then barely
at all, thinning the whole way, gone in one to two seconds scaled by the drop's size, and
never settling, splashing or staining. The stain field refuses a landing point that is
inside water, which catches the two callers that never fall (contact transfer from a wading
body, and a wall run shedding into a pool).

**The camera moves as a picture, not as an aim.** Everything the mod did to the camera
before went through `Entity.turn`, which moves the crosshair — right for a head being
knocked aside, wrong for weight, because a rotation of the aim is capped by how far a player
will tolerate losing their target. Vanilla's own hurt tilt is a roll on the view matrix and
never on the player, and `CameraMotion` rides the same hook (`GameRenderer.bobHurt`), so it
reaches the world and the first-person hand together and leaves the aim exactly where it
was. Two things live there:

- **The impact spring.** Pitch, yaw, roll and a small sideways/downward shove of the eye,
  each a damped spring (natural period about seven ticks, damping ratio near 0.28) kicked
  by a blow, a landing or a blast and integrated per frame with substeps. A spring rather
  than a scripted curve because it composes: two hits a quarter-second apart add, a hit
  landing on a ringing head overshoots further. Blows roll and yaw away from the attacker
  and snap the chin up; cuts roll less; a stab flinches the body down and in; a landing
  drops the eye and drives the chin down, replacing half of what used to be aim slam; a
  blast adds a fast decaying multi-sine shake. As consciousness fails the damping falls
  away, so a head that cannot steady itself keeps wobbling. Scaled by Impact Shock and by
  vanilla's Damage Tilt accessibility slider both.
- **The sway.** Below the middle of the ladder the horizon tilts: two slow, mutually prime
  rolls, a slower pitch, a heave on the shared breath clock, a lateral drift of the eye,
  growing quadratically toward collapse. A microsleep drops the head as the blackout closes
  and lifts it as it opens, so you *come to* with the world swinging back; unconsciousness
  slumps it over. Its own slider, **Head Sway**, on by default at a restrained level and
  full in Grounded, because it is the answer to a specific question — how to make a player
  feel the camera slipping without taking the camera away — and a player who cannot stand
  their aim being touched can still have this. Deterministic, frozen while dying.

## Added in 1.6.2

The camera brainstorm, answered in full; the stump the body was missing; and the limb the
1.7 roadmap had promised, pulled forward the way limb destruction was pulled into 1.0.

**Four more ways to lose the camera, all approved.** Camera Drift — the one effect that
moves the *aim* — is now on at 35% by default rather than Grounded-only; an existing file's
zero is raised once by a seeded migration unless the whole layer was silenced. The impact
spring is under-damped (ratio near 0.20) with larger impulses, so a blow visibly overshoots
two or three times at any health. A **heavy head** (its own slider, being the most
motion-sickness-prone thing here) puts a share of every fast turn into a spring that trails
the aim and catches up late, so the world swings and settles behind the crosshair — Look
Drift's view-only twin. And a **drunk walk** under Head Sway rolls and dips the view on each
footstep near collapse, unevenly, riding the walk cycle vanilla already animates.

**Stumps.** A destroyed limb used to be an absence — `PlayerModelMixin` hid the part and
nothing replaced it. `LimbMeshes` now bakes the upper five pixels of each limb from vanilla's
cube definitions, a thin cut-face cube on a synthesised cross-section texture
(`tools/generate_stump.py`), and a stump-length blood shell, and `BloodBodyLayer` submits
the three posed off the hidden vanilla part, which `setupAnim` still rotates — so the stump
swings with the walk. In first person vanilla positions the arm so the shoulder sits behind
the camera, which is where a five-pixel stump would be, so it is raised by the length it
lost: the cut end sits where the hand was, the player holding up what is left. The
destroyed-arm hand path is no longer cancelled outright; it is routed down vanilla's own
empty-hand branch, where the arm submit is swapped for the stump.

**The limb itself.** `SeveredLimb` is an entity thrown from `DestructionLogic.destroy` — the
same choke point as the wound, the scar and the sound, so no way of losing a limb can forget
it. Not saved (`noSave`), not summonable, not pickable, pushable or collidable: you walk
through it, no mob can see it (it is not a `LivingEntity`, and `PreyMath` reads bodies), and
it holds nothing. Blasts throw it along the blast radial at 17 b/s, blows toss it along the
swing at 6 b/s, a limb ground down or taken by command tips off to the side it was on and
drops there (straight down put it inside the player's own hitbox, where it read as the leg
still being there); the scatter is
`BloodFlow`'s hash seeded from victim, region and tick, so two clients see one flight. It
falls, bounces once low, stops dead rather than sliding, sinks in water, and burns away in
fire or lava over 24 ticks. It bleeds a critical wound's trickle from the client emitter,
running dry over 45 s, and is gone at 60 s, fading over the last two. The wire carries the
region, the owner's UUID (for the skin, resolved client-side because the server does not
know an arm width), an age once a second so a late joiner fades it on time, and burn
progress. `SeveredLimbRenderer` draws the piece below the cut — the lower seven pixels, so the
stump on the body and the limb on the floor add up to one limb — in the owner's skin plus
the skin's second layer, the cut face at the cut end, and a drying blood shell — all from
`LimbMeshes`, so the limb on the floor is the limb that was on the body. The drunk walk's
phase is the model's own `0.6662` scaling of the walk counter; the first build used pi and
shook the view nine times a second. Server rule
`spawnSeveredLimbs`, default on, nested under destruction by construction.

**Three more playtest fixes.** The blackout is drawn by a HUD element registered last of
all, so going dark in Grounded covers the hotbar, the hearts, the crosshair and the body
diagram instead of leaving them crisp over a black world; screens still draw above it, so
the death screen's buttons stay reachable. The eating stall was applied as whole extra
ticks per tick, and at a factor of 1.0 or more that cancels the countdown outright — below
consciousness 20 food could not be finished until recovery lifted you back over the line,
"about a minute" in play. `ImpairmentMath.useStallPerTick` now turns the curve's duration
factor into `1 - 1/factor` stalled ticks per tick, always under one, and caps the whole
meal at `EAT_MAX_TICKS` (five seconds). And the blur no longer snaps off on the death
frame: the death screen draws no blur of its own, so the frozen deep-band haze keeps
requesting vanilla's gaussian through the fade; the afterimage never stopped, and the
death test now asserts both.

## Added in 1.7 (in progress): Anatomy for everyone

**One pipeline, two kinds of body.** Every server system was typed against `ServerPlayer`,
and almost none of them needed one: a bounding box, a body rotation, equipment slots,
attributes and `hurtServer` are all `LivingEntity`. The pipeline, the hit resolver, armour,
breaks, destruction, blast dismemberment, bleeding, consciousness, healing and impairment
now take a `LivingEntity`, and the player-only consequences gate themselves inside:
messages and HUD payloads, knockdown and microsleeps, rescue and food, the shield arm, the
PvP multiplier, long-term ceilings, death violence. `Notifier.message` on a mob is a no-op;
its injury sounds still reach everyone nearby. The public 1.3 API keeps its player types;
a mob victim skips the external classifier and impact-point hooks.

**Which mobs is data.** `MobAnatomy.participates` is the one question every hook asks — a
non-creative player, or a mob in the `realisticdamage:has_anatomy` entity type tag while
`enableMobAnatomy` is on. The tag ships with the humanoid families whose models extend
`HumanoidModel` and whose hitboxes are player-shaped (zombie, husk, drowned, zombie
villager, zombified piglin, skeleton, stray, wither skeleton, bogged, parched, piglin, piglin
brute). A second tag, `throws_limbs`, names the types whose severed limb the client can
draw; the rest lose the limb on the model and keep the stump.

**What a mob body does.** The same six regions, routing, wounds, breaks, destruction and
hidden meters. Bleeding drains Blood and then hearts through the same bleed-out damage type.
The existing attribute modifiers cripple it — legs cut movement, arms cut attack damage —
and attributes a mob lacks are skipped. Both arms destroyed refuses `Mob.doHurtTarget`; a
skeleton without its drawing arm refuses `performRangedAttack`. Headshots run the
consciousness ladder: a concussed mob forgets its target every two seconds and finds it
again, and at zero it collapses — `setNoAi(true)` for the unconscious window, restored on
waking, leaving alone any mob that was NoAI for its own reasons. Time is its only cure: five
seconds unhurt and it mends a fraction of a regen-HP every eighty ticks, so a zombie that
walks away from a fight is whole in a couple of minutes and a minor wound is not a slow
death. No microsleeps, knockdown, rescue, food, sleep, ceilings or messages.

**Performance.** The body attachment is created on first damage, never before. A
per-server identity set of injured mobs is what ticks: added on damage, dropped when the
mob dies, unloads, or settles (no wound, blood full, consciousness at its cap, nothing
broken or missing — a mob missing a limb never settles, because its penalty has to keep
being asserted). A server of healthy zombies iterates nothing.

**On screen.** The limb, stain and pose carriers moved from `AvatarRenderState` to its
parent `HumanoidRenderState`, extracted for mobs from `HumanoidMobRenderer` by the same
`LimbStateExtractor` the player renderer uses. `HumanoidModel.setupAnim` hides destroyed
parts and applies the limp for any humanoid state. `BloodBodyLayer` is generic over
humanoid states and models, sits on every humanoid mob renderer, reads the texture from
its parent renderer, and picks its limb build from the body: slim or wide from a player's
skin, thin from a skeleton's model. `LimbMeshes` gained the thin two-by-two build from
`SkeletonModel`, mirrored on the left as vanilla mirrors it. The emitter and body staining
iterate living entities with a snapshot rather than players. The severed limb syncs its
owner's entity type, and `MobLooks` maps that to a texture and a build.

**Slice two: everyone who is not a humanoid model, and blood that knows whose it is.** The
carriers moved once more, from `HumanoidRenderState` up to `LivingEntityRenderState`, and
extraction runs from `LivingEntityRenderer` itself, so every living thing carries its body
onto its render state. Illagers hide lost limbs and limp through `IllagerModelMixin` (a
crossed pair is retired for the surviving arm, since half of it is not a thing the model can
draw); the villager-shaped witch loses legs but keeps her crossed arms. `FigureBloodLayer`
paints these models by part name off the root, with `LimbMeshes` builds for the illager,
villager and zombie-villager cubes (ten-pixel heads, six-deep bodies, their own arm UVs).
A collapsed or knocked-down mob is laid out by the renderer with vanilla's own death roll
about the feet, players excepted — they have the server's prone pose. Mobs fall like
players: the fall mixin records height and surface for any body, and a hard landing knocks
a mob down. A concussed skeleton's arrows spread three times as wide and a broken drawing
arm doubles it, multiplying the inaccuracy vanilla already applies.

Bodies now have blood profiles, by tag. `bloodless` (the skeleton family) opens no wound,
sprays nothing, has no stump wound and throws a bone that never bleeds — a skeleton that
bled was the first playtest report of the mob work. `blood_dark` (the undead) and
`blood_purple` (endermen, against the day they get bodies) select a `BloodTint`, carried by
every drop, stain, shell and severed limb from the moment it leaves the body, so a zombie's
old dark blood stays dark on the floor after the zombie has walked off.

**Still to come in 1.7.** Villagers by default (a pack can tag them now); endermen and the
other non-humanoid hostiles; tuning of the daze and collapse against real fights.

## Added in 1.7 (alpha.8): the afflictions

Five things that happened to a player without the mod having anything to say about them.
Each was a playtest complaint of the same shape: "X happens and nothing changes".

**Drowning and suffocation take the mind first.** There was a drain while out of air, but
it was a flat 2.5 points a second from 100, and vanilla drowning kills a full-health player
in about ten seconds — so it never mattered, and a head stuck inside a block did not count
at all. Now the brain runs an oxygen debt (`HypoxiaMath`): seconds without air, with a
drain that starts at 4/s and climbs 3/s for every second of debt. From full consciousness
you black out about seven seconds after the last bubble, which is three or four seconds
before the drowning damage would have finished you: the ladder plays on the way down
(vignette, ringing, heartbeat, microsleeps while you are trying to swim up), you sink, and
somebody has a few seconds to drag you out. A head in a wall (`isInWall`) is as airless as a
head under water. Breathing repays the debt at 0.6 s a second, and while any is owed the
normal recovery is divided by one plus the debt — you surface, gasp, and the world takes a
while to come back. Client side (`AirHunger`): as the air bar runs out the edges close in
dark and cold, and the chest heaves — a phase-accumulated contraction, a jerk of the whole
view, two seconds apart at first and three a second at the end. Coming out of it plays a
synthesised gasp and a stretch of heavy breathing sized by how far gone you got.

**A blast takes your hearing — any blast, not only one that hurt you.** The blast cue rode
the damage payload, so an explosion that did not damage you (Blast Protection, cover, range)
was vanilla's bang and nothing else, and even a point-blank one only fed the tinnitus tone
while the world stayed perfectly audible underneath it. `BlastShock` hooks the vanilla
explosion packet (`ExplosionPacketMixin`, at the tail, after the bang has started) and
judges every blast by radius and distance — `1.8r/d - 0.3`, clamped, so TNT takes your
hearing outright at four blocks, leaves you badly muffled at ten and is just a bang at
twenty-five — with a wall in the way costing 45% and hiding the flash. Three ticks after the
bang (so the crack itself is heard) the low-pass filter drops almost all the highs, the
category volumes fall by up to 90%, and the tinnitus tone fills the gap; it holds for up to
two seconds, then hearing returns over up to twenty. A flash if you saw it, a haze of dust,
a shake and, for the closest blasts, a moment of seeing double. Its own control, Blast
Deafening; a blast that does hurt you still guarantees at least its damage-sized shock.

**The concussion is an injury you play around, not a number you wait on.** It was a boolean
that moved the symptom ladder 18 points and drew faint ghosting until a 25-second timer
ran out. Now (`ConcussionMath`, `ConcussionSystem`):

- *Severity 0-100, read as mild / moderate / severe.* A first knock is graded by how hard it
  was. Moderate at no flare is exactly the old shift, so nothing tuned against it moved.
- *Second impact.* While concussed, a concussive head hit of 1.5 trauma deepens it and flares
  it on the spot, and past a bar that falls with the grade (5.0 / 3.5 / 2.0) it puts you
  straight out — a severe concussion is one solid tap from the floor. The concussion is now
  judged once per damage event on the worst candidate; it used to be asked per candidate,
  which was harmless only while "already concussed" meant "nothing to do".
- *Heals by rest.* 0.3 severity a second standing still, 0.12 moving gently, nothing while
  exerting; halved while the head itself is still mending, a quarter better in the dark. A
  mild knock is under two minutes' rest, a severe one about five, or one night's sleep. Mobs
  mend on time alone at 1/s.
- *The flare*, 0-100: sprinting, swimming, jumping, attacking, mining and taking hits raise
  it; bright light raises it too, but only up to 62 — light hurts and costs you your sprint,
  it never makes you vomit by itself. Stillness and darkness let it fall. It brings the
  ladder further forward (up to 14 more points), refuses sprinting at 60, and at 90 you
  vomit: doubled over for two and a half seconds, three food points and all saturation
  gone, and the flare drops to 50. Food is the cure in this mod; losing it is the cost of
  pushing a head injury.
- *Vertigo spells* on a phase accumulator, like the microsleeps: every 75 / 45 / 25 seconds
  by grade, up to twice as often at full flare, alternating direction. The horizon rolls and
  keeps rolling, the world warps, your feet veer and stagger the way the room is turning,
  movement drops a quarter and sprinting is refused.
- *Light and noise sensitivity*, client side: bright light glares and you squint (lids close
  in from top and bottom), staring toward the sun worst of all; a loud sound near you
  (heard through vanilla's own `SoundEventListener`, so no mixin) makes you wince — a flinch,
  a squint, a white spike, a short ring.

The grade and flare travel in the snapshot's `Meters` (two new ints), and vertigo and
retching as status flags. The HUD and body panel name the grade, and the panel gives the one
piece of advice the mod gives anywhere: flaring means stop and get out of the light.

**A Disabled arm's hand will not grip.** Driving an arm to Disabled (grey on the panel) used
to change nothing at all if it was the off arm: only the main arm's condition fed any
penalty. Now (`disabledArmsLoseGrip`, default on) that hand cannot use anything — a raised
shield drops and cannot be raised, nothing can be eaten or placed with it — two-handed items
(bows, crossbows, spears) need both arms, and a limp main arm fights at the one-handed
penalty a lost one does. It is not a lock that lasts: it ends the moment the limb heals out of
Disabled. The refusal says why, on a cooldown, and that line is classed with the rescue
lines as an answer to a keypress, so the Injury Text switch cannot leave a click silently
doing nothing. The arm hangs in third person (swing mostly taken away rather than cradled,
which stays the pose for a break) and droops low at the edge of the first-person view.

**The last two hearts.** At one heart with nothing else wrong, all that changed was the
heartbeat. Now (`AdrenalineMath`, `enableAdrenaline`) dropping to a fifth of max health
floods you with adrenaline for eight seconds: 15% faster, a little quicker to swing, half the
pain of injured limbs masked; tunnel vision (the field of view narrows instead of widening
with the speed), the world dull and distant while your own heart and breath get loud. Then
the crash, for at least thirty seconds and for as long as health stays low: 10% slower, the
colour leaving the edges, the hands shaking (the held item trembles, the aim shivers by a
hair on Camera Drift), heavy breathing, and a sprint that runs out after three seconds and
takes six to come back. It fires on the crossing, never on a roll, and cannot fire again
until two minutes have passed *and* health has been back over the heartbeat line —
otherwise hovering at two hearts would chain surges forever.

**Breathing** is synthesised like the injury sounds (`tools/generate_sounds.py`): noise
through a few broad formants, three inhales and three exhales so consecutive breaths are not
the same sample, and a gasp with a quarter-second of glottal catch under the rushing air.
They live outside `SOUNDS` and its spectral budget test — a breath is not an impact.

New client controls, all boxed and seeded from their nearest existing statement so a silenced
config stays silent: Blast Deafening (from Muffling), Concussion Symptoms and Adrenaline &
Crash (from the vignette), Breathing (from the heartbeat).

## Deliberate non-goals

Persistent blood as block/world state, medical items in the core mod (they live behind the field kit's Medicine switch, below), and numeric HUD stats. The inventory panel may show condition figures on request;
the HUD never does, and Blood/Consciousness are named by band and never numbered anywhere.
TACZ integration is still deferred, but `DamageCategory` + `BodyHitResolver` accept
externally supplied classification and hit points so an adapter can slot in later.

## Added in 2.0: the field kit switches

The field kit is about forty items in two halves, each behind a server switch:
`enableWeapons` (anything that hurts someone, or protects you from being hurt) and
`enableMedicine` (anything that treats, supports or reads an injury that already
happened). Both ship **off**, and the first launch with 2.0 asks about them.

**The rule.** With both off, the game is the core mod exactly: food and time are the
only cure. Nothing in the core mod knows the field kit exists; it lives in its own
package (`fieldkit`), with its own entrypoint and mixin config.

**Off without a restart.** Everything is always registered, so a world never fails to
load because a switch moved, and flipping it back restores everything. Instead every
route to an item asks its half at the moment it is taken:

| Route | Where it asks |
|---|---|
| Crafting | `realisticdamage:switched`, a wrapper around any crafting recipe; off, the grid does not match |
| Brewing | `PotionBrewing.hasMix`/`mix`: a mix whose result is switched off is not a mix |
| Loot | the `realisticdamage:field_kit_on` condition on our own entries, and a `MODIFY_DROPS` net that strips any switched-off stack from any table |
| Trades | the same condition as a trade's `merchant_predicate` (26.2 rolls trades through loot contexts), a net on `VillagerTrade.getOffer`, and an already-rolled offer gives nothing |
| Use | `ItemStack.use`/`useOn`/`interactLivingEntity`: refused, with "Medicine is switched off here" on the action bar |
| Effects | refused by `canBeAffected`; a running one ends on its next tick (`FieldKitEffect`), and what it already did stays done |
| Creative tab | the Field kit tab lists only halves that are on, rebuilt when a switch flips |

A potion belongs to the half of the effects it carries, which covers every form vanilla
makes of it (drinkable, splash, lingering, tipped arrow) through the one
`potion_contents` component.

**Who decides.** The server's `ServerConfig` decides everything. A client cannot read a
server's file, so `RulesSync` sends every setting (in `/rdmg config`'s own words) on
join and whenever any setting changes by any route, plus whether that player passes
`/rdmg`'s permission check. The client's copy (`RulesView`) is used only to draw: the
creative tab, a tooltip, the **World rules** page.

**World rules** is the first GUI for the server settings. It is generated from the
same fields as `/rdmg config`, so a setting added later appears there by itself (under
"Other" if no section names it). Singleplayer and the title screen edit this install's
file directly; on a server an operator's changes are sent as `/rdmg config` commands,
and everyone else reads only.

### The brewed effects (phase 3)

Each brewed effect is a status effect (a `FieldKitEffect`, so it ends on the tick its
switch goes off) plus vanilla potions, so vanilla supplies every form and milk clears it.
The effects carry almost no behaviour of their own: the body's systems ask about them,
through mixins in `fieldkit.mixin` rather than calls in the core, so the core mod still
does not know the field kit exists. All numbers are thresholds and factors in
`Balance` ("The field kit's brewed effects"); none is a chance.

| Effect | Where it acts |
|---|---|
| Haemorrhage | `TraumaPipeline.createWounds`: the hit is judged against ×0.6 thresholds (×0.45 at II), and the wound opens a step worse before armour takes its step back. `BleedingSystem.tick`: the stack may run to ×1.5 the cap (×2 at II), and hand pressure keeps half its effect. `Arrow.doPostHurtEffects`: a Moderate wound where the arrow struck, on anything that bleeds |
| Sedation | `LivingEntity.addEffect` counts every application as a dose into a saved `Sedation.State` (pending, level). The effect's tick absorbs and clears it, keeps the effect as long as the drug, re-tracks a mob so its body ticks, and runs the sleeping draught's wake and the overdose damage. `ConsciousnessSystem.tick` HEAD holds consciousness under the stage's cap; `starved` is true while overdosed; `Knockdowns.afterHit`'s blow bar drops while stumbling; `PotionContents.onConsume` marks a drunk dose. Refused by bosses and the mindless. The stage (never the dose) goes to the player for the eyelids |
| Numbing | `ImpairmentSystem.tick`: the walking and arm penalties from pain read 0. `Flinches.afterHit` and `TraumaPipeline.sendImpact` do nothing. The effect's tick wears a broken leg down while sprinting; an attack wears a broken arm. Client: `BodyHud` draws your own body as frost with no marks or icons |
| Adrenaline | `onEffectStarted` starts the body's own surge (`AdrenalineMath`) for the effect's duration and spends its refractory; removal (running out, milk, a switch) ends it into the crash; `canBeAffected` refuses a dose during the refractory. `BleedingSystem.tick`: ×1.3 bleeding while it lasts. Client: Cardio is fed at least 0.6 health distress, so the heart and the arterial spurts quicken without the low-health vignette |

Tipped arrows from drinkable potions are a special recipe
(`realisticdamage:field_kit_tipped_arrow`) that only takes field kit potions and asks the
potion's own half, so one recipe serves both switches.

### Medicine, round 1: the bleeding kit (phase 4)

A treatment is vanilla's hold-to-use (`TreatmentItem`): `use` treats yourself,
`interactLivingEntity` someone else; `Treatments` remembers the patient while the
key is held and loses it if they leave reach, and an interruptible treatment (the
needle) is spoiled by an `AFTER_DAMAGE` on either of them. The server decides
whether there is anything to treat when it starts and again when it lands.

What sits on a wound lives in a saved, all-synced attachment (`Dressings`):
bandages and tourniquets by region. `Dressings.factor(state, part, severity)` is
the one rule both the server's bleed rate (`FieldKitBleedMixin`) and a client's
drawing of the blood (`DressedBloodMixin` on `BloodEmitter.emitFor`) read. A
tourniquet holds its limb at condition 0 and keeps what it took to give back.
The attached state's maps are never changed in place: Fabric encodes a sync later,
on the network thread, and a bandage put on under it broke the packet and dropped
the player. Everything that comes on or off goes through `Dressings.change`, on a
copy that is then set; only counters and flags tick in place. The
uses of vanilla items (a glass bottle to draw blood, flint and steel or a fire
charge to cauterize) are `UseItemCallback`/`UseEntityCallback` handlers that only
act on a sneak with Medicine on, and only on a willing body (`Consent`).



### Medicine, round 2: bones, head and senses (phase 4)

Splints and slings live in the same synced `Dressings` state as bandages, with
a third map, `owed`: the ceiling band a limb had before its break lowered it
(`SplintCeilingMixin` wraps `CeilingLogic`'s `lowerCeilingTo`). `Dressings.tickBody`
takes a splint or sling off once its bone has knit; a splinted limb with a debt
gets `restoreCeiling()` then `lowerCeilingTo(prior)`. The core's own "mended"
line is held back for a dressed limb (`SplintMendMixin`), so there's one line.

The splint's halving is three `ImpairmentMath` call sites (`SplintPenaltyMixin`):
the per-leg penalty (a private helper with no body, so the body being ticked is
stashed at the head of `ImpairmentSystem.tick`), the main arm in `tick`, and
both arms in `twoHandedPenalty`. The sling is the core's grip (`SlingGripMixin` on
`gripBlocked`, which every use refusal, the per-tick stop and the grip line go
through) plus field kit attack and dig refusals in a first phase, ahead of the
core's strain listener. Its faster healing is the `heal` argument in
`HealingSystem.trackBrokenHeal`.

Smelling salts decide from `SaltsRule` (blood, air, Sedation stage) and wake
through the core: consciousness to `WAKE_THRESHOLD` + 5 and the minimum ticks
out, so the next `ConsciousnessSystem.tick` runs its private `wake()`.

The stethoscope is client-side (`StethoscopeEar`): the server only starts the
use. Your own heart is `ClientState`'s; anyone else's is a `Cardio` run from
their synced snapshot, the way `BloodEmitter` times a stranger's artery.

Treatments used on a body are routed in a `UseEntityCallback` phase after the
default one: the core's grip and drag listeners run first, and the entity's own
`interact` (trades, saddles) never sees the click.
### Medicine, round 3: food and drink (phase 4)

`Provisions` holds the four. Coffee and the blood sausages are items; spirits
and the iron tonic are potions (water + potato, awkward + iron nugget), so the
brewing stand, splash and lingering forms and the creative tab come from
vanilla and `Kit`, as for phase 3. Each drink's effect (Caffeinated, Tipsy,
Iron Tonic) is a bare `FieldKitEffect` the hooks ask about; the numbers are
`ProvisionsMath`, apart from the items so the unit tests can load them.

Coffee: `Sedation.scale` multiplies the body's size by `COFFEE_SEDATION_RESIST`
while caffeinated, so every stage reading (the consciousness cap, the slowing,
the salts, the client's eyelids) sees the same lighter stage.
`CoffeeMicrosleepMixin` cancels `ConsciousnessSystem.tickMicrosleep` while
caffeinated and not mid-blink, unwinding its phase as a well mind does.

Bleeding: `FieldKitBleedMixin` multiplies the rate by `ProvisionsMath.bleedFactor`
after Adrenaline's. Your own heart: `HeartRaceMixin` gives Cardio a distress
floor of 0.3 on coffee (Adrenaline's is 0.6).

Spirits' weaker numbing is `NumbingPainMixin` again: the walking and arm
penalties times `SPIRITS_PAIN_FACTOR` instead of zero. Flinches and the hit
blur stay; you still feel it. The doubled view is `TipsyAfterimageMixin`, a
floor on the concussion's input to `Afterimage.frame`, so it rides the same
slider and the same VR path.

The blood sausage adds `BLOOD_SAUSAGE_BLOOD` in `finishUsingItem`, after the
core's own food Blood. The blood bag has a glass-bottle crafting remainder, and
`SwitchedRecipe` refuses any grid holding a bag that has gone off. Coffee's
recipe matches only a water bottle, through Fabric's `fabric:components`
ingredient. The iron tonic doubles the first `restoreBlood` in
`HealingSystem.tick`, the passive one that waits for the bleeding to stop.
