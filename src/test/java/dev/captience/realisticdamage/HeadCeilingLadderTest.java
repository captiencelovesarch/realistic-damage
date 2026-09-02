package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.Vitals;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.CeilingMath;
import dev.captience.realisticdamage.systems.ImpairmentMath;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why the head and the chest stop one rung down (1.6). A ceiling may cost the
 * buffer above the symptom ladder; it may never cost a rung of it. The head
 * floor and the consciousness caps are tuned in two different places, so this is
 * the file that fails when a retune of either would put a permanently capped
 * head under a symptom the player can never climb back out of.
 */
class HeadCeilingLadderTest {
	@Test
	void theHurtCapClearsTheLadderEvenWhenConcussed() {
		RealisticDamageData data = new RealisticDamageData();
		double headCeiling = CeilingMath.fraction(CeilingMath.floorBand(BodyPart.HEAD))
				* BodyPart.HEAD.maxCondition();
		assertEquals(15.0, headCeiling);
		data.part(BodyPart.HEAD).setCondition(headCeiling);
		assertEquals(BodyState.HURT, data.part(BodyPart.HEAD).state());

		data.setConsciousness(Balance.CONSCIOUSNESS_MAX);
		assertEquals(Balance.HEAD_CAP_HURT, data.consciousness());
		assertTrue(data.consciousness() >= Balance.SYMPTOM_VIGNETTE);
		assertEquals(0, Vitals.consciousnessBand(data.consciousness(), false));

		// And with a concussion on top of the permanent cap.
		data.setConcussion(true);
		data.setConsciousness(Balance.CONSCIOUSNESS_MAX);
		assertEquals(Balance.HEAD_CAP_HURT - Balance.CONCUSSION_CAP_DROP, data.consciousness());
		double effective = Vitals.effectiveConsciousness(data.consciousness(), true);
		assertEquals(67.0, effective);
		assertTrue(effective >= Balance.SYMPTOM_SENSITIVITY);
		assertEquals(0.0, ImpairmentSystem.sluggishPenalty(effective));
		assertEquals(0.0, ImpairmentMath.eatingStallPenalty(effective));
	}

	@Test
	void headCeilingFloorNeverReachesTheSymptomLadder() {
		// The rung below is why the floor is one and not two, written in the
		// constants' own words: an Injured ceiling would install a permanent action
		// penalty the player could never heal off.
		assertTrue(Balance.HEAD_CAP_INJURED - Balance.CONCUSSION_CAP_DROP - Balance.CONCUSSION_SYMPTOM_SHIFT
				< Balance.SYMPTOM_ATTACK_RECOVERY);
		assertEquals(Balance.CEILING_FLOOR_BAND_CORE, CeilingMath.floorBand(BodyPart.HEAD));
		assertEquals(Balance.CEILING_FLOOR_BAND_CORE, CeilingMath.floorBand(BodyPart.TORSO));
		assertEquals(1, CeilingMath.floorBand(BodyPart.HEAD));

		// The concussion drop is bounded by the waking line plus its margin, which is
		// exactly HEAD_CAP_DISABLED — a concussion alone never holds you under.
		assertEquals(Balance.HEAD_CAP_DISABLED,
				Balance.WAKE_THRESHOLD + Balance.CONCUSSION_CAP_FLOOR_MARGIN);
	}
}
