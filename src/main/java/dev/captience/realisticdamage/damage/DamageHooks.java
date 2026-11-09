package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Integration with vanilla damage (spec §4-5). ALLOW_DAMAGE captures health so
 * AFTER_DAMAGE (same hurtServer call, fires pre-armor amounts only) can compute
 * the true post-mitigation damage as a health delta — no re-derived armor math,
 * i-frame partial hits and full blocks come out correct for free, and absorbed
 * damage causes no trauma (spec §39).
 */
public final class DamageHooks {
	private DamageHooks() {}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (MobAnatomy.participates(entity) && !isInternal(source)) {
				RDAttachments.get(entity).capturePreHurt(entity.getHealth());
			}
			return true;
		});

		// Players, and since 1.7 every mob in the has_anatomy tag: one pipeline,
		// two kinds of body. The player-only consequences gate themselves inside.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (!MobAnatomy.participates(entity) || isInternal(source)) {
				return;
			}
			RealisticDamageData data = RDAttachments.get(entity);
			double applied = data.consumePreHurt(entity.getHealth());
			if (applied < 0) {
				applied = damageTaken; // no capture (shouldn't happen) — degrade to the pre-armor amount
			}
			TraumaPipeline.handle(entity, data, source, baseDamageTaken, damageTaken, applied, blocked);
			Knockdowns.afterHit(entity, data, source, baseDamageTaken);
			RDAttachments.pushSnapshot(entity);
			MobAnatomy.track(entity);
		});

		// The killing blow is invisible to AFTER_DAMAGE — Fabric's contract says
		// so plainly, and it means the trauma pipeline never sees the hit that
		// kills. ALLOW_DEATH is the hook that does: it fires with the fatal
		// source and amount, before the totem check, which is why this only
		// *scores* the death rather than announcing it.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && participates(player)) {
				if (!isInternal(source)) {
					scoreDeathViolence(player, RDAttachments.get(player), source, amount);
				} else if (source.is(RDDamageTypes.SKULL_CRUSHED) || source.is(RDDamageTypes.CHEST_CRUSHED)) {
					// A destroyed head or torso is the mod's own damage, so the scoring
					// above never saw it and "crushed beyond saving" by an iron golem
					// got a bleed-out's slow fade (1.7 fix). There is no more violent
					// way to die in this mod.
					RDAttachments.get(player).stashDeathViolence(1.0, player.tickCount);
				}
			}
			return true;
		});

		// The violence of a death travels only once the death is real.
		// ALLOW_DEATH stashes it when it sees the fatal blow, but a totem can
		// still refuse that death — AFTER_DEATH is the first moment that cannot
		// be taken back. The stash is tick-stamped, so one that a totem orphaned
		// can never colour a later, calmer death.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Corpses.died(entity, source);
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}
			double violence = RDAttachments.get(player).consumeDeathViolence(player.tickCount);
			lastDeathKind = DeathKind.of(source);
			lastDeathViolence = violence;
			if (player.connection != null
					&& net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
							player, dev.captience.realisticdamage.net.DeathPayload.TYPE)) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
						new dev.captience.realisticdamage.net.DeathPayload((float) violence, lastDeathKind));
			}
		});
	}

	/**
	 * Scores how violent a fatal blow was and stashes it for AFTER_DEATH to
	 * send: falls by their height — 1.4's lethality compression deliberately
	 * shrinks exactly the HP figure a damage read would use — and everything
	 * else by the raw blow against the whole health bar. Sources the classifier
	 * ignores (the void, /kill, starving) score nothing, so those deaths keep
	 * the slow ending. Returns the stashed violence, which is what the gametests
	 * assert against.
	 */
	public static double scoreDeathViolence(ServerPlayer player, RealisticDamageData data,
			net.minecraft.world.damagesource.DamageSource source, float amount) {
		var classified = DamageClassifier.classify(player, source);
		if (classified == null) {
			return 0.0;
		}
		double violence = classified.category() == DamageCategory.FALL && data.pendingFallHeight() > 0.0
				? DeathMath.violenceFromFall(data.pendingFallHeight())
				: DeathMath.violenceFromBlow(amount, player.getMaxHealth());
		data.stashDeathViolence(violence, player.tickCount);
		return violence;
	}

	private static @org.jspecify.annotations.Nullable DeathKind lastDeathKind;

	private static double lastDeathViolence;

	/** The kind of the last player death AFTER_DEATH saw; for the gametests. Server thread only. */
	public static @org.jspecify.annotations.Nullable DeathKind lastDeathKind() {
		return lastDeathKind;
	}

	/** ...and the violence it sent with it. */
	public static double lastDeathViolence() {
		return lastDeathViolence;
	}

	/** Damage the mod deals to itself must never re-enter the trauma pipeline (spec §51). */
	private static boolean isInternal(net.minecraft.world.damagesource.DamageSource source) {
		return source.is(RDDamageTypes.INTERNAL);
	}

	private static boolean participates(ServerPlayer player) {
		return !player.isCreative() && !player.isSpectator();
	}
}
