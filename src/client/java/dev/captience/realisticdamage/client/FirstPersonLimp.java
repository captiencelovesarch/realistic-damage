package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.BrokenLegLimp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * A broken leg in first person (2.0): the game-facing half of
 * {@link BrokenLegLimp}. Reads the walk cycle vanilla already animates and the
 * player's own snapshot, and lays the step's dip and lean over the view at the
 * same hook {@link CameraMotion} uses, so the world and the first-person hand
 * move together and the aim does not move at all.
 *
 * <p>Only on foot: a leg takes no steps in the air, in water, on a ladder or in
 * a saddle. The change between walking and not is eased over a few ticks so a
 * jump does not snap the view. Not in VR (the caller stands down there: the
 * headset is the camera).
 */
public final class FirstPersonLimp {
	private FirstPersonLimp() {}

	/** How fast the limp fades in and out when the feet leave or find the ground, share per tick. */
	private static final double GROUND_EASE = 0.3;

	/** 0 in the air (or swimming, riding, dead), 1 walking; eased. */
	private static double grounded;
	private static BrokenLegLimp.Step last = BrokenLegLimp.Step.NONE;
	/** Frames the limp actually moved the view, for the client gametest. */
	private static long applications;
	private static boolean handPass;

	public static long applications() {
		return applications;
	}

	/** The last step laid on the view. */
	public static BrokenLegLimp.Step last() {
		return last;
	}

	/** Once per rendered frame, game-time delta. */
	public static void frame(double deltaTicks) {
		handPass = false;
		LocalPlayer player = Minecraft.getInstance().player;
		boolean walking = player != null && player.onGround() && !player.isPassenger() && !player.isSwimming()
				&& !player.onClimbable() && !ClientState.dying() && !RagdollView.active() && !DeathCamera.active();
		double target = walking ? 1.0 : 0.0;
		grounded += (target - grounded) * Math.min(1.0, GROUND_EASE * Math.max(0.0, deltaTicks));
		if (Math.abs(target - grounded) < 1.0e-3) {
			grounded = target;
		}
	}

	/**
	 * Marks the rest of this frame's calls to {@link #apply} as the hand's. The
	 * view hook runs twice a frame, for the world and then for the first-person
	 * hand, and cannot tell which it is in; the hand draw says so here, and
	 * {@link #frame} clears it for the next frame.
	 */
	public static void handPass() {
		handPass = true;
	}

	/**
	 * Called twice a frame: for the world, then for the hand. Both lean, so the
	 * hand stays attached to the head. Only the world moves for the dip: the
	 * eye sinking makes the ground come up toward it, while a hand goes down
	 * with the eye it is seen from and so does not move in the picture at all.
	 */
	public static void apply(PoseStack poseStack) {
		last = step();
		if (last == BrokenLegLimp.Step.NONE || (last.dip() < 1.0e-5F && Math.abs(last.roll()) < 1.0e-4F)) {
			return;
		}
		applications++;
		// This matrix moves the picture, not the eye: raising everything by the
		// dip is the eye lowered by it. A positive ZP turns the picture
		// anticlockwise, which is the head leaning right.
		if (!handPass) {
			poseStack.translate(0.0F, last.dip(), 0.0F);
		}
		poseStack.mulPose(Axis.ZP.rotationDegrees(last.roll()));
	}

	private static BrokenLegLimp.Step step() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || grounded <= 0.0 || !ClientEffectsConfig.get().firstPersonInjury) {
			return BrokenLegLimp.Step.NONE;
		}
		BodySnapshot snapshot = player.getAttached(RDAttachments.SNAPSHOT);
		if (snapshot == null) {
			return BrokenLegLimp.Step.NONE;
		}
		boolean left = snapshot.broken(BodyPart.LEFT_LEG) && !snapshot.destroyed(BodyPart.LEFT_LEG);
		boolean right = snapshot.broken(BodyPart.RIGHT_LEG) && !snapshot.destroyed(BodyPart.RIGHT_LEG);
		if (!left && !right) {
			return BrokenLegLimp.Step.NONE;
		}
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double phase = player.walkAnimation.position(partial) * BrokenLegLimp.CYCLE;
		float pace = (float) (Math.min(1.0F, player.walkAnimation.speed(partial)) * grounded);
		return BrokenLegLimp.step(phase, pace, left, right);
	}

	public static void reset() {
		grounded = 0.0;
		handPass = false;
		last = BrokenLegLimp.Step.NONE;
	}
}
