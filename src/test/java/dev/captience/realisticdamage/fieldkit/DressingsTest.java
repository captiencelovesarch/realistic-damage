package dev.captience.realisticdamage.fieldkit;

import com.mojang.serialization.JsonOps;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.medicine.Dressings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a bandage and a tourniquet let through (2.0, phase 4), as the one rule
 * the server's bleeding and a client's drawing of it both read.
 */
class DressingsTest {
	@Test
	void nothingOnLetsEverythingThrough() {
		assertEquals(1.0, Dressings.factor(null, BodyPart.LEFT_ARM, WoundSeverity.CRITICAL), 1e-12);
		assertEquals(1.0, Dressings.factor(new Dressings.State(), BodyPart.LEFT_ARM, WoundSeverity.MINOR), 1e-12);
	}

	@Test
	void aBandageSlowsTheSmallWoundsAndNotTheArterialOnes() {
		Dressings.State state = new Dressings.State();
		state.bandages.put(BodyPart.TORSO, new Dressings.Bandage());
		assertEquals(Balance.BANDAGE_MINOR_FACTOR, Dressings.factor(state, BodyPart.TORSO, WoundSeverity.MINOR), 1e-12);
		assertEquals(Balance.BANDAGE_MODERATE_FACTOR, Dressings.factor(state, BodyPart.TORSO, WoundSeverity.MODERATE),
				1e-12);
		assertEquals(1.0, Dressings.factor(state, BodyPart.TORSO, WoundSeverity.SEVERE), 1e-12, "severe is arterial");
		assertEquals(1.0, Dressings.factor(state, BodyPart.TORSO, WoundSeverity.CRITICAL), 1e-12);
		assertEquals(1.0, Dressings.factor(state, BodyPart.HEAD, WoundSeverity.MINOR), 1e-12, "only where it sits");
		assertTrue(Dressings.bandageable(WoundSeverity.MODERATE));
		assertFalse(Dressings.bandageable(WoundSeverity.SEVERE));
	}

	@Test
	void aSoakedBandageHoldsNothing() {
		Dressings.State state = new Dressings.State();
		Dressings.Bandage bandage = new Dressings.Bandage();
		bandage.soaked = true;
		state.bandages.put(BodyPart.LEFT_LEG, bandage);
		assertEquals(1.0, Dressings.factor(state, BodyPart.LEFT_LEG, WoundSeverity.MODERATE), 1e-12);
	}

	@Test
	void aTourniquetStopsAnything() {
		Dressings.State state = new Dressings.State();
		state.tourniquets.put(BodyPart.RIGHT_LEG, new Dressings.Tourniquet());
		assertEquals(0.0, Dressings.factor(state, BodyPart.RIGHT_LEG, WoundSeverity.CRITICAL), 1e-12);
	}

	/**
	 * Every change is made on a copy, because a sync may still be encoding the
	 * state it replaces: the copy must share nothing with it.
	 */
	@Test
	void aCopySharesNothing() {
		Dressings.State state = new Dressings.State();
		state.bandages.put(BodyPart.LEFT_ARM, new Dressings.Bandage());
		Dressings.Tourniquet tourniquet = new Dressings.Tourniquet();
		tourniquet.held = 4.0;
		state.tourniquets.put(BodyPart.RIGHT_LEG, tourniquet);
		Dressings.State copy = state.copy();
		copy.bandages.remove(BodyPart.LEFT_ARM);
		copy.tourniquets.get(BodyPart.RIGHT_LEG).ticks = 99;
		assertTrue(state.bandages.containsKey(BodyPart.LEFT_ARM), "the original keeps its bandage");
		assertEquals(0, state.tourniquets.get(BodyPart.RIGHT_LEG).ticks, "and its own tourniquet");
		assertEquals(4.0, copy.tourniquets.get(BodyPart.RIGHT_LEG).held, 1e-12);
	}

	/** Saved and synced the same way: everything that matters survives the trip. */
	@Test
	void theStateRoundTrips() {
		Dressings.State state = new Dressings.State();
		Dressings.Bandage bandage = new Dressings.Bandage();
		bandage.ticks = 120;
		bandage.soaked = true;
		state.bandages.put(BodyPart.LEFT_ARM, bandage);
		Dressings.Tourniquet tourniquet = new Dressings.Tourniquet();
		tourniquet.ticks = 4000;
		tourniquet.held = 9.5;
		state.tourniquets.put(BodyPart.RIGHT_LEG, tourniquet);
		Dressings.State back = Dressings.State.CODEC.parse(JsonOps.INSTANCE,
				Dressings.State.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow();
		assertTrue(back.bandages.get(BodyPart.LEFT_ARM).soaked);
		assertEquals(120, back.bandages.get(BodyPart.LEFT_ARM).ticks);
		assertEquals(9.5, back.tourniquets.get(BodyPart.RIGHT_LEG).held, 1e-12);
		assertEquals(4000, back.tourniquets.get(BodyPart.RIGHT_LEG).ticks);
	}
}
