package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.MobAnatomy;
import dev.captience.realisticdamage.systems.NeckMath;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Water that is too shallow to dive into (2.0).
 *
 * <p>Vanilla forgets a fall the moment you touch water, however deep, so a
 * block of water is as good as a lake from any height. Here water only stops
 * a fall if there is enough of it: in water {@link Balance#SHALLOW_WATER_DEPTH}
 * deep or shallower, the bottom arrives and the fall counts as a landing on
 * it, a little softened by the water ({@link Balance#SHALLOW_WATER_CUSHION}).
 * Feet first, the legs take it as on any landing. Head first (limp, or off an
 * elytra) is the neck ({@link BrokenNeck}).
 *
 * <p>Called by {@code ShallowWaterMixin} at each place vanilla resets a fall
 * for water, just before it does, with the fall still on the body.
 */
public final class ShallowWater {
	private ShallowWater() {}

	/** How far up and down the column is looked at: a little past the bar. */
	private static final int LOOK = Balance.SHALLOW_WATER_DEPTH + 2;

	/**
	 * A body falling into water at {@code at}: if the water is shallow, the fall
	 * lands on the bottom now, before vanilla forgets it.
	 */
	public static void entered(LivingEntity body, double fallDistance, BlockPos at) {
		Level level = body.level();
		if (level.isClientSide() || !MobAnatomy.participates(body) || !body.isAlive()) {
			return;
		}
		if (body instanceof Player player && (player.getAbilities().flying || player.isSpectator())) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(body);
		if (data.diving()) {
			return;
		}
		double safe = body.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		if (fallDistance * Balance.SHALLOW_WATER_CUSHION <= safe) {
			return;
		}
		int depth = depth(level, at);
		if (!NeckMath.shallow(depth)) {
			return;
		}
		data.beginDive(BrokenNeck.headFirst(body, data));
		try {
			body.causeFallDamage(fallDistance * Balance.SHALLOW_WATER_CUSHION, 1.0F, body.damageSources().fall());
		} finally {
			data.endDive();
		}
	}

	/**
	 * How deep the water is at this column, blocks, from its surface to its
	 * bottom; 0 if there is none. Starts from wherever the body touched it and
	 * looks up for the surface first, so a body that went in fast and is
	 * already a block down still measures the whole pool.
	 */
	public static int depth(Level level, BlockPos at) {
		BlockPos pos = at;
		if (!water(level, pos)) {
			pos = pos.below();
			if (!water(level, pos)) {
				return 0;
			}
		}
		for (int i = 0; i < LOOK && water(level, pos.above()); i++) {
			pos = pos.above();
		}
		int depth = 0;
		while (depth <= LOOK && water(level, pos.below(depth))) {
			depth++;
		}
		return depth;
	}

	private static boolean water(Level level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER);
	}
}
