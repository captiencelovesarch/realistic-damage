package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.PendingExplosion;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.systems.BleedingSystem;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.net.CorpsePayload;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * What a death does to the body, on the server (1.7): a lethal blast takes it
 * apart ({@link LethalBlast}), and every client watching is told how the body
 * goes down ({@link CorpsePayload}).
 */
public final class Corpses {
	private Corpses() {}

	/** A lethal blast at least this violent also bursts the torso. */
	private static final double GORE_BURST_VIOLENCE = 0.6;

	/** Fastest launch sent, blocks a tick; a corpse leaving the render distance helps nobody. */
	private static final double MAX_LAUNCH = 2.0;

	/**
	 * Bodies whose removal waits for the ragdoll, by identity. Weak-keyed so a
	 * body removed some other way (unloaded chunk, /kill of the corpse) cannot
	 * leak. Server thread only.
	 */
	private static final java.util.Set<LivingEntity> HELD =
			java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	/**
	 * Whether vanilla's removal of this dead body should wait: it was ragdolled
	 * and has not been dead for {@code Balance.CORPSE_HOLD_TICKS} yet.
	 */
	public static boolean holding(LivingEntity body) {
		if (!HELD.contains(body)) {
			return false;
		}
		if (!body.isDeadOrDying() || body.deathTime >= dev.captience.realisticdamage.config.Balance.CORPSE_HOLD_TICKS) {
			HELD.remove(body);
			return false;
		}
		return true;
	}

	/**
	 * A staged death for the ragdoll test command ({@code /rdmg ragdoll}): the
	 * next death of this body is sent as this kind, struck here, this hard,
	 * whatever actually killed it. Weak-keyed like {@link #HELD}.
	 */
	public record Script(DeathKind kind, @org.jspecify.annotations.Nullable BodyPart part, Vec3 dir,
			double kick, double violence) {}

	private static final java.util.Map<LivingEntity, Script> SCRIPTS = new java.util.WeakHashMap<>();

	public static void script(LivingEntity body, Script script) {
		SCRIPTS.put(body, script);
	}

