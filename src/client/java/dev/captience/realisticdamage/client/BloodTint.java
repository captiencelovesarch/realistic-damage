package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * What colour a body bleeds (1.7). Players and most mobs bleed the palette's
 * red; the undead bleed something darker and older; the End bleeds purple;
 * and since slice three, slimes and creepers green, magma cubes orange,
 * spiders and squid blue, bees yellow-green, the warden sculk. Decided by entity type tag so a pack can recolour a
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
	/** Slime: green, and greener when fresh (1.7 slice 3). Creepers bleed the same sap. */
	public static final BloodTint GREEN = new BloodTint(
			new float[] {0.25F, 0.52F, 0.16F}, new float[] {0.30F, 0.66F, 0.18F}, new float[] {0.17F, 0.28F, 0.10F});
	/** A magma cube's insides: molten orange that cools to a crust. */
	public static final BloodTint MAGMA = new BloodTint(
			new float[] {0.86F, 0.36F, 0.06F}, new float[] {0.98F, 0.46F, 0.08F}, new float[] {0.28F, 0.12F, 0.07F});
	/** Hemocyanin: what spiders, squid and nautiluses actually bleed. Blue, drying grey-green. */
	public static final BloodTint BLUE = new BloodTint(
			new float[] {0.12F, 0.26F, 0.55F}, new float[] {0.14F, 0.34F, 0.72F}, new float[] {0.13F, 0.18F, 0.22F});
	/** The glow squid's: bright teal. */
	public static final BloodTint GLOW = new BloodTint(
			new float[] {0.10F, 0.62F, 0.58F}, new float[] {0.14F, 0.80F, 0.74F}, new float[] {0.08F, 0.26F, 0.25F});
	/** Insect hemolymph: a thin yellow-green. */
	public static final BloodTint INSECT = new BloodTint(
			new float[] {0.55F, 0.58F, 0.18F}, new float[] {0.66F, 0.70F, 0.20F}, new float[] {0.32F, 0.30F, 0.14F});
	/** The warden: deep sculk teal, nearly black once dry. */
	public static final BloodTint SCULK = new BloodTint(
			new float[] {0.03F, 0.24F, 0.28F}, new float[] {0.04F, 0.34F, 0.40F}, new float[] {0.03F, 0.10F, 0.12F});
	/** The sulfur cube: yellow. */
	public static final BloodTint SULFUR = new BloodTint(
			new float[] {0.74F, 0.64F, 0.12F}, new float[] {0.86F, 0.76F, 0.14F}, new float[] {0.40F, 0.34F, 0.10F});
	/** Not blood at all (1.7): what a concussed stomach gives up. Same drop, same stain, other colour. */
	public static final BloodTint BILE = new BloodTint(
			new float[] {0.50F, 0.46F, 0.16F}, new float[] {0.58F, 0.54F, 0.18F}, new float[] {0.36F, 0.32F, 0.14F});

	/**
	 * The tint the server names on a bloodied item (2.0; see
	 * {@code BloodiedItem.tintOf}); red for a name this version does not know.
	 */
	public static BloodTint byName(String name) {
		return switch (name) {
			case "dark" -> DARK;
			case "purple" -> PURPLE;
			case "green" -> GREEN;
			case "magma" -> MAGMA;
			case "blue" -> BLUE;
			case "glow" -> GLOW;
			case "insect" -> INSECT;
			case "sculk" -> SCULK;
			case "sulfur" -> SULFUR;
			case "bile" -> BILE;
			default -> RED;
		};
	}

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
		if (holder.is(MobAnatomy.BLOOD_GREEN)) {
			return GREEN;
		}
		if (holder.is(MobAnatomy.BLOOD_MAGMA)) {
			return MAGMA;
		}
		if (holder.is(MobAnatomy.BLOOD_BLUE)) {
			return BLUE;
		}
		if (holder.is(MobAnatomy.BLOOD_GLOW)) {
			return GLOW;
		}
		if (holder.is(MobAnatomy.BLOOD_INSECT)) {
			return INSECT;
		}
		if (holder.is(MobAnatomy.BLOOD_SCULK)) {
			return SCULK;
		}
		if (holder.is(MobAnatomy.BLOOD_SULFUR)) {
			return SULFUR;
		}
		return RED;
	}
}
