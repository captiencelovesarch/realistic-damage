# Realistic Damage: roadmap to 1.7

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

## 1.4: Weight

Two things the mod had been describing rather than delivering. Unplanned —
this displaced Consequence, which moves to 1.5 — but both were holes in what
was already shipped rather than new territory, and they were the two places
where the screen and the gameplay were telling different stories.

**Losing consciousness now costs you something before it costs you everything.**
The symptom ladder was almost entirely sensory: at one point of consciousness a
player could still look around cleanly, swing, place blocks and eat at full
speed, and then collapse between one tick and the next. The approach to
collapse is now a slide rather than a cliff.

- **Microsleeps**: below 22 the player blacks out in episodes, deterministically
  — a phase accumulator, not a die roll — arriving more often and lasting longer
  the further down they are. Controls are actually taken away, not merely
  dimmed. Capped so you are always present more than you are absent.
- **Feebleness**: the action penalty caved in over the last twenty points (0.22
  at 20, 0.85 at 0, where it used to be 0.10 and 0.30), and below 8 you cannot
  swing or mine at all. Fading is no longer filed under the arm-injury cap,
  which is what let it be capped away.
- **Unsteady footing**: the movement vector wanders off the input as
  consciousness falls, up to 42°. Client-side, because a server-side nudge
  arrives as a rubber-band and a control that fights you reads as a broken game
  rather than a failing body.
- **Eating slows** to five times its length at the bottom. It is never refused
  for weakness — food is the only cure and that promise is kept — but it stops
  being free.
- **Concussion now decides these**, not just the visuals: the symptom shift
  moved into shared code, so a concussed player blacks out and stumbles at a
  consciousness a clear-headed one walks off.

**Falls are scored on height, not on the health bar.** Post-armour damage was
the wrong input: it is what vanilla decided your boots were worth, so diamond
and Feather Falling IV did not merely protect you, they deleted the event.

- **Height-keyed severity**: trauma, secondary torso and head shares, and
  knockdown all derive from how far you dropped. Armour still helps, through the
  same localized reduction every other category goes through.
- **Knockdown**: hard landings put you on the ground, prone and not steering,
  for up to 2.5 seconds, then a brief unsteady tail. There was previously no
  landing event of any kind.
- **Asymmetric legs**: the leading foot — read off the walk cycle, so it is
  reproducible — takes about twice the trailing one's share on a committed
  landing.
- **Surfaces matter**: hay, leaves, snow and wool genuinely cushion the body and
  can leave you standing where stone would floor you; stone, metal and obsidian
  make it worse.
- **Less fatal, worse aftermath**: vanilla's linear damage makes lethality a
  cliff edge at 23 blocks. The tail is compressed above 10 points, so a
  35-block drop is survivable from full health — at about two hearts, concussed,
  with a shattered leg — while fifty blocks still kills.

## 1.5: Blood

Also unplanned, and displacing Consequence a second time — but like Weight
before it, this was a hole in what had already shipped rather than new
territory. Bleeding was a first-class mechanic with a hidden meter, a
stacking rate model and its own death path, presented as four redstone
particles every 25 ticks that hung in the air and, in first person, spawned
behind the camera where they could not be seen at all. The mod could kill you
by a means it never actually showed you.

**Blood you can read.** Drops with gravity, drag and block collision that land
and leave marks; a bounded stain field that merges nearby marks into pools, so
standing still grows a pool while walking lays a trail; per-region staining on
the body, so a cut left arm is a bloody left arm; and droplets on the camera
driven by bleed *rate* rather than blood *level* — the one signal that says you
are losing blood right now. Emission moved to the client off the already-synced
snapshot, which costs less on the wire than the packets it replaced and lets an
arterial wound throw its blood on the beat the player is hearing.

**A heart that can falter.** The old beat raced but never stumbled, and its
envelope was measured in fractions of a beat, so the thump got shorter as the
rate rose and dying sounded like a metronome. Now: decays in seconds, ectopic
beats with a compensatory pause and a hard beat after it, and three characters
crossfaded by cause — blood loss fast and faint, head trauma slow and enormous,
ordinary injury the pounding one.

**A screen that says what happened.** Colour drain means blood loss and warping
means the head, instead of both reading one collapsed severity; and impacts
carry their damage category, folded into four physical events — cut, pierce,
blow, blast — so a slash and a blast no longer produce the same white flash.

**An ending instead of a cut.** Death used to snap every effect off between one
frame and the next. Now the heart runs down — a handful of beats with widening
gaps, each softer, silent past the middle — while the colour finishes draining,
the vignette closes and loses its red, and the world's audio sinks toward quiet
under the death screen. The respawn button stays readable the whole way, and a
fresh life starts with a clean frame.

## Shipped in 1.6: Consequence

Deepen what injuries mean rather than adding more of them. Four promised systems,
plus the presentation each one needed in order to be honest about what happened.

**Some injuries outlive recovery.** The optional `enableLongTermInjuries` rule is
off by default and lowers a region's best recovery one state band when a limb
breaks, a region is driven to zero or a limb is lost. A limb may bottom out at
Critical; a head or torso stops at Hurt so the hardcore rule cannot make the
symptom ladder permanent. Food and time still do all the healing, but stop at the
ceiling. Sleep and totems leave it alone, and death carries it only while the rule
is enabled. The body panel draws and names the band, never the figure underneath.

**A blast can take the limb in one event.** Server explosions capture their real
centre and exposure before vanilla damage runs. Only the nearest limb is eligible,
and it leaves at a deterministic bar of 45% of that limb's full condition after
the same localized armour reduction as ordinary explosion trauma. The existing
destruction choke point then owns the stump, wound, scar, sound and notification;
Grounded still means no destruction at all.

