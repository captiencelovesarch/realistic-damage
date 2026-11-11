# Realistic Damage 2.0, the field kit: what to test

The field kit is built on its own branch, beside the neck work, with its own
version numbers: `2.0.0-items.N`. Newest round on top.

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
