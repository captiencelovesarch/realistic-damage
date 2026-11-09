# Realistic Damage: active ragdolls, morning report (Nov 10 2026)

You went to bed after phase 2 ("works swell") and asked me to build through
phase 5. **All of it is built, committed on `1.7-anatomy`, and installed in
both Prism instances as `realistic-damage-1.7.0-alpha.21.jar`.** Older jars
are in `mods/replaced-jars/`. Nothing was pushed, tagged or merged.

Your checklist is **[RAGDOLL_TESTING.md](RAGDOLL_TESTING.md)**: newest phase on
top, a `/rdmg ragdoll …` command beside most items. The plan with what was
built is [RAGDOLL_PLAN.md](RAGDOLL_PLAN.md).

## What you'll see

| Phase | What happens | Setting |
|---|---|---|
| 3 Reflexes | A dying body reacts while it's still conscious. The arms go out to break the fall, a hand goes to the wound, a struck body staggers before it falls, the arms come up against a blast. It writhes in fire, jerks from lightning, and reaches up while drowning. A violent death twitches after it settles. | (always on) |
| 4a Bodies stay | Bodies keep lying where they fell after the server removes them, then sink into the ground. Your own body stays after you respawn. | **Bodies Stay** 5 s / 30 s / 2 min / 5 min |
| 4b Your own death | First person rides your ragdoll's eyes, smoothed. F5 shows your ragdoll. | **Death View** Ragdoll / Classic |
| 4c Knockdowns (your ask) | A blast or big hit that doesn't kill throws you as a ragdoll. You lie there, then get up as the view blends back. Hard landings and passing out work the same way. If you die while down, the same body becomes the corpse. | **Knockdown Ragdolls**; server rule `knockdownFromBlows` |
| 4d Other mobs | Cows, pigs, spiders, chickens, creepers, villagers and the rest ragdoll from their own models. Animals keel over onto their sides, and spiders often end up on their backs. | (with Bodies) |
| 5 Hit flinches | A body that's hit and stays standing flinches where it was struck, on top of its animation. | **Hit Flinches** |

What now knocks you down, measured on a 20-health body:
- a hard landing, or passing out (as before);
- a non-lethal blast of about 7.5 damage or more;
- any hit from an iron golem, ravager, warden, hoglin or zoglin of about
  4.5 damage or more;
- any single blow of about 13.5 damage or more.

## Commits

- `099c435` phases 0 and 2 (the checkpoint before bed)
- `6297f3b` phase 3, reflexes
- `79ebfd5` phase 4a, bodies stay
- `35b249f` phase 4b, your own death
- `dc74bae` + `62dcb87` phase 4c, knockdowns (plus a README and test fix)
- `1466965` phase 4d, other mobs
- `d5e1898` phase 5, flinches

## Tests

- **Unit tests:** all pass, 313 in all, 32 of them new ragdoll engine tests
  that run in a fake world.
- **Server game tests:** all 231 pass, 5 of them new (knockdown thresholds).
- **Client game tests:** the full suite passes. New ones:
  - `RagdollClientGameTest`, with scenes for stairs and ledge, muscles,
    reflexes, bodies staying, and the animal row;
  - `OwnRagdollClientGameTest`;
  - `KnockdownClientGameTest`, which includes a flinch.

What the tests measured:
- Joint gaps under 2 cm.
- No body ends inside a block.
- At rest, the camera sits within 1.5 mm of your ragdoll's eyes.
- After a knockdown you're back at exactly standing eye height.

## Things I changed in old tests (so you know)

- **Multiplayer "quiet death":** it now checks that the body is still once
  it's down, not the moment of impact. A full-violence death drops in a heap
  now (phase 2), which is on purpose.
- **Classic death-camera tests:** they pin Death View to Classic, so they keep
  testing the classic camera exactly.
- **Mob anatomy "collapsed zombie":** a knockdown ragdoll counts as laid out.

## Bugs found and fixed along the way

- **Twitch spinning a limb:** a skeleton's thin limb spun up to the speed cap,
  because the twitch kept re-aiming every tick.
- **Stagger sitting the body down:** it fought the "doubled over" reflex, so
  the body sat down.
- **Third-person view:** first it put the camera inside your own head, then it
  framed the wrong spot. Now it frames your ragdoll.
- **Animals rolling the wrong way:** they were rolled against the push. Found
  because the spider fell anyway and landed on its back.
- **Wrong knockdown message:** a blast or blow said "The landing takes your
  legs…". They have their own lines now.
- **My own process slip:** one commit went in while two guard tests were red.
  They were fixed straight after in `62dcb87`. Commits are now gated on the
  test exit codes.

## Not verified (please look at these)

- **VR:** nothing was tested in a headset. By design your own ragdoll view and
  your own knockdown ragdoll are off in VR, and others' bodies still ragdoll.
- **Multiplayer knockdowns:** a friend's knockdown ragdoll following their
  real position (the tether) is only tested in singleplayer.
- **Dragging an unconscious friend:** should drag their ragdoll. Untested.
- **Odd mob models:** only cow, pig, spider, chicken, creeper and villager were
  tested. Horses, llamas, foxes, goats, axolotls, frogs, camels and sniffers
  are untested, and some may look strange.
- **Shaders, EntityCulling, many bodies at once:** untested here.
- **`/rdragdoll debug`:** the boxes are drawn, but they never showed in test
  screenshots, so I've never seen them on screen.
- **Getting up:** a straight blend about the hips, not a full get-up animation.
  It might look stiff; tell me.

## How to roll back

If anything is badly off, drop
`mods/replaced-jars/realistic-damage-1.7.0-alpha.15.jar` (phase 2, which you
already approved) back in, and move the alpha.21 jar out.
