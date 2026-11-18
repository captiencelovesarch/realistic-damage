# Realistic Damage 2.0: what to test

## A broken arm and a broken leg in first person (Nov 18 2026)

The jar is `realistic-damage-2.0.0-alpha.4.fixes.1.firstperson.1.jar`, in
"Realistic Damage (dev)" (the fixes.1 jar is parked in `mods/replaced-jars/`).
It is fixes.1 plus the two things below. Pictures:
`previews/first_person_broken_arm_sheet.png` (sword, axe, shield, bread and an
empty hand, sound and broken, at rest and through a swing).

Looks only: damage, attack speed and aim are untouched. Off in VR. One switch,
Accessibility > Display > "First-Person Injuries" (the panic button turns it
off too).

- [ ] **1. The hold.** `/rdmg break @s right_arm`, then hold a sword, an axe, a
  shield, food, nothing. Each sits lower, turned in toward the middle and
  tipped forward; the axe and shield most, food least. Is any of it too much
  or too little? Is anything pushed too far off the screen?
- [ ] **2. The shake.** Standing still it trembles a little; sprinting more;
  during a swing most. Too jittery, or not noticeable?
- [ ] **3. The swing.** Attack with it: the arm manages a short lift, then the
  hand drops and comes back up instead of following through. Heavier things
  lift less and drop further. The hit itself lands as normal.
- [ ] **4. Each hand on its own arm.** Break only the left arm with a shield in
  the off hand: the shield sags, the sword in the right hand does not.
- [ ] **5. The limp.** `/rdmg break @s left_leg` and walk: the view dips and
  leans left on every second step. Break the right leg instead: it leans
  right. Both: a dip on every step. It should never make aiming harder (the
  crosshair itself does not move).
- [ ] **6. The switch.** Turn "First-Person Injuries" off: all of the above
  stops at once.

If a number wants changing, they are all at the top of two files:
`systems/BrokenArmHold.java` (`SAG_DROP`, `SAG_ROLL`, `SAG_TIP`,
`TREMOR_OFFSET`, `TREMOR_TWIST`, `TREMOR_SPRINT`, `TREMOR_SWING`,
`SWING_REACH`, `SWING_LIFT_TIME`, `SWING_DROP`, `SWING_DROP_TIP`; the four
numbers in each row are empty hand, light, medium, heavy) and
`systems/BrokenLegLimp.java` (`DIP`, `ROLL`, `SHARPNESS`).

## Bloodied weapons, blood on your screen, tidier commands (Nov 16 2026)

The jar is `realistic-damage-2.0.0-alpha.4.fixes.1.jar`, in "Realistic Damage
(dev)" (the corpse.1 jar is parked in `mods/replaced-jars/`). It is corpse.1
plus the four things below. Pictures: `previews/bloodied_items_sheet.png`
(every kind of mark on real items).

**Bloodied weapons.** Anything that doesn't stack (swords, axes, tools, a
bucket) keeps the blood of what it hits.

- [ ] **1. One zombie, one sword.** Cut a zombie a few times: dark streaks grow
  along the blade, in first person, in F5, and on the sword's icon in the
  hotbar and inventory. The sword must NOT dip down and come back up when the
  blood lands.
- [ ] **2. Whose blood.** Then hit an enderman (purple), a slime (green), a
  spider (blue), a friend or a villager (red). Each leaves its own colour in
  its own place, and where two wet ones overlap they run together.
- [ ] **3. How you hit.** Sword and axe leave streaks along the blade; a
  trident or spear soaks the tip; a mace leaves a splat on the head (it needs
  the target already bleeding, or the kill). A killing blow and taking a limb
  off soak it.
- [ ] **4. Drying.** Stop fighting and watch it for half a minute: it browns
  from the thin edges in, the thick middles last. While it is wet and heavy it
  drips onto the floor.
