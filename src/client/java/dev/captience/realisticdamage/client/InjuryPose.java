package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.systems.LimpMath;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * How an injured limb is carried (1.6) — the first thing the player model has
 * ever said about a break.
 *
 * <p>Until now the model's whole vocabulary was <em>gone</em>, <em>bloody</em>
 * and <em>scarred</em>. A fracture had a HUD line, a panel sentence, an icon, a
 * sound every player in earshot heard and a 25% penalty, and on the body itself
 * it had nothing — so the one release that put a mark on the model put it on
 * the memory of the injury and not on the injury. This is the missing half, and
 * it is a pose rather than a fourth texture pass for one decisive reason:
 * vanilla's armour cubes are inflated +0.5 and +1.0 and drawn in an opaque
 * pass, and the skin's own overlay sits at +0.25, so any mark painted on the
 * body at the blood shell's stand-off is invisible on an armoured player or on
 * a skin with sleeves. Fractures happen in fights and fights happen in armour;
 * a cue that switches off when you put on iron is not a cue. A pose survives
 * armour, survives every skin, reads at any distance, and costs no draw call
 * and no byte on the wire.
 *
 * <p>Every angle comes from {@link LimpMath}, which is also where the mob AI
 * reads its prey score. That is the whole reason the two ship in one release:
 * the roadmap promises hostiles that prefer targets who are <em>visibly</em>
 * limping, and the promise is only true while the pose and the score are the
 * same sentence about the same leg.
 *
 * <h2>What it does, and what it deliberately leaves alone</h2>
 *
 * <p>A leg's swing is <em>scaled</em>, not offset: the value vanilla has
 * already written into the part is multiplied down, so the bad leg takes a
 * shorter step than the good one and the asymmetry between the two is what
 * reads as a limp. It is also turned out and hung back a little, so a player
 * standing still still shows which leg it is. An arm is rolled in against the
 * ribs and held forward — additive, because arm rotation downstream of us is
 * overwritten by item poses, the attack animation and the swim lerp, and
 * subtracting a swing that is often no longer there would take a bite out of
 * whichever pose replaced it.
 *
 * <p>The legs fade out with {@code swimAmount}: a limp is something that
 * happens to a walk, and a player who is swimming or crawling — including in
 * this mod's own forced prone pose — is not walking. An arm held against the
 * ribs is held there whatever the rest of the body is doing, so the cradle does
 * not fade. Riding is skipped outright, because vanilla's passenger branch
 * overwrites the leg rotations with a fixed sitting angle and scaling a sitting
 * angle would lift the rider's leg off the saddle.
 *
 * <p>Nothing is drawn for a destroyed limb: {@code LimpMath.poseWeight} answers
 * zero there, and the limb is hidden anyway. The absence is the cue, and it is
 * a louder one than any angle.
 *
 * <h2>Determinism</h2>
 *
 * <p>No die roll and no clock of its own. Every value is a constant or a
 * function of fields the render state already publishes, so two identical
 * bodies are posed identically on every client, and the only thing that moves
 * is the walk cycle the pose is scaling — which is the animation, and the right
 * one to borrow.
 */
public final class InjuryPose {
	private InjuryPose() {}

	/** Indexed by rung; the two rungs that never pose are never written. */
	private static final long[] posesByRung = new long[LimpMath.RUNG_LOST + 1];

	/**
	 * How many limbs have been posed this session. Monotonic, and sampled as a
	 * delta over a fixed window by the client gametest — a render loop that has
	 * stalled then fails a "greater than zero" assertion instead of passing one,
	 * which is the safe direction for a test whose subject is whether anything
	 * happened at all. The same shape as {@code BloodBodyLayer.scarSubmits()},
	 * and for the same reason: a pose leaves no trace on screen that a test can
	 * read.
	 */
	public static long poseApplications() {
		long total = 0;
		for (long count : posesByRung) {
			total += count;
		}
		return total;
	}

	/**
	 * The same count narrowed to one rung, so a test can tell a fracture from a
	 * limb merely worn down to Critical. Without it the ladder is one number and
	 * "a break and a ruined limb look different" is unassertable, because the
	 * difference is an angle and an angle leaves no trace a gametest can read.
	 */
	public static long poseApplications(int rung) {
		return rung < 0 || rung >= posesByRung.length ? 0L : posesByRung[rung];
	}

	/**
	 * Poses whatever is injured. Called at the tail of every
	 * {@code HumanoidModel.setupAnim} for a player — which is the player's own
	 * model and, separately, each armour model rendered over it, so the sleeve
	 * follows the arm inside it.
	 */
	public static void apply(HumanoidRenderState state, HumanoidModel<?> model) {
		float grounded = state.isPassenger ? 0.0F : 1.0F - Math.clamp(state.swimAmount, 0.0F, 1.0F);
		applyParts((LimbStateHolder) state, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg, grounded);
	}

	/**
	 * The same limp on any model that has the four parts (1.7): illagers and
	 * villagers hand theirs in by reference. {@code grounded} is how much of the
	 * leg pose applies — zero for a passenger or a swimmer, one on foot. A null
	 * part (a villager's arms are one crossed piece) is simply not posed.
	 */
	public static void applyParts(LimbStateHolder holder, ModelPart leftArm, ModelPart rightArm,
			ModelPart leftLeg, ModelPart rightLeg, float grounded) {
		// Its own switch, not the scars one and not the panic button. See the
		// field's javadoc in ClientEffectsConfig for why.
		if (!ClientEffectsConfig.get().showInjuryPose) {
			return;
		}
		int rungs = holder.realisticdamage$limbRungs();
		if (rungs == 0) {
			return;   // by far the common case: nobody is hurt
		}
		if (leftArm != null) {
			poseArm(leftArm, LimpMath.rungAt(rungs, BodyPart.LEFT_ARM), true);
		}
		if (rightArm != null) {
			poseArm(rightArm, LimpMath.rungAt(rungs, BodyPart.RIGHT_ARM), false);
		}
		if (grounded <= 0.0F) {
			return;
		}
		if (leftLeg != null) {
			poseLeg(leftLeg, LimpMath.rungAt(rungs, BodyPart.LEFT_LEG), true, grounded);
		}
		if (rightLeg != null) {
			poseLeg(rightLeg, LimpMath.rungAt(rungs, BodyPart.RIGHT_LEG), false, grounded);
		}
	}

	private static void poseArm(ModelPart arm, int rung, boolean left) {
		if (LimpMath.poseWeight(rung) <= 0.0F) {
			return;
		}
		arm.zRot += LimpMath.armCradleRoll(rung, left);
		arm.xRot += LimpMath.armCradleLift(rung);
		posesByRung[rung]++;
	}

	private static void poseLeg(ModelPart leg, int rung, boolean left, float grounded) {
		if (LimpMath.poseWeight(rung) <= 0.0F) {
			return;
		}
		// The swing scale is blended toward 1.0 rather than switched off, so a
		// player wading into deeper water loses the limp gradually instead of
		// snapping upright at the waterline.
		leg.xRot *= 1.0F - (1.0F - LimpMath.legSwingScale(rung)) * grounded;
		leg.xRot += LimpMath.legTrail(rung) * grounded;
		leg.zRot += LimpMath.legTurnout(rung, left) * grounded;
		posesByRung[rung]++;
	}
}
