# Ragdolls: your in-game checklist

## Stumbling deaths, IK phase 2: Arcade (alpha.25)

Arcade is the **cinematic** one: readable, a bit theatrical, about the same
every time. The hit **shoves the body back along the shot** and turns it
toward whoever shot it; it takes **2 to 4 long, clean steps** (the heavier
the blow, the fewer) while it's held up firmly; then it goes down with a
**finisher** picked from how it was hit. About 1 to 2 seconds on its feet.

The setting on the effects screen is now **Death Stumble: Grounded / Arcade
/ Off**, and the preset buttons set it: **Preset: Arcade** picks Arcade,
**Preset: Grounded** picks Grounded. The default is still Grounded (one of
your open decisions). Arcade needs Bodies set to Ragdoll, like Grounded.

The finishers:
- **Face-plant** (it was going forward: shot in the back, or running): the
  last step gives and it falls on its face, hands out late.
- **Spin-drop** (hit from the side): it turns on, about a quarter, and
  crumples.
- **Sit-down** (pushed back): back steps, sits down hard with its legs out
  in front, then tips onto its back.
- **Last look** (pushed back, the other half of the time): it stops, faces
  whoever shot it for a beat, and drops.

Which of the last two you get comes from the body itself (its entity id),
so everyone watching sees the same one. Run the command a few times to see
both.

Tips: `/rdmg ragdoll ...` always strikes from where you stand. For a hit
from the side, look at a live mob (or a `/summon mannequin`) from its side
and run the command with it under your crosshair, or shoot a zombie side-on
with a real bow.

- [ ] **1. Arrow from the front, standing:** `/rdmg ragdoll projectile torso 0.3`
      Jerked back with a hand going to the arrow, 2 or 3 steps back away
      from you, then a sit-down or a last look. In my test run: 3 steps
      back, a last look, down after about 1.2 s, 2.6 blocks from where it
      stood.
- [ ] **2. Arrow from the side:** a mob side-on (see the tips). It turns
      toward you (up to 60°) as it staggers back, then spin-drops. In my
      test run: 3 steps, down after about 1 s.
- [ ] **3. Shot in the back, and at a sprint:**
      `/rdmg ragdoll projectile torso 0.3 sprinting away`, and
      `walking away`. It carries on forward a few steps and face-plants.
- [ ] **4. Light versus heavy:** `/rdmg ragdoll blow torso 0.1` (about four
      steps, near 2 s) against `/rdmg ragdoll blow torso 0.9` (two steps,
      about 1 s).
- [ ] **5. Shot in the leg:** `/rdmg ragdoll projectile right_leg 0.3`.
      It limps a couple of steps on it, then goes down over that leg.
- [ ] **6. Headshot hang:** `/rdmg ragdoll projectile head 0.3`, a crush,
      or a huge overkill. It **hangs where it was hit for a beat** (0.15 s,
      three ticks), stiff, then drops the way it always has. Keep or cut?
      (One of your open decisions.)
