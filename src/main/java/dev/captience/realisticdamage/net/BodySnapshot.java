package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, quantized presentation snapshot synced to the owning client via
 * the attachment sync layer. Quantization (condition to 1%, vitals to 0.5)
 * doubles as rate limiting: equal snapshots are never re-sent (spec §47, §56).
 *
 * <p>Blood and consciousness are hidden from ordinary gameplay UI (spec §42) —
 * the client uses them only to drive effects, never to display numbers.
 */
public record BodySnapshot(
		float head, float torso, float leftArm, float rightArm, float leftLeg, float rightLeg,
		int brokenMask, int woundMask, int statusFlags,
		float blood, float consciousness
) {
	public static final int FLAG_UNCONSCIOUS = 1;
	public static final int FLAG_CONCUSSION = 2;
	public static final int FLAG_BURNED = 4;

	public static final StreamCodec<RegistryFriendlyByteBuf, BodySnapshot> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, BodySnapshot::head,
			ByteBufCodecs.FLOAT, BodySnapshot::torso,
			ByteBufCodecs.FLOAT, BodySnapshot::leftArm,
			ByteBufCodecs.FLOAT, BodySnapshot::rightArm,
			ByteBufCodecs.FLOAT, BodySnapshot::leftLeg,
			ByteBufCodecs.FLOAT, BodySnapshot::rightLeg,
			ByteBufCodecs.VAR_INT, BodySnapshot::brokenMask,
			ByteBufCodecs.VAR_INT, BodySnapshot::woundMask,
			ByteBufCodecs.VAR_INT, BodySnapshot::statusFlags,
			ByteBufCodecs.FLOAT, BodySnapshot::blood,
			ByteBufCodecs.FLOAT, BodySnapshot::consciousness,
			BodySnapshot::new);

	public static BodySnapshot of(RealisticDamageData data) {
		int broken = 0;
		int wounds = 0;
		for (BodyPart part : BodyPart.values()) {
			if (data.part(part).broken()) {
				broken |= 1 << part.ordinal();
			}
			WoundSeverity worst = null;
			for (BleedWound wound : data.wounds()) {
				if (wound.part() == part && (worst == null || wound.severity().ordinal() > worst.ordinal())) {
					worst = wound.severity();
				}
			}
			if (worst != null) {
				wounds |= (worst.ordinal() + 1) << (part.ordinal() * 3);
			}
		}
		int flags = (data.unconscious() ? FLAG_UNCONSCIOUS : 0)
				| (data.concussion() ? FLAG_CONCUSSION : 0)
				| (data.burned() ? FLAG_BURNED : 0);
		return new BodySnapshot(
				quantizeFraction(data.part(BodyPart.HEAD).fraction()),
				quantizeFraction(data.part(BodyPart.TORSO).fraction()),
				quantizeFraction(data.part(BodyPart.LEFT_ARM).fraction()),
				quantizeFraction(data.part(BodyPart.RIGHT_ARM).fraction()),
				quantizeFraction(data.part(BodyPart.LEFT_LEG).fraction()),
				quantizeFraction(data.part(BodyPart.RIGHT_LEG).fraction()),
				broken, wounds, flags,
				quantizeVital(data.blood()),
				quantizeVital(data.consciousness()));
	}

	private static float quantizeFraction(double fraction) {
		return Math.round(fraction * 100.0) / 100.0f;
	}

	private static float quantizeVital(double value) {
		return Math.round(value * 2.0) / 2.0f;
	}

	public float fraction(BodyPart part) {
		return switch (part) {
			case HEAD -> head;
			case TORSO -> torso;
			case LEFT_ARM -> leftArm;
			case RIGHT_ARM -> rightArm;
			case LEFT_LEG -> leftLeg;
			case RIGHT_LEG -> rightLeg;
		};
	}

	public BodyState state(BodyPart part) {
		return BodyState.fromFraction(fraction(part));
	}

	public boolean broken(BodyPart part) {
		return (brokenMask & (1 << part.ordinal())) != 0;
	}

	@Nullable
	public WoundSeverity wound(BodyPart part) {
		int bits = (woundMask >> (part.ordinal() * 3)) & 0b111;
		return bits == 0 ? null : WoundSeverity.values()[bits - 1];
	}

	public boolean unconscious() {
		return (statusFlags & FLAG_UNCONSCIOUS) != 0;
	}

	public boolean concussion() {
		return (statusFlags & FLAG_CONCUSSION) != 0;
	}

	public boolean burned() {
		return (statusFlags & FLAG_BURNED) != 0;
	}

	public static BodySnapshot healthy() {
		return new BodySnapshot(1f, 1f, 1f, 1f, 1f, 1f, 0, 0, 0, 100f, 100f);
	}
}
