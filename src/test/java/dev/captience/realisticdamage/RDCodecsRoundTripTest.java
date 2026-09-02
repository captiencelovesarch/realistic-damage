package dev.captience.realisticdamage;

import java.util.Optional;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.persist.RDCodecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistence round-trip (spec §46, §55) — runs under fabric-loader-junit. */
class RDCodecsRoundTripTest {
	@Test
	void fullStateSurvivesEncodeDecode() {
		RealisticDamageData data = new RealisticDamageData();
		data.part(BodyPart.HEAD).setCondition(3.5);
		data.part(BodyPart.LEFT_ARM).setCondition(0.0);
		data.part(BodyPart.LEFT_ARM).setBroken(true);
		data.part(BodyPart.RIGHT_LEG).setCondition(7.25);
		data.part(BodyPart.RIGHT_LEG).strain(80);
		// A ceiling on a part that is otherwise untouched, so the band has to survive
		// as its own field rather than being re-derived from the condition.
		data.part(BodyPart.LEFT_LEG).lowerCeilingTo(2);
		// A DESTROYED scar on a part that is emphatically NOT destroyed: if the
		// scar were ever re-derived from broken/destroyed on load rather than
		// stored, this is the case that would come back wrong.
		data.part(BodyPart.TORSO).setScar(ScarLevel.DESTROYED);
		data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
		data.addWound(BodyPart.RIGHT_LEG, WoundSeverity.MINOR);
		data.setBlood(37.5);
		data.setConcussion(true);
		data.setBurned(true);
		data.setUnconscious(true);
		data.setUnconsciousTicks(31);
		data.setConsciousness(12.0);

		Codec<RealisticDamageData> codec = RDCodecs.DATA;
		Tag encoded = codec.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		RealisticDamageData decoded = codec.parse(NbtOps.INSTANCE, encoded).getOrThrow();

		for (BodyPart part : BodyPart.values()) {
			assertEquals(data.part(part).condition(), decoded.part(part).condition(), 1e-9, part.id());
			assertEquals(data.part(part).broken(), decoded.part(part).broken(), part.id() + " broken");
			assertEquals(data.part(part).strainPauseTicks(), decoded.part(part).strainPauseTicks(), part.id() + " strain");
			assertEquals(data.part(part).ceilingBand(), decoded.part(part).ceilingBand(), part.id() + " ceiling");
			assertEquals(data.part(part).scar(), decoded.part(part).scar(), part.id() + " scar");
		}
		assertEquals(2, decoded.part(BodyPart.LEFT_LEG).ceilingBand());
		assertEquals(ScarLevel.DESTROYED, decoded.part(BodyPart.TORSO).scar());
		assertFalse(decoded.part(BodyPart.TORSO).destroyed(), "the scar is its own field, not a summary");
		// The broken arm's own scar comes from the setter replay, unprompted.
		assertEquals(ScarLevel.BROKEN, decoded.part(BodyPart.LEFT_ARM).scar());
		assertEquals(37.5, decoded.blood());
		assertEquals(12.0, decoded.consciousness());
		assertTrue(decoded.unconscious());
		assertEquals(31, decoded.unconsciousTicks());
		assertTrue(decoded.concussion());
		assertTrue(decoded.burned());
		assertEquals(2, decoded.wounds().size());
		assertEquals(data.effectiveBleedRate(), decoded.effectiveBleedRate(), 1e-9);
	}

	@Test
	void aPristineBodyWritesNoCeilingKey() {
		// optionalFieldOf with a fixed default means DFU omits the key when the value
		// is the default, so a pristine 1.6 save is byte-identical to a pristine 1.5
		// one. This is the tripwire that keeps a future fieldOf out.
		CompoundTag encoded = (CompoundTag) RDCodecs.DATA
				.encodeStart(NbtOps.INSTANCE, new RealisticDamageData()).getOrThrow();
		CompoundTag body = encoded.getCompoundOrEmpty("body");
		for (BodyPart part : BodyPart.values()) {
			CompoundTag saved = body.getCompoundOrEmpty(part.id());
			assertTrue(saved.contains("condition"), part.id() + " must still write its condition");
			assertFalse(saved.contains("ceiling"), part.id() + " wrote a ceiling key it does not need");
		}

		RealisticDamageData capped = new RealisticDamageData();
		capped.part(BodyPart.LEFT_LEG).lowerCeilingTo(2);
		CompoundTag cappedTag = (CompoundTag) RDCodecs.DATA
				.encodeStart(NbtOps.INSTANCE, capped).getOrThrow();
		assertEquals(2, cappedTag.getCompoundOrEmpty("body").getCompoundOrEmpty("left_leg")
				.getIntOr("ceiling", 0));
	}

