# Realistic Damage 2.0, the field kit: what to test

The field kit is built on its own branch, beside the neck work, with its own
version numbers: `2.0.0-items.N`. Newest round on top.

**Combined (Nov 11 2026):** items.1 and items.2 are merged into the `2.0`
branch with the necks: the jar `realistic-damage-2.0.0-alpha.3.jar` has both
and is in all three instances. Everything below applies to it unchanged.

## items.8: the ragdoll is the body, and dragging (Nov 12 2026)

The jar is `realistic-damage-2.0.0-items.8.jar`, in "Realistic Damage items"
(items.7 parked in `mods/replaced-jars/`). Weapons on for dragging.

- [ ] **1. Smooth flight.** Blast a zombie (or knock it flat with an iron
  golem): it flies and tumbles smoothly again, no jerking.
- [ ] **2. The hitbox goes with the body.** F3+B while it flies and once it
  lands: the (low) box travels with the ragdoll and ends up where it lies,
  even in a hole next to where it was hit.
- [ ] **3. Dragging.** Sedate a zombie or villager, sneak and right-click the
  body with empty hands: "You take hold of them." and it comes with you (no
  "You let go." straight after). Sneak-click again to let go. Same for a
  downed friend.
- [ ] **4. Taking a tourniquet or sling off someone** with a sneak-click takes
  off one thing per click, not two.

## items.7: the gear you can see (Nov 12 2026)

The jar is `realistic-damage-2.0.0-items.7.jar`, in "Realistic Damage items"
(items.6 parked in `mods/replaced-jars/`). F5 to see yourself.

- [ ] **1. Bandages.** A white wrap round the middle of the limb, round the
  chest, or round the forehead. One that has soaked through goes red.
- [ ] **2. Tourniquet.** A leather strap high on the limb with its stick
  across the front.
- [ ] **3. Splint.** Two sticks down the sides of the limb, tied in three places.
- [ ] **4. Sling.** The arm held forward across your stomach in a cloth pouch,
  its strap tied round the back of your neck. In first person the arm lies low
  across the bottom of the view in the sling, and whatever that hand holds
  isn't shown.
- [ ] **5. Stethoscope.** While you listen, the earpieces are in your ears and
  the tube runs down the front of your chest.
- [ ] **6. Tinted goggles.** Real goggles on the head (strap, two violet lenses),
  not a flat picture.
