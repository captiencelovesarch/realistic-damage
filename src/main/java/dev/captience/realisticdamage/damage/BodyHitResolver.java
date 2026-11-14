package dev.captience.realisticdamage.damage;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPlan;
import dev.captience.realisticdamage.systems.MobAnatomy;
import dev.captience.realisticdamage.config.Balance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * "What you hit is what you damage" (spec §6). Server-authoritative, no RNG:
 * projectiles use their clipped impact point (26.x arrows are setPos'd onto the
 * target AABB before hurting), melee reconstructs the attacker's eye/look ray,
 * ambiguous boundaries resolve by fixed priority. Not a competitive-shooter
 * hitbox simulator — logical regions over the vanilla bounding box.
 */
public final class BodyHitResolver {
	private BodyHitResolver() {}

	/** Ray length for reconstructed melee rays: creative reach + vanilla leniency, with margin. */
	private static final double MELEE_RAY_LENGTH = 8.0;

	public static BodyPart resolve(LivingEntity target, DamageSource source) {
		// A firearm mod has already raycast and knows exactly where it hit —
		// better than anything reconstructed here, and for a hitscan weapon
		// there is no projectile to reconstruct from at all (1.3).
		if (target instanceof ServerPlayer player
				&& dev.captience.realisticdamage.api.RealisticDamageApi.hasImpactPoints()) {
			Vec3 supplied = dev.captience.realisticdamage.api.RealisticDamageApi
					.externalImpactPoint(player, source);
			if (supplied != null) {
				AABB box = target.getBoundingBox();
				// Clipped, so a point slightly off the model still lands sanely
				// rather than resolving to some arbitrary region.
				return classifyPoint(target, clipOrFallback(box, supplied, box.getCenter(), supplied));
			}
		}
		// The dragon is hit through its parts (1.7 slice 3), and the part vanilla
		// routed the blow through is a better answer than any ray against a
		// sixteen-block box that is mostly air.
		BodyPart viaPart = DragonHits.consume(target);
		if (viaPart != null) {
			return viaPart;
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof Projectile projectile) {
			return resolveProjectile(target, projectile);
		}
		if (direct instanceof LivingEntity attacker) {
			return resolveMelee(target, attacker);
		}
		Vec3 sourcePos = source.getSourcePosition();
		if (sourcePos != null) {
			Vec3 toCenter = target.getBoundingBox().getCenter();
			Vec3 point = clipOrFallback(target.getBoundingBox(), sourcePos, toCenter, toCenter);
			return classifyPoint(target, point);
		}
		return BodyPart.TORSO;
	}

	private static BodyPart resolveProjectile(LivingEntity target, Projectile projectile) {
		Vec3 position = projectile.position();
		Vec3 velocity = projectile.getDeltaMovement();
		if (velocity.lengthSqr() < 1.0e-6) {
			return classifyPoint(target, position);
		}
		// Arrows sit exactly on the clipped impact point; re-clipping the flight
		// segment snaps other projectiles (fireballs, spit) onto the box too.
		Vec3 from = position.subtract(velocity.scale(2.0));
		Vec3 to = position.add(velocity.scale(2.0));
		Vec3 point = clipOrFallback(target.getBoundingBox(), from, to, position);
		return classifyPoint(target, point);
	}

	private static BodyPart resolveMelee(LivingEntity target, LivingEntity attacker) {
		Vec3 from = attacker.getEyePosition();
		// Players aim; mobs lunge at the body (their look angle is unreliable mid-swing).
		Vec3 direction = attacker instanceof Player
				? attacker.getHeadLookAngle()
				: target.getBoundingBox().getCenter().subtract(from).normalize();
		Vec3 to = from.add(direction.scale(MELEE_RAY_LENGTH));
		Vec3 point = clipOrFallback(target.getBoundingBox(), from, to, target.getBoundingBox().getCenter());
		return classifyPoint(target, point);
	}

	/**
	 * Which way a body faces. The yaw for nearly everything; the ender dragon
	 * flies backwards by its own yaw — vanilla places its head at minus the look
	 * vector — so its facing is flipped to match the head.
	 */
	public static Vec3 facing(LivingEntity target) {
		Vec3 look = Vec3.directionFromRotation(0.0F, target.yBodyRot);
		return target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon ? look.scale(-1.0) : look;
	}

