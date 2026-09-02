package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.systems.CeilingMath;
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
 *
 * <p>Two nested records rather than one flat one (1.6). {@code StreamCodec.composite}
 * stops at twelve pairs and the record now needs fourteen, but the reason to
 * group rather than hand-write the buffer is that two features are appending to
 * the same record: a hand-written write/read pair that forgets a field compiles
 * and corrupts silently, while grouped composites are arity-checked per group
 * and derive encode and decode order from one pair list, so the two halves can
 * never disagree. The residual risk — pair order versus constructor order among
 * adjacent same-typed ints — is exactly what
 * {@code BodySnapshotCodecTest.everyFieldSurvivesWithADistinctValue} is for.
 *
 * <p>Every flat accessor below is preserved, so no consumer of this record had
 * to change shape when it was split.
 */
public record BodySnapshot(Regions regions, Meters meters) {

	/**
	 * Everything that is per-body-region. Eleven pairs, one slot free before the
	 * next group has to be split off.
	 *
	 * <p>The four 1.5 masks keep their exact bit layouts, bit for bit — the HUD,
	 * the rescue input, the avatar renderer and the multiplayer gametest all
	 * assert them whole — which is precisely why the two 1.6 values got their own
	 * ints rather than spare bits in an existing one.
	 */
	public record Regions(float head, float torso, float leftArm, float rightArm, float leftLeg, float rightLeg,
	                      int brokenMask, int woundMask, int destroyedMask, int ceilingMask, int scarMask) {

		public static final StreamCodec<RegistryFriendlyByteBuf, Regions> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, Regions::head,
				ByteBufCodecs.FLOAT, Regions::torso,
				ByteBufCodecs.FLOAT, Regions::leftArm,
				ByteBufCodecs.FLOAT, Regions::rightArm,
				ByteBufCodecs.FLOAT, Regions::leftLeg,
				ByteBufCodecs.FLOAT, Regions::rightLeg,
				ByteBufCodecs.VAR_INT, Regions::brokenMask,
				ByteBufCodecs.VAR_INT, Regions::woundMask,
				ByteBufCodecs.VAR_INT, Regions::destroyedMask,
				ByteBufCodecs.VAR_INT, Regions::ceilingMask,
				ByteBufCodecs.VAR_INT, Regions::scarMask,
				Regions::new);
	}

	/**
	 * Everything that is whole-body. Three pairs, nine slots free: any future
	 * per-player mode or legend int goes HERE, never as a spare bit of
	 * {@code statusFlags}.
	 *
	 * <p>Not called {@code Vitals} — {@code body.Vitals} already exists and is
	 * imported alongside this record in three client files.
	 */
	public record Meters(int statusFlags, float blood, float consciousness) {

		public static final StreamCodec<RegistryFriendlyByteBuf, Meters> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Meters::statusFlags,
				ByteBufCodecs.FLOAT, Meters::blood,
				ByteBufCodecs.FLOAT, Meters::consciousness,
				Meters::new);
	}

	public static final int FLAG_UNCONSCIOUS = 1;
	public static final int FLAG_CONCUSSION = 2;
	public static final int FLAG_BURNED = 4;
	/** Face-down after a hard landing (1.4). */
	public static final int FLAG_KNOCKED_DOWN = 8;
	/** Lights out for a moment (1.4). */
	public static final int FLAG_MICROSLEEP = 16;
	/**
	 * A hand is being held over the wound (1.5). Synced because blood is now drawn
	 * client-side from this snapshot: without it, a wound being compressed would
	 * carry on visibly pumping while the number behind it had already slowed, and
	 * the one piece of first aid in the mod would look like it did nothing.
	 */
	public static final int FLAG_PRESSURE = 32;

	public static final StreamCodec<RegistryFriendlyByteBuf, BodySnapshot> STREAM_CODEC = StreamCodec.composite(
			Regions.STREAM_CODEC, BodySnapshot::regions,
			Meters.STREAM_CODEC, BodySnapshot::meters,
			BodySnapshot::new);

	public static BodySnapshot of(RealisticDamageData data) {
		int broken = 0;
		int wounds = 0;
		int destroyed = 0;
		int ceilings = 0;
		for (BodyPart part : BodyPart.values()) {
			if (data.part(part).broken()) {
				broken |= 1 << part.ordinal();
			}
			if (data.part(part).destroyed()) {
				destroyed |= 1 << part.ordinal();
			}
			// Destroyed parts send no band: a crossed-out region already says
			// everything, and the band underneath only matters to the next body.
			if (!data.part(part).destroyed()) {
				ceilings |= data.part(part).ceilingBand() << (part.ordinal() * 2);
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
		// Scars are feature 3's; the slot ships now so the wire shape changes once.
		int scars = 0;
		int flags = (data.unconscious() ? FLAG_UNCONSCIOUS : 0)
				| (data.concussion() ? FLAG_CONCUSSION : 0)
				| (data.burned() ? FLAG_BURNED : 0)
				| (data.knockedDown() ? FLAG_KNOCKED_DOWN : 0)
				| (data.microsleeping() ? FLAG_MICROSLEEP : 0)
				| (data.underPressure() ? FLAG_PRESSURE : 0);
		return new BodySnapshot(
				new Regions(
						quantizeFraction(data.part(BodyPart.HEAD).fraction()),
						quantizeFraction(data.part(BodyPart.TORSO).fraction()),
						quantizeFraction(data.part(BodyPart.LEFT_ARM).fraction()),
						quantizeFraction(data.part(BodyPart.RIGHT_ARM).fraction()),
						quantizeFraction(data.part(BodyPart.LEFT_LEG).fraction()),
						quantizeFraction(data.part(BodyPart.RIGHT_LEG).fraction()),
						broken, wounds, destroyed, ceilings, scars),
				new Meters(flags, quantizeVital(data.blood()), quantizeVital(data.consciousness())));
	}

	private static float quantizeFraction(double fraction) {
		return Math.round(fraction * 100.0) / 100.0f;
	}

	private static float quantizeVital(double value) {
		return Math.round(value * 2.0) / 2.0f;
	}

	public float head() {
		return regions.head();
	}

	public float torso() {
		return regions.torso();
	}

	public float leftArm() {
		return regions.leftArm();
	}

	public float rightArm() {
		return regions.rightArm();
	}

	public float leftLeg() {
		return regions.leftLeg();
	}

	public float rightLeg() {
		return regions.rightLeg();
	}

	public int brokenMask() {
		return regions.brokenMask();
	}

	public int woundMask() {
		return regions.woundMask();
	}

	public int destroyedMask() {
		return regions.destroyedMask();
	}

	public int ceilingMask() {
		return regions.ceilingMask();
	}

	/** History, not state (1.6 slot, filled by scarring): never consulted by hurt(), the icon row or the caption. */
	public int scarMask() {
		return regions.scarMask();
	}

	public int statusFlags() {
		return meters.statusFlags();
	}

	public float blood() {
		return meters.blood();
	}

	public float consciousness() {
		return meters.consciousness();
	}

	public float fraction(BodyPart part) {
		return switch (part) {
			case HEAD -> head();
			case TORSO -> torso();
			case LEFT_ARM -> leftArm();
			case RIGHT_ARM -> rightArm();
			case LEFT_LEG -> leftLeg();
			case RIGHT_LEG -> rightLeg();
		};
	}

	public BodyState state(BodyPart part) {
		return destroyed(part) ? BodyState.DESTROYED : BodyState.fromFraction(fraction(part));
	}

	public boolean broken(BodyPart part) {
		return (brokenMask() & (1 << part.ordinal())) != 0;
	}

	public boolean destroyed(BodyPart part) {
		return (destroyedMask() & (1 << part.ordinal())) != 0;
	}

	/** 0 none, 1 pinned at Hurt, 2 at Injured, 3 at Critical (1.6). Bands, never figures. */
	public int ceilingBand(BodyPart part) {
		return (ceilingMask() >> (part.ordinal() * 2)) & 0b11;
	}

	/**
	 * The state word a band pins the part at. The band-to-ordinal mapping is exact
	 * (0 HEALTHY, 1 HURT, 2 INJURED, 3 CRITICAL) and is asserted in
	 * {@code CeilingMathTest}, never assumed here.
	 */
	public BodyState ceilingState(BodyPart part) {
		return BodyState.values()[ceilingBand(part)];
	}

	/**
	 * Hurt means "below what it can still reach", not "below new": a capped body
	 * sitting at its ceilings is a baseline, not an alarm, and the HUD may fade on it.
	 */
	public boolean belowCeiling(BodyPart part) {
		int band = ceilingBand(part);
		return band == 0 ? state(part) != BodyState.HEALTHY
				: fraction(part) < (float) CeilingMath.fraction(band);
	}

	@Nullable
	public WoundSeverity wound(BodyPart part) {
		int bits = (woundMask() >> (part.ordinal() * 3)) & 0b111;
		return bits == 0 ? null : WoundSeverity.values()[bits - 1];
	}

	public boolean unconscious() {
		return (statusFlags() & FLAG_UNCONSCIOUS) != 0;
	}

	public boolean concussion() {
		return (statusFlags() & FLAG_CONCUSSION) != 0;
	}

	public boolean burned() {
		return (statusFlags() & FLAG_BURNED) != 0;
	}

	public boolean knockedDown() {
		return (statusFlags() & FLAG_KNOCKED_DOWN) != 0;
	}

	public boolean microsleeping() {
		return (statusFlags() & FLAG_MICROSLEEP) != 0;
	}

	public boolean underPressure() {
		return (statusFlags() & FLAG_PRESSURE) != 0;
	}

	/**
	 * The attachment initializer, and what a fresh or respawned client sees. It
	 * must stay the all-zero point for every mask: a body that has never been hurt
	 * is not below any ceiling and carries no marks.
	 */
	public static BodySnapshot healthy() {
		return new BodySnapshot(new Regions(1f, 1f, 1f, 1f, 1f, 1f, 0, 0, 0, 0, 0), new Meters(0, 100f, 100f));
	}
}