- [ ] **7. On others.** The same on a friend, a zombie, a skeleton, a villager
  (a villager's arms are one crossed piece: no arm gear on it). Under armour
  the gear is hidden, as it would be.

## items.6: your items.5 fixes (Nov 12 2026)

The jar is `realistic-damage-2.0.0-items.6.jar`, in "Realistic Damage items"
(items.5 parked in `mods/replaced-jars/`). Turn **Weapons** on for Sedation
and dragging.

- [ ] **1. Sedation is short now.** One Arrow of Sedation: drowsy, stumbling,
  down at about 8 s, and back up about 12 s after that. A strong (glowstone)
  arrow is about 30 s on the floor, not 2½ minutes. The effect timer reads
  under a minute for one, about 1:15 for a strong one, not 4:20. Three doses still stop the
  breathing and kill unless you drink milk.
- [ ] **2. Hitboxes on the body.** F3+B, then knock a zombie down (a blast, or
  an Arrow of Sedation): its box drops to a low one where it lies, and the
  ragdoll stays on it, even next to a hole. It stands back up with the mob.
- [ ] **3. Dragging.** Sedate a zombie or a villager and sneak-use the body with
  an empty hand where you see it lying: you get hold of it. Same for a downed
  friend in multiplayer.

## items.5: bones, head and senses (phase 4, round 2)

The jar is `realistic-damage-2.0.0-items.5.jar`, in "Realistic Damage items"
(items.4 parked in `mods/replaced-jars/`). Turn **Medicine** on (K, then World
rules). Handy: `/rdmg break @s left_leg` (or `right_arm`, ...) breaks a limb;
`/rdmg heal @s` mends everything.

- [ ] **1. Crafting.** 2 sticks + string = a splint; any wool + string = a sling;
  glass bottle + bone meal = smelling salts; copper ingot + string = a
  stethoscope; 2 tinted glass + leather = tinted goggles. All in the Field kit tab.
- [ ] **2. Splint a leg.** Break a leg and walk: you limp. Hold use with a splint
  (4 s): "You splint your left leg." The limp is about half as bad.
- [ ] **3. Splint an arm.** Break your main arm: you swing and dig slowly, and a
  bow draws slowly. Splint it: both get about twice as quick again.
- [ ] **4. Knit.** Heal up (eat and wait, sleep, or `/rdmg heal`): "has knit. The
  splint comes off." With **long-term injuries** on, a broken limb gets "never
  be quite the same"; if it knits in a splint, you get "knit straight" and it
  heals all the way.
- [ ] **5. Sling.** Break your main arm and put it in a sling: you can't use what's
  in that hand, can't hit or dig ("Your right arm is in a sling."), and a bow in
  the other hand won't draw. Sneak and use with an empty hand in the air: the
  arm comes out and you get the sling back.
- [ ] **6. Sling heals.** With one arm slung and the other not (both broken), the
  slung one should mend about twice as fast. When it knits, the sling comes back.
- [ ] **7. Smelling salts.** Knock someone (or a mob) out with blows, then hold
  use on them with salts (1 s): they come round. Someone out from blood loss,
  underwater, or out cold on Sedation: "won't bring them round".
- [ ] **8. Stethoscope.** Hold use in the air: your own heart, slow and steady.
  Get hurt and bleed, and listen again: faster, weaker, skipping. Hold it on
  a friend or a mob for theirs. Nobody else hears it.
- [ ] **9. Tinted goggles.** Wear them (right-click, or the helmet slot): the
  world goes darker and a little violet, first person only. With a concussion,
  bright light (sun, glowstone) no longer brings the headache and the glare
  on. They don't protect your head.
- [ ] **10. Villagers.** Bandage or splint a villager: it gets treated, no trade
  screen.
- [ ] **11. Medicine off.** Every one of these refuses or does nothing; a splint or
  sling already on stays on but does nothing until Medicine is back.

## items.4: the bleeding kit (phase 4, round 1)

The jar is `realistic-damage-2.0.0-items.4.jar`, in "Realistic Damage items"
(items.3 parked in `mods/replaced-jars/`). Turn **Medicine** on (K, then World
rules). Handy: `/rdmg bleed @s left_arm minor` (or `moderate`, `severe`,
`critical`) opens a wound to treat.

- [ ] **1. Crafting.** 2 paper + string = 3 bandages; leather + stick + string = a
  tourniquet; iron nugget + string = needle and thread. All in the Field kit tab.
- [ ] **2. Bandage yourself.** With a minor or moderate wound, hold use with a
  bandage: about 3 seconds, then "You bandage your left arm." The blood drips
  much slower. A moderate one soaks through in under a minute ("has soaked
  through") and needs a new one.
- [ ] **3. Arteries.** On a severe or critical wound a bandage refuses ("It's
  spurting...").
- [ ] **4. Bandage someone else.** Right-click a friend or a villager with a
  bandage and hold. It goes on their worst wound.
- [ ] **5. Tourniquet.** On a bleeding leg: the bleed stops dead, the leg goes
  grey on the body diagram and you limp hard. Sneak and press use with an
  empty hand in the air: it comes off, the leg comes back and you get the
  tourniquet back.
- [ ] **6. Too long.** Leave one on: at about 4 minutes "has gone cold", at 5 the
  limb is lost (stump and scar).
- [ ] **7. Cauterize.** Sneak and use flint and steel in the air with a wound open:
  the bleed stops, the screen reels from the pain (it can knock you out if you
  were already fading), and you're Burned.
- [ ] **8. Stitch.** Hold use with needle and thread for 10 seconds: the worst
  wound is gone for good. Get hit while stitching and it's spoiled
  ("Interrupted"). One needle does four.
- [ ] **9. Blood bag.** Sneak and use a glass bottle in the air: you lose some
  blood and get a Blood Bag. When you're low, hold use with it: blood back, and
  the bottle back. Its tooltip says how many days it keeps. Try drawing from a
  villager too.
- [ ] **10. Consent.** You can't draw blood from, or cauterize, a friend who's
  awake. Once they're out cold, you can.
- [ ] **11. Medicine off.** Every one of these refuses and says so; a tourniquet
  that was on comes off by itself and the limb comes back.

## items.3: Sedation and Numbing (phase 3, round 2)

The jar is `realistic-damage-2.0.0-items.3.jar`, in "Realistic Damage items"
(alpha.3 is parked in `mods/replaced-jars/`). It has the necks too. Turn on
**Weapons** for Sedation and **Medicine** for Numbing (K, then World rules).

- [ ] **1. Brewing.** Awkward potion + poppy = Sleeping Draught (Sedation),
  glowstone makes it strong. Awkward potion + packed ice = Numbing, redstone
  makes it last longer. Both in the Field kit tab; Numbing only while Medicine
  is on.
- [ ] **2. The draught.** Drink a Sleeping Draught at full health: over about ten
  seconds your eyelids get heavy and blink slowly, you slow down, then the lids
  droop and you nod off (microsleeps), then you're out. You wake up groggy about
  25 seconds later, and the chat says "You slept it off." Hurt yourself first
  (`/rdmg damagepart`) and check you woke up healed like after a bed.
- [ ] **3. One arrow, one zombie.** Shoot a zombie with an Arrow of Sedation. It
  slows, stumbles, then drops into a ragdoll after about 8 seconds, and gets
  back up a while later. A ravager needs five arrows.
- [ ] **4. Dragging.** With an empty hand, sneak and use a knocked-out mob (a
  tranq'd villager is the classic): it's dragged behind you, slowly. Sneak-use
  again to let go. It lets go by itself when the mob wakes.
- [ ] **5. Too much.** Three doses (three arrows, or drink three): you go out and
  stop breathing. After about 20 seconds out of breath you start taking damage,
  and you die "stopped breathing" if nobody helps. Milk stops it.
- [ ] **6. Knockout gas.** A Lingering Sedation cloud: stand in it for a second
  and you're on your way down. Stay in it and it's an overdose.
- [ ] **7. Small things knock you down.** While stumbling on it, any hit,
  even a light one, puts you on the ground.
- [ ] **8. Immune.** Iron golems, snow golems and bosses don't care.
- [ ] **9. Milk.** Milk clears Sedation at once (the lids open, the drug is gone),
  but if you were already out you come round the normal way.
- [ ] **10. Numbing.** Break a leg (`/rdmg break @s left_leg`), then drink
  Numbing: you walk at full speed, a hit doesn't flinch you or blur the screen,
  and the body diagram goes pale blue with "Numb" and no marks. The leg is still
  broken: sprint on it and it gets worse (watch it once Numbing wears off).
  Swinging with a broken arm wears that arm down too.
- [ ] **11. The slider.** Settings, Afflictions, "Sedation": turning it down
  fades the eyelids; "Turn off all sensory effects" turns them off.
- [ ] **12. The switches.** Medicine off: Numbing is refused and its potions
  say so; Sedation still works. Weapons off: the reverse.

**Known:** Sedation is strong in PvP on purpose for now (one arrow, about 8
seconds, then out); balancing it is phase 7. The Haemorrhage, Sedation,
Adrenaline and Numbing brews are all in, so phase 3 is complete.

## items.2: Haemorrhage and Adrenaline (phase 3, round 1)

The jar is `realistic-damage-2.0.0-items.2.jar`, in the "Realistic Damage items"
Prism instance (items.1 is parked in `mods/replaced-jars/`). Turn **Weapons** on
first (K, then World rules), or none of this exists.

- [ ] **1. The tab.** In creative, the **Field kit** tab (on the second page of
  tabs) has Haemorrhage and Adrenaline in every form: drinkable, splash,
  lingering, tipped arrow, and the long and strong brews.
- [ ] **2. Brewing.** Awkward potion + sweet berries = Haemorrhage; + glow
  berries = Adrenaline. Redstone lengthens both, glowstone strengthens
  Haemorrhage, gunpowder and dragon's breath work as usual.
- [ ] **3. Tipped arrows.** Eight arrows around one drinkable Haemorrhage potion
  make eight Arrows of Haemorrhage, and you get the bottle back.
- [ ] **4. Haemorrhage.** Drink it and get hit by a sword or an arrow: hits that
  wouldn't normally make you bleed do, and bleeds open worse. Holding a wound
  (empty hand, hold use) helps half as much. Fists and maces still never cut.
- [ ] **5. The arrow.** Shoot a zombie with an Arrow of Haemorrhage: it bleeds
  from where it was hit. A skeleton doesn't (no blood).
- [ ] **6. Adrenaline.** Drink it at full health: the surge kicks in ("Adrenaline
  floods in.", faster, pain pushed back, a racing heart) for 20 seconds, then
  the crash (heavy legs, short winded sprints). Drink another straight after:
  "Your heart can't take another surge yet", and nothing happens.
- [ ] **7. Bleeding harder.** With a wound open, Adrenaline makes it drain
  faster (you'll see the blood come quicker).
- [ ] **8. Milk.** Milk clears both, and a bleed Haemorrhage opened stays open.
  Milk mid-surge ends the surge and starts the crash.
- [ ] **9. The icons.** The two new icons in your inventory's effect list and
  top right on the HUD.
- [ ] **10. Switching off.** Turn Weapons off on the World rules page. A potion
  in your inventory now says "Weapons is switched off: this does nothing" and
  won't drink; a running Haemorrhage or Adrenaline ends at once; a brewing stand
  with sweet berries won't start; the Field kit tab is gone from creative. Turn
  it back on and it all returns.
- [ ] **11. On a server, if you can.** With the Field kit tab open in creative,
  have an operator (or the console) run `/rdmg config enableWeapons false`: the
  tab empties and you land on Building Blocks, with no crash.

**Not in yet:** Sedation and Numbing (round 2).

## items.1: the switches (phase 2)

The jar is `realistic-damage-2.0.0-items.1.jar`. It has everything 2.0.0-alpha.1
has (heads come off) plus the field kit's switches. **There are no field kit
items yet.** The first ones are phase 3's four potions. So this round is about the
two switches and where you set them.

- [ ] **1. The first-launch question.** The first time the title screen comes
  up, you get "Realistic Damage 2.0: the field kit" instead: two switches,
  **Weapons** and **Medicine**, both OFF, a sentence each, and a line saying
  where to change them later. This should happen on an upgraded install too (a
  copied instance counts).
- [ ] **2. Once only.** Turn one switch on and press Done (or Escape). Quit and
  restart the game. The question never comes back.
- [ ] **3. It stuck.** Open a singleplayer world and type
  `/rdmg config enableWeapons` (or `enableMedicine`). It shows what you picked.
- [ ] **4. The World rules page.** Press **K** (or Mod Menu, then the mod's
  config button). The top button is **World rules...**. It lists every server
  setting in sections (Field kit, Injuries, Losing parts, Falls and knockdowns,
  Mobs, Scaling). Hovering a setting explains it. The line under the title
  says "Singleplayer: these apply to every world on this install".
- [ ] **5. Changing a setting there.** In a world, flip Medicine on the page,
  close it, then `/rdmg config enableMedicine`: it's on. Then the other way:
  `/rdmg config enableMedicine false`, reopen the page, and it shows off.
- [ ] **6. Sliders and choices.** Move one of the Scaling sliders (they go
  from ×0 to ×3 in steps of 0.05) and cycle Downed Aggro. Check
  with `/rdmg config` that they took.
- [ ] **7. From the title screen.** Open World rules from Mod Menu with no
  world loaded. It edits the same settings, so a change there shows up in the
  next world you open.
- [ ] **8. On a server, if you can.** As a non-operator the page is read-only
  ("This server's rules. Only an operator can change them"), with every control
  greyed out. As an operator it says your changes go through `/rdmg config`,
  and other operators see each change in chat, like typing the command.
- [ ] **9. Nothing else moved.** With both switches off, nothing plays any
  differently from alpha.1.

**Not in yet / known:**

- No items, so the **Field kit** creative tab doesn't appear yet (an empty tab
  isn't drawn). It shows up with phase 3's potions.
- Crafting, brewing, loot, trades, using an item and effects all check the
  switches already. Each check has a test, but you can only see them in play
  once there's something to check (phase 3).
- The switches are **global** (one pair for the whole install, or the whole
  server), as the plan recommends, until you answer open decision 5.
