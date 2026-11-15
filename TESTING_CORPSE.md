# Realistic Damage 2.0, dead bodies that still get hurt: what to test

Built on its own branch (`claude/2-0-corpse-damage-df6c3a`), on top of `2.0` at
2.0.0-alpha.4 (the field kit and the new sounds), so the jar has everything
alpha.4 has plus this. Version names: `2.0.0-alpha.4.corpse.N`. Newest round on top.

## corpse.1: a dead body keeps taking body damage until it comes to rest

The jar is `build/libs/realistic-damage-2.0.0-alpha.4.corpse.1.jar` in the
worktree. It is **not in a Prism instance yet**: it belongs in "Realistic Damage
(dev)", in place of the alpha.4 jar there.

**What changed, in one paragraph.** Until now nothing could happen to a body
after it died, except the blast that killed it. Now, when a body dies while it
is really travelling (falling, or thrown), the server follows it down with its
own copy of the ragdoll and judges what it lands on. A hard enough landing
breaks a leg or an arm; a harder one takes the limb off, with the severed limb,
the blood and the stump exactly as for the living. It stops when the body lies
still, or after five seconds.

**The numbers, on ordinary ground, no armour:**

| The body falls about | What happens |
| --- | --- |
| under 8 blocks | nothing |
| 9 to 15 blocks | legs break, nothing comes off |
| 16 to 23 blocks | one leg comes off, the other breaks |
| 24 blocks and up | both legs come off |
| 31 blocks and up | an arm as well, if it hits the ground with the legs |

Hay, slime, honey, beds and leaves soften it; boots (and Feather Falling) protect
the legs, like they do when you are alive.

### The checklist

Ragdolls must be on **Ragdoll** (K, then the Bodies setting) to see any of it.

- [ ] **1. The quick look.** Stand somewhere open and run `/rdmg ragdoll drop`.
  A husk falls from 30 blocks up, a little in front of you, and is killed in the
  air halfway down. It should keep falling (not stop in the air), hit the ground,
  and lose **both legs**: two legs lying next to the body, blood where it landed,
  stumps on the body.
- [ ] **2. The scale.** `/rdmg ragdoll drop 6`: nothing comes off.
  `/rdmg ragdoll drop 12`: nothing comes off, the legs break (they lie looser).
  `/rdmg ragdoll drop 20`: one leg. `/rdmg ragdoll drop 60`: both legs, and
  sometimes an arm.
- [ ] **3. Timing.** Watch one closely: the leg should leave as the body hits
  the ground or a blink after, never while it is still above the ground.
- [ ] **4. Soft ground.** Put a few hay bales down, stand so the husk lands on
  them, `/rdmg ragdoll drop 20`: it keeps its legs.
- [ ] **5. A real fall death.** Push a zombie off something 40 blocks or more
  high (or build up and let one walk off). It dies as it lands and its legs come
  off.
- [ ] **6. Yourself.** In survival, `/tp @s ~ ~70 ~` and fall. Best watched by a
  second player, or in third person if your own ragdoll is switched on. Your body
  should lose its legs as it lands.
- [ ] **7. Killed in the air.** `/tp @s ~ ~150 ~`, then at once open chat and
  run `/kill`. Your body should keep falling at speed (before this build a dead
  player's body stopped and started falling again from still) and lose its legs
  at the bottom.
- [ ] **8. Ordinary deaths stay ordinary.** Kill a few zombies with a sword on
  flat ground: nothing comes off. Let a creeper kill something: only what the
  blast itself takes, as before.
- [ ] **9. The switch.** World rules, turn **Limb destruction** off, and repeat
  step 1: nothing comes off (bones still break, which you can only tell from the
  legs lying a little looser).
- [ ] **10. The next life.** After step 6, respawn: you carry the scar for the
  leg your body lost. Say if you would rather it didn't.

### Things I chose, and you may want otherwise

- **Who:** players and human-shaped mobs (zombies, husks, skeletons, piglins,
  pillagers and so on). Not cows, spiders or anything else.
- **What:** arms and legs only. A head never comes off this way.
- **Scars:** a limb lost after death leaves the same scar on your next life as
  one lost to the blast that killed you.
- **How many at once:** the server follows at most six falling bodies at a time.
  A seventh simply isn't judged.

### What it cannot do

- A body that dies standing on the edge of a cliff and then topples off is not
  followed: it did not have the speed when it died. Only a body that was already
  falling or thrown, or had at least five blocks of clear air under its feet.
- If you press Respawn before your body lands, nothing more happens to it.
- Which arm comes off on a tumbling body will not always be the one that looks
  like it hit first on your screen. Every player sees the same limb go, but each
  screen draws its own ragdoll and they differ in the small movements.
- A body thrown along the ground into a wall is judged, but roughly: the copies
  disagree most there.
