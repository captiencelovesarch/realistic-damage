package dev.captience.realisticdamage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig.PreyOnWounded;
import dev.captience.realisticdamage.systems.LimpMath;
import dev.captience.realisticdamage.systems.PreyMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The prey ladder, by enumeration.
 *
 * <p>The point of every test here is that the ordering is a <em>property</em>
 * and not a tendency: a body on the floor outranks a lost leg outranks a broken
 * one outranks a limp outranks blood, no matter what else is stacked underneath.
 * The interesting assertions are therefore over <b>thresholds</b> rather than
 * over scores — a comment claiming "600 beats everything" is true of scores and
 * false the moment a grudge is added to one of them, which is exactly the class
 * of error that shipped in an earlier draft of these constants.
 *
 * <p>All arithmetic is integer, so the enumeration needs no tolerance, and every
 * failure message names the tuple that broke.
 */
class PreyMathTest {
	/** Every fraction that matters: both sides of each band cut, and the two ends. */
	private static final double[] FRACTIONS = {
		1.0, 0.76, Balance.HEALTHY_ABOVE, 0.6, Balance.HURT_ABOVE, 0.4, 0.26, Balance.INJURED_ABOVE, 0.1, 0.0
	};
	/** -1 is "not bleeding"; 0 is MINOR, which must score exactly as much as not bleeding. */
	private static final int[] WOUNDS = {-1, 0, 1, 2, 3};

	/** One point of the lattice, carried with its own description so failures name themselves. */
	private record Tuple(boolean down, boolean legDestroyed, boolean legBroken, double fraction, int wound) {
		int score(PreyOnWounded mode) {
			return PreyMath.score(mode, down, legDestroyed, legBroken, fraction, wound);
		}

		@Override
		public String toString() {
			return "down=" + down + " destroyed=" + legDestroyed + " broken=" + legBroken
					+ " fraction=" + fraction + " wound=" + wound;
		}
	}

	private static List<Tuple> lattice() {
		List<Tuple> tuples = new ArrayList<>();
		for (boolean down : new boolean[] {false, true}) {
			for (boolean destroyed : new boolean[] {false, true}) {
				for (boolean broken : new boolean[] {false, true}) {
					for (double fraction : FRACTIONS) {
						for (int wound : WOUNDS) {
							tuples.add(new Tuple(down, destroyed, broken, fraction, wound));
						}
					}
				}
			}
		}
		return tuples;
	}

	private static void forEach(Consumer<Tuple> assertion) {
		lattice().forEach(assertion);
	}

	/** The highest score reachable by every tuple the predicate accepts. */
	private static int highest(PreyOnWounded mode, java.util.function.Predicate<Tuple> among) {
		int best = 0;
		for (Tuple tuple : lattice()) {
			if (among.test(tuple)) {
				best = Math.max(best, tuple.score(mode));
			}
		}
		return best;
	}

	@Test
	void offScoresEveryoneZero() {
		forEach(tuple -> assertEquals(0, tuple.score(PreyOnWounded.OFF),
				"OFF must be 1.5 behaviour exactly, at " + tuple));
	}

	@Test
	void theModesFormAScale() {
		forEach(tuple -> {
			int off = tuple.score(PreyOnWounded.OFF);
			int downed = tuple.score(PreyOnWounded.DOWNED);
			int limping = tuple.score(PreyOnWounded.LIMPING);
			int visible = tuple.score(PreyOnWounded.VISIBLE);
			assertTrue(off <= downed && downed <= limping && limping <= visible,
					"each mode must only ever add, at " + tuple
							+ " got " + off + "/" + downed + "/" + limping + "/" + visible);
		});
	}

	/**
	 * I1, stated over thresholds. A grudge is the largest thing that can be added
	 * to a candidate's bar, so the honest question is whether the best non-down
	 * score plus a grudge still loses to a body on the floor: 300 + 30 + 175 =
	 * 505 &lt; 600.
	 */
	@Test
	void downOutranksEverythingEvenAGrudge() {
		int bestStanding = highest(PreyOnWounded.VISIBLE, tuple -> !tuple.down());
		assertTrue(bestStanding + Balance.PREY_REVENGE_BONUS < Balance.PREY_TIER_DOWN,
				"a body on the floor must outrank the best standing player even against a grudge: "
						+ bestStanding + " + " + Balance.PREY_REVENGE_BONUS
						+ " must be under " + Balance.PREY_TIER_DOWN);
		for (PreyOnWounded mode : new PreyOnWounded[] {
			PreyOnWounded.DOWNED, PreyOnWounded.LIMPING, PreyOnWounded.VISIBLE
		}) {
			forEach(tuple -> {
				if (tuple.down()) {
					assertTrue(tuple.score(mode) >= Balance.PREY_TIER_DOWN,
							"a body on the floor must always clear the tier, at " + tuple + " in " + mode);
				}
			});
		}
	}

