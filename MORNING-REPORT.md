# Realistic Damage 1.6 — Morning Report

1.6 **Consequence** is complete on `1.6-consequence`. The branch started at
`07d25dd` (1.5.0), keeps that base intact, and now builds a versioned 1.6.0 jar.
Nothing was tagged, merged to `main`, or pushed.

The interrupted handoff was recovered at `dd357d3`: all four headline features
were implemented and individually verified, but the adversarial review, the last
three blood consequences, ship documentation, and this report were unfinished.
Those pieces are now done.

## What shipped

### Long-term injuries

`enableLongTermInjuries` is an optional server rule and defaults off. A region
loses one recovery rung when it is newly driven to Disabled, when a limb newly
breaks, or when a limb is newly destroyed. Multiple qualifying outcomes in the
same damage event still cost one rung, so the outcome is deterministic and a
single complicated hit cannot be counted twice.

The ceiling is stored as a band on each `BodyPartData`, persisted with the body,
packed into the versioned `BodySnapshot`, and shown on the body panel as the
familiar Hurt / Injured / Critical boundary rather than as a figure. Limbs may
bottom out at Critical. Head and torso stop at Hurt; a deeper head ceiling would
make the consciousness ladder permanently active, which is punishment without a
recovery loop rather than a useful hardcore rule.

Food, natural regeneration, potions and time heal condition only as far as the
ceiling. Sleep and totems do not raise it. Death creates a fresh body at its old
ceilings while the rule is enabled; disabling the rule prevents new rungs and
lets death clear existing ones. `/rdmg ceiling` is the explicit admin escape.

### Explosive dismemberment

TNT, creepers and other server explosions now capture their real centre,
exposure and pre-health-bar blast amount. Only the nearest limb is eligible for
direct destruction, at a deterministic threshold equal to 45% of that limb's
full condition after the normal localized armour reduction and server trauma
multipliers. This avoids the old contradiction where better armour or absorption
could erase the physical event entirely, while still letting armour protect the
limb.

The rule is nested under `enableLimbDestruction` and its own
`enableExplosiveDismemberment` switch. It never takes a head or torso directly:
vanilla is already deciding whether a core-range blast killed the player, and a
second bypass-armour core kill would mostly punish totem saves. A qualifying
limb goes through the existing destruction choke point, so the wound, scar,
missing model part, impairment, sound, message and internal damage safeguards
cannot diverge between ordinary and explosive loss.

### Scarring

Every body region records a cosmetic history rung: none, once broken, or once
destroyed. The rung is raised inside the state setters rather than at individual
damage call sites, so commands and future injury paths cannot forget it. It is
persisted, synced to all tracking clients, and carried through death even when
long-term injuries are off.

Scars appear in the body panel and on an overlay-free player mesh, beneath the
inflated hat, jacket and sleeves. A currently missing limb has no geometry to
mark; its destroyed scar appears on the next body. Scars are never read by
healing, impairment, prey scoring, or any other outcome. They have a dedicated
client toggle and remain outside the sensory panic button because they are body
information, like the diagram and status icons.

### Injury-aware mob AI and visible posture

Injured arms are carried against the body and damaged legs turn out and shorten
their step, including the armour model. The pose scales the rotations vanilla
already calculated instead of copying Mojang's walk equation. A lost limb is not
posed; its absence is the stronger cue.

The client pose and the server prey score both call `LimpMath`, so a mob cannot
react to a limp the player model does not show. Hostile target resolution is
enforced at `Mob.setTarget` by substituting the best valid player, not by clearing
a target that vanilla would immediately reacquire. The ranking is downed body,
missing leg, broken leg, visible condition limp, then a running wound. MINOR
wounds, scars, Blood and Consciousness do not count. Existing targets receive a
switching margin, revenge a stronger hold, and ties resolve by distance then
entity id. Candidate scoring happens before geometry and line of sight, so a
healthy server performs no extra raycasts.

