# Realistic Damage: roadmap to 1.5

Where 1.0 landed and what comes next. Each release has one theme, because
shipping a coherent idea beats shipping a pile of options.

Ordering principle: fix what exists before adding what doesn't, and never add a
system that undermines "food is the cure".

## Shipped in 1.0

Six body regions with real hit location, deterministic injuries (Broken,
Bleeding, Concussion, Burned), hidden Blood and Consciousness, limb destruction
with permanent consequences, food-powered recovery, and a sensory layer that
does genuine colour drain, world warping, screen blur and low-pass audio rather
than imitating them with overlays.

Limb destruction was originally scoped for a later version and got pulled into
1.0 during playtesting. That leaves the "permanence" ground partly covered
already, which is why 1.2 to 1.5 below lean toward depth over new damage types.

## Shipped in 1.1

The unglamorous release: known rough edges, several found in play rather than
in tests.

- **Purpose-made injury audio.** Fracture, destruction and collapse no longer
  borrow vanilla sounds. Each has a muted set that sits inside Minecraft's
  palette and a visceral set that does not, selected by the Arcade/Grounded
  presets and overridable on its own. All of it is synthesised by
  `tools/generate_sounds.py`, so it is re-tunable by changing a number.
- **Injuries are heard from the right place.** Your own play close and
  non-positional at full volume; everyone else's play at their position and
  quieter. Sent as a message rather than a world sound, so each client picks
  its own style — two players on one server can disagree about how visceral
  their game is.
- **"Look Slowdown" split** into Look Slowdown and Look Drift. They were one
  control, so sloppy aim without slow aim was impossible.
- **Look Drift retuned.** It lasted far too long and read as nausea: a fast
  flick banked enough momentum to keep turning the camera for nearly three
  seconds. Now about half a second at worst, and momentum is dropped rather
  than frozen while a screen is open, which was causing the view to lurch off
  on its own the moment an inventory closed.
- **Optional numeric stats** in the inventory panel, off by default. Condition
  figures per region; Blood and Consciousness named by band and never as
  figures, because a player who can read their exact blood level is never
  surprised by one.
- **Aggro is configurable**, defaulting to prior attackers plus anything within
  six blocks that can see you. Pulling in every hostile within line of sight
  did not make collapsing dangerous so much as decide the fight in advance —
  in singleplayer nobody is coming to drag you out. `ALL` and `NONE` remain.
  Enforcing this needed a mixin on `Mob.setTarget` rather than the obvious
  periodic re-target: vanilla acquires a player from up to the mob's follow
  range, 35 blocks for a zombie, which is well outside either of our radii, so
  a version that only pushed and cleared targets left every mode behaving like
  `ALL` and `NONE` working about half the time.
- **A death message per cause.** The single "torn apart" covered both a crushed
  skull and a caved-in chest; they are now separate damage types with separate
  messages. Everything the mod inflicts carries an `internal` tag, so a damage
  type added later cannot forget the recursion guard in two places at once.
- **Injury message tone pass.** Second person, present tense, visceral over
  clinical, and exclamation marks spent only on the sudden violent moments.
- **The hover panel is verified.** The roadmap claimed the harness could not
  position a cursor; `TestInput.setCursorPos` does exactly that. Every region
  is now hovered by a real cursor in a client gametest that asserts the hover
  resolved, rather than only screenshotting it.
- **The settings screen scrolls.** It was a hand-placed grid that already ran
  off the bottom at large GUI scales, and 1.1 added three more controls. Now
  vanilla's scrolling options list, in labelled sections.

Several bugs fell out of testing and reviewing the above. The vitals block
originally sized the panel to its own text, which dragged the body diagram
sideways the moment numbers were switched on and slid the panel under the
inventory at smaller window sizes — the first version of the test screenshotted
it and sailed straight past. It now asserts the body does not move, and the
vitals read as a label above a value so the block stays narrower than the body.

Splitting a control also turned out to be an upgrade hazard: a config written
by 1.0 has no key for the new slider, and letting it default to full strength
would have switched the melty camera back on for a player who had turned it
off. Client settings now migrate, and the new value is seeded from the control
it was split out of.

The inertia guard was a rate limiter in disguise. It clamped the input buffer,
so a fast flick had everything past the limit discarded rather than delayed,
and because the limit applied per frame your maximum turn rate depended on your
frame rate. It now bounds only the leftover momentum, which is what the guard
was ever meant to be about.

## Shipped in 1.2

Rescue: the thing the original design deferred, and the one that changes the
multiplayer game most.

- **Drag** an unconscious player. Sneak-use to grab them; they are hauled along
  the ground by velocity rather than teleported or mounted, so a body snags on
  terrain, slides down slopes and can be lost over a ledge. Costs half your
  speed and both hands.