	@Test
	void scarKeyIsOmittedWhenNone() {
		// Same tripwire as the ceiling one above, for the same reason: the mod's
		// promise is that opening a 1.5 world under 1.6 and saving it again changes
		// nothing on disk.
		CompoundTag encoded = (CompoundTag) RDCodecs.DATA
				.encodeStart(NbtOps.INSTANCE, new RealisticDamageData()).getOrThrow();
		CompoundTag body = encoded.getCompoundOrEmpty("body");
		for (BodyPart part : BodyPart.values()) {
			assertFalse(body.getCompoundOrEmpty(part.id()).contains("scar"),
					part.id() + " wrote a scar key it does not need");
		}

		RealisticDamageData marked = new RealisticDamageData();
		marked.part(BodyPart.LEFT_ARM).markScar(ScarLevel.BROKEN);
		CompoundTag markedTag = (CompoundTag) RDCodecs.DATA
				.encodeStart(NbtOps.INSTANCE, marked).getOrThrow();
		assertEquals(1, markedTag.getCompoundOrEmpty("body").getCompoundOrEmpty("left_arm")
				.getIntOr("scar", 0));
	}

	@Test
	void outOfRangeScarIsClampedNotFatal() {
		// byOrdinal saturates rather than zeroing, unlike the ceiling sanitiser: a
		// scar changes no outcome, so keeping the mark costs nothing and losing the
		// world would cost everything.
		assertEquals(ScarLevel.DESTROYED, decodeHead("condition:20.0d,scar:7").part(BodyPart.HEAD).scar());
		assertEquals(ScarLevel.NONE, decodeHead("condition:20.0d,scar:-3").part(BodyPart.HEAD).scar());

		DataResult<RealisticDamageData> result = RDCodecs.DATA.parse(NbtOps.INSTANCE,
				snbt(headBody("condition:20.0d,scar:\"abc\"")));
		assertTrue(result.error().isPresent(), "a malformed scar should be reported");
		Optional<RealisticDamageData> partial = result.resultOrPartial();
		assertTrue(partial.isPresent(), "and it must still hand back a body");
		assertEquals(ScarLevel.NONE, partial.get().part(BodyPart.HEAD).scar());
		assertEquals(20.0, partial.get().part(BodyPart.HEAD).condition(), 1e-9);
	}

	private static String headBody(String headBody) {
		return "{body:{head:{" + headBody + "},torso:{condition:20.0d},left_arm:{condition:13.0d},"
				+ "right_arm:{condition:13.0d},left_leg:{condition:16.0d},right_leg:{condition:16.0d}},"
				+ "blood:100.0d,consciousness:100.0d}";
	}

	private static CompoundTag snbt(String text) {
		try {
			return TagParser.parseCompoundFully(text);
		} catch (CommandSyntaxException e) {
			throw new AssertionError("bad fixture: " + e.getMessage(), e);
		}
	}

	private static RealisticDamageData decodeHead(String headBody) {
		return RDCodecs.DATA.parse(NbtOps.INSTANCE, snbt(headBody(headBody))).getOrThrow();
	}

	@Test
	void decodedConsciousnessRespectsHeadCap() {
		RealisticDamageData data = new RealisticDamageData();
		data.part(BodyPart.HEAD).setCondition(2.0); // CRITICAL head
		data.setConsciousness(100.0);

		Tag encoded = RDCodecs.DATA.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		RealisticDamageData decoded = RDCodecs.DATA.parse(NbtOps.INSTANCE, encoded).getOrThrow();
		assertTrue(decoded.consciousness() <= decoded.consciousnessCap());
	}
}