	@Test
	void aLostLegOutranksABrokenOnePlusAnyBlood() {
		int bestIntact = highest(PreyOnWounded.VISIBLE, tuple -> !tuple.down() && !tuple.legDestroyed());
		assertTrue(bestIntact < Balance.PREY_TIER_LEG_DESTROYED,
				"a leg that is gone must beat any leg that is merely broken and bleeding, got " + bestIntact);
		forEach(tuple -> {
			if (tuple.legDestroyed() && !tuple.down()) {
				assertTrue(tuple.score(PreyOnWounded.VISIBLE) >= Balance.PREY_TIER_LEG_DESTROYED,
						"a lost leg must always clear its tier, at " + tuple);
			}
		});
	}

	@Test
	void aBreakOutranksAnyLimpPlusAnyBlood() {
		int bestUnbroken = highest(PreyOnWounded.VISIBLE,
				tuple -> !tuple.down() && !tuple.legDestroyed() && !tuple.legBroken());
		assertTrue(bestUnbroken < Balance.PREY_TIER_LEG_BROKEN,
				"a break must beat the worst limp plus arterial blood, got " + bestUnbroken);
		forEach(tuple -> {
			if (tuple.legBroken() && !tuple.legDestroyed() && !tuple.down()) {
				assertTrue(tuple.score(PreyOnWounded.VISIBLE) >= Balance.PREY_TIER_LEG_BROKEN,
						"a broken leg must always clear its tier, at " + tuple);
			}
		});
	}

	/**
	 * I4 and I5 together, which are the revenge decision written as arithmetic:
	 * hit a mob and it keeps you against any limp and any blood, and loses you to
	 * a broken leg. Both by exactly one switch margin, so neither is a near miss.
	 */
	@Test
	void aGrudgeHoldsAgainstAnyLimpButNotABreak() {
		int bestUnbroken = highest(PreyOnWounded.VISIBLE,
				tuple -> !tuple.down() && !tuple.legDestroyed() && !tuple.legBroken());
		assertEquals(bestUnbroken + Balance.PREY_SWITCH_MARGIN, Balance.PREY_REVENGE_BONUS,
				"the grudge must clear the worst limp-and-blood by exactly one margin");
		assertEquals(Balance.PREY_REVENGE_BONUS + Balance.PREY_SWITCH_MARGIN, Balance.PREY_TIER_LEG_BROKEN,
				"and a break must clear the grudge by exactly one margin");
		assertTrue(PreyMath.prefers(Balance.PREY_TIER_LEG_BROKEN, 0, true, true),
				"a broken leg must take a mob off the player who hit it");
		assertFalse(PreyMath.prefers(bestUnbroken, 0, false, true),
				"no limp and no amount of blood may take a mob off the player who hit it");
	}

	@Test
	void theGrudgeReplacesTheMarginRatherThanStacking() {
		assertEquals(Balance.PREY_REVENGE_BONUS, PreyMath.hold(true, true),
				"a grudge held by an engaged mob is still one grudge");
		assertEquals(Balance.PREY_REVENGE_BONUS, PreyMath.hold(false, true),
				"and a fresh one is the same number");
		assertEquals(Balance.PREY_SWITCH_MARGIN, PreyMath.hold(true, false));
		assertEquals(0, PreyMath.hold(false, false), "a fresh acquisition is loyal to nobody");
	}

	/**
	 * The rung cut, at every boundary. Hurt costs under 5% of your speed and
	 * Injured under 13% — the anchors are the leg curve's own (0.75, 0%),
	 * (0.50, 7%), (0.25, 18%) weighted 0.70 for the worse leg — so neither is
	 * something an observer could point at, and neither scores.
	 */
	@Test
	void hurtAndInjuredAreNotLimpsButCriticalIs() {
		int[] expected = {0, 0, 0, 0, 0, 0, 0, 1, 1, 2};
		for (int i = 0; i < FRACTIONS.length; i++) {
			assertEquals(expected[i], LimpMath.limpBand(FRACTIONS[i]),
					"the limp rung at fraction " + FRACTIONS[i]);
			assertEquals(expected[i] * Balance.PREY_LIMP_BAND,
					PreyMath.score(PreyOnWounded.VISIBLE, false, false, false, FRACTIONS[i], -1),
					"the limp score at fraction " + FRACTIONS[i]);
		}
	}

	@Test
	void aScratchIsNotBleeding() {
		assertEquals(0, PreyMath.woundStep(-1), "not bleeding");
		assertEquals(0, PreyMath.woundStep(WoundSeverity.MINOR.ordinal()), "a scratch seeps, it does not drip");
		assertEquals(1, PreyMath.woundStep(WoundSeverity.MODERATE.ordinal()));
		assertEquals(2, PreyMath.woundStep(WoundSeverity.SEVERE.ordinal()));
		assertEquals(3, PreyMath.woundStep(WoundSeverity.CRITICAL.ordinal()));
		assertEquals(0, PreyMath.score(PreyOnWounded.VISIBLE, false, false, false, 1.0,
				WoundSeverity.MINOR.ordinal()), "one arrow must not make a player a magnet");
	}

