package dev.captience.realisticdamage;

import com.mojang.serialization.Codec;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.persist.RDCodecs;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		}
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
	void decodedConsciousnessRespectsHeadCap() {
		RealisticDamageData data = new RealisticDamageData();
		data.part(BodyPart.HEAD).setCondition(2.0); // CRITICAL head
		data.setConsciousness(100.0);

		Tag encoded = RDCodecs.DATA.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		RealisticDamageData decoded = RDCodecs.DATA.parse(NbtOps.INSTANCE, encoded).getOrThrow();
		assertTrue(decoded.consciousness() <= decoded.consciousnessCap());
	}
}
