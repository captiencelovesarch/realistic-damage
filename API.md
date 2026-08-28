# Integrating with Realistic Damage

Two routes. Try the first one before reaching for the second.

## 1. A datapack tag, with no dependency at all

Add your damage type to one of these and it is classified. No code, no
`build.gradle` entry, no load-order problem, and it works from a server-side
datapack even if neither mod knows the other exists.

```
realisticdamage:ballistic          small arms
realisticdamage:heavy_ballistic    rifle and magnum calibres
realisticdamage:slash              blades
realisticdamage:heavy_slash        axes, greatswords
realisticdamage:puncture           arrows, spears
realisticdamage:heavy_puncture     bolts, tridents
realisticdamage:blunt              clubs, fists
realisticdamage:heavy_blunt        maces, hammers
```

`data/yourmod/tags/damage_type/../realisticdamage/ballistic.json`:

```json
{ "replace": false, "values": ["yourmod:bullet"] }
```

Use `{ "id": "...", "required": false }` for a mod that might not be installed.
That is how the four TACZ bullet types are already listed — they classify the
moment TACZ is present and cost nothing when it is not.

These tags are consulted **before** the built-in table, so a datapack can also
overrule how vanilla damage is read. A server that thinks tridents should count
as heavy puncture just says so.

### What the categories do

Ballistic bleeds sooner than anything else in the table and, for small arms,
cannot break a bone on impact — a bullet passes through rather than clubbing.
Bones still give way by accumulation once a limb is wrecked. Heavy ballistic
carries enough energy to shatter what it hits.

## 2. The API, when a tag cannot answer

Reach for this only when the answer depends on runtime state — which calibre is
chambered, say — or when you already know exactly where you hit.

`dev.captience.realisticdamage.api.RealisticDamageApi`, called from your mod
initialiser. Everything is server-side.

### Say what your damage is

```java
RealisticDamageApi.registerClassifier((victim, source) -> {
    if (!(source.getDirectEntity() instanceof MyBullet bullet)) {
        return null;               // defer to tags and the built-in table
    }
    return ExternalClassification.of(bullet.isRifle()
            ? DamageCategory.HEAVY_BALLISTIC
            : DamageCategory.BALLISTIC);
});
```

Returning null defers. Registered classifiers win over both the tags and the
built-in table — except for the mod's own internal damage, which stays out of
the pipeline no matter what you claim, because letting it back in would be an
infinite loop.

### Say where you hit

This is the whole integration for a hitscan weapon. There is no projectile
entity to reconstruct a location from, so without this every shot resolves to
the torso and the headshot your player just earned silently stops counting.

```java
RealisticDamageApi.registerImpactPoints((victim, source) ->
        MyGunMod.lastHitPosition(source));   // or null to defer
```

The point is clipped to the victim's bounding box, so being slightly off is
harmless — a wildly wrong point still resolves to some region rather than
breaking anything.

### Read the body

```java
BodyView body = RealisticDamageApi.bodyOf(player);
if (body != null && body.broken(BodyPart.RIGHT_ARM)) {
    // ...
}
```

Null for creative and spectator players, who are outside the mod entirely.

`blood()` and `consciousness()` are available, but the mod hides both from
players on purpose. If you surface them, show a band (`bloodBand()`) rather
than a figure — being surprised by your own blood level is the point of hiding
it.

## Stability

`DamageCategory`, `BodyPart` and `BodyState` are enums other mods are expected
to switch on; new constants may be added but existing ones will not be renamed
or removed without a major version. The `api` package is the supported surface —
everything else is internal and will move.
