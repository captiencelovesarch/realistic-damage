package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.net.CorpsePayload;
import dev.captience.realisticdamage.net.KnockdownPayload;
import dev.captience.realisticdamage.systems.MobAnatomy;
import dev.captience.realisticdamage.util.Notifier;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Thrown off your feet without dying (active ragdolls phase 4): a blast or a
 * heavy blow that does not kill knocks the body down, the same knockdown a
 * hard landing gives (movement and jumping locked, the prone hitbox), and
 * every client watching (the one knocked down included) is told how it was
 * thrown, so they can ragdoll it and stand it back up after.
 *
 * <p>From AFTER_DAMAGE, so only blows that did not kill; a killing one is
 * {@link Corpses}'s.
 */
public final class Knockdowns {
	private Knockdowns() {}

	/** Hitters heavy enough that a solid blow from one takes anyone off their feet. */
	private static final Set<EntityType<?>> HEAVY = Set.of(EntityTypes.IRON_GOLEM, EntityTypes.RAVAGER,
			EntityTypes.WARDEN, EntityTypes.HOGLIN, EntityTypes.ZOGLIN);

	/** After the trauma pipeline, for a hit that did not kill. */
	public static void afterHit(LivingEntity body, RealisticDamageData data, DamageSource source, float raw) {
		ServerConfig config = ServerConfig.get();
		if (!body.isAlive() || !config.enableKnockdown || !config.knockdownFromBlows || MobAnatomy.boss(body)) {
			return;
		}
		if (body instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}
		ClassifiedDamage classified = DamageClassifier.classify(body, source);
		if (classified == null) {
			return;
		}
		double violence = DeathMath.violenceFromBlow(raw, body.getMaxHealth());
		DeathKind kind = DeathKind.of(source);
		boolean blast = kind == DeathKind.EXPLOSION;
		Entity attacker = source.getEntity();
		boolean heavy = attacker != null && HEAVY.contains(attacker.getType());
		boolean down;
		if (blast) {
			down = violence >= Balance.KNOCKDOWN_BLAST_VIOLENCE;
		} else if (heavy) {
			down = violence >= Balance.KNOCKDOWN_HEAVY_VIOLENCE;
		} else {
			down = classified.routing() == ClassifiedDamage.Routing.LOCATIONAL
					&& violence >= Balance.KNOCKDOWN_BLOW_VIOLENCE;
		}
		if (!down) {
			return;
		}
		int ticks = (int) Math.round(Balance.KNOCKDOWN_HIT_TICKS_MIN
				+ (Balance.KNOCKDOWN_HIT_TICKS_MAX - Balance.KNOCKDOWN_HIT_TICKS_MIN) * Math.min(1.0, violence));
		data.knockDown(ticks);
		// Thrown down hard, the neck whips (2.0). Never snaps it: that bar is the blow's own.
		BrokenNeck.strain(body, data, dev.captience.realisticdamage.systems.NeckMath.knockdown(violence), attacker);
		body.stopUsingItem();
		if (body.isSprinting()) {
			body.setSprinting(false);
		}

		// How it was thrown: from the blast or the hitter, onto the region nearest it.
		Vec3 from = blast ? source.getSourcePosition() : attacker != null ? attacker.position() : null;
		Vec3 dir = Vec3.ZERO;
		BodyPart part = BodyPart.TORSO;
		if (from != null) {
			Vec3 away = body.position().add(0.0, body.getBbHeight() * 0.5, 0.0).subtract(from);
			dir = new Vec3(away.x, 0.0, away.z);
			dir = dir.lengthSqr() < 1.0e-6 ? Vec3.ZERO : dir.normalize();
			if (blast) {
				double best = -1.0;
				for (var share : BodyHitResolver.explosionShares(body, from).entrySet()) {
					if (share.getValue() > best) {
						best = share.getValue();
						part = share.getKey();
					}
				}
			}
		}
		// Vanilla's knockback is already on the body; a blast also lifts it.
		Vec3 launch = body.getDeltaMovement();
		if (blast) {
			launch = launch.add(dir.scale(0.15 * violence)).add(0.0, 0.2 + 0.25 * violence, 0.0);
			if (!(body instanceof ServerPlayer)) {
				// A mob's position is the server's: throw it here, and the ragdolls
				// that watch it follow.
				body.setDeltaMovement(launch);
				body.hurtMarked = true;
			}
		}
		double kick = (blast ? 0.2 : 0.12) + 0.25 * Math.min(1.0, violence);
		CorpsePayload launchPayload = new CorpsePayload(body.getId(), kind, (float) violence,
				(float) launch.x, (float) launch.y, (float) launch.z, part.ordinal(),
				(float) dir.x, (float) dir.y, (float) dir.z, (float) kick);
		KnockdownPayload payload = new KnockdownPayload(launchPayload, ticks);
		for (ServerPlayer watcher : PlayerLookup.tracking(body)) {
			if (watcher != body && ServerPlayNetworking.canSend(watcher, KnockdownPayload.TYPE)) {
				ServerPlayNetworking.send(watcher, payload);
			}
		}
		if (body instanceof ServerPlayer self && self.connection != null
				&& ServerPlayNetworking.canSend(self, KnockdownPayload.TYPE)) {
			ServerPlayNetworking.send(self, payload);
		}
		Notifier.message(body, blast ? "realisticdamage.msg.knocked_down.blast" : "realisticdamage.msg.knocked_down.blow");
		RDAttachments.pushSnapshot(body);
	}
}
