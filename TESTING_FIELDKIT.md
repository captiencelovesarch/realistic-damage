# Realistic Damage 2.0, the field kit: what to test

The field kit is built on its own branch, beside the neck work, with its own
version numbers: `2.0.0-items.N`. Newest round on top.

**Combined (Nov 11 2026):** items.1 and items.2 are merged into the `2.0`
branch with the necks: the jar `realistic-damage-2.0.0-alpha.3.jar` has both
and is in all three instances. Everything below applies to it unchanged.

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