**The body carries its history.** Broken and destroyed are separate scar rungs,
persisted and synced per region. A healed break shows on the panel and on an
overlay-free player mesh; a destroyed region records the stronger mark for the
next body, since there is no limb left to draw it on in the current one. Scars
change no healing, impairment or mob decision, and have their own presentation
toggle.

**Predators pick off the wounded.** Hostiles resolve target preference where the
target is actually written, at `Mob.setTarget`, rather than clearing it on a timer
for vanilla to reacquire. Down outranks a missing leg, which outranks a break,
which outranks a visible limp, which outranks a running wound. Existing fights get
a switching margin and revenge gets a stronger one. Ties go by distance and then
entity id, and the limp score calls the same pure function that poses the model.

**The consequences show in the world.** Injured limbs hang and step differently,
including their armour, and the remaining blood review closed three gaps: a lost
limb throws a wider, floor-biased severance mark; a bleeding region pressed to a
wall transfers blood to it; and a large wet wall mark sheds a gravity-driven run
before it dries. These are bounded client presentation, not persistent block
state, so blood remains a readout rather than a save-file decoration.

## Shipped in 1.6.1

The first playtest pass over Consequence, and the roadmap's own principle applied — fix
what exists before adding what doesn't.

- **Death inherits the ladder.** Dying at low consciousness used to clear the screen on
  the death frame and dim it again from nothing. The last living frame is now the floor
  the death look builds on; nothing on the death screen may be lighter than the frame
  before it.
- **Afterimages, actually.** The old one was a tinted nausea texture with a pulse. The new
  one is a post chain with a persistent history target: the previous frames really do
  trail behind the current one, frame-rate corrected, from the concussion floor down to
  the blur.
- **Blood on your own first-person arm**, from the same meshes and paint call as the
  third-person body, so the two cannot disagree.
- **Blood disperses in water** instead of sinking to the pool floor and marking it, and
  burns off in lava.
- **The camera moves as a picture.** Impacts kick a view-only spring — roll, pitch, yaw,
  a shove of the eye, a shake for blasts — on top of the existing aim kick, and the
  horizon sways, nods and slumps as consciousness fails, without the aim moving at all.
  New **Head Sway** slider; the spring rides Impact Shock and vanilla's Damage Tilt.

## Shipped in 1.6.2

The camera brainstorm's four answers, the stump the body was missing, and the limb
itself — pulled forward from 1.7 the way limb destruction was pulled into 1.0, because the
author had answered every design question and the mark alone had stopped being enough.

- **Camera Drift on by default** at a low level, a **heavy head** that lets the picture
  trail the aim on fast turns, a **drunk walk** that lurches the view on each footstep near
  collapse, and impact kicks that overshoot at any health.
- **Stumps.** A lost limb leaves five pixels of itself and a cut face, on the model and
  raised into view in first person, and the stump wound soaks into it.
- **The severed limb entity.** Thrown along the blow, hard by a blast, dropped when there
  was none; lands, bounces once, bleeds for most of a minute, burns away in fire or lava,
  and is gone. Walk-through, ignored by mobs, never saved. Server rule `spawnSeveredLimbs`.

## 1.7: Anatomy for everyone (in progress)

Mobs get bodies. Deliberately last, because it doubles the surface area of
every system and needs everything before it to be stable first.

**Slice one is in** (Sep 8 2026): the whole server pipeline generalised from
`ServerPlayer` to `LivingEntity`, humanoid mobs (the `has_anatomy` tag) taking
regional trauma, bleeding out, breaking, losing limbs to blows and blasts,
crippled through the same attribute modifiers, refusing to swing with no arms
or shoot with no drawing arm, dazed by concussion and dropped by collapse, and
mending on time alone. On screen they stain, limp, lose limbs to stumps, and
leave severed limbs in their own textures — bones for skeletons. Server rule
`enableMobAnatomy`.

**Slice two** (Sep 9 2026): illagers and witches, laid-out collapse and knockdown
poses, mob falls, concussed and broken-armed skeletons shooting wide, and blood
profiles by tag — the skeleton family is bloodless, the undead bleed dark, and
endermen are wired for purple. Remaining below.

**Death camera** (Sep 30 2026, alpha.4, from a playtest: knocked off a bastion roof
by a brute, and the view never moved). Vanilla deletes the local player a second
after death, which froze the camera; now the body keeps its physics until the
respawn (client-side only, never sent to the server), the view collapses to the
floor, rolls over and thuds on landing, the blackout holds at 60% until the body
comes to rest, and the death screen fades in, with its buttons asleep until
it is nearly visible. One switch, Death Camera, on by default.

**Playtest fixes** (Oct 1 2026, alpha.5). "It moves a bunch and then just
FREEZES": the server dropped the dead player from its world a second in, so every
mob vanished and nothing updated again — the server now keeps the body while the
death camera watches (`DeathHold`, released when the death screen is up, capped
at 20 s). Deaths move by kind (`DeathKind` → `DeathMotion`): falls slam, blasts
spin you while airborne, fire and lava thrash, lava takes you under face-up,
drowning drifts, lightning convulses, freezing topples like a plank, bleed-outs
faint; the body lands on springs, keeps settling for seconds and a violent death
twitches. Blood is darker in the world and shades by face; under shaders the
stain pipeline is registered with Iris (pools were missing) and the sprites are
a hair off grey (Complementary drew drops as glowing redstone). Limb breaks yell
and lost limbs scream — TF2 pain lines imported from the player's own install
(`tools/import_tf2_screams.py`, gitignored). Also: a crushed chest now counts as
full violence, and a corpse no longer heals.

- **Mob body regions**, starting with humanoids where the existing hitbox
  geometry transfers directly. (The severed-limb entity originally planned here
  shipped early in 1.6.2 and is ready for mobs to reuse.)
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