- [ ] **7. The steps:** do they read as clean? Straight legs can't really
      lift, so the swinging foot passes over the floor rather than catching
      on it (Grounded's scuffs and drags). Tell me if they look like they
      skate.
- [ ] **8. Terrain in Arcade** (`sprinting away` aimed at each): stairs up
      and down, a ledge, a wall. It should end lying down every time.
- [ ] **9. Your own death in first person** in Arcade, standing and at a
      sprint. The view rides your chest while you're on your feet, as in
      Grounded.
- [ ] **10. Preset buttons:** press Preset: Arcade and check Death Stumble
      shows Arcade; Preset: Grounded, and it shows Grounded.
- [ ] **11. Back to Grounded:** everything from alpha.24 should be exactly
      as it was (my tests check it death for death).

Things I know about:
- About 1 death in 200, a walking body that's shot puts its stride foot
  down and gets stopped dead by it: it stands a moment, or goes straight to
  its finisher with no stagger steps.
- A standing body can take one step more than planned (up to 5).
- The last look drops in a heap, and that often ends on its back.

## Stumbling deaths, IK phase 1: Grounded (alpha.24)

This is the one in normal play. A body killed by **a blow, an arrow or
blood loss**, that still had a mind and two legs, **stays on its feet for a
moment** (0.3 to 2 s, from its own leg muscles), stumbling, and then goes
down. It works on players, zombies, skeletons, piglins and the mannequin.

New setting on the mod's effects screen: **Death Stumble: Grounded / Off**
(default Grounded; it needs Bodies set to Ragdoll). Arcade comes in phase 2.

Grounded is the **subdued** one, on purpose: an arrow doesn't push a person,
so a body standing still barely moves from where it stood. The steps come
from the motion it already had.

You can use real kills, or the test command, which now takes a moving start:

`/rdmg ragdoll <kind> [part] [kick] [walking|sprinting [toward|away|left|right]]`

It places a mannequin 3 blocks in front of you and kills it a second later
(or kills the live mob under your crosshair instead). With `walking` or
`sprinting` it dies as if going that way as you see it. Stand to the side to
watch.

- [ ] **1. Arrow in the chest, standing still:**
      `/rdmg ragdoll projectile torso 0.3`, and a real bow on a zombie.
      The chest jolts, it hunches, maybe one step, then it folds forward and
      lies about where it stood. About a second.
- [ ] **2. Shot while moving:**
      `/rdmg ragdoll projectile torso 0.3 sprinting away`
      `/rdmg ragdoll projectile torso 0.3 walking toward`
      (also `left`, `right`). Two or three failing strides, each shorter,
      then down on its face a few blocks on. And for real: shoot a zombie
      that is walking at you. It differs every time, by where in its stride
      it died: now and then one pitches straight onto its face after a
      single step (about one in seventeen; one in seven walking at you).
      Tell me if that reads as variety or as a mistake.
- [ ] **3. Arrow in the leg:**
      `/rdmg ragdoll projectile right_leg 0.3 walking away`
      `/rdmg ragdoll projectile left_leg 0.3`
      That leg gives the first time it has to carry the body, and it goes
      down to that side. Quicker than a chest shot.
- [ ] **4. Headshot:** `/rdmg ragdoll projectile head 0.3`. No stumble: it
      drops on the spot. Same for a crush or a huge overkill.
- [ ] **5. A zombie killed with a sword** (and a skeleton, a piglin). It
      crumples in a step or two. It isn't thrown back the way it used to be.
- [ ] **6. Terrain** (use `sprinting away` aimed at each):
      - stairs up: it trips on them and ends lying on the steps;
      - stairs down and a ledge: it steps off and falls, limp;
      - a wall: it goes down against it, doesn't walk on the spot, and
        doesn't stay propped (it folds at the hips and sits within a few
        seconds);
      - a one-wide tunnel: it should go down where it stands. (My tests
        don't cover this one, so look closely.)
- [ ] **7. A bleed-out:** `/rdmg ragdoll fade`. It sways, a step at most,
      then sinks. The longest of them (about 1.3 s).
- [ ] **8. Your own death, first person** (Death View = Ragdoll): get killed
      by a skeleton or a zombie, standing and at a sprint. While your body is
      on its feet the view **lurches with your chest**: it doesn't whip about
      with the head. Then it goes down with you. Tell me if it makes you feel
      sick: that's the one thing I can't judge.
- [ ] **9. A friend watching you die**, if one is on: they see the same
      stumble (it may end a block off from where you saw it).
- [ ] **10. No stumble where there shouldn't be one:** killed in water, by a
      blast, a fall, lightning, freezing, or while unconscious: it falls as
      before. A burning body writhes as before.
- [ ] **Sound:** each step lands with a dull footstep, and a planted foot
      that slips is heard scuffing.
- [ ] **Death Stumble = Off** gives back alpha.21's deaths exactly (the old
      stagger).

Known, on purpose or for later:
- **Baby zombies and endermen fall the old way.** The stepper is tuned on
  legs about a player's length.
- **Villagers, illagers and animals don't stumble** (the generic rig).
- **Block edges are trips.** A stumbling body can't step up or down cleanly:
  it falls there. That's Grounded's column, and Arcade may get better.
