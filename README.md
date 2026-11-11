# Realistic Damage

**Minecraft, but damage is cooler.**

Vanilla damage is binary: a number goes down, you eat, the number goes back up. Realistic
Damage adds a physical layer underneath — where you got hit, how badly that part of you
works now, whether you're bleeding, and whether you can stay conscious — while leaving the
vanilla heart bar completely in charge of life and death.

Fabric · Minecraft 26.2 · Java 25

> "I survived the fall, but both legs are wrecked."
> "I still had seven hearts, but those arrows made me black out."
> "The creeper didn't kill me, but I was concussed and could barely fight."

## What it adds

**Six body regions** — head, torso, both arms, both legs — each with hidden condition that
degrades through `Healthy → Hurt → Injured → Critical → Disabled`. A hurt arm mines and
fights slower; hurt legs slow you down and eventually cost you your sprint. Nothing soaks
damage that would otherwise hit your hearts.

**Real hit location.** Arrows use their actual impact point; melee reconstructs the
attacker's swing ray. What you hit is what you damage — including left versus right.
Headshots deal no bonus heart damage; their advantage is that heads run consciousness.

**Deterministic injuries.** No fracture rolls, ever. A mace or an iron golem breaks a
healthy limb outright; swords and arrows only break one that's already critical. Cuts and
punctures bleed, blunt trauma doesn't. Identical hits always produce identical outcomes.

**Blood and Consciousness** — two hidden meters. Bleeding drains blood; low blood starts
costing hearts and pulls you toward blacking out. Head trauma can knock you unconscious
with most of your hearts intact. You wake up when you stabilize.

**Going under is a slide, not a switch.** Near the bottom your hands stop obeying: swinging
and mining fall off a cliff and eventually refuse outright, your feet start wandering off
the direction you pointed them, and you black out in brief episodes that come more often
and last longer the further down you are. Eating still always works — food is the cure —
but at the bottom a loaf takes eight seconds you may not have. A concussion brings all of
it forward.

**Falls land like falls.** Severity comes from how far you dropped, not from what your
armour let through, so a long drop in good gear still shatters the leg you landed on and
still rattles your skull. Hard landings put you on the ground for a moment. The leading
foot takes the worse of it. Hay, leaves and snow genuinely cushion; stone and iron don't.
And the lethal tail is compressed — you survive drops that used to kill you, concussed and
limping, which is usually worse.

**Limb destruction** (Arcade preset, on by default). Keep hitting a limb that is already
Disabled and it is gone for good — and an extreme TNT or creeper blast can take the nearest
limb outright, after the same localized armour check as every other injury. A destroyed arm
cannot use that hand and works one-handed at a heavy penalty, destroyed legs are a permanent
limp, losing both means you crawl, and a destroyed head or torso kills. Destroyed limbs
vanish from your body in first and third person; the stump keeps bleeding, and the loss
leaves a larger mark on the world than an ordinary wound. Turn it off with
`/rdmg preset grounded`.

**A body keeps the receipt.** Once a limb has broken or a region has been destroyed, its
scar stays in the body panel and on the player model, through healing and even through
death. Scars are presentation only: mobs, healing and impairment never read them, and the
accessibility screen can hide them.

**Long-term injuries** are an optional hardcore rule, off by default. A break, a region
driven to zero or a limb lost lowers that region's best possible recovery by one familiar
state band. Food and time still do all the healing, but they stop at that ceiling; sleep and
totems cannot erase it, and while the mode remains enabled it follows the character through
death. The panel shows the ceiling as a band, never a hidden number.

**Predators pick off the wounded.** Hostiles prefer a player whose injury is actually
visible: down first, then a missing or broken leg, then a visible limp, then running blood.
They do not roll for it, do not smell cosmetic scars or hidden meters, and keep a grudge
long enough that a scratch cannot peel them off the player who just hit them. The same limb
rungs drive both the mob's choice and the pose you see on the player model.

**Rescue.** Sneak-use a downed player to drag them out of the fire — half speed, both
hands full. Hold use with empty hands to press a wound shut and slow the bleeding, on a
teammate or on yourself (pressing your own pins you in place: stop the bleeding or keep
fighting, not both). Feed food to someone who can't use their own hands. No bandages
involved in any of it — pressure only buys time, food is still what fixes you.

**Food is the cure.** No bandages, no splints, no medical crafting. Eating, natural
regeneration, sleeping, golden apples and healing potions repair everything — the danger
is whether you have enough food and enough time before things spiral.

## Playing with it

Injuries show on a small body diagram that fades in when something's wrong. States and
injuries are shown by shape and color; exact numbers stay hidden. Action-bar messages call
out the moments that matter ("Your left leg breaks!", "You're bleeding heavily!").

