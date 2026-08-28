package dev.captience.realisticdamage.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Supplies the exact world position a hit landed on (1.3).
 *
 * <p>The mod can work this out on its own — projectiles carry their impact
 * point, melee reconstructs the attacker's swing ray — but a firearm mod has
 * already done a precise raycast and knows better than any reconstruction
 * could. Hitscan weapons in particular have no projectile entity to inspect,
 * so without this they would fall back to a torso hit and lose the headshot
 * the player just earned.
 */
@FunctionalInterface
public interface ImpactPointProvider {
	/**
	 * @return where on (or near) the victim the hit landed, in world space, or
	 *         null to let the mod resolve it. The point is clipped to the
	 *         victim's bounding box, so being slightly off is harmless.
	 */
	@Nullable
	Vec3 impactPoint(ServerPlayer victim, DamageSource source);
}
