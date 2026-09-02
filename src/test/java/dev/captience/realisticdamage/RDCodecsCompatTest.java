package dev.captience.realisticdamage;

import java.util.Optional;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.persist.RDCodecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blobs the codec did not write (1.6). {@code RDCodecsRoundTripTest} proves
 * {@code decode(encode(x)) == x} and should keep doing only that; this file's
 * whole job is to feed the codec a hand-built 1.5-shaped save and a hand-edited
 * one, because an existing world must open under 1.6 with nothing lost and
 * nothing loud.
 *
 * <p>Booleans are written {@code 1b}/{@code 0b} deliberately: {@code Codec.BOOL}
 * under {@code NbtOps} accepts any non-zero number, so {@code broken:1} would
 * also pass and would not be the shape 1.5 actually wrote.
 */
class RDCodecsCompatTest {
	private static final String F_PRISTINE = """
			{body:{head:{condition:20.0d},torso:{condition:20.0d},left_arm:{condition:13.0d},
			right_arm:{condition:13.0d},left_leg:{condition:16.0d},right_leg:{condition:16.0d}},
			blood:100.0d,consciousness:100.0d}""";

	private static final String F_INJURED = """
			{body:{head:{condition:3.5d},torso:{condition:20.0d},left_arm:{condition:0.0d,broken:1b},
			right_arm:{condition:0.0d,destroyed:1b},left_leg:{condition:16.0d},
			right_leg:{condition:7.25d,strain:80}},
			blood:37.5d,consciousness:12.0d,unconscious:1b,unconscious_ticks:31,concussion:1b,
			concussion_stable:120,burned:1b,
			wounds:[{part:"torso",severity:"severe"},{part:"right_leg",severity:"minor",decay:0.25d}]}""";

	private static CompoundTag snbt(String text) {
		try {
			return TagParser.parseCompoundFully(text);
		} catch (CommandSyntaxException e) {
			throw new AssertionError("bad fixture: " + e.getMessage(), e);
		}
	}

	private static RealisticDamageData decode(String text) {
		return RDCodecs.DATA.parse(NbtOps.INSTANCE, snbt(text)).getOrThrow();
	}

	@Test
	void pristine15SaveDecodesToPristineBody() {
		RealisticDamageData data = decode(F_PRISTINE);
		for (BodyPart part : BodyPart.values()) {
			assertEquals(part.maxCondition(), data.part(part).condition(), 1e-9, part.id());
			// Ceilings are never seeded from an old save: a permanent injury has to be
			// earned by an event the pipeline actually saw.
			assertEquals(0, data.part(part).ceilingBand(), part.id());
			assertEquals(BodyState.HEALTHY, data.part(part).state(), part.id());
			assertFalse(data.part(part).broken(), part.id());
			assertFalse(data.part(part).destroyed(), part.id());
			// Nothing to seed either: a body that was whole when it was saved has no
			// history the save could have recorded.
			assertEquals(ScarLevel.NONE, data.part(part).scar(), part.id());
		}
		assertEquals(100.0, data.blood());
		assertEquals(100.0, data.consciousness());
		assertTrue(data.wounds().isEmpty());
	}

	@Test
	void injured15SaveDecodesEveryLegacyFieldAndDefaultsTheNewOnes() {
		RealisticDamageData data = decode(F_INJURED);

		assertEquals(3.5, data.part(BodyPart.HEAD).condition(), 1e-9);
		assertTrue(data.part(BodyPart.LEFT_ARM).broken());
		assertTrue(data.part(BodyPart.RIGHT_ARM).destroyed());
		assertEquals(80, data.part(BodyPart.RIGHT_LEG).strainPauseTicks());
		assertEquals(7.25, data.part(BodyPart.RIGHT_LEG).condition(), 1e-9);
		assertEquals(37.5, data.blood());
		assertTrue(data.unconscious());
		assertEquals(31, data.unconsciousTicks());
		assertTrue(data.concussion());
		assertEquals(120, data.concussionStableTicks());
		assertTrue(data.burned());
		assertEquals(2, data.wounds().size());
		assertEquals(WoundSeverity.SEVERE, data.wounds().getFirst().severity());

		// A world that turns the hardcore mode on starts everybody whole.
		for (BodyPart part : BodyPart.values()) {
			assertEquals(0, data.part(part).ceilingBand(), part.id());
		}

		// Scars, by contrast, ARE seeded — and for free. toData replays the save
		// through setBroken and setDestroyed, and 1.6 raises the mark inside those
		// setters, so an existing world's currently-broken arm wakes up marked with
		// no migration code anywhere. What cannot be recovered is a limb that broke
		// in 1.5 and had already healed: 1.5 wrote nothing down, so that history
		// starts empty and there is no honest way to invent it.
		assertEquals(ScarLevel.BROKEN, data.part(BodyPart.LEFT_ARM).scar());
		assertEquals(ScarLevel.DESTROYED, data.part(BodyPart.RIGHT_ARM).scar());
		for (BodyPart part : BodyPart.values()) {
			if (part != BodyPart.LEFT_ARM && part != BodyPart.RIGHT_ARM) {
				assertEquals(ScarLevel.NONE, data.part(part).scar(), part.id());
			}
		}
	}

