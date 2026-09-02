package dev.captience.realisticdamage.damage;

import java.util.EnumMap;
import java.util.Map;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.damage.ClassifiedDamage.Routing;

/** Deterministic predefined routing for indirect/environmental damage (spec §7). */
public final class EnvironmentalRouter {
	private EnvironmentalRouter() {}

	/**
	 * Shares sum to 1.0.
	 *
	 * <p>Falls are no longer routed here: their distribution depends on drop
	 * height and on which foot was leading, which is more than a static table can
	 * say, and it lives in {@link FallMath}. {@code FALL_LEGS} keeps an even
	 * two-legged split as a fallback for any fall-tagged damage that arrives
	 * without a recorded landing — another mod's custom source, say.
	 */
	public static Map<BodyPart, Double> shares(Routing routing) {
		Map<BodyPart, Double> shares = new EnumMap<>(BodyPart.class);
		switch (routing) {
			case FALL_LEGS, LEGS -> {
				shares.put(BodyPart.LEFT_LEG, 0.5);
				shares.put(BodyPart.RIGHT_LEG, 0.5);
			}
			case HEAD_HEAVY -> {
				shares.put(BodyPart.HEAD, 0.6);
				shares.put(BodyPart.TORSO, 0.4);
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
}
