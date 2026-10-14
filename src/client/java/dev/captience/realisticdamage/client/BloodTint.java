package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * What colour a body bleeds (1.7). Players and most mobs bleed the palette's
 * red; the undead bleed something darker and older; endermen, when they get
 * bodies, bleed purple. Decided by entity type tag so a pack can recolour a
 * mob without code, and carried by every drop, stain, shell and severed limb
 * from the moment it leaves the body — blood does not change colour because
 * its owner walked away.
 *
 * @param fresh         the muted fresh colour
 * @param freshVisceral the visceral fresh colour
 * @param dried         where both dry to
 */
public record BloodTint(float[] fresh, float[] freshVisceral, float[] dried) {
	public static final BloodTint RED = new BloodTint(
			new float[] {0.545F, 0.078F, 0.086F}, new float[] {0.690F, 0.102F, 0.094F}, new float[] {0.282F, 0.125F, 0.094F});
	/** Old blood: darker, browner, barely wet even when fresh. */
	public static final BloodTint DARK = new BloodTint(
			new float[] {0.30F, 0.045F, 0.06F}, new float[] {0.40F, 0.055F, 0.07F}, new float[] {0.16F, 0.07F, 0.06F});
	public static final BloodTint PURPLE = new BloodTint(
			new float[] {0.42F, 0.10F, 0.55F}, new float[] {0.56F, 0.12F, 0.74F}, new float[] {0.22F, 0.10F, 0.28F});
	/** Not blood at all (1.7): what a concussed stomach gives up. Same drop, same stain, other colour. */
	public static final BloodTint BILE = new BloodTint(
			new float[] {0.50F, 0.46F, 0.16F}, new float[] {0.58F, 0.54F, 0.18F}, new float[] {0.36F, 0.32F, 0.14F});

	/** The tint for whatever this blood came out of; red when unknown. */
	public static BloodTint of(@Nullable Entity entity) {
		if (entity == null || entity instanceof Player) {
			return RED;
		}
		return ofType(entity.getType());
	}

	public static BloodTint ofType(@Nullable EntityType<?> type) {
		if (type == null) {
			return RED;
		}
		var holder = type.builtInRegistryHolder();
		if (holder.is(MobAnatomy.BLOOD_PURPLE)) {
			return PURPLE;
		}
		if (holder.is(MobAnatomy.BLOOD_DARK)) {
			return DARK;
		}
		return RED;
	}
}