Non-hostile mobs are never redirected. Neutral hostiles are redirected only to
players they are already angry with. Brain-driven hostiles that do not use
`Mob.setTarget` remain explicitly outside this rule rather than being only partly
supported.

### The final blood/consequence sweep

The user chose a mark now and a physical severed limb later. A severance therefore
throws 36 deterministic drops — 1.8 times the ordinary Critical opening — in a
wider, downward-biased cone. It is deliberately the same blood transport and
same destruction choke point; the visible flying limb stays in 1.7.

A bleeding body pressed against a collision face now transfers a bounded share
of visible wound flow to that wall. The 7.5 cm envelope includes Minecraft's
roughly 5.6 cm client/server settling quantum, which the client test exposed,
without reaching across a useful gap. Large wet marks on vertical faces advance
a deterministic phase, shed a real gravity-driven drop from the lower edge, pay
radius for it, and slow as they dry.

Both wall behaviors are client presentation over the existing 768-mark bounded
field. They add no block entities, chunks, save fields, packets or unbounded
world state. Persistent per-face blood would make blood decoration rather than a
temporary readout and was deliberately not smuggled into this release.

## Review findings closed

- The public `BodyView` API had gained ceiling and scar reads but `API.md` still
  documented only fractures and vitals. The new methods, player-facing band rule,
  and `ScarLevel` stability promise are documented now.
- The design document still described an obsolete owning-client custom-payload
  sync path. It now describes the persistent server data plus all-tracking-client
  versioned snapshot actually in the code.
- README still said death reset everything. It now distinguishes active injury,
  scars, and optional long-term ceilings.
- The first contact-transfer test found that a client reconciles a nominally
  one-millimetre server placement to 5.6 cm from the wall. Collision-shape lookup
  and the explicit contact envelope now cover the live position, and the test
  passes on the reconciled client rather than on an assumed server coordinate.
- A second audit pass found no further correctness, determinism, persistence,
  sync, performance, design-rule, or vacuous-test defects.

## Verification and exact counts

Baseline at `07d25dd`:

- **119** unit tests, passing.
- **90** required server game tests, passing.
- **4** client game-test classes under Xvfb, passing.

Final 1.6.0 tree:

- **239** unit tests across 29 result files, 0 failures (**+120**).
- **164** required server game tests, all passed (**+74**).
- **6** client game-test classes under Xvfb, all passed (**+2**), producing
  **76** screenshots in the final run.

Final commands:

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew build test --no-daemon
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew runGametest --no-daemon
JAVA_HOME=/usr/lib/jvm/java-25-openjdk xvfb-run -a -s '-screen 0 1600x1000x24' \
  ./gradlew runClientGametest --no-daemon
```

The release artifact is `build/libs/realistic-damage-1.6.0.jar` (557 KiB in the
verified build), and its embedded `fabric.mod.json` reports version `1.6.0`.

## Could not verify

No headline feature is blocked. The automated suite includes a real dedicated
server/client socket test, but it is not a substitute for a populated production
server or a large modpack. TACZ still has no compatible Fabric build for this
Minecraft version, so the optional firearm tags remain verified against stand-in
damage types rather than the real mod. Rasterizer-specific blood appearance under
Fabulous graphics also remains a human playtest item; behavior, bounds and
render-pipeline startup are covered by the client suite.

## Deliberately left out

- A flying severed-limb entity: scheduled with the entity/anatomy work in 1.7.
- Persistent blood stored on blocks or chunks: a possible later world-state
  system, not a late extension of the particle field.
- Body anatomy and injury behavior for mobs, including the brain-driven hostile
  families that bypass `Mob.setTarget`: 1.7 scope.
- Medical items, splints, surgery, infection and a wound screen: still explicit
  non-goals. Food and time remain the cure.
- A tag, merge to `main`, or push: the original release instructions explicitly
  reserved all three.
