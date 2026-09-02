package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.ScarLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The scar ladder itself (1.6). Two properties carry the whole feature: a value
 * that arrives from a save or a packet can never throw, and history only ever
 * goes one way.
 */
class ScarLevelTest {
	@Test
	void byOrdinalClampsInsteadOfThrowing() {
		// Deliberately unlike CeilingMath.legalBand, which sends garbage to 0. A
		// corrupt ceiling would be a permanent injury nobody earned; a corrupt scar
		// is a mark, so it saturates and the world still loads.
		assertEquals(ScarLevel.NONE, ScarLevel.byOrdinal(-7));
		assertEquals(ScarLevel.NONE, ScarLevel.byOrdinal(0));
		assertEquals(ScarLevel.BROKEN, ScarLevel.byOrdinal(1));
		assertEquals(ScarLevel.DESTROYED, ScarLevel.byOrdinal(2));
		assertEquals(ScarLevel.DESTROYED, ScarLevel.byOrdinal(99));
		assertEquals(ScarLevel.DESTROYED, ScarLevel.byOrdinal(Integer.MAX_VALUE));
		assertEquals(ScarLevel.NONE, ScarLevel.byOrdinal(Integer.MIN_VALUE));

		// The ordinal is the save value and the wire value, so the identity has to
		// hold for every constant that exists today and every one appended later.
		for (ScarLevel level : ScarLevel.values()) {
			assertEquals(level, ScarLevel.byOrdinal(level.ordinal()), level.name());
		}
	}

	@Test
	void maxIsMonotoneAndNoneIsIdentity() {
		for (ScarLevel a : ScarLevel.values()) {
			assertEquals(a, a.max(a), "idempotent: " + a);
			assertEquals(a, a.max(ScarLevel.NONE), "NONE is the identity: " + a);
			assertEquals(a, ScarLevel.NONE.max(a), "from the other side too: " + a);
			for (ScarLevel b : ScarLevel.values()) {
				assertEquals(a.max(b), b.max(a), "commutative: " + a + " / " + b);
				// The result is never below either input — the reason markScar can be
				// called from anywhere, in any order, without a call-site argument.
				assertEquals(Math.max(a.ordinal(), b.ordinal()), a.max(b).ordinal(), a + " / " + b);
				for (ScarLevel c : ScarLevel.values()) {
					assertEquals(a.max(b).max(c), a.max(b.max(c)), "associative: " + a + b + c);
				}
			}
		}
	}
}
