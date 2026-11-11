# Realistic Damage 2.0: what to test

The jar is `realistic-damage-2.0.0-alpha.1.jar`, in both Prism instances
("Realistic Damage" and "Realistic Damage vr"). The 1.7 alpha.26 jar is
parked in `mods/replaced-jars/`. Everything 1.7 had is still in it, so the
alpha.25 and alpha.26 checklists in [RAGDOLL_TESTING.md](RAGDOLL_TESTING.md)
still apply.

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
- Broken necks and whiplash are the next round.