	@Test
	void savedConditionAboveItsCeilingClampsDown() {
		RealisticDamageData data = decode("""
				{body:{head:{condition:20.0d},torso:{condition:20.0d},left_arm:{condition:13.0d},
				right_arm:{condition:13.0d},left_leg:{condition:16.0d,ceiling:2},right_leg:{condition:16.0d}},
				blood:100.0d,consciousness:100.0d}""");

		// This is why the ceiling is applied before the condition in toData.
		assertEquals(8.0, data.part(BodyPart.LEFT_LEG).condition(), 1e-9);
		assertEquals(2, data.part(BodyPart.LEFT_LEG).ceilingBand());
		assertEquals(BodyState.INJURED, data.part(BodyPart.LEFT_LEG).state());
		assertEquals(0.0, data.part(BodyPart.LEFT_LEG).missing(), 1e-9);

		// And once clamped it stays put: re-encoding the loaded body is idempotent.
		CompoundTag written = (CompoundTag) RDCodecs.DATA.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		RealisticDamageData again = RDCodecs.DATA.parse(NbtOps.INSTANCE, written).getOrThrow();
		assertEquals(8.0, again.part(BodyPart.LEFT_LEG).condition(), 1e-9);
		assertEquals(2, again.part(BodyPart.LEFT_LEG).ceilingBand());
	}

	@Test
	void anIllegalValueIsSanitisedNotFatal() {
		assertEquals(0, decode(withLeftLeg("condition:16.0d,ceiling:99")).part(BodyPart.LEFT_LEG).ceilingBand());
		assertEquals(0, decode(withLeftLeg("condition:16.0d,ceiling:-1")).part(BodyPart.LEFT_LEG).ceilingBand());

		// A head asked for band 3 gets the core floor, not the band it asked for.
		RealisticDamageData deepHead = decode("""
				{body:{head:{condition:20.0d,ceiling:3},torso:{condition:20.0d},left_arm:{condition:13.0d},
				right_arm:{condition:13.0d},left_leg:{condition:16.0d},right_leg:{condition:16.0d}},
				blood:100.0d,consciousness:100.0d}""");
		assertEquals(1, deepHead.part(BodyPart.HEAD).ceilingBand());
		assertEquals(15.0, deepHead.part(BodyPart.HEAD).condition(), 1e-9);

		// The scar sanitiser goes the other way on purpose: out of range saturates
		// to the nearest end rather than becoming "no scar". A corrupt ceiling would
		// be a permanent injury nobody earned, so 0 is the safe answer there; a
		// corrupt scar is only a mark, so keeping one costs nothing.
		assertEquals(ScarLevel.DESTROYED,
				decode(withLeftLeg("condition:16.0d,scar:7")).part(BodyPart.LEFT_LEG).scar());
		assertEquals(ScarLevel.NONE,
				decode(withLeftLeg("condition:16.0d,scar:-3")).part(BodyPart.LEFT_LEG).scar());

		// A malformed value is an error WITH a partial, which Fabric's promotePartial
		// keeps: one WARN, the field at its default, and the body still loads. A
		// required field would instead drop the whole attachment.
		DataResult<RealisticDamageData> result =
				RDCodecs.DATA.parse(NbtOps.INSTANCE, snbt(withLeftLeg("condition:16.0d,ceiling:\"abc\"")));
		assertTrue(result.error().isPresent(), "a malformed ceiling should be reported");
		Optional<RealisticDamageData> partial = result.resultOrPartial();
		assertTrue(partial.isPresent(), "and it must still hand back a body");
		assertEquals(0, partial.get().part(BodyPart.LEFT_LEG).ceilingBand());
		assertEquals(16.0, partial.get().part(BodyPart.LEFT_LEG).condition(), 1e-9);
	}

	@Test
	void unknownKeysAreIgnoredAtBothLevels() {
		// Forward compatibility, both ways: a 1.7 key inside a part compound and a
		// 1.7 key at the top level are both simply not this record's business.
		RealisticDamageData data = decode("""
				{body:{head:{condition:20.0d},torso:{condition:20.0d},left_arm:{condition:13.0d},
				right_arm:{condition:13.0d},left_leg:{condition:16.0d,future_field:7},right_leg:{condition:16.0d}},
				blood:100.0d,consciousness:100.0d,future_meter:1.5d}""");
		assertEquals(16.0, data.part(BodyPart.LEFT_LEG).condition(), 1e-9);
		assertEquals(100.0, data.blood());
	}

	private static String withLeftLeg(String leftLegBody) {
		return "{body:{head:{condition:20.0d},torso:{condition:20.0d},left_arm:{condition:13.0d},"
				+ "right_arm:{condition:13.0d},left_leg:{" + leftLegBody + "},right_leg:{condition:16.0d}},"
				+ "blood:100.0d,consciousness:100.0d}";
	}
}
