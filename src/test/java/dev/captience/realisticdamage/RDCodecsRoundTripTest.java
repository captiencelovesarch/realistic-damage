package dev.captience.realisticdamage;

import com.mojang.serialization.Codec;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.persist.RDCodecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
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
		}
		assertEquals(2, decoded.part(BodyPart.LEFT_LEG).ceilingBand());
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
	void decodedConsciousnessRespectsHeadCap() {
		RealisticDamageData data = new RealisticDamageData();
		data.part(BodyPart.HEAD).setCondition(2.0); // CRITICAL head
		data.setConsciousness(100.0);

		Tag encoded = RDCodecs.DATA.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		RealisticDamageData decoded = RDCodecs.DATA.parse(NbtOps.INSTANCE, encoded).getOrThrow();
		assertTrue(decoded.consciousness() <= decoded.consciousnessCap());
	}
}
