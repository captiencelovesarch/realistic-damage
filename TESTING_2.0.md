# Realistic Damage 2.0: what to test

## Getting up, in stages (Nov 12 2026)

The jar is `realistic-damage-2.0.0-items.8.getup.jar`: items.8 plus the new
get-up, in "Realistic Damage items" (items.8 parked in `mods/replaced-jars/`).
The preview animations were sent in the chat (`build/preview/getup_face_*.gif`).

- [ ] **1. Face down.** Knock a zombie flat so it lands on its front (a blast
  from behind it): it pushes up on straight arms, sits back on its heels,
  plants a foot and comes up off one knee, then stands. Not a plank swinging
  up about its feet.
- [ ] **2. Face up.** One that lands on its back sits up leaning on its hands,
  rocks forward into a squat with its feet planted, and rises.
- [ ] **3. It turns as it rises,** not on the ground: it gets up facing the way
  its head (face down) or feet (face up) pointed, and turns to where it looks
  only in the last part.
- [ ] **4. You, in first person.** Get knocked down (fall, blast): the view
  rises with your head through the same stages.
- [ ] **5. You, in third person (F5),** and a friend watching you: the same.
- [ ] **6. Knocked down again on the way up:** it drops straight back into the
  ragdoll.

The jar is `realistic-damage-2.0.0-alpha.3.jar`. It's **everything in one**:
heads and necks (alpha.1 and alpha.2, below) plus the field kit (items.1 and
items.2, checklist in [TESTING_FIELDKIT.md](TESTING_FIELDKIT.md)) plus the
settings-page fix. It's in all three Prism instances: "Realistic Damage",
"Realistic Damage vr" and "Realistic Damage items". Each instance's old jar
is parked in its `mods/replaced-jars/`.

Nothing in alpha.3 is new beyond the merge: the necks, the items and the fix
are each what their own rounds were. The one place they meet: the **World
rules** page (K, then World rules) now lists Broken Necks and Whiplash too,
so you can switch them off there.

The alpha.25 and alpha.26 checklists in [RAGDOLL_TESTING.md](RAGDOLL_TESTING.md)
still apply.

## alpha.2: broken necks and whiplash (phase 1, round 2)

**How a neck breaks.** Instant death, a dry crack, and a body that drops
where it stands with its head hanging at a wrong angle. No totem saves it,
and bosses never break theirs. The causes:
- **Landing head first.** Anyone out cold, knocked flat, mid-microsleep or
  gliding on an elytra lands head first. About 12 blocks onto stone does it
  (15 onto grass); from about 6 blocks it only wrenches the neck.
- **Water one block deep.** It no longer saves you from a big fall: you hit
  the bottom. Feet first, your legs take it like a normal landing. Head
  first, it's your neck. Two blocks of water or more still saves you as
  before.
- **An elytra into a wall at speed** (about 30 m/s, a normal cruise).
- **A huge blunt blow to a bare head**: a mace, an iron golem or a ravager.
  A sound head has three steps. Around 12 head trauma (about 8.5 points of
  heavy blunt) you're out cold, as before. From 16 (about 11.5 points) your
  neck snaps. From 20 the skull is crushed, as before. A helmet moves all of
  these up.

**Whiplash** is what you get just under each of those bars, and from being
knocked flat hard (a big blast, a heavy hit, a big landing). It lasts 30 to
90 s and eases off as it heals. Your view turns slower, and a quick turn hurts:
a wince, a flash of blur and red at the edges.

**Shortcuts:** `/rdmg whiplash` gives you the worst whiplash
(`/rdmg whiplash @s 0.3` for a mild one). `/rdmg breakneck` snaps your own
neck. Both take a target too, for example
`@e[type=zombie,limit=1,sort=nearest]`. You need to be in survival.

- [ ] **1. Stiff neck.** `/rdmg whiplash`, then look around slowly: it should
  feel normal. Flick the mouse: the view meets resistance and stops short,
  with no drift or catch-up afterwards, and it hurts. Does it feel like a
  stiff neck, or like input lag? This is the one the plan says gets reworked
  or cut if it feels like lag.
- [ ] **2. It heals.** Wait it out: the turns get freer over about 90 s, then
  it's gone. `/rdmg whiplash @s 0.3` is milder and shorter.
- [ ] **3. Stiff Neck: Off.** Settings → Accessibility → "Stiff Neck: OFF".
  Flicks no longer slow the view, but they still hurt.
- [ ] **4. Controller.** If you play with Controlify, the stick is capped the
  same way as the mouse.
- [ ] **5. Head first.** Glide on an elytra and dive steeply into the
  ground from 15 blocks or more: your neck snaps, because gliding counts as
  head first. A dive from about 7 blocks only wrenches it. (Also: a
  zombie knocked flat on a ledge 12 blocks up, shoved off onto stone.)