- **In a 1x1 hole** a body hangs by its arms or sits. There's no room to lie.
- The mannequin from the command is killed at violence 0.7, so its stumbles
  are shorter (about 0.8 s) than a real arrow's (about 1.1 s).

What I'd like to hear:
- **Too subdued, or right?** Standing bodies hardly travel now. If you miss
  the old throw, that's what Arcade (phase 2) keeps.
- **Time on its feet:** too short, too long?
- **Is Grounded the default you want** when no preset has been pressed, or
  should that be Arcade once it exists?

## The stepper, loosened (alpha.23)

After your screenshot ("incredibly stiff", upright in a pit with a leg stuck
out). The test body no longer stands like a statue: it **weakens while it
stumbles**, takes a few steps, and goes over by itself. Still not in normal
play; same command, one new argument:

`/rdmg ragdoll shove [speed] [away|toward|left|right] [seconds]`

`seconds` is how long it has on its feet at the very most (1.6 if you leave
it out, up to 8). `0` is the old alpha.22 body: full strength until it stands
still, then it drops.

- [ ] **It stumbles, then falls:** `shove 3 away`. It backs up two or three
      steps, arms out, leaning further each step, and goes over on its back
      in under a second.
- [ ] **It falls the way it was shoved:** `away` on its back, `toward` on its
      face, both well away from where it stood.
- [ ] **Loose, not stiff:** arms swing out, the head lags, the torso leans.
      Nothing looks held on a pole.
- [ ] **The pit from your screenshot:** `shove 5 away` with a 1x1 hole behind
      it. It drops in (or sprawls across it) as a limp body. Never upright,
      never with a leg held out.
- [ ] **A ledge and stairs down:** it goes off the edge and falls, limp.
- [ ] **Stairs up and a wall:** it falls against them and slumps. It doesn't
      stay propped, leaning, for long.
- [ ] **Once it's down it lies flat:** not propped on its hands and toes, no
      arched back.
- [ ] **Longer and shorter:** `shove 2 away 3` (a long stagger, about seven
      steps), `shove 3 away 0.8` (a step and down). Which length feels like
      "shot by an arrow"? That number becomes Grounded's default.
- [ ] **A soft shove:** `shove 1 left`. It catches itself, sways, and
      crumples at 1.6 s. (Known: which way it crumples is arbitrary.)
- [ ] **No shove:** `shove 0`. It stands, sags, and falls on its face at 1.6 s.

Tell me what still looks **stiff or fake**, and where: legs, torso, arms,
head, or the fall itself. And which `seconds` felt right.

## Stumbling deaths, IK phase 0: the stepper (alpha.22)

(Superseded by the section above: the full-strength body it describes is now
`seconds` = `0`.)

Nothing changes in normal play yet. This phase is the foundation both presets
stand on: a body held on its feet that **steps to catch itself**. You test it
with a new command:

`/rdmg ragdoll shove [speed] [away|toward|left|right]`

It places a mannequin 3 blocks in front of you, facing you (or uses the body
under your crosshair). A second later its ragdoll is held up and shoved that way
as you see it, at `speed` blocks a second (3 if you leave it out, up to 8). It
steps until it stands still, holds still for three seconds, then drops like a
normal body. Walk round to the side to watch it.

- [ ] **The steps look like steps:** legs alternate, each foot lands somewhere
      and the body goes over it. Not skating, not a cartoon scurry.
- [ ] **Planted feet stay put** while the other foot is in the air.
- [ ] **It gets harder the harder you shove:** `shove 1` is a step or two,
      `shove 3` three or four, `shove 6` four or five, and it stays upright.
- [ ] **Every direction:** `away` (it backs up), `toward` (it lurches at you),
      `left` and `right` (it side-steps, the other foot following).
- [ ] **It squares up:** if it stops with its feet wide apart, it brings one
      foot back in beside the other.
- [ ] **A wall:** shove it at a wall. It stops in front of it, not in it.
- [ ] **Stairs up:** it stops at the bottom step. It doesn't climb (a straight
      leg can't), and it doesn't kick the riser and bounce off.
