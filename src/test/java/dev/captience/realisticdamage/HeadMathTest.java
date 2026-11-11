package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.DamageCategory;
import dev.captience.realisticdamage.systems.HeadMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a head comes off (2.0): the whole table, so the rule is a property and not a tendency. */
class HeadMathTest {
	private static final double ENOUGH = Balance.DESTROY_TRAUMA_CORE;

	@Test
	void onlyAnEdgeTakesAHead() {
		for (DamageCategory category : DamageCategory.values()) {
			boolean edge = category == DamageCategory.SLASH || category == DamageCategory.HEAVY_SLASH;
			assertEquals(edge, HeadMath.edged(category), category.name());
			assertEquals(edge, HeadMath.takes(category, BodyState.DISABLED, false, false, ENOUGH),
					"a serious " + category + " blow on a ruined head");
		}
		assertFalse(HeadMath.edged(null), "an unclassified blow is not an edge");
	}

	@Test
	void theHeadHasToBeRuinedFirst() {
		for (BodyState state : BodyState.values()) {
			assertEquals(state == BodyState.DISABLED,
					HeadMath.takes(DamageCategory.HEAVY_SLASH, state, false, true, 0.0),
					"a killing axe on a head that is " + state);
		}
	}

	@Test
	void aBlowThatIsNotEnoughLeavesItOn() {
		assertFalse(HeadMath.takes(DamageCategory.SLASH, BodyState.DISABLED, false, false, ENOUGH - 0.01));
		assertTrue(HeadMath.takes(DamageCategory.SLASH, BodyState.DISABLED, false, false, ENOUGH));
		// A blow that killed by itself was enough, whatever it was worth.
		assertTrue(HeadMath.takes(DamageCategory.SLASH, BodyState.DISABLED, false, true, 0.0));
	}

	@Test
	void aHeadOnlyComesOffOnce() {
		assertFalse(HeadMath.takes(DamageCategory.HEAVY_SLASH, BodyState.DISABLED, true, true, 100.0));
	}

	@Test
	void aWitherSkullComesThroughAsOftenAsVanillaDropsOne() {
		int plain = 0;
		int looted = 0;
		int unowned = 0;
		int n = 200_000;
		for (long seed = 0; seed < n; seed++) {
			plain += HeadMath.skullSurvives(seed * 7919L, 0, true) ? 1 : 0;
			looted += HeadMath.skullSurvives(seed * 7919L, 3, true) ? 1 : 0;
			unowned += HeadMath.skullSurvives(seed * 7919L, 3, false) ? 1 : 0;
		}
		assertEquals(0.025, plain / (double) n, 0.004, "a player's kill, no Looting");
		assertEquals(0.055, looted / (double) n, 0.005, "Looting III");
		assertEquals(0, unowned, "no player, no skull, as in vanilla");
		// The same skeleton always gives the same answer.
		assertEquals(HeadMath.skullSurvives(12345L, 1, true), HeadMath.skullSurvives(12345L, 1, true));
	}

	@Test
	void aHeadlessZombieWalksThreeToFiveSeconds() {
		assertEquals(Balance.HEADLESS_WALK_TICKS, HeadMath.walkTicks(1.0), "an ordinary zombie");
		assertEquals(Balance.HEADLESS_WALK_TICKS, HeadMath.walkTicks(0.5), "never less than an ordinary one");
		assertTrue(HeadMath.walkTicks(1.4) > Balance.HEADLESS_WALK_TICKS, "a bigger one walks longer");
		assertEquals(Balance.HEADLESS_WALK_TICKS_MAX, HeadMath.walkTicks(5.0), "a giant, at the cap");
		assertEquals(60, Balance.HEADLESS_WALK_TICKS);
		assertEquals(100, Balance.HEADLESS_WALK_TICKS_MAX);
	}
}
