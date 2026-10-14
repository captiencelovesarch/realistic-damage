package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.body.BodyPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Which part of the ender dragon a blow went through (1.7 slice 3).
 *
 * <p>Every hit on the dragon arrives through {@code EnderDragon.hurt(level,
 * part, source, damage)} — a sword on a wing, an arrow in the neck, and the
 * dragon's own {@code hurtServer} routes through its body part. The mixin
 * stashes the part there and the hit resolver consumes it a few frames down the
 * same call, inside {@code AFTER_DAMAGE}, so the region is the part vanilla
 * itself decided was hit. Server thread only; stamped with the dragon and its
 * tick so a stash a refused hit left behind can never answer for a later one.
 */
public final class DragonHits {
	private DragonHits() {}

	private static @Nullable EnderDragon dragon;
	private static int tick;
	private static @Nullable BodyPart part;

	public static void stash(EnderDragon target, EnderDragonPart hit) {
		dragon = target;
		tick = target.tickCount;
		part = regionOf(target, hit);
	}

	/** The stashed region for this target, once; null for anything that is not that dragon this tick. */
	public static @Nullable BodyPart consume(LivingEntity target) {
		if (target != dragon || target.tickCount != tick) {
			return null;
		}
		BodyPart result = part;
		dragon = null;
		part = null;
		return result;
	}

	/** head and neck are the head, body and tail the torso, and a wing the side it is on. */
	public static BodyPart regionOf(EnderDragon target, EnderDragonPart hit) {
		return switch (hit.name) {
			case "head", "neck" -> BodyPart.HEAD;
			case "wing" -> {
				Vec3 forward = BodyHitResolver.facing(target);
				Vec3 right = forward.yRot((float) (-Math.PI / 2.0));
				double lateral = hit.position().subtract(target.position()).dot(right);
				yield lateral >= 0.0 ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
			}
			default -> BodyPart.TORSO;
		};
	}
}
