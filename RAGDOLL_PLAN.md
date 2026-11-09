# Active ragdolls: plan

Status (Nov 9 2026):
- **Phase 0 done** (alpha.14). Passed the gate: the user's verdict was "the
  ragdolls are AWESOME".
- **Phase 1 dropped** by the user: the six-box body *is* the Minecraft look.
- **Phase 2 (muscles) built** (alpha.15). The user: "works swell".
- **Phase 3 (reflexes) built** (alpha.16, overnight Nov 9-10).
- **Phase 4a (bodies that stay) and 4b (your own death) built** (alpha.17 and
  18). 4b is a new Death View setting (Ragdoll / Classic) so the classic
  camera and its tests stay intact.
- **Phase 4c (knockdown ragdolls) built** (alpha.19): `damage/Knockdowns`
  (server: non-lethal blasts, big hitters, huge blows) and
  `client/LiveRagdolls` (thrown, then down, then the get-up blend).
- **Phase 4d (other mobs) built** (alpha.20): `ragdoll/GenericRagdoll`
  (any model's top-level parts jointed to the biggest, limits from each
  part's rest angle, a keel computed from the body's own shape) and
  `client/GenericCorpse`; `SimulatedCorpse` is the base both kinds share.
- Phases 3 and 4 follow. Phase 4 now also covers knockdown ragdolls (being
  thrown without dying).

## The goal

Bodies that fall the way people fall. A real physics body whose joints have
muscles, plus a small controller that decides what those muscles do while
there is still someone in there. They throw an arm out at the floor, cover
their head, grab the wound, stagger. Then they let go as consciousness runs
out.

The industry name for this is an *active ragdoll*. "Dynamic Motion Synthesis"
and "euphoria" are NaturalMotion's brand names for their version (GTA IV/V,
Red Dead), so we never use them on the Modrinth page.

**The bar to beat.** The Minecraft ragdoll mods we know of are Haubna's
Physics Mod and Saro's Mob Ragdoll / Player Ragdoll. From their listings, all
of them are *passive*: the body goes limp, or breaks into pieces, and nothing
reacts. None of us has playtested them yet. A body that reacts, and reacts to
a real injury model, is the gap.

**Why this mod can do it better than most games.** The hard part of an active
ragdoll is deciding what the body *should* do. Realistic Damage already knows,
and syncs to every client in `BodySnapshot`, things most games don't track:
- which region was hit;
- each region's wounds, breaks and lost limbs;
- consciousness, blood and adrenaline;
- how limp each limb is (`LimpMath` levels, which the client already works
  out from the region data) and the knocked-down flag;
- the death's kind and violence.

Those become the muscles' inputs directly. No other ragdoll gets told that the
left leg is broken and the man is half-conscious.

## What exists today (alpha.13)

- `damage/Corpses` (server, from AFTER_DEATH) sends `CorpsePayload` (kind,
  violence, launch velocity) to every *other* tracking client. It also holds
  the body against vanilla's removal for `CORPSE_HOLD_TICKS = 100`.
- `client/RemoteCorpses` treats the body as **one rigid plank** that rotates
  about **one horizontal axis**. It topples or tumbles, then springs flat onto
  its back or front. `LimbPhysics` swings 9 joint angles as independent damped
  pendulums toward the effective gravity.
- The pose goes onto the vanilla model at the TAIL of `HumanoidModel.setupAnim`
  (`HumanoidModelMixin` → `RemoteCorpses.limp`), and the root goes on at the
  end of `setupRotations`. Because of that, armour, blood shells and held
  items all follow.
- **Your own death** is separate. `LocalPlayerDeathMixin` keeps your body with
  vanilla physics, and `DeathCamera` plays a `DeathMotion` profile chosen by
  `DeathKind` and violence.

What the plank cannot do:
- land on its side;
- fold over a ledge or fence;
- slide down stairs;
- let a limb hit a wall or the floor as a solid thing;
- react in any way.

Only humanoid render states use it.

## Architecture decisions

1. **Client-side and cosmetic, simulated on each watching client.** Where a
   corpse lies never affects gameplay. Simulating on the server would cost TPS
   and mean syncing 6 to 11 transforms per body per tick. The server sends one
   richer death event and nothing else. Every watcher runs the same
   deterministic sim, so falls agree in broad strokes but can differ in detail
   between clients. Every big game accepts this. (Saro's runs on the server and
   syncs, which is the opposite trade.)