	private static Vec3 clipOrFallback(AABB box, Vec3 from, Vec3 to, Vec3 fallback) {
		Optional<Vec3> hit = box.clip(from, to);
		if (hit.isPresent()) {
			return hit.get();
		}
		// Vanilla applies up to a 0.3 hit margin; mirror it before giving up.
		return box.inflate(0.3).clip(from, to).orElse(fallback);
	}

	/**
	 * Maps a world-space impact point to a body region. Vertical bands for
	 * upright poses; horizontal (facing-axis) bands while swimming/crawling/
	 * elytra so prone bodies still resolve head/torso/legs sensibly.
	 */
	public static BodyPart classifyPoint(LivingEntity target, Vec3 point) {
		AABB box = target.getBoundingBox();
		Vec3 center = box.getCenter();
		Vec3 forward = facing(target);
		Vec3 right = forward.yRot((float) (-Math.PI / 2.0));
		double lateral = point.subtract(center).dot(right);
		double halfWidth = Math.max(box.getXsize(), box.getZsize()) / 2.0;

		BodyPlan plan = MobAnatomy.plan(target);
		if (plan != BodyPlan.HUMANOID) {
			// Every other body (1.7 slice 3) is laid out in its own plan's units.
			double along = point.subtract(center).dot(forward) / halfWidth;
			double heightFraction = (point.y - box.minY) / box.getYsize();
			return plan.classify(along, lateral / halfWidth, heightFraction);
		}

		Pose pose = target.getPose();
		if (pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK || lyingMob(target)) {
			// Body lies along the facing axis; the 0.6-cube box only covers the
			// core, so band along the horizontal instead of Y. Degrade gracefully.
			double along = point.subtract(center).dot(forward);
			double halfLength = halfWidth;
			if (along > halfLength * 0.4) {
				return BodyPart.HEAD;
			}
			if (along < -halfLength * 0.4) {
				return lateral >= 0.0 ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
			}
			if (lateral > halfWidth * Balance.ARM_LATERAL_FRACTION) {
				return BodyPart.RIGHT_ARM;
			}
			if (lateral < -halfWidth * Balance.ARM_LATERAL_FRACTION) {
				return BodyPart.LEFT_ARM;
			}
			return BodyPart.TORSO;
		}

		double heightFraction = (point.y - box.minY) / box.getYsize();
		if (heightFraction >= Balance.HEAD_BAND_FRACTION) {
			return BodyPart.HEAD;
		}
		if (heightFraction < Balance.LEG_BAND_FRACTION) {
			return lateral >= 0.0 ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
		}
		if (lateral > halfWidth * Balance.ARM_LATERAL_FRACTION) {
			return BodyPart.RIGHT_ARM;
		}
		if (lateral < -halfWidth * Balance.ARM_LATERAL_FRACTION) {
			return BodyPart.LEFT_ARM;
		}
		return BodyPart.TORSO;
	}

	/** A mob down on the floor, in its lying box (DownedHitboxMixin): banded along the body, like a crawler. */
	private static boolean lyingMob(LivingEntity target) {
		if (target instanceof net.minecraft.world.entity.player.Player) {
			return false;
		}
		var data = target.getAttached(dev.captience.realisticdamage.RDAttachments.DATA);
		return data != null && (data.unconscious() || data.knockedDown());
	}

	/**
	 * Explosion trauma shares (spec §29): the region nearest the blast center is
	 * primary, the core takes secondary shock, the rest a small remainder.
	 */
	public static Map<BodyPart, Double> explosionShares(LivingEntity target, Vec3 center) {
		BodyPlan plan = MobAnatomy.plan(target);
		BodyPart primary = nearestRegion(target, center);
		Map<BodyPart, Double> shares = new EnumMap<>(BodyPart.class);
		BodyPart secondary = primary == BodyPart.TORSO ? BodyPart.HEAD : BodyPart.TORSO;
		if (!plan.has(secondary)) {
			// A blob has nothing but a torso; the whole blast is the primary's.
			shares.put(primary, 1.0);
			return shares;
		}
		int others = 0;
		for (BodyPart part : BodyPart.values()) {
			if (plan.has(part) && part != primary && part != secondary) {
				others++;
			}
		}
		double remainder = 1.0 - Balance.EXPLOSION_PRIMARY_SHARE - Balance.EXPLOSION_SECONDARY_SHARE;
		// With nothing else to take the remainder, the core does: shares still sum to one.
		double each = others == 0 ? 0.0 : remainder / others;
		for (BodyPart part : BodyPart.values()) {
			if (!plan.has(part)) {
				continue;
			}
			if (part == primary) {
				shares.put(part, Balance.EXPLOSION_PRIMARY_SHARE);
			} else if (part == secondary) {
				shares.put(part, Balance.EXPLOSION_SECONDARY_SHARE + (others == 0 ? remainder : 0.0));
			} else {
				shares.put(part, each);
			}
		}
		return shares;
	}

