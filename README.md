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

## The field kit (2.0, optional)

2.0 adds about forty survival items in two halves, each behind its own server switch.
**Both start off**, and with both off everything above holds exactly: food and time are
the only cure.

- **Weapons:** anything that hurts someone, or protects you from being hurt. Brewed
  poisons (Haemorrhage, Sedation, Adrenaline), trick arrows, blowgun darts, bolas, nets,
  caltrops, the bear trap, the blackjack, flash bangs, blood bait, and the gas mask and
  padding that answer them. They hit players exactly as hard as mobs, so they change PvP.
- **Medicine:** anything that treats, supports or reads an injury that already happened.
  Bandages, tourniquets, cauterizing, needle and thread, blood bags, splints, slings,
  smelling salts, the stethoscope, Numbing, coffee, spirits, crutches and prosthetics.

The first time the game starts with 2.0 it asks about both. After that they live on the
**World rules** page (the mod's settings, from Mod Menu or **K**), which shows every
server setting: in singleplayer you change them there directly; on a server an
operator's changes go through `/rdmg config`, and everyone else sees them read-only.

Off means off, without a restart: a switched-off half can't be crafted or brewed, isn't
in loot, trades or the creative tab, anything already in a chest does nothing and says
why, and its effects wear off at once. Flip it back and it all returns. The items arrive
over the 2.0 alphas; the **Field kit** creative tab shows whatever exists so far.

**The brews** (phase 3): start from an awkward potion. Redstone makes them last longer,
glowstone makes them stronger where there's a level to raise, and gunpowder and dragon's
breath make splash and lingering potions as usual. Eight arrows round one drinkable potion
make eight tipped arrows, no dragon's breath needed. Milk clears them, but what they already
did stays done.

- **Haemorrhage** (Weapons; sweet berries): the blood stops clotting. A lighter hit opens a
  bleed, wounds open a step worse, they bleed past the usual cap, and a hand pressed on them
  holds back half as much. An arrow of it opens a bleed where it lands, on anything that
  bleeds.
- **Sedation** (Weapons; poppy): every dose builds. Your eyelids get heavy and you slow down,
  then you stumble and nod off, then you're out, and enough of it stops your breathing. One
  arrow drops a zombie or a player; a ravager takes five. It wears off with time. Drunk, it's
  the **Sleeping Draught**: you sleep where you stand and wake healed like a night in a bed,
  lying on the floor where everything hunts first. Sneak-use an unconscious mob with an
  empty hand to drag it. Bosses and golems shrug it off.
- **Adrenaline** (Weapons; glow berries): the last-two-hearts surge, now, for as long as it
  lasts, then the usual crash. It heals nothing, your racing heart makes open wounds bleed
  harder, and a second dose is refused until your heart has recovered from the last surge.
- **Numbing** (Medicine; packed ice): for a minute you feel nothing. No limp, no flinch, no
  pain blur, and your body diagram goes pale because you can't feel what's wrong. Every
  injury is still there: sprinting on a broken leg wears it down, and so does swinging a
  broken arm.

**The bleeding kit** (Medicine, phase 4). Hold use to treat yourself, or right-click
someone with it and hold to treat them; each lands on the worst wound it can help.

- **Bandage** (2 paper + string makes 3; about 3 s): slows a minor or moderate wound. A
  moderate one soaks it through in under a minute and it needs changing; a spurting
  (arterial) wound it can't hold at all.
- **Tourniquet** (leather + stick + string; 2 s): stops a limb's bleed dead, but the limb
  is Disabled while it's on, and after five minutes it dies. Sneak and use with an empty
  hand to take it off (in the air for your own, on someone for theirs).
- **Cauterizing:** sneak and use flint and steel or a fire charge in the air (or on
  someone). The worst bleed stops at once, the pain can knock you out, and it leaves a
  burn.
- **Needle and Thread** (iron nugget + string; 10 s, four wounds): closes the worst wound
  for good. Getting hurt, either of you, spoils the stitching.
- **Blood Bag:** sneak and use a glass bottle in the air to bank your own blood, or on a
  mob or someone who's out cold; use the bag (5 s) to give the blood back. It keeps three
  days.

**Bones, head and senses** (Medicine, phase 4). The same hold-to-use.

- **Splint** (2 sticks + string; 4 s): goes on the worst broken arm or leg and halves what
  the break costs you (the limp, the slow swing, the stalled bow). It stays on until the
  bone has knit. With long-term injuries on, a limb that knits in a splint keeps its full
  strength.
- **Sling** (any wool + string; 3 s): holds a broken arm still. Its hand can't use
  anything, and if it's your main arm you can't swing or dig either. In exchange it heals
  twice as fast. It comes back to you when the arm has knit; sneak and use with an empty
  hand to take it out sooner.
- **Smelling Salts** (glass bottle + bone meal; 1 s): bring round someone who was knocked
  out or fainted. Not someone out from blood loss, from lack of air, or from Sedation.
- **Stethoscope** (copper ingot + string): hold use on someone, or in the air for
  yourself, and hear their heart: fast, weak or skipping. Only you hear it, and it's only
  sound.
- **Tinted Goggles** (2 tinted glass + leather): worn on the head instead of a helmet
  (they're no protection). Bright light stops making a concussion worse, and the world
  looks darker through them.

**Food and drink** (Medicine, phase 4).

- **Coffee** (cocoa beans + water bottle + sugar; drunk, the bottle comes back):
  Caffeinated for 3 minutes. Sedation takes you a stage less far (one dart leaves you
  stumbling, not out), and you don't nod off in microsleeps. Your heart runs faster, so
  open wounds bleed more.
- **Spirits** (brew a potato into a water bottle): Tipsy for 2 minutes. A limp and a sore
  arm slow you half as much, the world doubles as it moves, and wounds bleed a little more.
- **Blood Sausage** (raw porkchop + blood bag makes a raw one, and the bag's bottle comes
  back; cook it like pork): a cooked porkchop's worth of food, and 20 more Blood than any
  other food that size. A bag that has gone off makes nothing.
- **Iron Tonic** (brew an iron nugget into an awkward potion; redstone makes it last 8
  minutes): Blood comes back twice as fast for 3 minutes. Like all Blood coming back, it
  waits until you've stopped bleeding.

## Playing with it

Injuries show on a small body diagram that fades in when something's wrong. States and
injuries are shown by shape and color; exact numbers stay hidden. Action-bar messages call
out the moments that matter ("Your left leg breaks!", "You're bleeding heavily!").

Open your inventory and the box that normally holds your turning model shows your body
instead: a paper doll of your own skin with what is wrong drawn on it (blood, a grey cast
and a crack on a broken limb, a stump where one is gone, a frame round each region in the
colour of its state). Hover a region for the detail. The small button in the corner of the
box switches to an x-ray on Minecraft's own skeleton, or back to the ordinary model. Turn on
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

`config/realisticdamage-server.json`, generated on first run. Every one is also on the
**World rules** page in game:

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
| `enableBloodiedItems` | true | A held weapon or tool that opens a wound, lands in one or kills keeps the blood on it: it dries from red to brown over half a minute and stays until rain or standing water rinses it off. Only things that don't stack |
| `enableWeapons` | false | The field kit's Weapons half: poisons, trick arrows, traps and the gear that answers them. Off, none of it can be made or found and any already held does nothing |
| `enableMedicine` | false | The field kit's Medicine half: bandages, splints, tourniquets and other treatment. Off, food and time are the only cure |

Every tuning constant lives in one file (`config/Balance.java`) so rebalancing never means
hunting magic numbers.

## Admin & debug commands

`/realisticdamage` (alias `/rdmg`), requires permission level 2 or the
`realisticdamage:command/debug` node:

```
/rdmg info [target]                        show all hidden values
/rdmg heal [targets]                       full reset
/rdmg set <targets> <part> <value>         set a part's condition
/rdmg set <targets> blood <0-100>
/rdmg set <targets> consciousness <0-100>
/rdmg damage <targets> <part> <amount>     apply test trauma
/rdmg break <targets> <part>               fracture an arm or leg; head = break the neck
/rdmg destroy <targets> <part>             destroy a part outright
/rdmg behead [targets]                     take the head off
/rdmg bleed <targets> <part> <severity>
/rdmg whiplash [targets] [0-1]
/rdmg concussion <targets> [mild|moderate|severe|none]
/rdmg burn <targets>
/rdmg blast <targets> <limb> [radius]      test a localized explosion beside a limb
/rdmg ceiling <players> <part|all> <band>  set max/hurt/injured/critical recovery
/rdmg scar <players> <part> <level>        set none/broken/destroyed history
/rdmg config <setting> [value]             inspect or change any server setting
/rdmg preset <arcade|grounded>             switch the server-side preset
```

Every verb reads the same way: the verb, then who, then which part, then how much
(`/rdmg break Steve head`, `/rdmg set @s blood 40`). "Who" is any selector: players
always, and any mob with anatomy (`@e[type=zombie,limit=1,sort=nearest]`).

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

`ROADMAP.md` covers what comes after Consequence: anatomy for mobs. `2.0_PLAN.md` covers
2.0: heads and necks, and the switchable field kit. Medicine in the core mod is still
explicitly not planned; it lives behind the Medicine switch.

## Design

`DESIGN.md` holds the full architecture and the balance tables. Limb destruction was pulled
forward into 1.0 during playtesting; medical items stay out of the core mod and live behind
the field kit's Medicine switch.
Damage classification is tag- and component-driven so a future compatibility layer (e.g.
firearms mods with exact impact coordinates) can feed the same pipeline.
