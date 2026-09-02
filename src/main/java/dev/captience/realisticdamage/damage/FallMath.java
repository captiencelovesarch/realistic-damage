package dev.captience.realisticdamage.damage;

import java.util.EnumMap;
import java.util.Map;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;

/**
 * Everything a landing decides, as pure arithmetic (1.4). Kept free of
 * Minecraft types so the whole fall rework is testable without a running game —
 * the height curves, the leg asymmetry and the lethality compression are the
 * parts most likely to be retuned, and they are exactly the parts that are
 * painful to verify in-world.
 *
 * <p>Falls are keyed to <em>height</em> here, not to the health the player
 * actually lost. Post-armour damage was the wrong input: it is what vanilla
 * decided your boots were worth, and it made a forty-block drop in good gear
 * indistinguishable from stepping off a fence.
 */
public final class FallMath {
	private FallMath() {}

	/**
	 * Condition-damage equivalent of a fall, before the FALL profile multiplier
	 * and before any armour or config scaling.
	 *
	 * @param heightOverSafe blocks fallen beyond the player's safe fall distance
	 * @param surfaceFactor  what the ground gave back (see {@link LandingSurface})
	 */
	public static double traumaEquivalent(double heightOverSafe, double surfaceFactor) {
		if (heightOverSafe <= 0.0) {
			return 0.0;
		}
		return heightOverSafe * Balance.FALL_TRAUMA_PER_BLOCK * surfaceFactor;
	}

	/**
	 * How much of the impact reaches a region, by drop height. Ramps rather than
	 * steps: a share that snaps on at a threshold makes one extra block of fall
	 * feel like a different kind of accident.
	 */
	private static double ramp(double height, double from, double to, double share) {
		if (height <= from) {
			return 0.0;
		}
		if (height >= to) {
			return share;
		}
		return share * (height - from) / (to - from);
	}

	/**
	 * Trauma distribution for one landing. Legs always take the bulk; the torso
	 * joins in once you are far enough up to fold on impact, and the head only
	 * once the drop is bad enough that you are no longer landing so much as
	 * arriving.
	 *
	 * @param height        the effective drop, in blocks
	 * @param leadingIsLeft which foot was forward in the walk cycle
	 */
	public static Map<BodyPart, Double> shares(double height, boolean leadingIsLeft) {
		Map<BodyPart, Double> shares = new EnumMap<>(BodyPart.class);
		double legs = 0.5;
		double lead = legs * leadingLegShare(height);
		double trail = legs - lead;
		shares.put(leadingIsLeft ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG, lead);
		shares.put(leadingIsLeft ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG, trail);

		double torso = ramp(height, Balance.FALL_SECONDARY_TORSO_HEIGHT,
				Balance.FALL_SECONDARY_TORSO_FULL_HEIGHT, Balance.FALL_TORSO_SHARE);
		if (torso > 0.0) {
			shares.put(BodyPart.TORSO, torso);
		}
		double head = ramp(height, Balance.FALL_SECONDARY_HEAD_HEIGHT,
				Balance.FALL_SECONDARY_HEAD_FULL_HEIGHT, Balance.FALL_HEAD_SHARE);
		if (head > 0.0) {
			shares.put(BodyPart.HEAD, head);
		}
		normalize(shares);
		return shares;
	}

	/**
	 * The leading leg's cut of the leg share: even on a short hop, tending to
	 * {@link Balance#FALL_LEADING_LEG_SHARE} once you are falling far enough to
	 * land on one foot rather than settle onto both.
	 */
	public static double leadingLegShare(double height) {
		double t = Math.clamp(height / Balance.FALL_ASYMMETRY_FULL_HEIGHT, 0.0, 1.0);
		return 0.5 + t * (Balance.FALL_LEADING_LEG_SHARE - 0.5);
	}

	/**
	 * How long you spend on the ground after a hard landing, in ticks; 0 when the
	 * drop was not enough to take you off your feet.
	 */
	public static int knockdownTicks(double height) {
		if (height < Balance.KNOCKDOWN_MIN_HEIGHT) {
			return 0;
		}
		double t = Math.clamp(
				(height - Balance.KNOCKDOWN_MIN_HEIGHT)
						/ (Balance.KNOCKDOWN_FULL_HEIGHT - Balance.KNOCKDOWN_MIN_HEIGHT),
				0.0, 1.0);
		return (int) Math.round(Balance.KNOCKDOWN_TICKS_MIN
				+ t * (Balance.KNOCKDOWN_TICKS_MAX - Balance.KNOCKDOWN_TICKS_MIN));
	}

	/**
	 * Vanilla's fall damage, with its lethal tail compressed (spec 1.4). Below the
	 * soft cap nothing changes at all — short falls are vanilla's business and
	 * meddling with them would be felt everywhere. Above it, height buys injury
	 * rather than a respawn screen.
	 */
	public static int compressLethality(int vanillaDamage) {
		if (vanillaDamage <= Balance.FALL_HP_SOFT_CAP) {
			return vanillaDamage;
		}
		double over = vanillaDamage - Balance.FALL_HP_SOFT_CAP;
		return (int) Math.floor(Balance.FALL_HP_SOFT_CAP + over * Balance.FALL_HP_COMPRESSION);
	}

	private static void normalize(Map<BodyPart, Double> shares) {
		double sum = shares.values().stream().mapToDouble(Double::doubleValue).sum();
		if (sum > 0) {
			shares.replaceAll((part, value) -> value / sum);
		}
	}
}
