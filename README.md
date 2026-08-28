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
fights slower; hurt legs slow you down and eventually cost you your sprint. Nothing is
permanent, and nothing soaks damage that would otherwise hit your hearts.

**Real hit location.** Arrows use their actual impact point; melee reconstructs the
attacker's swing ray. What you hit is what you damage — including left versus right.
Headshots deal no bonus heart damage; their advantage is that heads run consciousness.

**Deterministic injuries.** No fracture rolls, ever. A mace or an iron golem breaks a
healthy limb outright; swords and arrows only break one that's already critical. Cuts and
punctures bleed, blunt trauma doesn't. Identical hits always produce identical outcomes.

**Blood and Consciousness** — two hidden meters. Bleeding drains blood; low blood starts
costing hearts and pulls you toward blacking out. Head trauma can knock you unconscious
with most of your hearts intact. You wake up when you stabilize.

**Food is the cure.** No bandages, no splints, no medical crafting. Eating, natural
regeneration, sleeping, golden apples and healing potions repair everything — the danger
is whether you have enough food and enough time before things spiral.

## Playing with it

Injuries show on a small body diagram that fades in when something's wrong. States and
injuries are shown by shape and color; exact numbers stay hidden. Action-bar messages call
out the moments that matter ("Your left leg breaks!", "You're bleeding heavily!").

Press **K** (rebindable) or use Mod Menu for accessibility settings. Every sensory
effect — vignette, look slowdown, afterimages, exposure shift, ear ringing, heartbeat —
has its own slider and can be turned off individually without disabling unconsciousness
itself. There's a one-click "turn off all sensory effects" button.

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
```

## Notes

State persists across logout, dimension changes and restarts — logging out is not a cure.
Death resets everything. Creative and Spectator are ignored entirely.

## Building

```sh
./gradlew build          # jar in build/libs
./gradlew test           # pure-math unit tests
./gradlew runGametest    # in-server behavior tests
./gradlew runClient      # dev client
```

## What's next

`ROADMAP.md` sketches 1.1 through 1.5: sharpening the rough edges, then rescuing
downed teammates, deeper consequences, mob anatomy, and a public API for other
mods. Medical items are explicitly not on it.

## Design

`DESIGN.md` holds the full architecture and the balance tables. Version 1.0 deliberately
stops at `Disabled` — no destroyed limbs, no permanent injuries, no medical items, no mob
anatomy. Damage classification is tag- and component-driven so a future compatibility
layer (e.g. firearms mods with exact impact coordinates) can feed the same pipeline.