Open your inventory for a larger panel you can hover for detail on any region. Turn on
**Exact Numbers** and it will also show condition figures there — but only there, and
Blood and Consciousness are still only ever named ("heavy loss", "fading"), never
numbered. Being surprised by your own blood level is the point of hiding it.

As consciousness fails the world genuinely changes rather than being overlaid: colour
drains out of it, the view warps, doubles and blurs — the frames you just saw trail behind
the one you're seeing — your aim goes slippery and slow, the horizon starts to tilt and
your head nods, and the world's audio is low-passed into a dull murmur behind a ringing
tone and a heartbeat. Being hit moves the picture as well as the crosshair: a blow rolls
and shoves the view and it springs back, a landing drops it, a blast shakes it. What the screen does tells you *which* thing is wrong — the colour drains for
blood loss and the world warps for head trauma — and how a blow looked tells you what
landed: a cut throws blood across the camera, a puncture pinches the view shut, a club
leaves it ringing and doubled, a blast whites everything out and takes your hearing with
it for several seconds.

Your heart is not a metronome. It races when you're bleeding and slows to something heavy
and enormous when your head is going, and once you're in real trouble it starts to skip —
an early weak beat, a pause too long, then a thump that lands harder than any of them.

Bleeding is something you can see. Drops fall, land, and mark whatever they hit, so you
leave a trail behind you and a pool under you when you stop; a bad wound spurts in time
with your pulse; blood soaks into the limb it came out of and dries there; and it gets on
the camera, which is the part you can't miss. Every layer of it has its own slider, and
none of it is on the HUD.

Losing a limb leaves a stump — on your model, and raised into view in first person — and
the limb itself, thrown along the blow, lying where it lands and bleeding out for most of a
minute before it's gone. You can walk through it, mobs ignore it, and fire burns it away.

Mobs have bodies too. Zombies, skeletons, piglins and the rest of the humanoid families take
the same regional trauma you do: they bleed and bleed out, limp on a broken leg, lose limbs
to a blow or a blast, stop swinging with no arms and stop shooting without a drawing arm,
lose the thread when concussed, and go down at zero consciousness. They mend on time alone.
Skeletons break and lose limbs but never bleed, the undead bleed dark, and a concussed
skeleton shoots wide. Which mobs, and what colour they bleed, are data tags, so a pack can
add its own.

Breaking, losing a limb and going down each have their own synthesised audio, in a muted
set that sits inside Minecraft's palette and a visceral set that doesn't. Your own
injuries are close and loud; a teammate's carry from where they're standing, quieter —
useful for knowing someone across the cave is in trouble.

Press **K** (rebindable) or use Mod Menu for accessibility settings. Every one of those
effects has its own slider and can be turned off individually without disabling
unconsciousness itself, and there's a one-click "turn off all sensory effects" button.
Slow aim and sloppy aim are separate sliders (**Look Slowdown** and **Look Drift**), so
you can have one without the other, and the camera effects that never touch your aim
(**Head Sway**, **Impact Shock**) are separate from the ones that do (**Camera Drift**).
Two presets: **Arcade** (limb destruction, readable effects) and **Grounded** (no
destruction, full blackout, camera drift, everything at full strength).

**Something to hit:** vanilla's mannequin (`/summon minecraft:mannequin`) has a full
body: it bleeds, breaks and loses limbs like a player. (It replaced the mod's own
practice dummy in 1.7.)

**Deaths, as everyone else sees them:** a blast that kills you takes limbs with it, the
nearest first and all four point-blank, and other players see the body thrown, tumbling
and landing flat with a thud, limbs flailing, instead of vanilla's stiff tip onto its
side. It follows the **Death Camera** toggle.

## Server settings

`config/realisticdamage-server.json`, generated on first run:

| Setting | Default | Effect |
|---|---|---|
| `bodyDamageMultiplier` | 1.0 | Scales all body-condition trauma |
| `bleedingRateMultiplier` | 1.0 | Scales blood loss |
| `consciousnessLossMultiplier` | 1.0 | Scales consciousness drain |
| `healingMultiplier` | 1.0 | Scales all recovery |
| `fallTraumaMultiplier` | 1.0 | Scales fall trauma specifically |
| `pvpTraumaMultiplier` | 1.0 | Scales trauma from other players |
| `enableLimbDestruction` | true | Arcade preset: limbs can be destroyed permanently |
| `enableExplosiveDismemberment` | true | Extreme TNT and creeper trauma can take the nearest limb outright; nested under limb destruction |
| `enableDecapitation` | true | An edge on an already-Disabled head takes it off (anything else still crushes it), the most violent lethal blasts throw it, and the head can be picked up and placed but never worn; nested under limb destruction |
| `enableBrokenNecks` | true | A limp or gliding body landing head first, a dive into water one block deep, an elytra into a wall at speed or a huge blunt blow to the head snaps the neck: instant death, no totem saves it, bosses are immune. Off, those only ever give whiplash |
| `enableWhiplash` | true | The same causes under the bar, and hard knockdowns, leave the neck stiff for 30 to 90 s: players turn their view slower and quick turns hurt, mobs turn and aim worse |
| `enableLongTermInjuries` | false | Worst injuries permanently lower a region's healing ceiling by state band |
| `enableMobAnatomy` | true | Mobs in the `realisticdamage:has_anatomy` tag (humanoids by default) take regional trauma, bleed, break, lose limbs and collapse like players |
| `spawnSeveredLimbs` | true | A lost limb is thrown into the world, lands, bleeds for a minute and is gone; nested under limb destruction |
| `enableKnockdown` | true | Hard landings put you on the ground for a moment |
| `knockdownFromBlows` | true | A blast or a heavy blow that does not kill knocks you down too (with `enableKnockdown`) |
| `enableMicrosleeps` | true | Consciousness cuts out in episodes on the way down |
| `disabledArmsLoseGrip` | true | An arm driven to Disabled can't grip until it heals: no raised shield or item use in that hand, no bows/crossbows/spears, and a limp main arm fights like a missing one |
| `enableAdrenaline` | true | Dropping to the last two hearts gives a few seconds of adrenaline (faster, pain masked), then a crash (heavy legs, short winded sprints) |
| `softenFallLethality` | true | Compresses vanilla's lethal fall tail so long drops maim rather than delete |
| `unconsciousAggro` | `NEARBY` | Who notices a body on the floor. `ATTACKERS` keeps whatever was already fighting you, `NEARBY` adds anything within 6 blocks that can see you, `ALL` pulls in every hostile with line of sight, `NONE` takes you off the menu entirely |
| `preyOnWounded` | `VISIBLE` | Which visible injuries hostiles prefer: `OFF`, `DOWNED`, `LIMPING`, or `VISIBLE` (limping and bleeding) |
| `enableBleeding` / `enableBrokenLimbs` / `enableConsciousness` / `enableBurns` / `enableHitLocation` | true | Turn whole systems off |

Every tuning constant lives in one file (`config/Balance.java`) so rebalancing never means
hunting magic numbers.

## Admin & debug commands

`/realisticdamage` (alias `/rdmg`), requires permission level 2 or the
`realisticdamage:command/debug` node:

```
/rdmg info <player>                       show all hidden values
/rdmg heal <player>                       full reset
/rdmg setpart <player> <part> <value>     set body condition
/rdmg damagepart <player> <part> <amount> apply test trauma
/rdmg setblood <player> <0-100>
/rdmg setconsciousness <player> <0-100>
/rdmg break <player> <part>
/rdmg bleed <player> <part> <severity>
/rdmg concussion <player> [mild|moderate|severe|none]
/rdmg burn <player>
/rdmg destroy <player> <part>             destroy a limb outright
/rdmg blast <player> <part> [radius]      test a localized explosion beside a limb
/rdmg ceiling <player> <part|all> <band>  set max/hurt/injured/critical recovery
/rdmg scar <player> <part> <level>        set none/broken/destroyed history
/rdmg config <setting> [value]            inspect or change any server setting
/rdmg preset <arcade|grounded>            switch the server-side preset
```

## For mod authors

`API.md` covers integration. The short version: add your damage type to a
`realisticdamage:ballistic` (or `slash`, `blunt`, ...) tag in a datapack and it's
classified, with no dependency in either direction. There's a small API for the cases a
tag can't answer — runtime-varying categories, and supplying an exact hit point for
hitscan weapons that have no projectile to reconstruct from.

## Notes

State persists across logout, dimension changes and restarts — logging out is not a cure.
Death resets active injury; scars remain, and long-term ceilings remain while that server
rule is enabled. Creative and Spectator are ignored entirely.

## Building

```sh
./gradlew build          # jar in build/libs
./gradlew test           # pure-math unit tests
./gradlew runGametest    # in-server behavior tests
./gradlew runClient          # dev client
./gradlew runClientGametest  # boots a world and screenshots the UI
```

## What's next

`ROADMAP.md` covers what comes after Consequence: anatomy for mobs. Medical items are
explicitly not on it.

## Design

`DESIGN.md` holds the full architecture and the balance tables. Limb destruction was pulled
forward into 1.0 during playtesting; medical items and mob anatomy remain out of scope.
Damage classification is tag- and component-driven so a future compatibility layer (e.g.
firearms mods with exact impact coordinates) can feed the same pipeline.
