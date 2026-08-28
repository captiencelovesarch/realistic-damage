# Realistic Damage — Design (v1.1)

> "Minecraft, but damage is cooler." Vanilla hearts stay authoritative; this mod adds a
> physical layer: locational body trauma, deterministic injuries (Broken / Bleeding /
> Concussion / Burned), a hidden Blood meter, and a hidden Consciousness meter.
> Food remains the universal cure. No RNG in outcomes. Player-only. Nothing permanent.

Target: **Fabric, Minecraft 26.2, Fabric API 0.158.0+26.2, Java 25, Mojang mappings.**

## Four questions, four systems

| System | Question it answers | Storage |
|---|---|---|
| Vanilla HP | "Am I dying?" | vanilla (untouched) |
| Body condition ×6 | "How badly does my body work?" | attachment |
| Blood (0–100) | "Can I keep sustaining myself?" | attachment |
| Consciousness (0–100) | "Can I remain functional right now?" | attachment |

## Architecture

Server-authoritative. All state lives in a `RealisticDamageData` player attachment
(fabric-data-attachment-api), synced to the owning client via custom payloads on
login/respawn/meaningful-change plus a low-rate interpolation channel for Blood and
Consciousness. The client only presents.

Package layout (`dev.captience.realisticdamage`):

- `body/` — `BodyPart`, `BodyState`, `BodyPartData`, `RealisticDamageData`, `BleedWound`,
  `Vitals` (band math for Blood/Consciousness, shared server/client so the warnings and
  the inventory panel can never word the same state differently)
- `damage/` — `DamageCategory`, `DamageProfile`, `DamageProfiles`, `DamageClassifier`,
  `BodyHitResolver`, `EnvironmentalRouter`, `TraumaCalculator`, `DamageHooks`
- `systems/` — `BleedingSystem`, `ConsciousnessSystem`, `HealingSystem`,
  `ImpairmentSystem`, `TickDispatcher`
- `net/` — payload records + `RealisticDamageNetworking`
- `command/` — `RealisticDamageCommands` (debug/admin)
- `config/` — `Balance` (all tuning constants), `ServerConfig`
- `client/` (client source set) — `BodyHud`, `ConsciousnessEffects`, `ClientState`,
  accessibility config screen glue

Pure-math logic (state thresholds, wound stacking, impairment curves, trauma math) is
written MC-type-free where possible so it unit-tests without a Minecraft runtime.

## The damage pipeline

```
vanilla damage happens (armor/enchants/resistance/absorption all vanilla)
  └─ AFTER_DAMAGE hook: post-mitigation amount, source, blocked flag
       ├─ classify → DamageCategory + DamageProfile
       ├─ locate  → BodyHitResolver (projectile pos / melee ray / environmental route)
       ├─ trauma  → TraumaCalculator: condition damage, deterministic break check,
       │            concussion check, localized-armor modifier, secondary limb shock
       ├─ wounds  → BleedingSystem: deterministic wound creation by threshold
       └─ mind    → ConsciousnessSystem: head trauma / blunt / explosion / fall pressure
```

Body parts never soak vanilla damage. Bleed-out HP damage uses a tagged damage type that
the pipeline ignores (recursion safety, spec §51).

## Balance constants (authoritative copy lives in `config/Balance.java`)

Body condition max: HEAD 20, TORSO 20, ARM 13/13, LEG 16/16.
States by % of max: HEALTHY >75, HURT >50, INJURED >25, CRITICAL >0, DISABLED =0.

Trauma multipliers: GENERIC .75, SLASH 1.0, HEAVY_SLASH 1.15, PUNCTURE 1.10,
HEAVY_PUNCTURE 1.20, BLUNT 1.20, HEAVY_BLUNT 1.40, EXPLOSION 1.35, FALL 1.25,
FIRE .60, LAVA 1.0, SYSTEMIC .10.

Head/torso hits leak 10% of localized trauma to each limb. Systemic damage ≈10%
trauma-equivalent spread across regions.