- [ ] **5. Washing.** Stand in water or in rain holding it: gone in a few
  seconds (dried blood takes twice as long). Mining with a pickaxe in the
  rain must not reset the block's cracks.
- [ ] **6. It stays on the item.** Drop it, put it in a chest or an item
  frame, relog: still bloody. The tooltip says how bloody ("Bloodied",
  "Caked in dried blood").
- [ ] **7. Your own arm.** `/rdmg bleed @s right_arm severe`, hold a clean
  pickaxe for ten seconds: your blood runs onto the grip end only.
- [ ] **8. Mobs too.** A zombie or vindicator with a weapon that wounds you
  ends up with your blood on it, and drops it that way.
- [ ] **9. Off switch.** World rules > Injuries > "Bloodied Weapons" off:
  nothing new is marked. (Your Blood slider at 0 hides it without removing it.)

**Blood on your screen from others.** No rule puts it there: it is real drops
landing on you. The spray when a wound opens flies back along the blow, and
every further hit into a body that is already bleeding now throws a few drops
back at whoever is close.

- [ ] **10.** Fight a zombie at arm's length: drops land on your screen in the
  zombie's dark red. Fight an enderman: purple. From three blocks back with a
  bow: nothing.
- [ ] **11.** Is it too much or too little? Say which, it is two numbers.

**Your body, in the inventory's own box.** The side panel is gone. The box
that held your little turning model now holds a paper doll of your skin with
your injuries on it (`previews/body_in_inventory_in_game.png`).

- [ ] **13.** Open the inventory unhurt: your skin, front on, nothing else.
- [ ] **14.** Get hurt (`/rdmg bleed @s torso severe`, `/rdmg break @s left_arm`,
  `/rdmg destroy @s right_leg`): blood on the torso that grows while it bleeds,
  the arm greyed with a crack, the leg a stump over a faint ghost, and a frame
  round each hurt region in its state's colour. Icons down the left edge say
  what kinds of trouble there are.
- [ ] **15.** Hover each region: an ordinary tooltip with its name, its state
  and what is wrong. With "Exact Numbers" on it adds the condition figures and
  your blood and consciousness, in words.
- [ ] **16.** The small button in the box's bottom right corner: click for the
  x-ray (Minecraft's own skeleton, a broken bone is parted, a lost one stops
  short), again for your ordinary model, again for the body. It remembers.
- [ ] **17.** Open the recipe book: the body moves with the window and nothing
  overlaps, at any GUI scale.
- [ ] **18.** A slim (Alex) skin, and a skin with a jacket or hat layer: do the
  arms and the second layer look right?

**Commands.** One grammar: the verb, who, which part, how much. Who can be you
(`@s`), a name, or any selector, and mobs work everywhere.

- [ ] **12.** `/rdmg break @s head` (was `breakneck`), `/rdmg break @s left_leg`,
  `/rdmg set @s blood 40` (was `setblood`), `/rdmg set @s left_arm 2` (was
  `setpart`), `/rdmg set @s consciousness 30`, `/rdmg damage @s torso 3` (was
  `damagepart`), `/rdmg bleed @e[type=zombie,limit=1,sort=nearest] torso severe`.
  The old spellings are gone.

**One jar, one place (Nov 14 2026).** Everything in progress on 2.0 is in
`realistic-damage-2.0.0-alpha.4.jar`: heads and necks, the field kit through
items.10 with getting up in stages, and the new injury sounds. It is in the
Prism instance **"Realistic Damage (dev)"**, the only place dev jars go from
now on. That instance has Vivecraft in it, so it is also the VR instance: VR
is switched on from the main menu button. **"Realistic Damage (public)"**
holds the public version (1.7.0-alpha.13) and nothing else. The old "items"
and "vr" instances are kept whole in
`~/.local/share/PrismLauncher/instances-archived/`.

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
(`/rdmg whiplash @s 0.3` for a mild one). `/rdmg break @s head` snaps your own
neck. Both take any target, for example
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
