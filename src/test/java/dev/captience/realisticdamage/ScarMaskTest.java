package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.ScarMask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The 2-bits-per-region packing (1.6). The shift is written once, in
 * {@code ScarMask}, precisely so it can be pinned here rather than trusted in
 * three separate copies — the snapshot, the accessor and the render layer.
 */
class ScarMaskTest {
	@Test
	void everyPartRoundTripsWithoutBleeding() {
		for (BodyPart part : BodyPart.values()) {
			for (ScarLevel level : ScarLevel.values()) {
				int mask = ScarMask.with(0, part, level);
				assertEquals(level, ScarMask.level(mask, part), part.id() + " " + level);
				for (BodyPart other : BodyPart.values()) {
					if (other != part) {
						assertEquals(ScarLevel.NONE, ScarMask.level(mask, other),
								"marking the " + part.id() + " also marked the " + other.id());
					}
				}
			}
		}

		// with() replaces rather than accumulates, including downward: it is what
		// the debug command writes through, and the debug command may erase.
		int both = ScarMask.with(ScarMask.with(0, BodyPart.LEFT_ARM, ScarLevel.DESTROYED),
				BodyPart.RIGHT_LEG, ScarLevel.BROKEN);
		assertEquals(ScarLevel.DESTROYED, ScarMask.level(both, BodyPart.LEFT_ARM));
		assertEquals(ScarLevel.BROKEN, ScarMask.level(both, BodyPart.RIGHT_LEG));
		assertEquals(ScarLevel.NONE,
				ScarMask.level(ScarMask.with(both, BodyPart.LEFT_ARM, ScarLevel.NONE), BodyPart.LEFT_ARM));

		// RIGHT_LEG is ordinal 5, so its pair is the top one in use: DESTROYED there
		// is 2 << 10. Written out as a literal because a shift that has drifted by
		// one part would still round-trip through level().
		assertEquals(2 << 10, ScarMask.with(0, BodyPart.RIGHT_LEG, ScarLevel.DESTROYED));
	}

	@Test
	void pristineBodyPacksToZero() {
		assertEquals(0, ScarMask.pack(new RealisticDamageData()));
	}

	@Test
	void allLostIsTheLargestMask() {
		RealisticDamageData data = new RealisticDamageData();
		for (BodyPart part : BodyPart.values()) {
			data.part(part).markScar(ScarLevel.DESTROYED);
		}
		// Six DESTROYED, two bits each: 0b10 repeated six times.
		assertEquals(0xAAA, ScarMask.pack(data));

		// And the packing is what the accessor reads back, per part.
		int mask = ScarMask.pack(data);
		for (BodyPart part : BodyPart.values()) {
			assertEquals(ScarLevel.DESTROYED, ScarMask.level(mask, part), part.id());
		}
	}
}