- [ ] **6. Shallow water.** Dig a pool one block deep and jump into it from
  25 blocks up: your legs get hurt, and you don't walk away clean any more.
  Do it into a pool two blocks deep: as before, nothing.
- [ ] **7. Elytra.** Fly into a wall at cruise speed: dead, neck broken. Clip
  one gently: whiplash.
- [ ] **8. Mace.** A mace hit to a zombie's head (no fall) wrenches its
  neck. A hard smash snaps it, or crushes the skull if it's very hard.
- [ ] **9. Whiplashed mobs.** `/rdmg whiplash` on a skeleton: it's slow to
  turn its head toward you, and its arrows go wide.
- [ ] **10. The ragdoll.** A body whose neck snapped drops at once, with no
  stumble, and its head flops loose wherever it lands.
- [ ] **11. Totems.** A totem doesn't save a broken neck. But if a totem saves
  you from the crash itself (an elytra into a wall, say), the neck is only
  wrenched.
- [ ] **12. VR.** In the VR instance with whiplash: smooth turning is slowed,
  snap turns come no faster than a stiff neck could make them, and turning
  your real head fast gives a buzz in both hands and red at the edges. Your
  real head is never slowed.
- [ ] **13. The crack.** Both Injury Sound styles (Visceral, Muted) have their
  own crack. Do they sound like a neck?

**Known, not done yet:**
- Only humanoid ragdolls (players, zombies, skeletons, illagers and so on)
  get a loose neck. Animals still die with a normal neck.
- An elytra landing on the ground (rather than a wall) may count as feet
  first in multiplayer: the client often stops gliding before the server
  sees the landing.

## alpha.1: heads come off (phase 1, round 1)

**How a head comes off:** hit something in the head until the head is ruined
(Disabled), then land one more serious blow with a **sword or an axe**. A
killing blow counts. Anything blunt (fists, a mace) still crushes the skull
the way it always has. In practice: an axe to a zombie's head two or three
times.

**Shortcut:** `/rdmg behead` takes your own head. `/rdmg behead <target>` takes
a mob's head (for example, look at a zombie and use `@e[type=zombie,limit=1,sort=nearest]`).
You need to be in survival for your own head.

- [ ] **1. Mob heads.** Behead a zombie, a skeleton, a villager, a pillager and a
  piglin. Each body falls without its head, the neck sprays up, and the head
  flies off along the swing, bounces, and rolls to a stop on its base or its
  crown (never face-down).
- [ ] **2. Fist vs blade.** On a ruined head, a punch crushes the skull (no
  head on the floor). An axe takes it off.
- [ ] **3. Headless zombies.** A beheaded zombie doesn't drop at once: it
  staggers blind with its arms out for about 3 seconds, ignores you, can't hit
  you, then falls. Hitting it ends it early. (Sunlight burning it doesn't.)
- [ ] **4. Picking heads up.** Walk over a zombie's, skeleton's, piglin's or
  player's head and you pick it up. It says "Severed: can't be worn" and
  won't go on your head (use key, shift-click, dragging into the slot).
  Heads with no vanilla item (villagers, illagers) just lie there and fade
  in a minute.
- [ ] **5. Placing heads.** Place a severed head on the ground and on a wall,
  break it, pick it up again: still severed, still unwearable.
- [ ] **6. The wither skull (your idea).** Behead a wither skeleton. Its skull
  hisses and flashes purple for a second and a half, and can't be picked up
  meanwhile. Then it either burns away in soul fire with a thin, high Wither
  cry, or pops into a skull you can take. The pop is as rare as vanilla's
  skull drop (2.5%, +1% per Looting level), so you'll mostly see the burn.
  A popped skull can summon the Wither.
- [ ] **7. Your own head.** In survival, `/rdmg behead`. The view goes with
  your head as it flies and rolls, and you watch your body drop. Grounded
  fades out in about 2 seconds; Arcade lets you watch for about 4. The chat
  says "<you> lost their head".
- [ ] **8. Cut to Black.** Settings → Accessibility → "Losing Your Head:
  Cut to Black". Your own beheading now goes straight to black.
- [ ] **9. VR.** In the VR instance, your own beheading cuts straight to black
  (nothing may turn a headset's view). Other heads fly as normal.
- [ ] **10. Totems.** A totem of undying doesn't save you from losing your head.
- [ ] **11. Multiplayer, if you can:** a friend sees your headless body fall
  and your head roll, and can pick your head up with your skin on it.

**Known, not done yet:**
- The flying head doesn't show its helmet. The helmet just isn't drawn on the
  headless body; it still drops when the mob dies, like normal gear.
- Animal heads (cows, pigs and so on) don't come off yet: only humanoids,
  villagers and the witch.
