package dev.captience.realisticdamage.api;

import dev.captience.realisticdamage.damage.ClassifiedDamage;
import dev.captience.realisticdamage.damage.DamageCategory;
import org.jspecify.annotations.Nullable;

/**
 * What an external mod says a hit is (1.3).
 *
 * @param category    the kind of trauma
 * @param locational  true to resolve the hit against body regions — nearly
 *                    always what a weapon wants; false spreads it over the
 *                    whole body, for something systemic
 * @param multiplier  scales trauma for this hit only, for weapons whose bite
 *                    varies shot to shot. 1.0 for no change
 * @param woundAs     bleed as a different category than the hit bruises, for
 *                    compound sources. Null to bleed as {@code category}
 */
public record ExternalClassification(
		DamageCategory category,
		boolean locational,
		double multiplier,
		@Nullable DamageCategory woundAs
) {
	public static ExternalClassification of(DamageCategory category) {
		return new ExternalClassification(category, true, 1.0, null);
	}

	public static ExternalClassification of(DamageCategory category, double multiplier) {
		return new ExternalClassification(category, true, multiplier, null);
	}

	/** Internal: converts to the pipeline's own shape. */
	public ClassifiedDamage toClassified() {
		return new ClassifiedDamage(category,
				locational ? ClassifiedDamage.Routing.LOCATIONAL : ClassifiedDamage.Routing.SPREAD,
				multiplier, woundAs);
	}
}
