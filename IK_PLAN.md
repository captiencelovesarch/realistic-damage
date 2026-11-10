# Stumbling deaths (IK): plan

Status:
- **Phase 0 built** (alpha.22), **loosened after your first playtest**
  (alpha.23: "incredibly stiff", and the body held upright in a pit): the
  test body now weakens, stumbles and goes over by itself, and is let go the
  moment it has nothing to stand on. Your second look: "SO much better",
  no complaints.
- **Phase 1 built** (alpha.24, Nov 9 2026): Grounded, in real deaths. Your
  playtest: "works well".
- **Phase 2 built** (alpha.25, Nov 9 2026): Arcade, its finishers, the
  headshot hang, and the setting's third choice. Awaiting your playtest
  ([RAGDOLL_TESTING.md](RAGDOLL_TESTING.md), top section).
- Phases 3 and 4 not started.

It comes after the active-ragdoll work ([RAGDOLL_PLAN.md](RAGDOLL_PLAN.md),
phases 0 to 5, alpha.21).

## The goal

The user's example: you're killed by an arrow. Your body stumbles a few steps,
and *then* it falls, and it looks natural. There are two presets, **Arcade**
and **Grounded**.

Today, the phase 3 stagger holds the torso up for 0.35 to 0.7 s while the
legs scissor on a sine wave. The feet slide, and the legs never go anywhere
in particular. This plan swaps that for real steps: each foot is placed
somewhere on purpose, it stays put once it lands, and the body falls when it
runs out of strength, not when a timer ends.

## What "IK" means for a Minecraft body

Since phase 1 was dropped, every limb is **one straight bone**: no knees and
no elbows. So inverse kinematics (working out how a limb must turn to put its
end on a target) has an exact, cheap answer. Point the limb at the target.
`HumanoidRagdoll.aimWorld` already does this.

A straight leg can't get shorter, though. If the target is closer or farther
than one leg length, the hips have to move instead, and that comes from
*where we choose to put the feet*, not from a solver. So IK is the easy part.
The real work is three things:

1. **Foot placement:** deciding where each foot goes and when.
2. **Balance:** keeping the body up for just long enough.
3. **The fall:** failing in a way that looks like a person failing.

Games call this an active ragdoll with a stepping controller. Foot placement
uses the **capture point**, the spot a body would have to step on to come to
a stop (Pratt et al., 2006). The SIMBICON walking controller (Yin et al.,
2007) uses the same idea. Everything stays physical. The body is the same
ragdoll as today, and the new code only sets muscle targets and two helper
constraints.

## How a step works (both presets)

Every tick, before the physics step:

1. **Where is it falling?** The body's centre of mass, its velocity, and the
   capture point `cp = com + v / ω`, with `ω = √(g / com height)`.
   - The sim's gravity is 32 blocks/s². A player's centre of mass sits at
     about 0.94 blocks, so ω ≈ 5.8 per second.
   - A walking death (4.3 blocks/s) has its capture point 0.74 blocks ahead.
     A sprint puts it 0.96 ahead. A body standing still has it under its
     feet.
2. **Does it need a step?** It does if the capture point is outside what the
   standing foot can hold. The step goes toward the capture point, plus each
   preset's own bias (below). It is clamped to what a 0.75-block straight leg
   can reach (about 0.9 blocks between the feet), and to the free space
   around the body, so a foot never goes into a wall.
3. **What's there?** A probe finds the ground under the target: a block top
   up to 0.5 above (a slab or stair) or down to 1 below. If there's nothing,
   the foot goes there anyway and finds nothing. That's a misstep.
4. **Swing.** The swinging leg follows an arc to the target over 0.22 to
   0.4 s, depending on speed and strength. A straight leg swinging under the
   hips would hit the ground halfway, so:
   - **Grounded** lets it scuff. The swinging foot slides with low friction,
     which is also how a dying person moves.
   - **Arcade** lifts it clear. The swinging foot ignores the ground until the
     last part of the swing.
5. **Plant.** When a foot lands, a **foot lock** pins its sole to that spot.
   This is a new point constraint in `BodySim`, and it's what games mean by
   "foot planting IK": no skating. The lock lets go when that foot lifts
   again. In Grounded it weakens with the muscles, so late steps can slip.
