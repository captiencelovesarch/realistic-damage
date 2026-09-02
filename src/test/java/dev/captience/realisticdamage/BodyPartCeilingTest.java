package dev.captience.realisticdamage;

import java.util.Map;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.HealingMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a ceiling does to the body model (1.6). Everything here is byte-identical
 * to 1.5 at band 0 — that is the whole point of the design — so each case first
 * lowers a ceiling and then asks whether the ordinary mutators honour it.
 */
class BodyPartCeilingTest {
	@Test
	void ceilingClampsHealAndSetCondition() {
		RealisticDamageData data = new RealisticDamageData();
		BodyPartData leg = data.part(BodyPart.LEFT_LEG);
		assertTrue(leg.lowerCeilingTo(2));

		leg.heal(100.0);
		assertEquals(8.0, leg.condition(), 1e-9);
		leg.setCondition(16.0);
		assertEquals(8.0, leg.condition(), 1e-9);

		// fraction() still measures against the part's own max, so the ceiling shows
		// up as a permanent colour rather than vanishing into a rebased "full".
		assertEquals(0.5, leg.fraction(), 1e-9);
		assertEquals(BodyState.INJURED, leg.state());
		assertTrue(leg.atCeiling());
	}

	@Test
	void missingIsMeasuredAgainstTheCeiling() {
		RealisticDamageData data = new RealisticDamageData();
		BodyPartData leg = data.part(BodyPart.LEFT_LEG);
		leg.lowerCeilingTo(2);
		leg.heal(100.0);

		// Nothing left to heal: this is the "addExhaustion forever" bug the ceiling
		// would otherwise create, and missing() is where it would start.
		assertEquals(0.0, leg.missing(), 1e-9);
		assertTrue(leg.atCeiling());
		Map<BodyPart, Double> budget = HealingMath.distributeBudget(data, 10.0);
		assertFalse(budget.containsKey(BodyPart.LEFT_LEG));

		leg.damage(1.0);
		assertEquals(1.0, leg.missing(), 1e-9);
		assertFalse(leg.atCeiling());
	}

	@Test
	void lowerCeilingIsMonotoneAndSanitised() {
		RealisticDamageData data = new RealisticDamageData();
		BodyPartData leg = data.part(BodyPart.LEFT_LEG);

		assertTrue(leg.lowerCeilingTo(2));
		assertEquals(2, leg.ceilingBand());
		// Never raises, and never reports a move it did not make.
		assertFalse(leg.lowerCeilingTo(1));
		assertEquals(2, leg.ceilingBand());
		assertFalse(leg.lowerCeilingTo(0));
		assertEquals(2, leg.ceilingBand());
		assertFalse(leg.lowerCeilingTo(99));
		assertFalse(leg.lowerCeilingTo(-1));
		assertEquals(2, leg.ceilingBand());

		// The head is floored at one rung however deep the caller asks.
		BodyPartData head = data.part(BodyPart.HEAD);
		assertTrue(head.lowerCeilingTo(3));
		assertEquals(1, head.ceilingBand());
		assertEquals(15.0, head.ceiling(), 1e-9);
		assertEquals(15.0, head.condition(), 1e-9);

		// A ceiling pulls condition under it, but being capped is not mending.
		BodyPartData arm = data.part(BodyPart.RIGHT_ARM);
		arm.setBroken(true);
		assertTrue(arm.lowerCeilingTo(1));
		assertEquals(13.0 * Balance.HEALTHY_ABOVE, arm.condition(), 1e-9);
		assertTrue(arm.broken());
	}

	@Test
	void brokenClearsAtTheCeiling() {
		RealisticDamageData data = new RealisticDamageData();
		BodyPartData leg = data.part(BodyPart.LEFT_LEG);
		leg.setCondition(2.0);
		leg.setBroken(true);
		leg.lowerCeilingTo(2);

		leg.heal(5.9);
		assertEquals(7.9, leg.condition(), 1e-9);
		assertTrue(leg.broken(), "still short of the ceiling");

		leg.heal(0.1);
		assertEquals(8.0, leg.condition(), 1e-9);
		assertFalse(leg.broken(), "food and time have done all they can");
		assertEquals(BodyState.INJURED, leg.state());
	}

	@Test
	void brokenStillClearsAtHealthyWithNoCeiling() {
		RealisticDamageData data = new RealisticDamageData();
		BodyPartData leg = data.part(BodyPart.RIGHT_LEG);
		leg.setCondition(2.0);
		leg.setBroken(true);

		leg.heal(10.0);
		assertEquals(12.0, leg.condition(), 1e-9);
		assertTrue(leg.broken(), "12/16 is HURT, not HEALTHY");

		leg.heal(4.0);
		assertEquals(16.0, leg.condition(), 1e-9);
		assertFalse(leg.broken());
		assertEquals(0, leg.ceilingBand(), "healing never touches the band");
	}

