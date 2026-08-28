package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig.UnconsciousAggro;
import org.junit.jupiter.api.Test;

/** The pure half of the aggro policy; MobTargetMixin is what applies it. */
class UnconsciousAggroPolicyTest {
	private static final double NEAR = Balance.UNCONSCIOUS_AGGRO_NEARBY_RADIUS - 1.0;
	private static final double FAR = Balance.UNCONSCIOUS_AGGRO_NEARBY_RADIUS + 1.0;

	@Test
	void noneRefusesEverythingIncludingCurrentAttackers() {
		assertFalse(UnconsciousAggro.NONE.allowsTargeting(false, NEAR));
		assertFalse(UnconsciousAggro.NONE.allowsTargeting(true, NEAR));
		assertFalse(UnconsciousAggro.NONE.allowsTargeting(true, 0.0));
	}

	@Test
	void attackersKeepsEngagementsAndRefusesEveryoneElse() {
		assertTrue(UnconsciousAggro.ATTACKERS.allowsTargeting(true, FAR));
		assertFalse(UnconsciousAggro.ATTACKERS.allowsTargeting(false, 0.0),
				"proximity must not create an engagement under ATTACKERS");
	}

	@Test
	void nearbyIsAttackersPlusAShortRadius() {
		assertTrue(UnconsciousAggro.NEARBY.allowsTargeting(false, NEAR));
		assertTrue(UnconsciousAggro.NEARBY.allowsTargeting(false,
				Balance.UNCONSCIOUS_AGGRO_NEARBY_RADIUS), "the radius is inclusive");
		assertFalse(UnconsciousAggro.NEARBY.allowsTargeting(false, FAR));
		assertTrue(UnconsciousAggro.NEARBY.allowsTargeting(true, FAR),
				"distance must not drop someone already fighting you");
	}

	@Test
	void allIsUnconditional() {
		assertTrue(UnconsciousAggro.ALL.allowsTargeting(false, 1000.0));
	}

	/**
	 * The modes must be ordered by mercy: anything a stricter mode permits, a
	 * looser one must permit too. Without this the settings would not form a
	 * scale and "turning it down" could increase aggro in some situation.
	 */
	@Test
	void theModesFormAScale() {
		for (boolean engaged : new boolean[] {false, true}) {
			for (double d : new double[] {0.0, NEAR, FAR, 100.0}) {
				boolean none = UnconsciousAggro.NONE.allowsTargeting(engaged, d);
				boolean attackers = UnconsciousAggro.ATTACKERS.allowsTargeting(engaged, d);
				boolean nearby = UnconsciousAggro.NEARBY.allowsTargeting(engaged, d);
				boolean all = UnconsciousAggro.ALL.allowsTargeting(engaged, d);
				String at = " (engaged=" + engaged + ", distance=" + d + ")";
				assertTrue(!none || attackers, "ATTACKERS must permit whatever NONE does" + at);
				assertTrue(!attackers || nearby, "NEARBY must permit whatever ATTACKERS does" + at);
				assertTrue(!nearby || all, "ALL must permit whatever NEARBY does" + at);
			}
		}
	}
}
