package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;

/**
 * What a limp is (1.6). One definition, read from both ends.
 *
 * <p>The roadmap states the last feature of this release as hostiles preferring
 * targets that are <em>visibly limping</em>, and the word only means anything if
 * the two halves agree: the pose a player sees on the model and the score a mob
 * reads off a body have to be the same statement about the same leg. Two
 * definitions would not disagree on the day they were written — they would
 * disagree six months later, when one of them was retuned and the other was
 * not, and the symptom would be a mob singling out a player who looks fine.
 * So the ladder lives here, once, in Minecraft-free arithmetic: the client's
 * {@code InjuryPose} turns a rung into an angle, and {@code PreyMath} turns the
 * same rung into a score, and neither of them owns it.
 *
 * <h2>The ladder</h2>
 *
 * <p>Five rungs, worst last: {@link #RUNG_SOUND}, {@link #RUNG_FAVOURED},
 * {@link #RUNG_RUINED}, {@link #RUNG_FRACTURED}, {@link #RUNG_LOST}. A break
 * outranks any amount of wear that did not break anything, which looks odd next
 * to the impairment curves — a Disabled leg costs 0.55 of your speed and a
 * broken Critical one 0.38 — and is right anyway, because this ladder is about
 * what can be <em>seen</em>. A fracture is a discrete event with a sound every
 * player in earshot heard and a bone that no longer takes weight; a leg worn
 * down without breaking is a leg that still works badly. The band rung exists
 * for exactly the damage that never fractures anything — fire, lava, systemic
 * bleed-out — which the ordinary combat paths skip, since slash, puncture,
 * blunt, blast and fall all set Broken once a limb reaches Critical.
 *
 * <p>The band cut is Critical, not Injured: an Injured leg costs between 5% and
 * 13% of your speed and the bottom of that band is indistinguishable from a
 * Hurt one, so a mob preferring it would read as unexplained aggro and a model
 * posing it would read as a twitch. Everything that scores here is something an
 * observer can point at. This depends on {@code fraction()} staying
 * {@code condition / maxCondition} — measuring against a long-term ceiling
 * instead would make a capped leg read Sound and delete the whole feature for
 * exactly the players it was written for.
 *
 * <p>One honest limit on "the same statement", worth writing down rather than
 * discovering: the server scores from the live condition and the client poses
 * from {@code BodySnapshot}, which quantizes each region's fraction to two
 * decimals before it goes on the wire. So a leg sitting within a hundredth of
 * the Critical boundary can be posed on one rung and scored on the one below.
 * That is the snapshot's existing bargain — bands, never figures — and the
 * panel and the HUD have been reading the same rounded number since 1.0, so the
 * model agrees with every other surface the player can see and disagrees with
 * the server only across a sliver of one band. Rounding the server's own input
 * to match would be the wrong repair: it would put a display concern inside a
 * gameplay decision to buy back half a percent of a limb.
 *
 * <p>And the disagreement only ever runs one way, which is why it is a caveat
 * and not a hole in the promise. Every band cut is an exact multiple of 0.01
 * (0.75, 0.50, 0.25, 0.0, each of them binary-exact as a float), and the
 * snapshot rounds to the nearest hundredth, so a fraction can only be quantized
 * <em>above</em> a cut {@code c} if it was already above it: {@code round(f) >
 * c} needs {@code f >= c + 0.005}. The client's rung is therefore never lower
 * than the server's. A player can look very slightly worse than the mobs treat
 * them; a player the mobs single out is always one the model is already posing.
 * That is the direction that matters — the roadmap promises hostiles prefer
 * targets who are <em>visibly</em> limping, and the failure that would break it
 * is a mob hunting somebody who looks fine, which this rounding cannot produce.
 * Anyone changing {@code quantizeFraction} or moving a cut off a hundredth
 * boundary is changing that guarantee.
 *
 * <p>{@link #RUNG_LOST} is the one rung with no pose. A limb that is gone is
 * hidden on the model, so there is no surface to angle and
 * {@link #poseWeight(int)} answers zero — the absence is the cue, and it is a
 * louder one than any pose. The prey score still ranks it highest, which is not
 * an inconsistency: the mob is reading the same body, and a missing leg is the
 * most visible thing on it.
 */
public final class LimpMath {
	private LimpMath() {}

