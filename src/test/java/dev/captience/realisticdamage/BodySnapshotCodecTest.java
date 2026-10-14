package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.ScarMask;
import dev.captience.realisticdamage.net.BodySnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire shape (1.6). {@code BodySnapshot} is two nested records joined by a
 * two-pair composite, and the one thing a grouped composite still cannot catch
 * for you is a pair list whose order has drifted from the constructor's among
 * adjacent same-typed components — which {@code ceilingMask} and {@code scarMask}
 * now are. Hence a distinct value in every single component: a swapped pair
 * compiles, ships, and fails only here.
 */
class BodySnapshotCodecTest {
	private static RegistryFriendlyByteBuf buffer() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
	}

	@Test
	void everyFieldSurvivesWithADistinctValue() {
		BodySnapshot original = new BodySnapshot(
				new BodySnapshot.Regions(0.11f, 0.22f, 0.33f, 0.44f, 0.55f, 0.66f,
						0b010101, 0b001_010_011_100_001_010, 0b101010, 0x123, 0x2C9),
				new BodySnapshot.Meters(0b1101010110, 71.5f, 42.5f, 3, 65, 2));

		RegistryFriendlyByteBuf buf = buffer();
		BodySnapshot.STREAM_CODEC.encode(buf, original);
		BodySnapshot decoded = BodySnapshot.STREAM_CODEC.decode(buf);

		assertEquals(original, decoded);
		assertEquals(0, buf.readableBytes(), "the reader must consume exactly what the writer wrote");

		// Named individually as well: record equality would still pass if two
		// same-typed components were swapped in BOTH the pair list and the getters.
		assertEquals(0.11f, decoded.head());
		assertEquals(0.22f, decoded.torso());
		assertEquals(0.33f, decoded.leftArm());
		assertEquals(0.44f, decoded.rightArm());
		assertEquals(0.55f, decoded.leftLeg());
		assertEquals(0.66f, decoded.rightLeg());
		assertEquals(0b010101, decoded.brokenMask());
		assertEquals(0b001_010_011_100_001_010, decoded.woundMask());
		assertEquals(0b101010, decoded.destroyedMask());
		assertEquals(0x123, decoded.ceilingMask());
		assertEquals(0x2C9, decoded.scarMask());
		assertEquals(0b1101010110, decoded.statusFlags());
		assertEquals(71.5f, decoded.blood());
		assertEquals(42.5f, decoded.consciousness());
		assertEquals(3, decoded.concussionGrade());
		assertEquals(65, decoded.concussionFlare());
		assertEquals(dev.captience.realisticdamage.systems.AdrenalineMath.Phase.CRASH, decoded.adrenaline());
		assertTrue(decoded.vertigo());
		assertTrue(decoded.retching());
		assertTrue(decoded.winded());
	}

	@Test
	void healthyIsTheZeroPoint() {
		BodySnapshot healthy = BodySnapshot.healthy();
		assertEquals(0, healthy.ceilingMask());
		assertEquals(0, healthy.scarMask());
		assertEquals(0, healthy.brokenMask());
		assertEquals(0, healthy.destroyedMask());
		assertEquals(0, healthy.woundMask());
		assertEquals(0, healthy.statusFlags());
		for (BodyPart part : BodyPart.values()) {
			assertEquals(1f, healthy.fraction(part), part.id());
			assertEquals(0, healthy.ceilingBand(part), part.id());
			assertEquals(BodyState.HEALTHY, healthy.ceilingState(part), part.id());
			assertEquals(ScarLevel.NONE, healthy.scar(part), part.id());
			assertFalse(healthy.belowCeiling(part), part.id());
		}
	}

	@Test
	void theCeilingMaskDoesNotBleedBetweenParts() {
		for (BodyPart pinned : BodyPart.values()) {
			int mask = 3 << (pinned.ordinal() * 2);
			BodySnapshot snapshot = new BodySnapshot(
					new BodySnapshot.Regions(1f, 1f, 1f, 1f, 1f, 1f, 0, 0, 0, mask, 0),
					new BodySnapshot.Meters(0, 100f, 100f, 0, 0, 0));
			for (BodyPart part : BodyPart.values()) {
				assertEquals(part == pinned ? 3 : 0, snapshot.ceilingBand(part),
						pinned.id() + " pinned, reading " + part.id());
			}
			assertEquals(BodyState.CRITICAL, snapshot.ceilingState(pinned));
		}
	}

	/**
	 * The scar mask is read through {@code ScarMask}, which is unit-tested on its
	 * own — but the pairing of that packing with <em>this</em> record's component
	 * is what a consumer actually calls, and a mask read at the wrong offset would
	 * quietly hand every player their neighbour's history.
	 */
	@Test
	void scarIsPerPartWithoutBleedingIntoNeighbours() {
		for (BodyPart marked : BodyPart.values()) {
			BodySnapshot snapshot = new BodySnapshot(
					new BodySnapshot.Regions(1f, 1f, 1f, 1f, 1f, 1f, 0, 0, 0, 0,
							ScarMask.with(0, marked, ScarLevel.DESTROYED)),
					new BodySnapshot.Meters(0, 100f, 100f, 0, 0, 0));
			for (BodyPart part : BodyPart.values()) {
				assertEquals(part == marked ? ScarLevel.DESTROYED : ScarLevel.NONE, snapshot.scar(part),
						marked.id() + " marked, reading " + part.id());
			}
			// A mark is history, so none of the state the HUD reads may move with it.
			assertEquals(0, snapshot.brokenMask());
			assertEquals(0, snapshot.destroyedMask());
			assertEquals(0, snapshot.woundMask());
			assertEquals(0, snapshot.ceilingMask());
			assertFalse(snapshot.belowCeiling(marked), marked.id());
		}
	}

	@Test
	void belowCeilingReadsTheBandNotTheColour() {
		// With no ceiling, "hurt" means anything short of new.
		assertFalse(atFraction(0.76f, 0).belowCeiling(BodyPart.HEAD));
		assertTrue(atFraction(0.75f, 0).belowCeiling(BodyPart.HEAD));

		// With a band-1 ceiling, sitting exactly on the rung is the new baseline —
		// permanently Hurt, and the HUD is allowed to stop shouting about it.
		assertFalse(atFraction(0.75f, 1).belowCeiling(BodyPart.HEAD));
		assertTrue(atFraction(0.74f, 1).belowCeiling(BodyPart.HEAD));
		assertFalse(atFraction(1.0f, 1).belowCeiling(BodyPart.HEAD));
	}

	/** A snapshot whose HEAD sits at {@code fraction} under ceiling band {@code band}. */
	private static BodySnapshot atFraction(float fraction, int band) {
		return new BodySnapshot(
				new BodySnapshot.Regions(fraction, 1f, 1f, 1f, 1f, 1f, 0, 0, 0,
						band << (BodyPart.HEAD.ordinal() * 2), 0),
				new BodySnapshot.Meters(0, 100f, 100f, 0, 0, 0));
	}
}