2. **Our own physics, in pure Java: XPBD** (Müller et al. 2020, "Detailed
   Rigid Body Simulation with Extended Position Based Dynamics").
   - No native libraries (Bullet/Jolt bindings would need per-platform natives
     in the jar).
   - Unconditionally stable with big mass ratios, which ragdolls need.
   - Joint limits and contacts are one kind of constraint.
   - A muscle is just a joint drive with a *compliance*, so muscle strength =
     1 / compliance, a single number we can fade.
3. **The physics core has no Minecraft imports.** It lives in
   `src/main/java/.../ragdoll/physics/` (the common source set, so the
   existing JUnit setup in `src/test` can see it, and a future server use is
   possible). The world is an interface: collision boxes in a region, and the
   fluid at a point. A Minecraft adapter implements it on the client; tests
   implement it with a fake world (floor, stairs, slab, fence, wall).
4. **Tick-rate simulation, render-time interpolation.**
   - Step on the client tick with substeps (start at 10, so 200 Hz).
   - Keep the previous and current pose; slerp between them by `partialTicks`.
   - Never advance anything from a render call. Vivecraft renders once per
     eye, plus the mirror and camera passes.
5. **Drive the vanilla model, don't replace it** (for phase 0). The torso body
   becomes the render root (position + orientation via `setupRotations`, as
   today). Every other part's rotation relative to the torso becomes ModelPart
   rotations at the TAIL of `HumanoidModel.setupAnim`, as today, plus part
   offsets so the joints line up. Armour, blood layers, held items and capes
   keep following for free. Verify the Euler order `ModelPart` uses before
   converting.
6. **Keep today's plank sim.** It becomes the **Simple** mode, and the LOD
   fallback for far or over-budget bodies. `RemoteCorpses` stays the entry
   point (`start`, `tick`, `extract`, `applyRotation`, `limp`), so the mixins
   don't change.
7. **Deterministic, no RNG** (house rule). Asymmetry and writhing come from
   the entity id and from sums of sines.

### The richer death event

`CorpsePayload` gains:
- the killing blow's **body part** (byte, -1 = none);
- its **direction** (unit vector);
- its **impulse** (a magnitude).

Everything else (wounds, breaks, lost limbs, consciousness at death,
adrenaline) is already in the synced `BodySnapshot`.

`TraumaPipeline` resolves the part per blow (`BodyHitResolver.resolve`) but
doesn't keep it. Record the last locational blow's part and direction on
`RealisticDamageData`, transient and not persisted, so `Corpses.died` can read
it. Blasts already have the `PendingExplosion` centre. `DeathPayload`, sent to
the one who died, gets the same fields for phase 4.

### File layout (planned)

```
src/main/java/.../ragdoll/physics/   Body, Joint (Ball, Hinge), Drive, Contact,
                                     Solver, PhysicsWorld (interface) - pure Java
src/client/java/.../client/ragdoll/  Rig (bodies + joints from LimbMeshes geometry
                                     and BodyPlan), Ragdoll (one corpse), McWorld
                                     (the adapter), Muscles, Behaviours, DebugDraw
src/test/java/.../ragdoll/           solver tests against a fake world
```

## Phases

Each phase ends with a jar in both Prism instances and a short in-game
checklist for the user. The user's playtest is the review. Phase 0 is a gate:
if the physics body doesn't clearly beat the plank in play, stop and rethink
before building on it.

### Phase 0: a real body (passive)

The six boxes become six rigid bodies joined at vanilla's pivots: neck,
shoulders, hips.
- **Bodies:** oriented boxes, sized from `LimbMeshes`, so wide and slim arms
  and the skeleton's thin build are right.
- **Masses:** by human proportion, not by volume. Head 8%, torso 50%, each arm
  5%, each leg 16%.
- **Joints:** ball joints with swing-twist limits from human range of motion.
  Hips flex about 120° and extend about 20°; shoulders reach overhead; the
  neck turns about ±60° and nods about ±45°. Start from `LimbPhysics.MIN/MAX`.
- **World contacts:** sample points per box (corners, edge midpoints, face
  centres) against block collision AABBs gathered once per tick over the
  body's swept bounds. Static and dynamic friction, a little restitution.
- **Self-collision:** arms against the torso, legs against each other. Joint
  limits do the rest.
- **Water and lava:** buoyancy plus drag per body, by submerged fraction.
  Drowned bodies drift; lava takes them under slowly.
- **Sleep:** a body at rest for a second stops simulating. It wakes if a block
  it rests on changes.
- **Launch:** start from the pose the model was in on the death frame, with
  limb velocities taken from the last two frames. Add the entity's velocity,
  and apply the killing impulse *at the hit region*. A headshot throws the
  head, a leg hit takes the leg out.
- **Budget:** at most N simulated bodies (setting, default 16). Past that, the
  oldest or farthest drop to Simple. Target under 0.1 ms per body per tick;
  measure it, don't trust the target.
- **Tuning tools** (build these first, they decide how fast everything after
  goes):
  - a debug overlay that draws the physics boxes and joint axes over the body;
  - slow motion (a time scale on the sim);
  - an op-only test command that kills a mannequin at the crosshair with a
    chosen `DeathKind`, part and impulse, so tuning doesn't need a real death.

Done when the fake-world unit tests pass, the client gametests pass, and on
the user's checklist a body:
- lands on its side sometimes;
- slides down stairs;
- hangs off a ledge;
- hits a wall with its arm.

### Phase 1: elbows, knees, waist — dropped

The user's call (Nov 9 2026): "I really like the simplistic look of the 6
body part system. Any more than that and it stops looking like Minecraft."
The body stays six boxes for good. What that changes downstream:
- **Reaching** (brace, protect head, clutch) aims the whole arm from the
  shoulder (one-bone aiming), with no elbow to bend.
- **Folding** over a fence or a ledge happens at the hips, not a waist.
- **"Knees go"** in a collapse means the legs give at the hips and splay. The
  body cannot kneel.
- The `LimbMeshes` split, the armour split and the preview round are not
  needed.

### Phase 2: muscles

Every joint gets a drive (a muscle) pulling toward the pose the body was
holding when it died, with a strength (as compliance) and damping. How strong,
and for how long, comes from the body itself, all of it already on the
client in the synced `BodySnapshot` plus the death packet:
- **Tone at death** comes from consciousness (an unconscious body has none),
  then fades by kind of death:
  - **Strings cut** (zero at once): a headshot or a head blow, a crushed skull,
    violence at full, or already unconscious. It drops on the spot, folding
    at the hips, rather than going over like a plank.
  - **Bleed-out, faint (FADE):** starts at current consciousness and fades
    over 2 to 3 seconds, legs first. The hips give and the body sinks and
    folds before the arms let go.
  - **FREEZE:** stays high and stiff; it goes over like a plank on purpose.
  - **Drowning:** soft from the start and slow to fade: the body drifts.
  - **Blasts:** near zero almost at once; the tumble is all physics.
  - **Ordinary blows and shots:** a fast fade, so they crumple rather than
    tip over.
- **Groups fade separately.** Neck, arms and legs each have their own fade
  speed, so a collapse has an order to it.
- **Injuries:**
  - a lost limb has no body (as in phase 0);
  - a broken limb has no tone and looser limits, so it swings free;
  - a limp limb (`LimpMath.RUNG_RUINED`) has no tone;
  - a concussed head has less neck tone;
  - adrenaline holds the tone up for longer.
- **A quiet death doesn't need a shove.** A body with no tone left crumples
  under gravity. The phase 0 nudge stays only as the direction of the fall.

Built Nov 9 2026 (`ragdoll/Muscles`). Measured in the fake world:
- **Hip fold in the first 0.4 s:** strings cut 1.04 rad, held 0.13.
- **Down (neck below hip height):** strings cut at tick 9, bleed-out at
  tick 17.
- **Arm swing when thrown:** broken 1.39 rad, sound 0.50.
- **Frozen spread** under 0.5 rad.

Two physics fixes came out of it:
- **Hip flexion stops at 80°.** Legs with no knee are always straight, and a
  wider limit made a limp body fold into an upside-down V.
- **Bodies at rest settle their joints before freezing.** Joints win over
  limb-against-limb; the floor wins over both.

Originally, done when, in the fake world, these come out measurably different:
- a strings-cut body against a held one (how much it folds, how far it travels);
- a broken arm against a sound one (how much it swings);
- a frozen body (it stays straight).

And the user can see the difference in play.

### Phase 3: behaviours

A small per-body controller, running at tick rate, sets the joint targets and
a few reach goals (one-bone aiming from the shoulder: no elbows, see phase 1).
It picks a behaviour from a table of (death kind, hit part, violence,
consciousness, injuries). The table is plain data with unit tests.
- **Brace:** conscious, arms usable, falling forward or sideways. Raycast the
  predicted contact; the arms swing out toward it and give on impact.
- **Protect head:** blasts and headfirst falls. The arms come up in front of
  the head and the chin tucks, fading through the tumble.
- **Clutch:** a hand goes to the killing wound (throat, chest, gut, thigh),
  from the hit part plus the `BodySnapshot` wound mask.
- **Stagger:** a blow to someone still standing on two good legs.
  - A few steps in the push direction before the fall.
  - The steps come from a fading upright assist on the torso plus a foot
    placed under the predicted centre of mass. True balance is out of scope
    on purpose.
  - A broken or lost leg means no stagger: the body falls toward that side.
  - Unify this with the death camera's existing run-death stumble.
- **Projectile:** the head snaps back from a headshot; a body hit folds the
  torso around the wound.
- **Writhe (fire, lava):** slow drifting targets and patting at burning parts,
  fading with consciousness. Stop-drop-roll is a stretch goal.
- **Convulse (shock):** a tremor in tone for about a second, then limp.
- **Drown:** arms reach up slowly, then go limp and drift.
- **Fall:** feet-first landings buckle at the hips; violent ones lose all
  tone at impact.
- **Void:** a slow tumble, with the arms moving slowly.
- **Afterward:** a violent death twitches once or twice after it settles. The
  death camera already does this for the view.

The server's hold (`CORPSE_HOLD_TICKS`, 5 s) is too short for writhing.
Phase 4 decouples the two.

### Phase 4: your own death, knockdowns, bodies that stay, mobs

- **Your own death in the ragdoll.**
  - Your client runs the same ragdoll for your own body. The first-person
    death camera rides the head body, smoothed with a capped turn rate so it
    isn't a camera strapped to a tumbling skull. `DeathMotion` becomes that
    smoothing profile.
  - F5 shows your ragdoll.
  - The Death Intensity slider scales how closely the view follows.
  - VR keeps today's rule: no view turns. The view only follows the head's
    position, or keeps today's VR behaviour.
- **Knockdowns are ragdolls** (the user's ask, Nov 9 2026). A blast or a
  heavy hit that doesn't kill throws you as a ragdoll instead of dropping you
  into the prone crawl pose. You get up once the body has settled, the
  knockdown has run out and you're conscious.
  - **What knocks you down.** Today only a hard landing does (1.4, `FallMath`)
    or passing out. Added:
    - a non-lethal blast above a violence line;
    - a heavy blow (crush, falling blocks, the big hitters: iron golem,
      ravager, warden);
    - being blown off your feet by anything with that much knockback.

    Each starts the same knockdown timer, with a launch (velocity, hit part,
    kick) like a death's.
  - **Who moves the body.** A living player's movement is their own client's,
    so their client runs the ragdoll and carries the real player position
    along with the torso, the way the death camera already keeps a dead body
    local. Everyone else gets the launch packet and runs the same sim, softly
    tethered to the position the player's client reports, so the two can't
    drift apart. A mob's position is the server's: the server applies the
    launch as knockback and every client's ragdoll is tethered to that.
  - **The hitbox** stays in the prone pose (`PlayerPoseMixin`) while you're
    down, so you can still be hit, dragged or rescued (`RescueSystem`) as now.
  - **Your view** rides the ragdoll's head, the same camera as the own-death
    one (capped turn rate; no view turns in VR; scaled by Death Intensity).
  - **Getting up** blends the ragdoll pose into a get-up: roll onto the front,
    push up, stand, over about a second. With a broken or lost leg, you stay
    down and crawl, as now. This is the hardest piece of the phase.
  - **Muscles** (phase 2) apply as for a death, from consciousness at the
    moment of the hit, but they recover instead of fading to nothing. A
    conscious body thrown by a blast lands harder than it could have braced
    for, then gathers itself.
  - **Setting:** Knockdown ragdolls, On/Off, so a player who wants the old
    prone drop keeps it.
- **Bodies that stay.** When the server removes the entity, convert the
  ragdoll into a client-only corpse: our renderer, the last render state
  (skin, armour, blood) held, posed by the sim. It stays for a set lifetime,
  then sinks or fades. This frees the server hold to stay short.
- **Mobs.**
  - Humanoid mobs first: zombie, skeleton, piglin, illagers, witch,
    villager-likes. Same rig, their own geometry.
  - Then a generic rig built from any model's `ModelPart` tree, using its
    `BodyPlan`. QUADRUPED covers the most kills (cows, pigs, wolves), then
    ARTHROPOD and AVIAN.
  - Bosses excluded. GeckoLib-style models fall back to vanilla.
  - Mob farms hit the body cap and LOD, never the frame rate.

### Phase 5 (stretch): ragdolls that live

- **Knockouts** moved into phase 4 as knockdown ragdolls.
- **Hit reactions while alive:** a short physics flinch layered over vanilla
  animation (a shot arm jerks back, a leg hit buckles for a step). This is
  euphoria's real selling point.

## Settings (all in the GUI, none in files)

- **Ragdolls:** Off / Simple (today's) / Full. The Arcade and Grounded presets
  pick the default.
- **Body limit** (simulated at once), **Bodies stay for** (5 s / 30 s /
  2 min).
- **Reactions** (brace, clutch, …), **Knockdown ragdolls** (phase 4).
- Broken limbs bending wrongly is gated behind the existing Visceral Blood
  toggle.

## Tests

- **Unit tests (fake world, no Minecraft):**
  - energy doesn't grow;
  - joint limits hold under extreme impulses;
  - no NaN after thousands of deterministic launches;
  - settles on a slab, stairs and a fence;
  - bitwise determinism;
  - a benchmark (N bodies x 600 ticks).
- **Gametests:** the server `CorpseGameTest` and the client
  `DeathCameraClientGameTest` and `MultiplayerClientGameTest` (under xvfb)
  keep passing.
  New assertions:
  - no part below the floor;
  - rests at the right height on stairs;
  - stays within reach of the launch;
  - the quiet-death limb spread stays bounded.
- **Screenshots** from the client gametest are for the user to judge the look.

### The scenes the user judges each phase by

1. Zombie killed with a sword on flat ground: crumples or staggers, no plank
   tip.
2. Killed on stairs: slides or drapes down them.
3. Killed at a fence or ledge: goes over it, folding at the hips.
4. Creeper point-blank: tumbles, the limbs move from physics, lands in a heap.
5. Bleed-out: the legs give first and it sinks.
6. Bow headshot: drops on the spot.
7. A 30-block fall: slams, the limbs bounce.
8. Burning: writhes, slows, stops.
9. One leg gone: falls toward that side.
10. Two clients watching one death: both see a believable fall.
11. A creeper that doesn't kill (phase 4): you're thrown, land, and get back
    up.

## Landmines this plan must respect (already learned the hard way)

- **Models are shared per renderer.** Write every rotation of every part every
  frame, at the TAIL of `setupAnim`, so nothing leaks to the next entity.
- **The armour layer is a separate `HumanoidModel`** with the same render
  state. Pose in `HumanoidModel.setupAnim`, not `PlayerModel`, or armour stays
  behind.
- **Deferred rendering:** a layer cannot submit a shared vanilla part (it is
  read at prepare time). Submit our own `LimbMeshes` parts instead.
- **Skin overlays are *children*** of the base parts.
- **Vanilla's `scale(-1, -1, 1)`** is a 180° turn about Z. Body-frame forward
  is -Z, and positive arm `xRot` swings the hand back.
- **Vanilla removes dead bodies at deathTime 20.** `Corpses.holding` and
  `DeathHold` exist because of it.
- **Vivecraft** renders per eye and head-cancels several vanilla hooks. Don't
  step the sim from render code.
- **Never initialise a "last time" stamp to `Long.MIN_VALUE`** (overflow), and
  never model a recurring event as `elapsed >= period` (integrator landmine).
- **Builds:** `JAVA_HOME` = java-25. Client gametests under xvfb. Public jars
  only from a fresh clone (`publish/`), never `build/libs`.

## Open decisions (the user's)

- **Version:** ship the phases as more 1.7 alphas (default: the corpse work
  already lives in 1.7, and Modrinth is on the alpha channel), or make it
  1.8's theme.
- **"Bodies stay for" default:** 30 s suggested.
- **Gore:** do broken limbs bending the wrong way ship at all, or stay behind
  Visceral Blood? Default: behind Visceral Blood.
- **The competition:** should someone playtest Physics Mod and Saro's before
  phase 0 to confirm the bar? It's cheap, and it guards against building
  something that already exists.

## Size, roughly

- **Phase 0** is the biggest single build (solver, contacts, rig, render
  mapping, tools): one long session.
- **Phase 1:** dropped.
- **Phase 2:** small.
- **Phase 3:** the long tail. Several tuning rounds driven by playtests.
- **Phase 4:** two or three sessions; knockdowns (the get-up) are the big one.
- **Phase 5:** only if everything before it lands.