Break thresholds (single qualifying heavy impact): ARM 5.5, LEG 6.5 condition damage.
Accumulated path: qualifying slash/puncture/blunt hits that leave the limb CRITICAL or
DISABLED set Broken. Broken clears when the limb re-enters HEALTHY.

Wound bleed rates (Blood/sec): MINOR .25, MODERATE .6, SEVERE 1.2, CRITICAL 2.0.
Stacking weights sorted desc: 100% / 50% / 25% / 12.5% each after (cap total).
Blood bands: ≥70 stable, 50–70 mild, 30–50 significant (HP ticks begin <30),
15–30 critical, <15 catastrophic; 0 = bleed-out (rapid HP+consciousness drain, death by
ordinary HP loss only).

Consciousness: baseline recovery ~2.5/s modified by head state, concussion, blood,
asphyxia. Head-state caps: HEALTHY 100, HURT ~95, INJURED ~82, CRITICAL ~55,
DISABLED ~25. Collapse at 0; wake at ≥20 with ≥2.5 s minimum unconscious time.
Symptom ladder thresholds: 75 vignette, 65 sensitivity, 55 attack recovery, 45
afterimages, 35 exposure, 30 ringing, 25 heartbeat, 20 sprint-loss/sluggishness,
10 heavy everything + micro-blackouts, 0 collapse. Sensitivity reduction capped 35%.

Arm penalty by state: 0 / 5% / 12% / 30% / 50% (smooth-lerped inside bands, +Broken adds
a flat strong penalty, total capped ~65%). Legs: worse leg weighted 70/30 vs better leg;
Disabled leg disables sprint; both-legs-wrecked floor keeps limp speed ≥ ~25% walk.

Totem floor: consciousness ≥60, blood ≥45, bleeding stopped, Disabled parts → 15% floor
(low CRITICAL); Broken/Concussion/Burned persist.

Sleep: consciousness →100 (subject to head cap), Blood +60, repair ~50% of missing
condition, minor wounds cleared.

Healing loop: every vanilla natural-regen event grants a body-repair budget (weighted
toward most-damaged regions), Blood restoration, and wound decay; eating grants an
immediate Blood bump scaled by nutrition+saturation; golden foods multiply strongly.
No regeneration of body condition while starving (hunger 0).

## Added in 1.1

Injury audio is synthesised (`tools/generate_sounds.py`) in two styles, muted and
visceral, and is **routed rather than played as a world sound**: the server sends an
`InjurySoundPayload` to the injured player and to everyone within
`INJURY_SOUND_RADIUS`, and each client plays its own configured style — full volume and
non-positional for your own body, 40% and positional for anybody else's. Style is
presentation, so the server never decides it.

Every damage type the mod inflicts carries the `realisticdamage:internal` tag, and the
recursion guards (spec §51) test that tag rather than listing keys, so a damage type
added later cannot be forgotten in one of two places. Core destruction split into
`skull_crushed` and `chest_crushed` so the two read as different deaths.

`unconsciousAggro` (ServerConfig) governs what notices a downed player. Default
`NEARBY` = prior attackers plus anything within 6 blocks with line of sight; `ALL` is
1.0's behaviour, `NONE` takes you off the menu.

Client presentation gained `lookInertiaIntensity` (split out of the sensitivity
control), `showNumericStats`, `injurySoundVolume` and `visceralInjurySounds`. The
accessibility screen is now an `OptionsSubScreen` with a scrolling, sectioned list.

## Deliberate non-goals

DESTROYED state beyond what 1.0 shipped, medical items, mob anatomy, rescue/carry
mechanics (1.2), numeric HUD stats — the inventory panel may show condition figures on
request, the HUD never does, and Blood/Consciousness are named by band and never
numbered anywhere. TACZ integration is still deferred, but DamageCategory +
BodyHitResolver accept externally-supplied classification/hit-points so an adapter can
slot in later.
