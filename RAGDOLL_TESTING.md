# Ragdolls: your in-game checklist

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
