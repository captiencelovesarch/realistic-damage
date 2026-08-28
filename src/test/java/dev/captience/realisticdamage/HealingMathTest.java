package dev.captience.realisticdamage;

import java.util.Map;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.systems.HealingMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealingMathTest {
	@Test
	void healthyBodyGetsNothing() {
		RealisticDamageData data = new RealisticDamageData();
		assertTrue(HealingMath.distributeBudget(data, 5.0).isEmpty());
	}

	@Test
	void singleDamagedPartGetsWholeBudgetUpToMissing() {
		RealisticDamageData data = new RealisticDamageData();
		data.part(BodyPart.LEFT_LEG).damage(4.0);
		Map<BodyPart, Double> result = HealingMath.distributeBudget(data, 10.0);
		assertEquals(4.0, result.get(BodyPart.LEFT_LEG), 1e-9, "capped at missing condition");
	}

	@Test
	void worseDamagedPartGetsMore() {
		RealisticDamageData data = new RealisticDamageData();
		data.part(BodyPart.LEFT_LEG).damage(12.0);  // badly hurt
		data.part(BodyPart.RIGHT_ARM).damage(2.0);  // scratched
		Map<BodyPart, Double> result = HealingMath.distributeBudget(data, 2.0);
		assertTrue(result.get(BodyPart.LEFT_LEG) > result.get(BodyPart.RIGHT_ARM),
				"normalized missing condition prioritizes the worse part");
	}

	@Test
	void strainedPartIsSkipped() {
		RealisticDamageData data = new RealisticDamageData();
		data.part(BodyPart.RIGHT_ARM).damage(10.0);
		data.part(BodyPart.RIGHT_ARM).strain(100);
		data.part(BodyPart.TORSO).damage(3.0);
		Map<BodyPart, Double> result = HealingMath.distributeBudget(data, 2.0);
		assertFalse(result.containsKey(BodyPart.RIGHT_ARM), "strained arm recovery paused (spec §14)");
		assertTrue(result.containsKey(BodyPart.TORSO));
	}

	@Test
	void budgetIsConservedWhenDamageExceedsIt() {
		RealisticDamageData data = new RealisticDamageData();
		for (BodyPart part : BodyPart.values()) {
			data.part(part).damage(8.0);
		}
		Map<BodyPart, Double> result = HealingMath.distributeBudget(data, 3.0);
		double total = result.values().stream().mapToDouble(Double::doubleValue).sum();
		assertEquals(3.0, total, 1e-6, "everything allocated, nothing invented");
	}
}