	/** Nothing to see. */
	public static final int RUNG_SOUND = 0;
	/** Critical, unbroken: the limb still works, and it is being spared. */
	public static final int RUNG_FAVOURED = 1;
	/** Disabled, unbroken: worn down until it does nothing useful. */
	public static final int RUNG_RUINED = 2;
	/** Broken. A bone that does not take weight, whatever the condition figure says. */
	public static final int RUNG_FRACTURED = 3;
	/** Gone. Not posed — see the class javadoc. */
	public static final int RUNG_LOST = 4;

	/**
	 * The worst rung that still has a limb to pose. Also the denominator of
	 * {@link #poseWeight}, so the pose reaches full strength exactly at a break
	 * and the two rungs below it are honest fractions of it rather than three
	 * numbers somebody picked.
	 */
	public static final int RUNG_POSED_MAX = RUNG_FRACTURED;

	/**
	 * Visible wear on a limb that has not broken: Critical 1, Disabled 2, and
	 * Healthy, Hurt and Injured all 0.
	 *
	 * <p>Bands rather than the continuous impairment penalty, because bands are
	 * the mod's idiom everywhere else and because a second continuous definition
	 * of "how bad" is a second thing to keep in step with the first.
	 */
	public static int limpBand(double fraction) {
		return switch (BodyState.fromFraction(fraction)) {
			case HEALTHY, HURT, INJURED -> RUNG_SOUND;
			case CRITICAL -> RUNG_FAVOURED;
			default -> RUNG_RUINED;   // DISABLED; fromFraction never yields DESTROYED
		};
	}

	/**
	 * One limb's rung. Total: every combination of the three inputs lands
	 * somewhere, including the ones the body cannot currently produce.
	 *
	 * @param destroyed the limb is gone
	 * @param broken    the bone is broken right now — not the scar it leaves
	 * @param fraction  {@code condition / maxCondition}, never against a ceiling
	 */
	public static int rung(boolean destroyed, boolean broken, double fraction) {
		if (destroyed) {
			return RUNG_LOST;
		}
		if (broken) {
			return RUNG_FRACTURED;
		}
		return limpBand(fraction);
	}

	/**
	 * The worse of two limbs, which is what a whole body limps at.
	 *
	 * <p>Plain {@code max}, and that is the load-bearing claim: the prey score
	 * says "either leg destroyed, else either leg broken, else the worse leg's
	 * band", which is a different sentence and the same function, because the
	 * ladder is ordered. Stated here so the two can never be written twice.
	 */
	public static int worse(int leftRung, int rightRung) {
		return Math.max(leftRung, rightRung);
	}

	/**
	 * Whether a limb says anything at all — the literal reading of the roadmap's
	 * "visibly". Exactly the rungs that pose, plus the one that is invisible by
	 * being absent, and exactly the rungs a mob scores above zero.
	 */
	public static boolean visible(int rung) {
		return rung >= RUNG_FAVOURED;
	}

	/**
	 * How strongly a rung is posed, as a share of the full pose: 0 for a sound
	 * limb, 1/3, 2/3, and 1 at a break. Zero again at {@link #RUNG_LOST}, which
	 * is not a weaker pose but no pose at all.
	 */
	public static float poseWeight(int rung) {
		if (rung <= RUNG_SOUND || rung >= RUNG_LOST) {
			return 0.0F;
		}
		return (float) rung / (float) RUNG_POSED_MAX;
	}

	// --- The pose, in radians. Presentation constants, so they live beside the
	// definition they scale rather than in Balance: nothing here changes an
	// outcome, and Balance is where outcomes are tuned.

	/**
	 * Share of a leg's swing taken away at a break. A broken leg takes a step
	 * two fifths as long as the sound one (1 − 0.60), and that asymmetry between
	 * the two legs is the thing an eye actually reads as a limp — not the angle
	 * of either leg on its own. The rungs below scale with the weight: a Ruined
	 * leg loses 0.40 of its step (0.60 × 2/3) and a Favoured one 0.20, which is
	 * a favouring rather than a drag.
	 */
	public static final float LEG_SWING_DAMP = 0.60F;
	/**
	 * How far the bad leg turns out, in radians — 0.10 is 5.7°. Vanilla's own
	 * duck-walk splay is 0.005 (HumanoidModel.DUCK_WALK_ROTATION), so this is
	 * twenty times it and cannot be mistaken for it.
	 */
	public static final float LEG_TURNOUT = 0.10F;
	/** And it hangs back a little: 0.06 rad, 3.4°, behind the leg carrying you. */
	public static final float LEG_TRAIL = 0.06F;
	/**
	 * A broken arm is carried, not swung: rolled in against the ribs by 0.30 rad
	 * (17°). Vanilla's idle bob adds at most 0.10 to an arm's zRot
	 * (AnimationUtils.bobModelPart, cos·0.05 + 0.05) and pushes it <em>outward</em>,
	 * so three times that, inward, is not a bob at any point in its cycle.
	 */
	public static final float ARM_CRADLE_ROLL = 0.30F;
	/**
	 * And held forward, 0.22 rad (12.6°). Vanilla raises both arms by π/5 =
	 * 0.628 to hold a mount's reins; a third of that is an arm kept close, not
	 * an arm presented.
	 */
	public static final float ARM_CRADLE_LIFT = 0.22F;