	@Test
	void bloodAloneNeverFlipsAnEngagedMobUntilItIsArterial() {
		assertTrue(2 * Balance.PREY_WOUND_STEP < Balance.PREY_SWITCH_MARGIN,
				"two wound steps must not flip an engagement");
		assertTrue(Balance.PREY_SWITCH_MARGIN < 3 * Balance.PREY_WOUND_STEP,
				"three must");
		assertTrue(Balance.PREY_LIMP_BAND > Balance.PREY_SWITCH_MARGIN,
				"and a leg giving out must always flip one");
		int base = 40;
		assertFalse(PreyMath.prefers(base + 2 * Balance.PREY_WOUND_STEP, base, true, false));
		assertTrue(PreyMath.prefers(base + 3 * Balance.PREY_WOUND_STEP, base, true, false));
		assertTrue(PreyMath.prefers(base + Balance.PREY_LIMP_BAND, base, true, false));
	}

	@Test
	void tiesKeepTheRequestedTarget() {
		for (int score = 0; score <= Balance.PREY_TIER_DOWN; score += 25) {
			assertFalse(PreyMath.prefers(score, score, false, false),
					"an equal candidate must never displace the requested one, at " + score);
			assertTrue(PreyMath.prefers(score + 1, score, false, false),
					"but a fresh acquisition is eager, at " + score);
		}
	}

	/**
	 * The adapter reads the fields the design says it reads, and the ladder is
	 * the shared one: the same leg at the same rung scores the same whether it is
	 * described by three primitives or by a body.
	 */
	@Test
	void theBodyOverloadAgreesWithThePrimitives() {
		RealisticDamageData data = new RealisticDamageData();
		data.part(BodyPart.LEFT_LEG).setCondition(3.0);   // 3/16 = 0.1875, Critical, rung 1
		data.addWound(BodyPart.RIGHT_ARM, WoundSeverity.SEVERE);
		data.knockDown(20);
		assertEquals(Balance.PREY_TIER_DOWN + Balance.PREY_LIMP_BAND + 2 * Balance.PREY_WOUND_STEP,
				PreyMath.score(data, PreyOnWounded.VISIBLE),
				"down plus one limp rung plus a severe wound");

		data.part(BodyPart.RIGHT_LEG).setBroken(true);
		assertEquals(Balance.PREY_TIER_DOWN + Balance.PREY_TIER_LEG_BROKEN + 2 * Balance.PREY_WOUND_STEP,
				PreyMath.score(data, PreyOnWounded.VISIBLE),
				"the break replaces the rung rather than adding to it");

		RealisticDamageData pristine = new RealisticDamageData();
		assertEquals(0, PreyMath.score(pristine, PreyOnWounded.VISIBLE),
				"an unhurt body must score exactly nothing, or every acquisition is a substitution");
	}

	@Test
	void limpingIgnoresBloodAndDownedIgnoresLegs() {
		assertEquals(Balance.PREY_TIER_LEG_BROKEN,
				PreyMath.score(PreyOnWounded.LIMPING, false, false, true, 1.0, 3),
				"LIMPING must not read blood");
		assertEquals(0, PreyMath.score(PreyOnWounded.DOWNED, false, false, true, 1.0, 3),
				"DOWNED must not read legs or blood");
		assertEquals(Balance.PREY_TIER_DOWN,
				PreyMath.score(PreyOnWounded.DOWNED, true, true, true, 0.0, 3),
				"DOWNED reads exactly one thing");
	}

	/**
	 * The seam this feature was paired with the visible limp for: the score's leg
	 * term is a function of the rung the model poses from, and of nothing else.
	 * If the two ever drift, mobs single out players who look fine.
	 */
	@Test
	void theLegTermIsAFunctionOfTheSharedRung() {
		forEach(tuple -> {
			int rung = LimpMath.rung(tuple.legDestroyed(), tuple.legBroken(), tuple.fraction());
			int expected = Balance.PREY_TIER_DOWN * (tuple.down() ? 1 : 0)
					+ PreyMath.legTerm(rung)
					+ PreyMath.woundStep(tuple.wound()) * Balance.PREY_WOUND_STEP;
			assertEquals(expected, tuple.score(PreyOnWounded.VISIBLE),
					"the score must be the shared rung plus the two other terms, at " + tuple);
		});
		assertEquals(0, PreyMath.legTerm(LimpMath.RUNG_SOUND));
		assertEquals(Balance.PREY_LIMP_BAND, PreyMath.legTerm(LimpMath.RUNG_FAVOURED));
		assertEquals(2 * Balance.PREY_LIMP_BAND, PreyMath.legTerm(LimpMath.RUNG_RUINED));
		assertEquals(Balance.PREY_TIER_LEG_BROKEN, PreyMath.legTerm(LimpMath.RUNG_FRACTURED));
		assertEquals(Balance.PREY_TIER_LEG_DESTROYED, PreyMath.legTerm(LimpMath.RUNG_LOST));
		// Every rung the model draws on is a rung a mob scores above zero, and the
		// one exception is stated rather than left to be discovered: a lost leg is
		// not posed, because there is nothing left to angle.
		for (int rung = LimpMath.RUNG_SOUND; rung <= LimpMath.RUNG_LOST; rung++) {
			assertEquals(LimpMath.visible(rung), PreyMath.legTerm(rung) > 0,
					"visibility and scoring must agree at rung " + rung);
		}
	}
}
