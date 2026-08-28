package dev.captience.realisticdamage.damage;

import java.util.EnumMap;
import java.util.Map;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.ClassifiedDamage.Routing;

/** Deterministic predefined routing for indirect/environmental damage (spec §7). */
public final class EnvironmentalRouter {
	private EnvironmentalRouter() {}

	/** Shares sum to 1.0. {@code appliedDamage} drives fall-severity secondary shares. */
	public static Map<BodyPart, Double> shares(Routing routing, double appliedDamage) {
		Map<BodyPart, Double> shares = new EnumMap<>(BodyPart.class);
		switch (routing) {
			case FALL_LEGS -> {
				shares.put(BodyPart.LEFT_LEG, 0.5);
				shares.put(BodyPart.RIGHT_LEG, 0.5);
				if (appliedDamage >= Balance.FALL_SECONDARY_TORSO_DAMAGE) {
					shares.put(BodyPart.TORSO, 0.3);
				}
				if (appliedDamage >= Balance.FALL_SECONDARY_HEAD_DAMAGE) {
					shares.put(BodyPart.HEAD, 0.2);
				}
				normalize(shares);
			}
			case HEAD_HEAVY -> {
				shares.put(BodyPart.HEAD, 0.6);
				shares.put(BodyPart.TORSO, 0.4);
			}
			case LEGS -> {
				shares.put(BodyPart.LEFT_LEG, 0.5);
				shares.put(BodyPart.RIGHT_LEG, 0.5);
			}
			case TORSO -> shares.put(BodyPart.TORSO, 1.0);
			case HEAD_TORSO -> {
				shares.put(BodyPart.HEAD, 0.4);
				shares.put(BodyPart.TORSO, 0.6);
			}
			case LEGS_TORSO -> {
				shares.put(BodyPart.LEFT_LEG, 0.3);
				shares.put(BodyPart.RIGHT_LEG, 0.3);
				shares.put(BodyPart.TORSO, 0.4);
			}
			case SPREAD -> {
				double totalMax = 0.0;
				for (BodyPart part : BodyPart.values()) {
					totalMax += part.maxCondition();
				}
				for (BodyPart part : BodyPart.values()) {
					shares.put(part, part.maxCondition() / totalMax);
				}
			}
			default -> {}
		}
		return shares;
	}

	private static void normalize(Map<BodyPart, Double> shares) {
		double sum = shares.values().stream().mapToDouble(Double::doubleValue).sum();
		if (sum > 0) {
			shares.replaceAll((part, value) -> value / sum);
		}
	}
}