6. **The torso.** The existing upright hold (`BodySim.upright`) keeps the
   torso from toppling.
   - A real person does this with their hips and ankles. A six-box body has
     neither, so the hold stands in for them.
   - **How strong it is, and how long it lasts, is the biggest difference
     between the two presets.**
7. **Arms and head.**
   - The arms go out for balance (as now), or one hand holds the wound (the
     existing Clutch) while the other balances.
   - The head gets a look-at target.
8. **The fall.** The fall starts when any of these happens:
   - the body's time on its feet runs out;
   - the capture point is out of reach and there's no strength left to step;
   - a leg gives.

   Then the hold lets go, the standing leg gives way (it splays or folds),
   the arms reach for the ground, and the phase 2 muscles take over as they
   do now.

**Guarantee: every stumble ends lying down.** A hard cap lets go of the hold
whatever happens. A corpse left standing up would be the worst possible bug,
so a test checks for it over thousands of deaths.

## The two presets

The arrow example, both ways:

- **Arcade, arrow in the chest, standing still.**
  1. The body jerks back from the hit, and a hand goes to the arrow.
  2. It staggers back two or three clear steps along the line of the shot,
     turning a little toward the shooter.
  3. The last step gives, and it goes down on its face. Or it sits down hard
     and tips back.

  It takes about 1.5 s, and every arrow death reads like that: easy to
  follow, a bit like a film.
- **Grounded, the same arrow.**
  - Standing still: an arrow barely moves a person. A hand goes to the
    wound, maybe one step to catch the sway, then the legs fail and the body
    buckles where it stands, folding over the wound.
  - Walking or running: momentum carries it one to three failing steps, each
    shorter than the last, then down on its face.
  - Shot in the leg: that leg gives on its next step, and it falls to that
    side.
  - Headshot: it drops on the spot.

  It takes 0.3 to 2 s, and it's different every time, because the situation
  decides.

