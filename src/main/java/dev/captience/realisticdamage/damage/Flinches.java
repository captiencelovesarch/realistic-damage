package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.net.FlinchPayload;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * A living body hit and still standing flinches (active ragdolls phase 5):
 * the struck part jerks with the blow and the body rocks, on every watching
 * client. The server knows where the blow landed (the trauma pipeline just
 * worked it out) and which way it was going; the clients play it.
 */
public final class Flinches {
	private Flinches() {}

	/** From AFTER_DAMAGE, after the trauma pipeline, for a hit that did not kill. */
	public static void afterHit(LivingEntity body, DamageSource source, List<TraumaPipeline.AppliedTrauma> applied,
			double damage) {
		if (!body.isAlive() || applied == null || applied.isEmpty() || damage <= 0.0) {
			return;
		}
		BodyPart part = null;
		double most = -1.0;
		for (TraumaPipeline.AppliedTrauma t : applied) {
			if (t.primary() && t.attempted() > most) {
				most = t.attempted();
				part = t.part();
			}
		}
		if (part == null) {
			return;
		}
		Vec3 dir = Vec3.ZERO;
		Entity direct = source.getDirectEntity();
		if (direct instanceof Projectile) {
			dir = direct.getDeltaMovement();
		} else if (direct != null) {
			dir = body.position().subtract(direct.position()).multiply(1.0, 0.0, 1.0);
		} else if (source.getSourcePosition() != null) {
			dir = body.position().subtract(source.getSourcePosition()).multiply(1.0, 0.0, 1.0);
		}
		dir = dir.lengthSqr() < 1.0e-8 ? Vec3.ZERO : dir.normalize();
		double strength = Math.clamp(damage / Math.max(1.0, body.getMaxHealth()) * 4.0, 0.15, 1.0);
		FlinchPayload payload = new FlinchPayload(body.getId(), part.ordinal(), (float) dir.x, (float) dir.y,
				(float) dir.z, (float) strength);
		for (ServerPlayer watcher : PlayerLookup.tracking(body)) {
			if (ServerPlayNetworking.canSend(watcher, FlinchPayload.TYPE)) {
				ServerPlayNetworking.send(watcher, payload);
			}
		}
		if (body instanceof ServerPlayer self && self.connection != null
				&& ServerPlayNetworking.canSend(self, FlinchPayload.TYPE)) {
			ServerPlayNetworking.send(self, payload);
		}
	}
}