- [ ] **Stairs down / a slab edge:** it drops onto the lower step and stands
      there. (Known rough edge: it can lurch sideways on the way down.)
- [ ] **A ledge:** it stops at the edge if it can; if not, it goes over.
- [ ] **The drop:** after three seconds still, it falls like any other body.
- [ ] **The head** stays steady while it steps (it doesn't flop about).

Tell me how it **feels**: too floaty, too stiff, steps too quick or too slow,
too many or too few. Those are all single numbers to change, and phases 1
(Grounded) and 2 (Arcade) are built on top of whatever you settle here.

## Phase 5: hit flinches (alpha.21)

New setting **Hit Flinches** (On/Off). A living body that's hit and stays
standing reacts where it was struck, on top of its normal animation, and
springs back within about half a second.

- [ ] **Hit in the head:** it snaps back (or sideways, from the side).
- [ ] **Hit in an arm:** that arm jerks back.
- [ ] **Hit in a leg:** the leg buckles and the body dips for a step.
- [ ] **Hit in the body:** the whole body rocks away from the blow.
- [ ] **A friend fighting:** you see them flinch. You see your own flinch in
      F5.
- [ ] **A flurry of hits:** they stack but never fling a limb silly.
- [ ] **Hit Flinches off:** the vanilla red flash only.

## Phase 4d: animals and other mobs (alpha.20)

Mobs that aren't humanoids now ragdoll too, built from their own models: cows,
pigs, sheep, wolves, cats, horses, spiders, chickens, parrots, creepers,
villagers, illagers, iron golems, striders, and so on. Bosses, slimes, fish,
squids, ghasts, guardians, bats and phantoms keep vanilla's death.

- [ ] **Animals** keel over onto their side, toward where the blow pushed them.
- [ ] **Spiders** often end on their backs, legs in the air.
- [ ] **Two-legged bodies** (villagers, golems) topple over.
- [ ] **Their bodies stay** like everyone else's (Bodies Stay).
- [ ] **A mob farm** doesn't stutter: past 16 ragdolls they fall the simple way
      or vanilla's.
- [ ] **Odd models** look sane, or at least not broken: a horse, a llama, a
      fox, a goat, an axolotl, a frog, a camel, a sniffer.

## Phase 4c: knockdown ragdolls (alpha.19) — your ask

New settings: **Knockdown Ragdolls** (On/Off, client) and the server rule
**knockdownFromBlows** (`/rdmg config`).

**What knocks you down now:**
- a hard landing (as before);
- passing out (as before);
- **new:** a blast that doesn't kill (about 7.5 damage or more to a
  20-health body);
- **new:** a blow from a big hitter (iron golem, ravager, warden, hoglin,
  zoglin);
- **new:** any single blow of about 13.5 damage or more.

- [ ] **A creeper that doesn't kill you.** You're thrown as a ragdoll, your
      view rides your head down, you lie there a moment, then you get up and
      the view blends back to where you're looking.
- [ ] **An iron golem's swing** knocks a zombie (or you) down as a ragdoll, and
      it gets back up.
- [ ] **Passing out** collapses you as a ragdoll. While you're out, a friend
      dragging you drags the ragdoll across the ground. When you come to, you
      get up.
- [ ] **A hard landing:** the same, from the fall.
- [ ] **Dying while down:** the same body carries on as the corpse, with no
      jump.
- [ ] **No legs:** a body with both legs gone stays down (the crawl), as
      before.
- [ ] **Getting up** looks like sitting up and rising, not a plank swinging
      up from its feet.
- [ ] **Watching a friend** get knocked down: their ragdoll stays where their
      body is, and doesn't wander off from it.
- [ ] **Knockdown Ragdolls off:** the old prone drop.
- [ ] **VR:** your own knockdown stays the old prone drop (no view turns);
      others still ragdoll.

## Phase 4a and 4b: bodies that stay, and your own death (alpha.17 and 18)

New settings: **Bodies Stay** (5 s / 30 s / 2 min / 5 min, default 30 s) and
**Death View** (Ragdoll / Classic, default Ragdoll).