	/**
	 * Approximate world-space center of a body region (particles, effects).
	 *
	 * <p>Takes a {@link LivingEntity} rather than a {@code ServerPlayer} because
	 * the client draws blood for every player it can see, not only for the one it
	 * is authoritative over. Everything it reads — the bounding box and the body
	 * rotation — exists on both sides, and the box shrinking when a player crawls
	 * is what makes a prone bleeder leave a trail at floor level for free.
	 */
	public static Vec3 regionCenter(LivingEntity target, BodyPart part) {
		AABB box = target.getBoundingBox();
		Vec3 forward = facing(target);
		Vec3 right = forward.yRot((float) (-Math.PI / 2.0));
		double halfWidth = Math.max(box.getXsize(), box.getZsize()) / 2.0;
		BodyPlan plan = MobAnatomy.plan(target);
		if (plan != BodyPlan.HUMANOID) {
			return planCenter(plan, part, box, forward, right, halfWidth);
		}
		return regionCenter(part, box, box.getCenter(), right, halfWidth, box.getYsize());
	}

	/** A plan region's anchor in the world (1.7 slice 3). */
	private static Vec3 planCenter(BodyPlan plan, BodyPart part, AABB box, Vec3 forward, Vec3 right, double halfWidth) {
		double[] anchor = plan.anchor(part);
		Vec3 center = box.getCenter();
		return new Vec3(center.x, box.minY + anchor[2] * box.getYsize(), center.z)
				.add(forward.scale(anchor[0] * halfWidth))
				.add(right.scale(anchor[1] * halfWidth));
	}

	private static BodyPart nearestRegion(LivingEntity target, Vec3 center) {
		AABB box = target.getBoundingBox();
		Vec3 boxCenter = box.getCenter();
		Vec3 forward = facing(target);
		Vec3 right = forward.yRot((float) (-Math.PI / 2.0));
		double halfWidth = Math.max(box.getXsize(), box.getZsize()) / 2.0;
		double height = box.getYsize();
		BodyPlan plan = MobAnatomy.plan(target);

		BodyPart nearest = BodyPart.TORSO;
		double best = Double.MAX_VALUE;
		for (BodyPart part : BodyPart.values()) {
			if (!plan.has(part)) {
				continue;
			}
			Vec3 regionCenter = plan == BodyPlan.HUMANOID
					? regionCenter(part, box, boxCenter, right, halfWidth, height)
					: planCenter(plan, part, box, forward, right, halfWidth);
			double distance = regionCenter.distanceToSqr(center);
			if (distance < best) {
				best = distance;
				nearest = part;
			}
		}
		return nearest;
	}

	private static Vec3 regionCenter(BodyPart part, AABB box, Vec3 center, Vec3 right, double halfWidth, double height) {
		double legY = box.minY + height * (Balance.LEG_BAND_FRACTION / 2.0);
		double torsoY = box.minY + height * ((Balance.LEG_BAND_FRACTION + Balance.HEAD_BAND_FRACTION) / 2.0);
		double headY = box.minY + height * ((Balance.HEAD_BAND_FRACTION + 1.0) / 2.0);
		double armOffset = halfWidth * 0.8;
		double legOffset = halfWidth * 0.5;
		return switch (part) {
			case HEAD -> new Vec3(center.x, headY, center.z);
			case TORSO -> new Vec3(center.x, torsoY, center.z);
			case RIGHT_ARM -> new Vec3(center.x, torsoY, center.z).add(right.scale(armOffset));
			case LEFT_ARM -> new Vec3(center.x, torsoY, center.z).add(right.scale(-armOffset));
			case RIGHT_LEG -> new Vec3(center.x, legY, center.z).add(right.scale(legOffset));
			case LEFT_LEG -> new Vec3(center.x, legY, center.z).add(right.scale(-legOffset));
		};
	}
}