	/** From AFTER_DEATH, inside the killing hurt call. */
	public static void died(LivingEntity body, DamageSource source) {
		if (!MobAnatomy.participates(body)) {
			return;
		}
		Script script = SCRIPTS.remove(body);
		if (script != null) {
			scripted(body, script);
			return;
		}
		RealisticDamageData data = RDAttachments.get(body);
		DeathKind kind = DeathKind.of(source);
		PendingExplosion blast = kind == DeathKind.EXPLOSION ? data.pendingExplosion() : null;
		Vec3 launch;
		double violence;
		// Where the killing blow landed, which way it was going and how hard it
		// hit that one region: the ragdoll takes it there.
		BodyPart hitPart = null;
		Vec3 hitDir = Vec3.ZERO;
		double kick = 0.0;
		if (blast != null) {
			if (!LethalBlast.apply(body, data, blast).isEmpty()) {
				RDAttachments.pushSnapshot(body);
			}
			double v = DeathMath.violenceFromBlow(blast.amount(), body.getMaxHealth());
			Vec3 centre = new Vec3(blast.x(), blast.y(), blast.z());
			// At its most violent a blast takes the head with the limbs (2.0).
			if (v >= Balance.DECAP_BLAST_VIOLENCE && ServerConfig.get().enableExplosiveDismemberment
					&& Decapitation.can(body, data)) {
				Decapitation.sever(body, data,
						BodyHitResolver.regionCenter(body, BodyPart.HEAD).subtract(centre), true, null);
			}
			if (v >= GORE_BURST_VIOLENCE && ServerConfig.get().enableBleeding && MobAnatomy.bleeds(body)) {
				// What is left of the body comes apart too: a burst from the torso,
				// out along the blast.
				// spray() throws against the direction it is given (the way back out of
				// a wound), so these point at the blast and at the ground.
				BleedingSystem.spray(body, BodyPart.TORSO, WoundSeverity.CRITICAL,
						centre.subtract(body.position()), true);
				BleedingSystem.spray(body, BodyPart.HEAD, WoundSeverity.SEVERE, new Vec3(0.0, -1.0, 0.0), true);
			}
			launch = LethalBlast.launch(body, blast);
			violence = v;
			// The region the blast reached most of is the one it throws hardest.
			double best = -1.0;
			for (var share : BodyHitResolver.explosionShares(body, centre).entrySet()) {
				if (share.getValue() > best) {
					best = share.getValue();
					hitPart = share.getKey();
				}
			}
			if (hitPart != null) {
				hitDir = direction(BodyHitResolver.regionCenter(body, hitPart).subtract(centre));
				kick = lerp(violence, Balance.CORPSE_KICK_BLAST_MIN, Balance.CORPSE_KICK_BLAST_MAX);
			}
		} else {
			launch = body.getDeltaMovement();
			double stashed = data.peekDeathViolence(body.tickCount);
			violence = stashed >= 0.0 ? stashed : 0.4;
			// A snapped neck cuts every string at once (2.0): the body drops where
			// it stands, mob or player, with no stumble.
			if (source.is(RDDamageTypes.NECK_BROKEN)) {
				violence = 1.0;
			}
			ClassifiedDamage classified = DamageClassifier.classify(body, source);
			if (classified != null && classified.routing() == ClassifiedDamage.Routing.LOCATIONAL
					&& ServerConfig.get().enableHitLocation) {
				hitPart = BodyHitResolver.resolve(body, source);
				Entity direct = source.getDirectEntity();
				boolean shot = direct instanceof Projectile;
				if (shot) {
					hitDir = direction(direct.getDeltaMovement());
				} else if (direct != null) {
					hitDir = direction(body.position().subtract(direct.position()).multiply(1.0, 0.0, 1.0));
				} else if (source.getSourcePosition() != null) {
					hitDir = direction(body.position().subtract(source.getSourcePosition()).multiply(1.0, 0.0, 1.0));
				}
				// The killing edge on a ruined head (2.0): the pipeline never saw this
				// blow, so the finish-off rule never could. The head's state is still
				// the one it was in before the blow landed.
				var head = data.part(BodyPart.HEAD);
				if (hitPart == BodyPart.HEAD && ServerConfig.get().enableHitLocation
						&& Decapitation.takes(classified.category(), head.state(), head.destroyed(), true, 0.0)
						&& Decapitation.can(body, data)) {
					Decapitation.sever(body, data, hitDir, false, source.getEntity());
				}
				if (hitDir.lengthSqr() > 0.0) {
					kick = shot
							? lerp(violence, Balance.CORPSE_KICK_SHOT_MIN, Balance.CORPSE_KICK_SHOT_MAX)
							: lerp(violence, Balance.CORPSE_KICK_BLOW_MIN, Balance.CORPSE_KICK_BLOW_MAX);
				}
			}
		}
		// The neck that went with the death (2.0), on the snapshot before the
		// corpse packet, so the ragdoll's head hangs from the start.
		BrokenNeck.died(body, data);
		if (launch.length() > MAX_LAUNCH) {
			launch = launch.normalize().scale(MAX_LAUNCH);
		}
		HELD.add(body);
		CorpsePayload payload = new CorpsePayload(body.getId(), kind, (float) violence,
				(float) launch.x, (float) launch.y, (float) launch.z,
				hitPart == null ? -1 : hitPart.ordinal(),
				(float) hitDir.x, (float) hitDir.y, (float) hitDir.z, (float) kick);
		send(body, payload);
	}

	private static void scripted(LivingEntity body, Script s) {
		Vec3 dir = direction(s.dir());
		Vec3 launch = s.kind() == DeathKind.EXPLOSION
				? new Vec3(dir.x, 0.0, dir.z).scale(lerp(s.violence(), Balance.CORPSE_BLAST_SPEED_MIN, Balance.CORPSE_BLAST_SPEED_MAX))
						.add(0.0, lerp(s.violence(), Balance.CORPSE_BLAST_LIFT_MIN, Balance.CORPSE_BLAST_LIFT_MAX), 0.0)
				: dir.scale(s.kick() * 0.25);
		HELD.add(body);
		send(body, new CorpsePayload(body.getId(), s.kind(), (float) s.violence(),
				(float) launch.x, (float) launch.y, (float) launch.z,
				s.part() == null ? -1 : s.part().ordinal(),
				(float) dir.x, (float) dir.y, (float) dir.z, s.part() == null ? 0.0F : (float) s.kick()));
	}

	private static void send(LivingEntity body, CorpsePayload payload) {
		for (ServerPlayer watcher : PlayerLookup.tracking(body)) {
			if (watcher != body && ServerPlayNetworking.canSend(watcher, CorpsePayload.TYPE)) {
				ServerPlayNetworking.send(watcher, payload);
			}
		}
		// And the one who died, for their own ragdoll (phase 4); their client
		// decides whether to use it.
		if (body instanceof ServerPlayer self && self.connection != null
				&& ServerPlayNetworking.canSend(self, CorpsePayload.TYPE)) {
			ServerPlayNetworking.send(self, payload);
		}
	}

	private static Vec3 direction(Vec3 v) {
		return v.lengthSqr() < 1.0e-8 ? Vec3.ZERO : v.normalize();
	}

	private static double lerp(double t, double a, double b) {
		return a + (b - a) * Math.clamp(t, 0.0, 1.0);
	}
}
