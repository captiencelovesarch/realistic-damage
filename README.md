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

**Limb destruction** (Arcade preset, on by default). Keep hitting a limb that is already
Disabled and it is gone for good: no healing, no golden apple, only death restores it. A
destroyed arm cannot use that hand and works one-handed at a heavy penalty, destroyed legs
are a permanent limp, losing both means you crawl, and a destroyed head or torso kills.
Destroyed limbs vanish from your body in first and third person. Turn it off with
`/rdmg preset grounded`.

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
drains out of it, the view warps and blurs, your aim goes slippery and slow, your head
sags, and the world's audio is low-passed into a dull murmur behind a ringing tone and a
rising heartbeat. Heavy blows flash and kick the camera on impact.

Breaking, losing a limb and going down each have their own synthesised audio, in a muted
set that sits inside Minecraft's palette and a visceral set that doesn't. Your own
injuries are close and loud; a teammate's carry from where they're standing, quieter —
useful for knowing someone across the cave is in trouble.

Press **K** (rebindable) or use Mod Menu for accessibility settings. Every one of those
effects has its own slider and can be turned off individually without disabling
unconsciousness itself, and there's a one-click "turn off all sensory effects" button.
Slow aim and sloppy aim are separate sliders (**Look Slowdown** and **Look Drift**), so
you can have one without the other.
Two presets: **Arcade** (limb destruction, readable effects) and **Grounded** (no
destruction, full blackout, camera drift, everything at full strength).

## Practice dummy

`/rdmg dummy spawn` puts down a player-equivalent target — a real player as far as the
mod is concerned, so it bleeds, breaks, concusses and goes down exactly as you would.

| Command | Effect |
|---|---|
| `/rdmg dummy spawn` | Spawn one in front of you, facing you |
| `/rdmg dummy fightback <true\|false>` | Have it swing back with whatever it's holding |
| `/rdmg dummy equip` | Copy your own held item and armour onto it |
| `/rdmg dummy heal` | Full health and a pristine body, without respawning it |
| `/rdmg dummy remove` | Remove them all |

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
| `unconsciousAggro` | `NEARBY` | Who notices a body on the floor. `ATTACKERS` keeps whatever was already fighting you, `NEARBY` adds anything within 6 blocks that can see you, `ALL` pulls in every hostile with line of sight, `NONE` takes you off the menu entirely |
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
/rdmg concussion <player>
/rdmg burn <player>
/rdmg destroy <player> <part>             destroy a limb outright
/rdmg preset <arcade|grounded>            switch the server-side preset
/rdmg dummy spawn|remove|heal|equip       practice target (see above)
/rdmg dummy fightback <true|false>
```

## For mod authors

`API.md` covers integration. The short version: add your damage type to a
`realisticdamage:ballistic` (or `slash`, `blunt`, ...) tag in a datapack and it's
classified, with no dependency in either direction. There's a small API for the cases a
tag can't answer — runtime-varying categories, and supplying an exact hit point for
hitscan weapons that have no projectile to reconstruct from.

## Notes

State persists across logout, dimension changes and restarts — logging out is not a cure.
Death resets everything. Creative and Spectator are ignored entirely.

## Building

```sh
./gradlew build          # jar in build/libs
./gradlew test           # pure-math unit tests
./gradlew runGametest    # in-server behavior tests
./gradlew runClient          # dev client
./gradlew runClientGametest  # boots a world and screenshots the UI
```

## What's next

`ROADMAP.md` covers 1.3 through 1.5: deeper consequences, mob anatomy, and a public API for other
mods. Medical items are explicitly not on it.

## Design

`DESIGN.md` holds the full architecture and the balance tables. Limb destruction was pulled
forward into 1.0 during playtesting; medical items and mob anatomy remain out of scope.
Damage classification is tag- and component-driven so a future compatibility layer (e.g.
firearms mods with exact impact coordinates) can feed the same pipeline.