| | **Arcade** | **Grounded** |
|---|---|---|
| Feel | Readable, cinematic, consistent | Physical, varied, subdued |
| Who stumbles | Nearly every conscious death by blow or arrow, even standing still | Only bodies with motion to catch: moving at death, or pushed |
| Steps | 2 to 4. The count is planned (heavier blow, fewer steps) | 0 to 4, whatever the capture point needs: standing 0 to 1, walking 2 to 3, sprinting 3 to 4 |
| Time on feet | 1.0 to 2.2 s, about the same each time | 0.3 to 2 s, taken from the leg muscles (phase 2) |
| The hit | A stagger shove of 1.5 to 2.5 blocks/s along the shot, and a turn of up to 60° toward it | The struck part jolts, the body barely moves (an arrow doesn't push a person) |
| Upright hold | Strong, fading only over the last step | Weak, about the legs' own strength, fading with it |
| Feet | Long, clean steps that lift clear, slightly slow | Scuffs and drags; steps shorten as strength fades; late feet can slip |
| Wounded leg | Limps a couple of steps on it, then it goes | Gives on its first step: falls to that side |
| Torso wound | Hand on the wound while it staggers | Hand on the wound, then folds over it |
| Headshot, crush, overkill | Drops, with a 0.15 s hang first (the film beat) | Drops on the spot |
| Path | Planned: back along the shot, drifting or spinning | Wherever momentum goes |
| Ledges | Teeters at the edge, then goes over | Steps off it (a misstep) |
| Block edges, stairs | Steps up and down cleanly (open: phase 0 could only make edges falls, see "What the first playtest taught") | Can trip on an edge it didn't lift for; steps down stairs or tumbles |
| The end | A finisher, picked from the situation (below) | Whatever physics does |
| Head | Looks toward the shot for a beat | Held by the neck's strength, drooping; looks at the ground as it falls |

**Arcade finishers.** The choice depends on how the body was hit. Where more
than one fits, the entity's id breaks the tie, so every client watching sees
the same one.
- **Face-plant:** moving forward, the last step gives and it falls forward,
  the arms going out late.
- **Spin-drop:** hit from the side, it turns about a quarter and crumples.
- **Sit-down:** pushed back, it takes two back steps, sits down hard (this
  works without knees: the legs go out in front), then tips back.
- **Last look:** it turns toward where the shot came from, holds that for a
  beat, and drops.
- **Wall slump:** a wall within reach (phase 3). It stumbles into it, a hand
  or shoulder hits the wall, and it slides down to sit against it.

**Which deaths stumble at all:**
- **Both presets:**
  - blows and arrows, as above;
  - bleed-outs: a sway, a step or two to keep balance, then it sinks. The
    legs give first, as they do now.
- **Arcade only:** fire. It runs a few flailing steps before it goes down.
  In Grounded, a burning body keeps writhing (as now).
- **Never:**
  - blasts and falls (the body is already thrown);
  - water (no footing);
  - lightning (a convulsion);
  - freezing (stiff);
  - unconscious or "strings cut" deaths.
- **Which bodies:**
  - Players, zombies, skeletons, piglins and other `HumanoidModel` bodies
    stumble.
  - Villagers, illagers and animals use the generic rig from phase 4d. They
    don't step and fall as they do now.

**Grounded is calmer than today.** Today's arrow deaths throw the body more
than a real arrow would. Grounded takes most of that out, and Arcade keeps it
and adds steps on top. If you love today's deaths, that's worth knowing.

## Settings (all in the GUI)

- **Death Stumble: Grounded / Arcade / Off** is a new client setting on the
  effects screen.
  - Off keeps today's phase 3 stagger exactly.
  - The existing **Preset: Arcade** and **Preset: Grounded** buttons set it,
    the same way they set Realistic Death, and it can be changed on its own
    afterwards.
- **It's per player.** Like the injury sound sets, it only changes how things
  look on your screen. Two players can watch the same death in different
  styles, and the body may come to rest in different spots for each of them.
  That's fine, because bodies are cosmetic.
- **Nothing on the server, and gameplay doesn't change.**
  - You die the moment you die, as now. The stumble belongs to your body; you
    don't control anything during it.
  - Items drop where you died, so the body may end up one to three blocks
    from them.
- **Classic death view and Simple ragdolls** keep the 1.7 running-death
  stumble (`DeathMotion`) as it is.

## Where it lives

| File | What it does |
|---|---|
| `ragdoll/ik/Reach.java` | One-bone aim of a limb at a world point; reach checks; where a foot's sole and a hand are. Plain Java. |
| `ragdoll/Balance.java` | Centre of mass, its velocity, the capture point, the support area, and "has it fallen". Plain Java, unit-tested. |
| `ragdoll/Stepper.java` | The step cycle (stand, swing, plant), the ground and wall probes, the swing arcs, and the foot locks. |
| `ragdoll/Stumble.java` | Plans the stumble from the death and the preset (time on feet, steps, path, finisher), then drives the stepper, the upright hold, the arms and the head each tick. Replaces Behaviour's STAGGER. |
| `ragdoll/StumbleStyle.java` | `ARCADE` / `GROUNDED`, and **every tuning number in one table**. |
| `ragdoll/physics/BodySim.java` | The foot lock (a point pinned to the world, with a softness), and "ignore the ground" for a clean swing. |
| `ragdoll/physics/PhysicsWorld.java` | A ground probe and a wall probe (the client's version reads blocks). |
| Client | `RagdollCorpse.create` passes the style in; `ClientEffectsConfig.deathStumble` plus the presets; `AccessibilityScreen`; footstep and scuff sounds when a foot lands. |
| Test command | `/rdmg ragdoll <kind> [part] [kick]` gains a style and a moving start (walk or sprint). |

## Phases

Each phase ends with tests, a checklist (in `RAGDOLL_TESTING.md`, newest on
top), a commit, and alpha.N installed in both Prism instances.

### Phase 0: the stepper (what both presets stand on)

- Balance, Reach and Stepper, and the foot lock.
- A test body that only stands. You shove it with
  `/rdmg ragdoll shove [speed] [away|toward|left|right] [seconds]`, and it
  steps to catch itself while it weakens: `seconds` is how long it has on its
  feet at most (1.6 if left out). `0` is the old test body, at full strength
  until it stands still.
- **The gate (you judge it):**
  - the feet don't slide;
  - the steps land on the ground;
  - it works on stairs and slabs;
  - it looks like stepping, not skating.
- **Measured in tests:**
  - a planted foot moves less than 1 cm;
  - a 4 blocks/s shove is caught within the expected number of steps;
  - nothing passes through blocks;
  - no NaN;
  - two runs give the same result.

**What phase 0 taught (built Nov 2026):**
- **The pendulum is fast.** Minecraft's gravity is over three times ours for a
  body this size. So the stepper carries 45% of the body's weight on the
  torso (`support`), and a step's swing time depends on speed: 0.16 s when
  moving fast, 0.34 s when slow. Slow steps at speed let the body travel too
  far over the planted foot.
- **Two straight legs with both feet pinned make a rigid triangle.** A body
  caught in one stops dead and bounces back. So when a foot lands while the
  body is still moving, the trailing foot peels off. A swinging foot has
  almost no grip, so it can't brace against the floor before it lands.
- **The capture point predicts too far ahead.** A free pendulum's runs away
  exponentially, but this body is held up and braked. Steps aimed that far
  ahead overshoot and throw it back, so the step leads by only 35% of the
  body's travel while the foot is in the air.
- **A swinging straight leg would catch the floor.** It may brush up to 3 cm
  into the floor (`Body.sink`), never more, with the floor coming back
  gradually before it lands. Passing a foot right through let it get
  buried, and the floor then threw the body into the air.
- **Stairs and steps:**
  - Ground more than 0.2 above the standing foot is a wall to a stumble: it
    stops short of a riser.
  - Stepping down is a fall (alpha.23): see below.
- **Small sways are not steps.** A slow body allows its capture point 7 cm
  outside its feet before stepping.
- **Measured** in a fake world, 1 to 6 blocks a second, four directions:
  - 1 to 6 steps;
  - worst planted-foot slide 1.2 cm;
  - worst lean 20°;
  - it always ends standing.

  In the real client: 6 steps at 3 blocks a second, 6 mm slide.

**What the first playtest taught (alpha.23):**
- **A full-strength hold reads as a statue.** You called it "incredibly
  stiff", and a body shoved into a pit stayed upright with a leg stuck out
  sideways. The hold was doing the work the legs should do.
- **Balance goes first, strength after.** The torso's upright hold is now a
  channel of its own (`Stepper.balance`). It starts at 35% and is all but
  gone 40% of the way through the time on its feet; the legs' strength fades
  after that. With little balance the body has to step to stay up, each step
  catches less, and it goes over by itself, the way it was shoved. That one
  number decides between "catches itself" and "stumbles and falls".
- **Time on its feet scales the stumble.** 0.8 s: one to three steps, down
  in about 0.6 s. 1.6 s (the default): one to three steps, over in 0.4 to
  1.05 s. 3 s: a 2 blocks/s shove backs up seven steps over 2.25 s.
- **Lost footing ends it at once.** The stepper lets go when:
  - it leans past 55° (over);
  - its hips are low (down);
  - it is in the air;
  - a step's landing spot is a hole, or more than 0.2 below the foot it
    stands on (a pit, a ledge, stairs or a slab down): let go within a
    quarter of a second, and the ragdoll falls in;
  - it has stopped moving while leaning more than 35° (lying against stairs
    or a wall).

  So block edges are trips and falls for now, in both presets. Arcade's
  "steps up and down cleanly" needs knees, or a hop: decide in phase 2.
- **A body let go keeps the pose it was caught in.** Its joints rest in the
  pose it was released in, so one that fell forward propped itself on its
  hands and toes like a table for three seconds. Once let go, the rest pose
  now eases back to the one it died in (0.8 s).
- **Measured**, weakening over 1.6 s, 1 to 6 blocks a second:
  - shoved back or forward it always goes over the way it was shoved, 0.9 to
    3.2 blocks from where it stood;
  - soft sideways shoves (1 to 3) are still caught, and it crumples where it
    stands at 1.6 s;
  - worst planted-foot slide 1 cm; worst sink into the floor 3.3 cm.

  In the real client: 3 steps at 3 blocks a second, over after 0.45 s, down
  1.6 blocks along the shove.

### Phase 1: Grounded

- Wired into real deaths: arrows, swords, mob hits, bleed-outs.
- Time on feet comes from the muscles.
- The rest of Grounded's column:
  - wounded legs buckle;
  - feet scuff;
  - bodies trip on block edges and step off ledges.
- It replaces the STAGGER reaction. A death on the run goes through the
  stepper instead of momentum alone, and the running-death game test still
  has to pass: at least 2 strides, at least 1.4 blocks, face first.
- **Your own death in first person:** the camera rides a walking head now,
  so its smoothing is checked against the bob of the steps.
- The setting arrives here (Grounded / Off).

**What phase 1 taught (built Nov 9 2026, alpha.24):**
- **The game's knockback would end it before it began.** A hit sends a body
  off at 8 blocks a second each way: made to throw a living player back a
  block, and enough to throw this one off its feet before a step. Grounded
  keeps 5% of it along the ground and none of the lift. The blow itself goes
  into the part it struck: all of it into a limb or the head, a fifth into
  the torso (most of the body's weight).
- **A body that catches itself topples backward.** The stepper stops it
  dead and upright, and when the hold goes it tips whichever way it was
  nudged last, often onto its heels. So the torso is held a little forward
  of upright (`Stepper.hunch`): 0.1 rad at first, 0.35 at the end. It hunches
  over the wound and folds forward as it goes, which is also what the plan
  asked of a torso wound. The stepper's own test body doesn't hunch (off
  unless a style sets it).
- **Time on its feet** is twice what the leg muscles alone would take to
  fade to a tone of 0.5, kept within 0.3 to 2 s. The rest of the body's
  muscles are stretched over the same time, so the arms and neck weaken on
  the same clock as the legs. In the real client:
  - a real arrow in the chest (violence 0.4): 1.08 s planned, one step, off
    its feet after 0.65 s, 1 block from where it stood, face down;
  - shot at a sprint (0.7): 0.79 s, two strides (one foot put down, the
    other swung through), 2.6 blocks, face first;
  - a walking body shot in the leg: 0.47 s, the leg gave after 0.35 s;
  - a husk killed by a player's blow: 1.08 s, two steps, 1 block, in a heap;
  - a bleed-out: 1.35 s, no steps, sinks 1 block from where it stood.
- **A wounded leg** (broken, limp, or the one the killing blow struck) has
  40% of a leg's strength and the body 60% of its time. It gives once it has
  carried the body alone for a tenth of a second, and the body is tipped
  over it. Two wounded legs, or a missing one, is nothing to stand on: it
  falls the old way.
- **Stiff legs prop a dead body up.** Let go of against a wall, a riser or
  the side of a hole, it can stay leaning there on two sticks: it has no
  knees to fold. A body still upright 0.6 s after it was let go of loses
  the grip of its feet and folds at the hips until it sits (4 s at most).
  "Upright" needed care: leaning less than 45°, head 1.2 above the lower
  foot, that foot on something, and nothing under the chest for 0.7. One
  lying up a flight of stairs leans no more than the stairs do, and one
  hanging in a hole by its arms has nothing under its feet. Neither is
  standing, and neither is made to slump.
- **A 1x1 hole has no room to lie down in.** A body that goes in feet first
  wedges at the shoulders or sits on the bottom. Accepted: the tests ask
  only that it isn't held up, isn't in a block and is in one piece.
- **Leg length.** The stepper's reaches and step heights are in blocks,
  tuned on a player's 0.7 leg. Bodies with legs outside 0.55 to 0.9 (baby
  zombies, endermen) fall the old way.
- **The late movement is the twitch.** A body that moves again seconds
  after it settled is the death twitch from phase 3. Tests must not ask for
  stillness at a fixed time.
- **First person: the head is the wrong thing to ride.** Grounded lets the
  head hang loose, and the view rode it: at a sprint it turned 12.9° a tick
  (the camera's own limit), 7.4° on average, and rolled 13.8°. While the
  body is on its feet the view is now carried by the **chest**: it turns as
  the chest has turned since death, from where the eyes sat on it. Same
  death: about 5° a tick at most, 2.3° on average, roll 2°, bob 0.06 blocks
  (8.6° a tick is its limit). Those are from ticks with no screenshot in
  them: the test framework draws a frame of its own for each one, a whole
  tick long to the view, which then chases twice in that tick. The head's
  numbers above were taken with screenshots in, so they read a little high.
  It eases back to the head over 0.3 s once the body is let go of, and the
  fall is seen as before. The client game test fails if the head carries it.
- **Sound.** Each step that lands is heard (duller and heavier than a
  walking step), and a planted foot that slips another 5 cm scuffs.
- **A stride is not always a step put down.** A body let go of with a leg
  swung well ahead of the other (more than 0.25 along its travel) took that
  stride though the foot never landed: it is counted (`Stepper.strides()`),
  and the running-death rule is judged by strides.
- **A raked foot was never put down.** A foot lands once it is out of the
  floor it swung through, judged by the lowest corner of its sole. The
  leading leg of a body going down at a run is raked some 45° with the hips
  sunk, so a corner is always under the ground: the swing was given up
  after 2.5 times its length and the body hopped over a foot it was not
  standing on. Grounded forgives the tilt (`rakedFoot 1`: the middle of the
  sole is what counts). The stepper's own test body does not (0).
- **Where in its stride it died decides the first step.** The game takes
  the stride from the body itself, so it differs every death. A body that
  died with its feet together puts its first foot down short, pivots over
  that straight leg (past 55° within 0.3 to 0.45 s) and is over before the
  second foot arrives: one step, face first, 1.3 to 3 blocks on. Some 6% of
  running deaths; one in seven walking into the shot. Left as it is: it is
  what a weak hold on the torso does, and it reads as variety.
- **Measured** over 3744 deaths on flat ground (every kind, part,
  direction, speed and stride phase), eight seconds each:
  - every one ends lying down, no part deeper than 0.08 in a block, and
    in one piece;
  - none takes more than 4 steps;
  - standing still: over 80% take one step at most, all lie within 1.6
    blocks;
  - walking and sprinting: 95% take two strides or more and get 1.4 blocks
    or further, 92% end face first, and a sprinter is down within 5.5
    (that sweep has six points of a stride; see the next);
  - stairs up and down, a ledge (90% of running bodies go over it), a wall
    and a pit, each with 168 deaths: the same rules hold.
- **Measured round the whole stride:** 768 deaths, walking and sprinting,
  arrow and blow to the torso from every side, at 48 points of a stride:
  - walking: 93% put two feet down, 94.5% take two strides, 97% get 1.4
    blocks or further, 99.5% end face first. Walking into the shot is the
    worst of it: 87.5% two strides, 92% 1.4 blocks, none under 0.95;
  - sprinting: 95% put two feet down, 99% take two strides, 99.7% get 1.4
    blocks or further, 98% end face first;
  - one of the 768 (a sprinter shot in the back) comes down low on a leg
    stuck out in front of it, is stopped dead by it and topples backward
    half a block from where it died. Known, rare, left.

### Phase 2: Arcade

- The shove and turn, the planned step count, the steps that lift clear.
- The finishers: face-plant, spin-drop, sit-down, last look.
- The headshot hang.
- The preset buttons are wired to the setting.

**What phase 2 taught (built Nov 9 2026, alpha.25):**
- **Grounded didn't move.** Everything Arcade added to the stepper is off
  unless a style turns it on. Over the 3744 deaths of the Grounded tests,
  every number of every death is the same as alpha.24's.
- **The shove is spent in a step or two.** A shove of 1.5 to 2.5 blocks a
  second (heavier blow, harder) is caught by the first step, and a body
  standing on two planted feet can't be pushed along, only tipped. So the
  stumble is kept going at 45% of the speed it set off at until its
  finisher, along the way it faces (back or forward).
- **Straight legs can't step sideways.** Shoved across, the leading foot
  steps out wide, the trailing one can't cross it, and the hips sink to the
  floor. So only 30% of a shove's sideways part is kept, and the body turns
  toward the shooter quickly (60° at most, over 0.35 s): hit from the side,
  it ends up stepping backward. A face-plant doesn't turn (it looks where
  it's going).
- **Slow steps only when slow.** "Slightly slow" steps take 0.48 s at a
  standstill but 0.18 s at 3 blocks a second. A slow step at walking speed
  is aimed where the hips will be a third of a second on; the body brakes
  meanwhile, and the foot lands in the splits a block and a half ahead. A
  beat on each foot (0.22 s, gone at 3 blocks a second) gives the stagger
  its rhythm and its length.
- **A slow step needs the leg behind it.** The stepper lifts a foot the body
  has left behind. During a slow step that was the only foot down: the hips
  sank to the floor, and the stepper's "down" rule needs a planted foot, so
  the body went on being held while it sat on the ground. Arcade keeps that
  foot down until the other lands (`holdAlone`), and carries 70% of the
  body's weight (Grounded 45%). Before both, a third of Arcade's bodies
  spent half a second or more sunk on their hips; now 9 in 2112.
- **Mid-air support floated them off.** Grounded's "carry the weight while
  both feet are up" (for running deaths) lifted Arcade's slower bodies off
  the ground. Arcade has none.
- **Time and steps.** Steps are 4 for the lightest blow and 2 for the
  heaviest (a sit-down at most 2, a last look at most 3). The finisher comes
  with the first foot put down from (steps − 1) × 0.42 + 0.5 s on (1 s at
  the earliest), and at (steps × 0.42 + 0.5) s at the latest. A body that
  died walking first puts down the foot it had in the air: that one isn't
  counted.
- **The finisher is picked by the way it is sent off**, against the way it
  faced: within 60° of forward a face-plant, within 60° of backward a
  sit-down or a last look (the entity id picks), otherwise a spin-drop.
  Within a cosine of 0.15 of those borders the entity id picks between the
  two.
- **Losing its footing early.** On open ground a planned body that sinks
  onto its hips, goes over, or has no foot down for 0.6 s goes down with
  its own finisher a moment early, not a generic fall. At edges, holes and
  walls it falls as Grounded's do.
- **The headshot hang needed a new fact.** `Muscles.awake` is zeroed
  whenever the strings are cut, so it couldn't tell a headshot from a body
  that was out cold: `Muscles.conscious` keeps that. The hang is 3 ticks:
  torso held upright, limbs stiff, 80% of the weight carried, the head
  loose (it snaps); then the drop from before, launch and all. Unconscious
  bodies don't hang.
- **"Lift clear" is a skim.** A straight leg can't lift its foot. The
  swinging foot ignores the floor for 80% of its swing (it may pass 7 cm
  into it, and is aimed to skim its top) and only then comes down: nothing
  catches or drags. 12 cm showed as a foot in a block.
- **The unit tests outgrew 512 MB.** Two caches of 3744 whole ragdolls (one
  per style) left the test JVM collecting garbage for minutes and dropped
  results; it has 2 GB now, and the suite runs in 30 s.
- **Measured** over 3744 Arcade deaths on flat ground (every kind, part,
  side, speed and stride phase), eight seconds each:
  - every one ends lying down, in one piece and in no block;
  - of 1152 conscious blows and arrows to the torso or arms, 1146 go down
    with their finisher (32 of them early), 6 lose their footing in the
    air;
  - steps before the finisher: 94.5% take 2 to 4 (0 to 5 in all; the
    0 and 1 are walkers stopped dead by their stride foot);
  - time on its feet, finishers on time: 2 steps 1.05 to 1.35 s, 3 steps
    1.35 to 2.25 s, 4 steps 1.8 to 2.2 s;
  - face-plants end face down 99%, sit-downs on their backs 100%,
    spin-drops on their backs 96%, last looks 84%;
  - standing, shot from any side, it goes 1.6 to 2.4 blocks along the shot
    (median);
  - a leg hit: 89% go down over that leg, after 2 or 3 steps;
  - every headshot, crush and overkill hangs exactly 0.15 s;
  - stairs up and down, a ledge, a wall and a pit (840 deaths): the same
    rules hold. One body on the stairs up rests propped on them for two
    seconds after its face-plant, then slides back down and sits a moment
    at the bottom.
- **In the real client:** an arrow from the front (two runs, two entity
  ids): 2 steps and a sit-down, down after 18 ticks, 2.2 blocks; and
  3 steps and a last look, 24 ticks, 2.6 blocks. From the side: turned
  toward the shooter, 3 steps and a spin-drop, 19 ticks, 1.7 blocks. A
  headshot hung 0.15 s and dropped. Both preset buttons set the setting.

### Phase 3: reaching for the world (both presets)

- **Hands land where the ground really is:** the brace reaches for the actual
  spot the hands will hit, not just "forward and down".
- **Walls:** a hand reaches for a wall within reach, and the wall slump
  finisher goes in.
- **Ledges:** Arcade teeters at the edge.
- **The head:** Arcade looks toward the shot; Grounded looks at the ground
  ahead as it falls (a protective reflex).

### Phase 4 (optional): the living

Only if you want it after phase 3.
- **Knockback steps:** a hit that doesn't knock you down makes the legs step
  to stay under the body, instead of vanilla's slide with frozen legs. This
  is foot planting on top of the phase 5 flinch, seen on other players and
  mobs.
- **An IK get-up from a knockdown:** push up on the hands, walk the feet in,
  stand. It would replace today's blend about the hips, if that looks stiff
  to you.

## Scenes you judge it by

1. Arrow in the chest, standing still, in each preset.
2. Arrow while walking toward a skeleton; arrow while sprinting away.
3. Arrow in the leg.
4. Headshot.
5. A zombie killed with a sword (zombies stumble too).
6. Killed on stairs (going up and going down), at a ledge, beside a wall, in
   a one-wide tunnel.
7. A bleed-out: sway, a step or two, sink.
8. Your own death in first person, each preset. It must not make you feel
   sick.
9. A friend watching your death, if one is on.
10. In water: no stumble. Burning: Arcade runs, Grounded writhes.

## Tests

- **Unit and trace tests, in a fake world:**
  - a planted foot slides less than 1 cm;
  - over a sweep of hits × speeds × slopes, each preset takes the number of
    steps its column says;
  - every stumble over thousands of deaths ends lying down within the cap,
    and none ends standing;
  - no part ends inside a block;
  - the feet never pass through each other;
  - a run gives the same result bit for bit when repeated;
  - the cost per body per tick is measured.
- **The trace tool** (`RagdollTraceTest` with `RD_*` variables) gains
  `RD_STYLE` and `RD_SPEED`, so a stumble can be printed step by step and
  tuned without starting the game.
- **Client game tests:**
  - an arrow death in each preset, with screenshots mid-stumble;
  - the running-death test on the ragdoll path;
  - the existing suites keep passing.

## What might not work (and what we'd do)

- **A body with no knees or ankles can't really balance.** The upright hold
  does it instead.
  - Too strong, and the body looks like a puppet on a string (floaty).
  - Too weak, and it falls before it can step.

  Most of the tuning is finding that line, and the two presets sit at either
  end of it on purpose.
- **A stiff leg swinging forward hits the ground.** Grounded scuffs it and
  Arcade lifts it clear. If neither looks right, the fallback is to tilt the
  hips up on the swinging side.
- **A strong foot lock can fling the body when it lets go.** It fades in and
  out over a couple of substeps.
- **Tight spaces:** in a one-wide tunnel the feet have nowhere to go. The
  steps are clamped to free space; if there's no room at all, the body
  buckles where it stands.
- **Multiplayer:** a stumble magnifies small differences, so two clients may
  see the same body end up a block or two apart. It's cosmetic and can't
  affect anything.
- **Animals don't stumble.** Four legs need a different gait controller, so
  that's a later idea and isn't in this plan.

## Open decisions (yours)

- **Default when no preset has been pressed:** Grounded (suggested, since the
  mod is called Realistic Damage) or Arcade.
- **Arcade's headshot hang:** keep or cut.
- **Arcade at a ledge:** teeter, then go over (suggested), or turn away from
  it.
- **Burning run in Arcade:** in or out.
- **Phase 4 (the living):** do it at all.

## Size, roughly

- **Phase 0** is the big one: one long session.
- **Phases 1 and 2:** medium each, then tuning from your playtests.
- **Phase 3:** medium.
- **Phase 4:** optional and large.
