package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.ScarLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a scar is written, and everything that must not touch it (1.6).
 *
 * <p>The mark is raised inside {@code setBroken} and {@code setDestroyed}
 * rather than at the call sites, because Broken clears itself silently once a
 * limb mends: by the time anything downstream could ask "was this ever broken",
 * the only honest answer is whatever was written down at the moment it happened.
 */
class ScarRecordTest {
	@Test
	void breakingMarksAndMendingKeepsIt() {
		BodyPartData leg = new BodyPartData(BodyPart.LEFT_LEG);
		leg.damage(10.0);
		leg.setBroken(true);
		assertEquals(ScarLevel.BROKEN, leg.scar());

		// Healing past HEALTHY is what clears Broken (spec §15) — and it is exactly
		// the moment the history would otherwise vanish.
		leg.heal(7.0);
		assertEquals(BodyState.HEALTHY, leg.state());
		assertFalse(leg.broken(), "the mend must actually mend");
		assertEquals(ScarLevel.BROKEN, leg.scar(), "the mend must not take the memory with it");

		leg.setCondition(BodyPart.LEFT_LEG.maxCondition());
		assertEquals(ScarLevel.BROKEN, leg.scar());
		assertFalse(leg.broken());
	}

	@Test
	void destroyingMarksTheHeavierScarAndOutranksABreak() {
		BodyPartData arm = new BodyPartData(BodyPart.LEFT_ARM);
		arm.setBroken(true);
		arm.setDestroyed(true);
		assertEquals(ScarLevel.DESTROYED, arm.scar(), "losing the part outranks breaking it");
		assertFalse(arm.broken(), "setDestroyed wipes Broken, which is why the scar is raised inside it");

		// A respawn or a debug command can give the part back; it cannot give back
		// the fact that it was gone.
		arm.setDestroyed(false);
		arm.setCondition(BodyPart.LEFT_ARM.maxCondition());
		assertEquals(ScarLevel.DESTROYED, arm.scar());

		arm.markScar(ScarLevel.BROKEN);
		assertEquals(ScarLevel.DESTROYED, arm.scar(), "a lighter mark can never overwrite a heavier one");

		// And the other order: a part broken after it was destroyed stays DESTROYED.
		arm.setBroken(true);
		assertEquals(ScarLevel.DESTROYED, arm.scar());
	}

	@Test
	void markNeverLowersButSetDoes() {
		BodyPartData arm = new BodyPartData(BodyPart.RIGHT_ARM);
		arm.markScar(ScarLevel.BROKEN);
		arm.markScar(ScarLevel.NONE);
		assertEquals(ScarLevel.BROKEN, arm.scar(), "markScar is a max, so NONE is a no-op");

		// setScar is the one verb allowed to lower one, and it belongs to /rdmg scar.
		arm.setScar(ScarLevel.NONE);
		assertEquals(ScarLevel.NONE, arm.scar());
		arm.setScar(ScarLevel.DESTROYED);
		arm.setScar(ScarLevel.BROKEN);
		assertEquals(ScarLevel.BROKEN, arm.scar(), "a raw overwrite really does overwrite");

		// A null from a caller that could not parse its argument is NONE, not a crash.
		arm.setScar(null);
		assertEquals(ScarLevel.NONE, arm.scar());

		arm.markScar(ScarLevel.DESTROYED);
		arm.clearScar();
		assertEquals(ScarLevel.NONE, arm.scar(), "clearScar belongs to reset(), and it clears");
	}

	@Test
	void damageAndStrainNeverTouchHistory() {
		BodyPartData leg = new BodyPartData(BodyPart.RIGHT_LEG);
		leg.damage(4.0);
		leg.strain(80);
		leg.tickStrain();
		leg.heal(1.0);
		leg.setCondition(9.0);
		leg.lowerCeilingTo(2);
		assertEquals(ScarLevel.NONE, leg.scar(), "being hurt is not the same as having been broken");

		// And once there is a mark, none of those may lower it either.
		leg.setBroken(true);
		leg.setBroken(false);
		leg.damage(3.0);
		leg.strain(40);
		leg.tickStrain();
		leg.heal(20.0);
		leg.setCondition(0.0);
		leg.restoreCeiling();
		assertEquals(ScarLevel.BROKEN, leg.scar());
		assertTrue(leg.scar() != ScarLevel.NONE);
	}
}