	@Test
	void resetRestoresTheCeiling() {
		RealisticDamageData data = new RealisticDamageData();
		data.part(BodyPart.LEFT_LEG).lowerCeilingTo(3);
		data.part(BodyPart.HEAD).lowerCeilingTo(1);
		data.part(BodyPart.RIGHT_ARM).setDestroyed(true);

		data.reset();

		for (BodyPart part : BodyPart.values()) {
			assertEquals(0, data.part(part).ceilingBand(), part.id());
			assertEquals(part.maxCondition(), data.part(part).condition(), 1e-9, part.id());
			assertEquals(BodyState.HEALTHY, data.part(part).state(), part.id());
		}
	}

	@Test
	void totemFloorNeverExceedsACeiling() {
		RealisticDamageData data = new RealisticDamageData();
		BodyPartData leg = data.part(BodyPart.LEFT_LEG);
		leg.lowerCeilingTo(3);
		leg.setCondition(0.0);

		data.totemStabilize();

		// The floor is 0.15 of max and the deepest ceiling is 0.25, so the clamp
		// never binds: a totem is a rescue, not a cure.
		assertEquals(16.0 * Balance.TOTEM_CONDITION_FLOOR_FRACTION, leg.condition(), 1e-9);
		assertEquals(3, leg.ceilingBand());
		assertEquals(BodyState.CRITICAL, leg.state());
	}

	@Test
	void recoveryFractionIsAgainstTheCeiling() {
		RealisticDamageData data = new RealisticDamageData();
		BodyPartData leg = data.part(BodyPart.LEFT_LEG);
		leg.lowerCeilingTo(2);
		leg.setCondition(8.0);
		assertEquals(1.0, leg.recoveryFraction(), 1e-9);

		leg.damage(4.0);
		assertEquals(0.5, leg.recoveryFraction(), 1e-9);

		BodyPartData arm = data.part(BodyPart.LEFT_ARM);
		arm.setDestroyed(true);
		assertEquals(0.0, arm.recoveryFraction(), 1e-9);
		assertFalse(arm.atCeiling(), "gone is not recovered");
	}

	@Test
	void freshCarryingKeepsOnlyCeilings() {
		RealisticDamageData old = new RealisticDamageData();
		old.part(BodyPart.LEFT_LEG).lowerCeilingTo(3);
		old.part(BodyPart.HEAD).lowerCeilingTo(1);
		old.part(BodyPart.RIGHT_ARM).setBroken(true);
		old.part(BodyPart.RIGHT_ARM).setCondition(1.0);
		old.part(BodyPart.LEFT_ARM).setDestroyed(true);
		old.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
		old.setBlood(10.0);
		old.setConcussion(true);

		RealisticDamageData carried = RealisticDamageData.freshCarrying(old, true);
		assertEquals(3, carried.part(BodyPart.LEFT_LEG).ceilingBand());
		assertEquals(4.0, carried.part(BodyPart.LEFT_LEG).condition(), 1e-9, "reborn AT the ceiling");
		assertTrue(carried.part(BodyPart.LEFT_LEG).atCeiling());
		assertEquals(1, carried.part(BodyPart.HEAD).ceilingBand());
		assertEquals(15.0, carried.part(BodyPart.HEAD).condition(), 1e-9);

		// Everything else is forgotten by default. That is the rule the death reset
		// rests on, so it is asserted rather than left to the listener.
		assertFalse(carried.part(BodyPart.RIGHT_ARM).broken());
		assertFalse(carried.part(BodyPart.LEFT_ARM).destroyed());
		assertEquals(13.0, carried.part(BodyPart.LEFT_ARM).condition(), 1e-9);
		assertTrue(carried.wounds().isEmpty());
		assertEquals(Balance.BLOOD_MAX, carried.blood());
		assertEquals(Balance.CONSCIOUSNESS_MAX, carried.consciousness());
		assertFalse(carried.concussion());

		RealisticDamageData pristine = RealisticDamageData.freshCarrying(old, false);
		for (BodyPart part : BodyPart.values()) {
			assertEquals(0, pristine.part(part).ceilingBand(), part.id());
			assertEquals(part.maxCondition(), pristine.part(part).condition(), 1e-9, part.id());
		}
	}
}