- **Bleeding pressure**, with no item at all. Hold use with empty hands on a
  downed teammate, or on yourself — but pressing your own wound pins you where
  you stand, so you can stop your own bleeding or you can keep fighting. A
  companion is better at it than you are (×0.25 against ×0.40), which is the gap
  that makes fetching one worth the trip.
- **Feed a teammate** who cannot use their own hands. Routed through vanilla's
  own consume path on the recipient, so hunger, golden-apple effects and the
  mod's Blood restoration all land exactly as if they had eaten it.
- **Triage view**: the patient's body, icons and status, drawn beside your own —
  but only while you are actually treating them. You have to reach them and
  commit a hand before you learn what is wrong.
- **Retuned for a survivable downed state.** Bleed-out at zero blood eased from
  5.0 to 3.5 HP/sec: at four seconds from full health that was not a last stand
  so much as an announcement, and now that somebody can reach you and put food
  in you it needs to be a window.

Nothing here heals directly. Pressure buys time and feeding is still just food,
so the spec's bet — the danger is whether you have enough food and enough time —
is intact.

**Practice dummy** (`/rdmg dummy spawn`), added alongside for testing PVP
mechanics. It is a genuine ServerPlayer rather than a mob in a player skin,
which is the whole point: every system is typed against ServerPlayer, so a real
one inherits hit location, six regions, bleeding, breaks, consciousness and limb
destruction with no code aware it is not a person. `fightback` makes it swing
back, `equip` copies your own gear onto it, `heal` puts it back together.
Generalising the pipeline to LivingEntity instead is the 1.5 mob-anatomy work and would have
meant refactoring twelve files to get one test target.

**Multiplayer** is verified rather than assumed: a client gametest boots a real
dedicated server, connects a real client, and checks that body state, a second
player's state and the triage view all survive an actual socket. Every
clientbound send is now gated on `canSend`, so a vanilla client on a modded
server — or the dummy's connection, which goes nowhere — cannot break anything.

## 1.3: Interoperability

Make the mod a platform rather than an island. See `API.md`.

Done:

- **Public API** (`api` package): classify a damage source, supply an exact
  impact point, read body state. Built on the `DamageCategory` and
  `BodyHitResolver` seams that were designed for this from the start.
- **Data-driven classification** by damage-type tag, consulted ahead of the
  built-in table so a datapack can classify a modded weapon — or overrule how
  vanilla damage is read — with no code and no dependency in either direction.
  Tags rather than a bespoke JSON format because they already merge across
  packs, already reload with `/reload`, and pack authors already know them.
- **Ballistic categories**, small arms and heavy. They bleed sooner than
  anything else in the table, and small arms cannot break a bone on impact: a
  bullet passes through rather than clubbing, though a wrecked limb still gives
  way by accumulation.

Still open:

- **A real firearm mod to test against.** TACZ is Forge-only and the unofficial
  Fabric port stops well short of this Minecraft version, so there is nothing to
  integrate with yet. Its four bullet damage types ship as *optional* tag
  entries, which cost nothing while absent and classify the moment a port
  appears — but that path has never run against the real mod, only against a
  stand-in damage type.
- **Compatibility passes** against the big combat and armour mods, which have
  the same availability problem for now.

## 1.4: Consequence

Deepen what injuries mean rather than adding more of them.

- **Long-term injuries**: an optional hardcore mode where the worst injuries
  heal to a permanently reduced ceiling rather than to full.
- **Explosive dismemberment**: TNT and creepers able to destroy limbs directly
  at extreme trauma, instead of only finishing off an already-Disabled one.
- **Scarring**: purely cosmetic marks on the body panel for regions that have
  been destroyed or broken before, so your character carries a history.
- **Injury-aware mob AI**: hostiles preferring targets that are visibly limping
  or bleeding. Predators pick off the wounded.

## 1.5: Anatomy for everyone

Mobs get bodies. Deliberately last, because it doubles the surface area of
every system and needs everything before it to be stable first.

- **Mob body regions**, starting with humanoids where the existing hitbox
  geometry transfers directly.
- **Headshots on mobs** that impair rather than simply damaging, mirroring the
  player rules so the mod stays symmetric.
- **Crippling**: hobbled mobs move slower, disarmed ones stop using weapons.
- **Performance work**: per-entity state at mob scale needs pooling and a much
  cheaper tick than the per-player path gets away with.

## Deliberately not planned

A medical crafting tree, a wound-management screen, surgery, infection,
splints, or anything else that answers an injury with an item rather than with
food and time. The spec was right the first time: the danger should be whether
you have enough food and enough time, not whether you packed the right supply.