	/**
	 * What to multiply a leg's swing by. 1.0 leaves vanilla's own step exactly
	 * as it wrote it, which is what a sound leg and a lost one both get.
	 *
	 * <p>A multiplier rather than an offset to subtract, deliberately. The
	 * arithmetic is the same either way, but the caller applies this to the
	 * value vanilla has already written into the part, so it cannot drift when
	 * vanilla retunes the 1.4 amplitude or the 0.6662 frequency, and it inherits
	 * the swim lerp on the same field for free. A recomputed term would also
	 * have to reproduce {@code Mth.cos}, which is a lookup table and not
	 * {@code Math.cos}, and a residue that small is exactly the kind of thing
	 * nobody would ever find.
	 */
	public static float legSwingScale(int rung) {
		return 1.0F - LEG_SWING_DAMP * poseWeight(rung);
	}

	/**
	 * The bad leg's turnout, signed for the side it is on. Positive zRot swings
	 * a right leg outward and a left leg inward — vanilla's own splay is +0.005
	 * on the right and −0.005 on the left — so the two sides are exact mirrors
	 * and a left limp can never be the same rotation as a right one.
	 */
	public static float legTurnout(int rung, boolean left) {
		return scaled(left ? -LEG_TURNOUT : LEG_TURNOUT, rung);
	}

	/** How far the bad leg hangs back. Positive xRot is backward. */
	public static float legTrail(int rung) {
		return scaled(LEG_TRAIL, rung);
	}

	/**
	 * The roll that pulls a bad arm in against the ribs, signed for its side.
	 * Inward is the opposite sign from the leg's turnout, because one is a limb
	 * being spared by holding it close and the other is a limb being spared by
	 * putting it out of the way.
	 */
	public static float armCradleRoll(int rung, boolean left) {
		return scaled(left ? ARM_CRADLE_ROLL : -ARM_CRADLE_ROLL, rung);
	}

	/** How far forward a bad arm is held. Negative xRot is forward. */
	public static float armCradleLift(int rung) {
		return scaled(-ARM_CRADLE_LIFT, rung);
	}

	/**
	 * An angle scaled by the rung's weight — and exactly zero, not minus zero,
	 * on the two rungs that say nothing. The sign of zero changes no rotation
	 * anywhere, but it is the difference between "this rung is silent" being a
	 * property of the function and being a property of which constant happened
	 * to be negative, and only one of those is assertable.
	 */
	private static float scaled(float radians, int rung) {
		float weight = poseWeight(rung);
		return weight == 0.0F ? 0.0F : radians * weight;
	}

	// --- Packing, so the client carries one int instead of six floats.

	/** Three bits per region at {@code part.ordinal() * BITS_PER_PART}; eighteen used. */
	public static final int BITS_PER_PART = 3;

	private static final int RUNG_MASK = 0b111;

	/** The mask with one region's rung replaced. */
	public static int withRung(int mask, BodyPart part, int rung) {
		int shift = part.ordinal() * BITS_PER_PART;
		return (mask & ~(RUNG_MASK << shift)) | ((rung & RUNG_MASK) << shift);
	}

	/**
	 * One region's rung back out. Unsigned shift, so a sign bit cannot walk in,
	 * and saturated at {@link #RUNG_LOST} so a rung nobody can produce still
	 * lands on a rung that draws nothing rather than on an array index.
	 */
	public static int rungAt(int mask, BodyPart part) {
		return Math.min(RUNG_LOST, (mask >>> (part.ordinal() * BITS_PER_PART)) & RUNG_MASK);
	}
}
