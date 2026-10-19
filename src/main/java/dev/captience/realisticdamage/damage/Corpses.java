package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.PendingExplosion;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.systems.BleedingSystem;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.net.CorpsePayload;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
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

	/** From AFTER_DEATH, inside the killing hurt call. */
	public static void died(LivingEntity body, DamageSource source) {
		if (!MobAnatomy.participates(body)) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(body);
		DeathKind kind = DeathKind.of(source);
		PendingExplosion blast = kind == DeathKind.EXPLOSION ? data.pendingExplosion() : null;
		Vec3 launch;
		double violence;
		if (blast != null) {
			if (!LethalBlast.apply(body, data, blast).isEmpty()) {
				RDAttachments.pushSnapshot(body);
			}
			double v = DeathMath.violenceFromBlow(blast.amount(), body.getMaxHealth());
			if (v >= GORE_BURST_VIOLENCE && ServerConfig.get().enableBleeding && MobAnatomy.bleeds(body)) {
				// What is left of the body comes apart too: a burst from the torso,
				// out along the blast.
				// spray() throws against the direction it is given (the way back out of
				// a wound), so these point at the blast and at the ground.
				BleedingSystem.spray(body, BodyPart.TORSO, WoundSeverity.CRITICAL,
						new Vec3(blast.x(), blast.y(), blast.z()).subtract(body.position()), true);
				BleedingSystem.spray(body, BodyPart.HEAD, WoundSeverity.SEVERE, new Vec3(0.0, -1.0, 0.0), true);
			}
			launch = LethalBlast.launch(body, blast);
			violence = DeathMath.violenceFromBlow(blast.amount(), body.getMaxHealth());
		} else {
			launch = body.getDeltaMovement();
			violence = 0.4;
		}
		if (launch.length() > MAX_LAUNCH) {
			launch = launch.normalize().scale(MAX_LAUNCH);
		}
		CorpsePayload payload = new CorpsePayload(body.getId(), kind, (float) violence,
				(float) launch.x, (float) launch.y, (float) launch.z);
		for (ServerPlayer watcher : PlayerLookup.tracking(body)) {
			if (watcher != body && ServerPlayNetworking.canSend(watcher, CorpsePayload.TYPE)) {
				ServerPlayNetworking.send(watcher, payload);
			}
		}
	}
}
