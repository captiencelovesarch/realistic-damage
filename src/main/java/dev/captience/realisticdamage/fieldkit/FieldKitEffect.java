package dev.captience.realisticdamage.fieldkit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * A status effect that belongs to a field kit half (2.0).
 *
 * <p>Its half is asked every tick, which is how "its effects wear off at once"
 * is kept: a switch flipped off removes the effect from everything wearing it on
 * the next tick (vanilla removes an effect whose tick says false), and what it
 * already did stays done. {@code EffectGateMixin} is the other half of the same
 * promise: a switched-off effect is never added in the first place.
 *
 * <p>Subclasses put their per-tick work in {@link #tickOn}; it only runs while
 * the half is on.
 */
public abstract class FieldKitEffect extends MobEffect {
	private final Half half;

	protected FieldKitEffect(Half half, MobEffectCategory category, int colour) {
		super(category, colour);
		this.half = half;
	}

	public Half half() {
		return half;
	}

	@Override
	public final boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
		return true;
	}

	@Override
	public final boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplifier) {
		if (!half.on()) {
			return false;
		}
		return tickOn(level, mob, amplifier);
	}

	/** The effect's own work this tick, while its half is on. Return false to end it early. */
	protected boolean tickOn(ServerLevel level, LivingEntity mob, int amplifier) {
		return true;
	}
}
