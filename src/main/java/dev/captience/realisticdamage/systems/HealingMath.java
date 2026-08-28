package dev.captience.realisticdamage.systems;

import java.util.EnumMap;
import java.util.Map;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;

/** Pure math for distributing a healing budget across damaged regions (spec §37). */
public final class HealingMath {
	private HealingMath() {}

	/** Extra weight for CRITICAL/DISABLED parts so severe injuries get meaningful attention. */
	private static final double SEVERE_WEIGHT_BONUS = 0.5;

	/**
	 * Splits {@code budget} condition points across damaged, non-strained parts,
	 * weighted by normalized missing condition (severe parts boosted). Amounts are
	 * capped at each part's missing condition; surplus re-flows deterministically.
	 * Returns the per-part allocation; parts with zero allocation are absent.
	 */
	public static Map<BodyPart, Double> distributeBudget(RealisticDamageData data, double budget) {
		Map<BodyPart, Double> result = new EnumMap<>(BodyPart.class);
		if (budget <= 0) {
			return result;
		}
		for (int pass = 0; pass < 3 && budget > 1e-9; pass++) {
			double totalWeight = 0.0;
			Map<BodyPart, Double> weights = new EnumMap<>(BodyPart.class);
			for (BodyPartData part : data.allParts()) {
				if (part.strainPauseTicks() > 0) {
					continue;
				}
				double alreadyGiven = result.getOrDefault(part.part(), 0.0);
				double missing = part.missing() - alreadyGiven;
				if (missing <= 1e-9) {
					continue;
				}
				double weight = missing / part.part().maxCondition();
				if (part.state().isAtLeastAsBadAs(BodyState.CRITICAL)) {
					weight += SEVERE_WEIGHT_BONUS;
				}
				weights.put(part.part(), weight);
				totalWeight += weight;
			}
			if (totalWeight <= 0) {
				break;
			}
			double spent = 0.0;
			for (Map.Entry<BodyPart, Double> entry : weights.entrySet()) {
				double share = budget * (entry.getValue() / totalWeight);
				double alreadyGiven = result.getOrDefault(entry.getKey(), 0.0);
				double missing = data.part(entry.getKey()).missing() - alreadyGiven;
				double give = Math.min(share, missing);
				if (give > 0) {
					result.merge(entry.getKey(), give, Double::sum);
					spent += give;
				}
			}
			budget -= spent;
			if (spent <= 1e-9) {
				break;
			}
		}
		return result;
	}
}