- [ ] **Bodies stay.** Kill something, then wait. The body keeps lying where it
      fell after the 5 seconds the server keeps it, then sinks into the ground
      at the end. There's no puff of smoke where it died.
- [ ] **Your own body stays.** Die, respawn, walk back: your body is still
      lying where it fell (for the Bodies Stay time).
- [ ] **Your own death, first person.** The camera is in your ragdoll's eyes and
      turns with its head, smoothed so it isn't nauseating. Thrown off a
      roof, you see the tumble and end up looking at the sky or the dirt.
      **Death Intensity** lowers how much of the head's turn the view follows.
- [ ] **Your own death, F5.** Third person looks at your ragdoll, not at the
      spot you died.
- [ ] **Death View = Classic** gives back the old death camera exactly.
- [ ] **VR:** nothing changes (no view turns in VR); the classic VR death
      camera still runs.
- [ ] **Thud.** A body that lands hard makes a sound and kicks up floor dust.

## Phase 3: reflexes (alpha.16)

A dying body still reacts while there's a mind left in it. A headshot, or
someone already knocked out, does none of this: they just drop. Each item has
a `/rdmg ragdoll` command to trigger it on a mannequin or the mob you're
looking at.

- [ ] **Brace.** Something falling forward or sideways puts its arms out and
      lands on its hands (`/rdmg ragdoll blow`).
- [ ] **Falling backward.** The arms fling forward and out.
- [ ] **Hand to the wound.**
  - Sword to the gut (`/rdmg ragdoll blow torso`): a hand comes across the
    belly, and the body folds over it once it stops staggering.
  - Hit in a leg: the hand on that side reaches for the thigh.
  - Hit in an arm: the other hand reaches for it.
  - A bleed-out holds its worst wound.
- [ ] **Stagger.** A blow to someone standing on two good legs. They're held up
      a moment, shuffling back with arms out, then they go. A broken or
      missing leg means no stagger.
- [ ] **Blast** (`/rdmg ragdoll explosion left_leg 0.5`). The arms come up in
      front of the face and the chin tucks while it tumbles.
- [ ] **Fire or lava** (`/rdmg ragdoll fire`). It writhes, thrashing on the
      ground and slowing over about four seconds.
- [ ] **Lightning** (`/rdmg ragdoll shock`). A stiff, jerking second, then
      limp.
- [ ] **Drowning** (`/rdmg ragdoll drowning`, best in water). The arms reach up,
      then it drifts.
- [ ] **The void.** Slow, drifting limbs as it falls.
- [ ] **Death twitch.** A violent death (headshot, crush, big blast) twitches
      a limb once or twice after it has settled.
- [ ] **Nothing silly:** no limb spinning, no body sitting itself down, no
      reaction from a body that should have none.

## Phase 2: muscles (alpha.15)

Jar: `realistic-damage-1.7.0-alpha.15.jar`, in both Prism instances (alpha.14
is in `mods/replaced-jars/`).

A dying body now holds the pose it died in for a moment, then lets go. How
hard it holds and in what order comes from the body itself: its
consciousness, its injuries, its adrenaline, and how it died.

- [ ] **Headshot** (`/rdmg ragdoll projectile head 1`, or a real bow
      headshot). It drops where it stands, folding at the hips. There's no
      stiff topple.
- [ ] **Sword to the chest** (`/rdmg ragdoll blow torso`). It goes over in one
      piece for a moment, then folds as it falls.
- [ ] **Bleed-out or poison** (`/rdmg ragdoll fade`). It holds a beat, then the
      legs give first and it sinks forward. The head is the last thing to
      drop.
- [ ] **Frozen** (`/rdmg ragdoll freeze`). It tips over stiff as a board.
- [ ] **Drowned** (`/rdmg ragdoll drowning`). It's soft from the start and
      drifts.
- [ ] **Broken arm.** Break a zombie's arm in a fight (or a friend's with
      `/rdmg break <them> left_arm`), then kill it. That arm swings loose
      while the other is held.
- [ ] **Knocked out first.** Kill someone already unconscious: they drop like
      a headshot.
