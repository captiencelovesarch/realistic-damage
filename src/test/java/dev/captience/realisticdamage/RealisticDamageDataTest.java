package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.PendingExplosion;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealisticDamageDataTest {
	@Test
	void freshDataIsPristine() {
		RealisticDamageData data = new RealisticDamageData();
		assertEquals(Balance.BLOOD_MAX, data.blood());
		assertEquals(Balance.CONSCIOUSNESS_MAX, data.consciousness());
		assertFalse(data.unconscious());
		assertFalse(data.bleeding());
		for (BodyPart part : BodyPart.values()) {
			assertEquals(BodyState.HEALTHY, data.part(part).state());
		}
	}

	@Test
	void resetRestoresEverything() {
		RealisticDamageData data = new RealisticDamageData();
		data.part(BodyPart.LEFT_LEG).damage(16.0);
		data.part(BodyPart.LEFT_LEG).setBroken(true);
		data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
		data.setBlood(10.0);
		data.drainConsciousness(100.0);
		data.setUnconscious(true);
		data.setConcussion(true);
		data.setBurned(true);
		data.part(BodyPart.RIGHT_ARM).markScar(ScarLevel.DESTROYED);

		data.reset();

		assertEquals(Balance.BLOOD_MAX, data.blood());
		assertEquals(Balance.CONSCIOUSNESS_MAX, data.consciousness());
		assertFalse(data.unconscious());
		assertFalse(data.bleeding());
		assertFalse(data.concussion());
		assertFalse(data.burned());
		assertFalse(data.part(BodyPart.LEFT_LEG).broken());
		assertEquals(BodyState.HEALTHY, data.part(BodyPart.LEFT_LEG).state());
		// The debug heal is a full restore, so it forgets the history as well —
		// this is the one path in the mod that erases a scar without a command
		// saying the word.
		for (BodyPart part : BodyPart.values()) {
			assertEquals(ScarLevel.NONE, data.part(part).scar(), part.id());
		}
	}

	/**
	 * Death is not {@code reset()}. The reborn body forgets everything except the
	 * marks, and it forgets them <em>unconditionally</em> — the hardcore toggle
	 * governs ceilings only, so this passes with it off, which is the whole
	 * reason the assertion is written that way round.
	 */
	@Test
	void freshCarryingCopiesScarsAndNothingElse() {
		RealisticDamageData old = new RealisticDamageData();
		old.part(BodyPart.LEFT_ARM).setDestroyed(true);
		old.part(BodyPart.RIGHT_LEG).setCondition(2.0);
		old.part(BodyPart.RIGHT_LEG).setBroken(true);
		old.part(BodyPart.LEFT_LEG).lowerCeilingTo(2);
		old.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
		old.setBlood(10.0);
		old.setUnconscious(true);
		old.setConcussion(true);
		old.setBurned(true);

		RealisticDamageData fresh = RealisticDamageData.freshCarrying(old, false);
		assertTrue(fresh != old, "a new body, not the old one handed back");

		assertEquals(ScarLevel.DESTROYED, fresh.part(BodyPart.LEFT_ARM).scar());
		assertEquals(ScarLevel.BROKEN, fresh.part(BodyPart.RIGHT_LEG).scar());
		for (BodyPart part : BodyPart.values()) {
			if (part != BodyPart.LEFT_ARM && part != BodyPart.RIGHT_LEG) {
				assertEquals(ScarLevel.NONE, fresh.part(part).scar(), part.id());
			}
			// Everything else really is forgotten, including the ceiling: with the
			// toggle off, a scar test that also passed for a ceiling reason would be
			// no test at all.
			assertEquals(part.maxCondition(), fresh.part(part).condition(), 1e-9, part.id());
			assertEquals(0, fresh.part(part).ceilingBand(), part.id());
			assertFalse(fresh.part(part).broken(), part.id());
			assertFalse(fresh.part(part).destroyed(), part.id());
		}
		assertEquals(Balance.BLOOD_MAX, fresh.blood());
		assertEquals(Balance.CONSCIOUSNESS_MAX, fresh.consciousness());
		assertTrue(fresh.wounds().isEmpty());
		assertFalse(fresh.unconscious());
		assertFalse(fresh.concussion());
		assertFalse(fresh.burned());
	}

	@Test
	void consciousnessRespectsHeadCap() {
		RealisticDamageData data = new RealisticDamageData();
		// Drive head into CRITICAL (fraction in (0, 0.25]).
		data.part(BodyPart.HEAD).setCondition(4.0);
		assertEquals(BodyState.CRITICAL, data.part(BodyPart.HEAD).state());
		data.setConsciousness(100.0);
		assertEquals(Balance.HEAD_CAP_CRITICAL, data.consciousness());
	}

	@Test
	void concussionLowersTheCap() {
		RealisticDamageData data = new RealisticDamageData();
		data.setConcussion(true);
		data.setConsciousness(100.0);
		assertEquals(Balance.HEAD_CAP_HEALTHY - 10.0, data.consciousness());
	}

	@Test
	void totemStabilizesWithoutFullHeal() {
		RealisticDamageData data = new RealisticDamageData();
		data.part(BodyPart.RIGHT_LEG).damage(16.0); // DISABLED
		data.part(BodyPart.RIGHT_LEG).setBroken(true);
		data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
		data.setBlood(5.0);
		data.drainConsciousness(100.0);
		data.setUnconscious(true);
		data.setConcussion(true);

		data.totemStabilize();

		assertFalse(data.bleeding(), "bleeding stops");
		assertEquals(Balance.TOTEM_BLOOD_FLOOR, data.blood());
		assertTrue(data.consciousness() >= Balance.WAKE_THRESHOLD, "capable of escaping");
		assertFalse(data.unconscious());
		// Disabled part raised to a low-critical floor, NOT healed.
		assertEquals(16.0 * Balance.TOTEM_CONDITION_FLOOR_FRACTION, data.part(BodyPart.RIGHT_LEG).condition(), 1e-9);
		assertEquals(BodyState.CRITICAL, data.part(BodyPart.RIGHT_LEG).state());
		// Statuses persist (spec §41).
		assertTrue(data.part(BodyPart.RIGHT_LEG).broken());
		assertTrue(data.concussion());
	}

	@Test
	void totemDoesNotReduceExistingValues() {
		RealisticDamageData data = new RealisticDamageData();
		data.setBlood(80.0);
		data.totemStabilize();
		assertEquals(80.0, data.blood(), "totem must never lower blood");
		assertEquals(Balance.CONSCIOUSNESS_MAX, data.consciousness());
	}

	@Test
	void woundListIsCappedAndMerges() {
		RealisticDamageData data = new RealisticDamageData();
		// Distinct regions, distinct severities: fill to cap without merging.
		for (int i = 0; i < Balance.MAX_WOUNDS; i++) {
			data.addWound(BodyPart.values()[i % 6], i % 2 == 0 ? WoundSeverity.MINOR : WoundSeverity.MODERATE);
		}
		int size = data.wounds().size();
		assertTrue(size <= Balance.MAX_WOUNDS);
		// Adding more must never grow the list beyond cap.
		for (int i = 0; i < 20; i++) {
			data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
		}
		assertTrue(data.wounds().size() <= Balance.MAX_WOUNDS, "unbounded wound growth");
	}

	@Test
	void repeatSameSeverityHitEscalates() {
		RealisticDamageData data = new RealisticDamageData();
		data.addWound(BodyPart.LEFT_ARM, WoundSeverity.MODERATE);
		data.addWound(BodyPart.LEFT_ARM, WoundSeverity.MODERATE);
		assertEquals(1, data.wounds().size(), "same-region wounds merge");
		assertEquals(WoundSeverity.SEVERE, data.wounds().getFirst().severity());
	}

	@Test
	void brokenClearsWhenHealedToHealthy() {
		RealisticDamageData data = new RealisticDamageData();
		data.part(BodyPart.LEFT_ARM).damage(10.0);
		data.part(BodyPart.LEFT_ARM).setBroken(true);
		data.part(BodyPart.LEFT_ARM).heal(6.0); // 9/13 = 69% -> still not HEALTHY
		assertTrue(data.part(BodyPart.LEFT_ARM).broken());
		data.part(BodyPart.LEFT_ARM).heal(1.0); // 10/13 = 77% -> HEALTHY
		assertFalse(data.part(BodyPart.LEFT_ARM).broken(), "Broken clears at HEALTHY (spec §15)");
	}

	/**
	 * A blast scores exactly one damage event (1.6). A stale record left lying
	 * around would re-score the next unrelated hit as though the player had been
	 * standing on a barrel of TNT, which is the same reason the pending fall and
	 * the pre-hurt health are single-use.
	 */
	@Test
	void aPendingBlastIsSingleUse() {
		RealisticDamageData data = new RealisticDamageData();
		PendingExplosion blast = new PendingExplosion(43.0, 1.0, 2.0, 3.0);
		data.setPendingExplosion(blast);
		assertTrue(data.hasPendingExplosion());
		assertEquals(blast, data.pendingExplosion(), "reading it must not spend it");
		assertEquals(blast, data.consumePendingExplosion());
		assertFalse(data.hasPendingExplosion());
		assertNull(data.consumePendingExplosion(), "and a second consume finds nothing");
	}

	@Test
	void resetClearsThePendingBlast() {
		RealisticDamageData data = new RealisticDamageData();
		data.setPendingExplosion(new PendingExplosion(57.0, 0.0, 64.0, 0.0));
		data.reset();
		assertFalse(data.hasPendingExplosion(), "a pristine body is not mid-blast");
		// The record exists to be exact, so a blast with no force and no centre is
		// a programmer error rather than a value the game can produce: vanilla's
		// own formula ends in "+ 1", so every real amount is at least 1.
		assertThrows(IllegalArgumentException.class, () -> new PendingExplosion(0.0, 0.0, 0.0, 0.0));
		assertThrows(IllegalArgumentException.class,
				() -> new PendingExplosion(43.0, Double.NaN, 0.0, 0.0));
	}
}
