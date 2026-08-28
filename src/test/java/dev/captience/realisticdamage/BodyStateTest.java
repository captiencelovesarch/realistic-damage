package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BodyStateTest {
	@Test
	void thresholdBoundariesMatchSpec() {
		// Spec §3: HEALTHY >75%, HURT >50-75%, INJURED >25-50%, CRITICAL >0-25%, DISABLED 0%.
		assertEquals(BodyState.HEALTHY, BodyState.fromFraction(1.0));
		assertEquals(BodyState.HEALTHY, BodyState.fromFraction(0.751));
		assertEquals(BodyState.HURT, BodyState.fromFraction(0.75));
		assertEquals(BodyState.HURT, BodyState.fromFraction(0.501));
		assertEquals(BodyState.INJURED, BodyState.fromFraction(0.50));
		assertEquals(BodyState.INJURED, BodyState.fromFraction(0.251));
		assertEquals(BodyState.CRITICAL, BodyState.fromFraction(0.25));
		assertEquals(BodyState.CRITICAL, BodyState.fromFraction(0.001));
		assertEquals(BodyState.DISABLED, BodyState.fromFraction(0.0));
		assertEquals(BodyState.DISABLED, BodyState.fromFraction(-0.5));
	}

	@Test
	void partMaximaMatchSpec() {
		assertEquals(20.0, BodyPart.HEAD.maxCondition());
		assertEquals(20.0, BodyPart.TORSO.maxCondition());
		assertEquals(13.0, BodyPart.LEFT_ARM.maxCondition());
		assertEquals(13.0, BodyPart.RIGHT_ARM.maxCondition());
		assertEquals(16.0, BodyPart.LEFT_LEG.maxCondition());
		assertEquals(16.0, BodyPart.RIGHT_LEG.maxCondition());
	}

	@Test
	void severityOrdering() {
		org.junit.jupiter.api.Assertions.assertTrue(BodyState.DISABLED.isAtLeastAsBadAs(BodyState.CRITICAL));
		org.junit.jupiter.api.Assertions.assertTrue(BodyState.CRITICAL.isAtLeastAsBadAs(BodyState.CRITICAL));
		org.junit.jupiter.api.Assertions.assertFalse(BodyState.HURT.isAtLeastAsBadAs(BodyState.INJURED));
	}
}