- [ ] **Hips.** No body folds into an upside-down V on its head and feet. Legs
      with no knees now stop folding at about 80 degrees, like real straight
      legs.
- [ ] **Bodies settle** within a few seconds and lie still: no slow creeping,
      no gap at a shoulder or hip.

## Phase 0: the physics body (alpha.14, passed)

Passed Nov 9 2026 ("the ragdolls are AWESOME"). Phase 1 (elbows, knees,
waist) was dropped by choice: six boxes is the Minecraft look. The phase 0
notes below are kept for reference.

### What changed

- Other bodies (players, zombies, skeletons, piglins, illagers, mannequins…)
  are now six real physics parts: head, torso, two arms, two legs. They land
  on their side, slide down stairs, hang off ledges, and hit walls with their
  limbs.
- The blow lands where it hit: a headshot snaps the head, a sword pushes the
  shoulder it hit, a blast throws the nearest limb hardest so the body tumbles.
- Lost limbs take no part in the fall.
- Bodies float in water and sink slowly in lava.
- New setting, **Settings → Bodies**: Ragdoll / Simple (the old plank) /
  Vanilla. **Ragdolls at Once** (default 16): any more than that, or any farther
  than 64 blocks, fall the Simple way.
- **Your own death is unchanged.** You see your body ragdoll in phase 4. For
  now you see other people's and mobs' bodies.

### Tools (need cheats on)

- `/rdmg ragdoll <kind> [part] [kick]` kills whatever you're looking at, or
  places a mannequin 3 blocks ahead and kills it a second later.
  - kinds: `blow`, `projectile`, `fall`, `explosion`, `crush`, `fire`, `lava`,
    `drowning`, `freeze`, `shock`, `void`, `fade`
  - parts: `head`, `torso`, `left_arm`, `right_arm`, `left_leg`, `right_leg`
  - kick: 0 to 2 (default 0.3). Try `/rdmg ragdoll blow head 1.5`.
- `/rdragdoll debug` draws the physics boxes over each body (grey = at rest).
- `/rdragdoll slowmo 0.2` is 5x slow motion; `/rdragdoll slowmo` goes back to
  real time.
- `/rdragdoll status` says how many ragdolls there are.

### The scenes

Tick each one and note anything odd. "Looks bad" is useful; "the arm went
through the floor on stairs" is gold.

- [ ] **1. Sword kill on flat ground** (a zombie or a mannequin). It crumples
      or goes over, with no stiff 90-degree tip.
- [ ] **2. Killed on stairs.** It slides or tumbles down them and doesn't sink
      into them.
- [ ] **3. Killed at a ledge.** It goes over the edge, or hangs off it.
- [ ] **4. Creeper point-blank, or `/rdmg ragdoll explosion left_leg 0.5`.**
      It's thrown and tumbles, the limbs move from the physics, and it lands in
      a heap.
- [ ] **5. Bleed-out, or `/rdmg ragdoll fade`.** It sags over, softer than a
      blow.
- [ ] **6. Bow headshot, or `/rdmg ragdoll projectile head 1`.** The head snaps
      back and the body drops.
- [ ] **7. A 30-block fall.** It slams and the limbs bounce.
- [ ] **8. Burning or lava.** It sinks in lava and floats in water. (Phase 3
      adds writhing.)
- [ ] **9. One leg gone.** Use a zombie that lost a leg in a fight, or a friend
      after `/rdmg destroy <them> left_leg`. It falls without the leg and the
      stump stays put.
- [ ] **10. Two of you watching one death.** Both of you see a believable fall.
      It doesn't have to be identical.
- [ ] **11. A mob farm or a big fight.** No stutter when lots of things die.
      Past 16 bodies they fall the Simple way.
- [ ] **12. With shaders (Complementary) and EntityCulling.** Bodies don't
      vanish mid-air.

### Known limits of phase 0 (on purpose)

- No elbows, knees or waist: limbs are straight sticks, by choice.
- No reactions yet (phase 3). Muscles came in phase 2.
- Bodies disappear 5 seconds after death, the same as alpha.13. Phase 4 makes
  them stay.
- Corpses don't bump into each other or other entities, only blocks.
