package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Camera movement that is <em>view</em>, not aim (1.6.1).
 *
 * <p>Everything the mod did to the camera before this went through
 * {@code Entity.turn}: it moved the crosshair. That is the right tool for a
 * head being knocked aside — the blow really does point you somewhere else —
 * and the wrong one for everything that gives a hit its weight, because a
 * rotation of the aim is capped by how far the player will tolerate losing
 * their target, which is a few degrees. A rotation of the <em>picture</em> is
 * not. Vanilla knows this: its own hurt tilt is a roll applied to the view
 * matrix and never to the player, and this class rides the same hook
 * ({@code GameRenderer.bobHurt}, see {@code GameRendererFrameMixin}), so what
 * it does reaches the world and the first-person hand together and leaves the
 * aim exactly where the player put it.
 *
 * <h2>Two things live here</h2>
 *
 * <p><b>The impact spring.</b> Pitch, yaw, roll and a small sideways and
 * downward shove of the eye, each a damped spring kicked by a blow, a landing or
 * a blast and integrated per frame. A spring rather than a scripted curve
 * because a spring composes: two hits a quarter-second apart add, a hit landing
 * while the last one is still ringing overshoots further, and the whole thing
 * settles in the same way whatever combination produced it. As consciousness
 * fails the damping falls away — a head that cannot steady itself keeps
 * wobbling after a blow a clear one would have shrugged off. Blasts add a fast,
 * decaying multi-sine shake on top, since overpressure is not one impulse.
 *
 * <p><b>The sway.</b> Below the middle of the ladder the horizon starts to
 * tilt: two slow, mutually prime rolls, a slower pitch, a breathing heave on
 * the shared breath clock, and a lateral drift of the eye, all growing
 * quadratically toward collapse. A microsleep drops the head — the pitch sags
 * as the blackout closes and comes back up as it lifts, so you <em>come to</em>
 * with the world swinging back into place — and unconsciousness slumps it over
 * on one side. None of it moves the aim, which is the point of the design
 * question it answers: how do you make a player feel they are losing the camera
 * without taking the camera away from them. The head sways; the hand still
 * points where it pointed.
 *
 * <p><b>The heavy head and the drunk walk (1.6.2).</b> Two more answers to the
 * same question, both approved from the brainstorm. A share of every fast turn
 * goes into a spring that trails the aim and catches up late, so the world
 * swings and settles behind the crosshair (its own slider, Heavy Head, because
 * it is the most motion-sickness-prone thing here). And each footstep near
 * collapse rolls and dips the view unevenly, riding the walk cycle vanilla
 * already animates, under Head Sway.
 *
 * <p>All deterministic (sines of a tick clock, no RNG), all frozen while dying
 * so death inherits the last living pose, and all behind two sliders: the
 * spring behind Impact Shock, the sway behind its own Head Sway control.
 * Vanilla's Damage Tilt accessibility slider scales the spring as well, because
 * a player who turned that down asked for a camera that does not lurch when
 * they are hit, and this is that lurch.
 */
public final class CameraMotion {
	private CameraMotion() {}

	// Spring channels: view rotations in degrees, eye offsets in blocks.
	private static final int PITCH = 0;
	private static final int YAW = 1;
	private static final int ROLL = 2;
	private static final int SHOVE_X = 3;
	private static final int SHOVE_Y = 4;
	private static final double[] pos = new double[5];
	private static final double[] vel = new double[5];

	/**
	 * Rotational stiffness and damping, per tick and per tick squared. A natural
	 * period of about seven ticks and a damping ratio near 0.28: two visible
	 * overshoots, settled inside three quarters of a second.
	 */
	private static final double ROT_STIFFNESS = 0.80;
	/**
	 * 0.36 rather than the 0.50 of the first build (1.6.2): a damping ratio near
	 * 0.20, so a blow visibly overshoots two or three times even on a clear head.
	 * The author's call — the picture is allowed to be violent because none of
	 * this touches the aim.
	 */
	private static final double ROT_DAMPING = 0.36;
	/** The eye shove is snappier and better damped; a camera that slides about reads as a bug. */
	private static final double SHOVE_STIFFNESS = 1.40;
	private static final double SHOVE_DAMPING = 0.85;
	/** Integration step; the spring is stiff enough to want substeps at low frame rates. */
	private static final double MAX_STEP = 0.25;

	// Impulse sizes at full magnitude and full intensity.
	private static final double BLOW_ROLL = 13.0;
	private static final double BLOW_PITCH = 5.5;
	private static final double BLOW_YAW = 5.5;
	private static final double BLOW_SHOVE = 0.085;
	private static final double CUT_ROLL = 5.0;
	private static final double PIERCE_PITCH = 3.5;
	private static final double BLAST_SHAKE = 1.0;
	private static final double LANDING_PITCH = 7.0;
	private static final double LANDING_DROP = 0.22;

	// Blast shake: amplitude decays linearly over SHAKE_TICKS.
	private static double shake;
	private static double shakePhase;
	private static final double SHAKE_TICKS = 14.0;

	// The heavy head (1.6.2): the picture trails the aim on fast turns.
	private static final int LAG_YAW = 0;
	private static final int LAG_PITCH = 1;
	private static final double[] lagPos = new double[2];
	private static final double[] lagVel = new double[2];
	private static float lastAimYaw;
	private static float lastAimPitch;
	private static boolean aimKnown;
	/** Snappier than the impact spring — a lag that lingers reads as broken mouse input. */
	private static final double LAG_STIFFNESS = 1.10;
	private static final double LAG_DAMPING = 0.75;
	/** Share of a turn the picture initially misses, at full severity and intensity. */
	private static final double LAG_SHARE_MAX = 0.55;
	/** The lag never exceeds this, whatever the flick. */
	private static final double LAG_CLAMP = 30.0;
	/** A jump larger than this in one frame is a teleport or respawn, not a turn. */
	private static final double LAG_TELEPORT = 60.0;

	// The drunk walk (1.6.2): each footstep lurches the view.
	private static final double LURCH_ROLL_MAX = 5.0;
	private static final double LURCH_DIP_MAX = 1.6;
	private static final double LURCH_X_MAX = 0.03;

	// Sway.
	private static double time;
	private static double swayRoll;
	private static double swayPitch;
	private static double swayX;
	/** Peak sway at collapse, before the slider. */
	private static final double SWAY_ROLL_MAX = 6.0;
	private static final double SWAY_PITCH_MAX = 2.6;
	private static final double SWAY_HEAVE_MAX = 1.4;
	private static final double SWAY_X_MAX = 0.045;
	/** The microsleep nod and the unconscious slump, in degrees at full fade. */
	private static final double NOD_PITCH = 16.0;
	private static final double SLUMP_PITCH = 20.0;
	private static final double SLUMP_ROLL = 13.0;

	/**
	 * Times {@link #apply} actually pushed a transform onto a view stack. The
	 * readouts below say what the motion <em>wants</em>; this says the hook
	 * that carries it into the picture fired. A test that only checked the
	 * former passed on a build whose horizon never moved.
	 */
	private static long applications;

	public static long applications() {
		return applications;
	}

	/** The last frame's total roll, for the client gametest: sway and spring together. */
	public static double lastRoll() {
		return pos[ROLL] + swayRoll;
	}

	/** The last frame's total pitch offset, degrees, positive looking down. */
	public static double lastPitch() {
		return pos[PITCH] + swayPitch;
	}

	/**
	 * A blow landed. {@code relative} is the attacker's bearing in radians
	 * relative to the player's facing, as {@code ImpactEffect} already computes
	 * it; signs follow the aim kick's so the picture and the crosshair move the
	 * same way. {@code intensity} is Impact Shock; vanilla's Damage Tilt is
	 * applied here as well.
	 */
	public static void impact(double magnitude, double relative, ImpactFamily family, double intensity) {
		double scale = magnitude * intensity * damageTilt();
		if (scale <= 0.0) {
			return;
		}
		// Away from the blow: hit from the right, the head goes left. Roll and
		// yaw share the aim kick's -sin(relative); pitch snaps the chin up, more
		// for a frontal blow, exactly as the kick does.
		double away = -Math.sin(relative);
		double chin = -0.55 - 0.45 * Math.cos(relative);
		switch (family) {
			case CUT -> {
				vel[ROLL] += away * CUT_ROLL * scale;
				vel[PITCH] += chin * BLOW_PITCH * 0.4 * scale;
			}
			case PIERCE -> {
				// A stab does not knock the head; the body flinches down and in.
				vel[PITCH] += PIERCE_PITCH * scale;
				vel[SHOVE_Y] -= BLOW_SHOVE * 0.5 * scale;
			}
			case BLAST -> {
				shake = Math.max(shake, BLAST_SHAKE * scale);
				shakePhase = 0.0;
				vel[PITCH] += chin * BLOW_PITCH * scale;
				vel[SHOVE_Y] -= BLOW_SHOVE * scale;
			}
			default -> {
				vel[ROLL] += away * BLOW_ROLL * scale;
				vel[PITCH] += chin * BLOW_PITCH * scale;
				vel[YAW] += away * BLOW_YAW * scale;
				vel[SHOVE_X] += away * BLOW_SHOVE * scale;
				vel[SHOVE_Y] -= BLOW_SHOVE * 0.6 * scale;
			}
		}
	}

	/** The ground arrived: the eye drops and the chin goes down, then both spring back. */
	public static void landing(double severity, double intensity) {
		double scale = severity * intensity * damageTilt();
		if (scale <= 0.0) {
			return;
		}
		vel[PITCH] += LANDING_PITCH * scale;
		vel[SHOVE_Y] -= LANDING_DROP * scale;
	}

	private static double damageTilt() {
		Minecraft client = Minecraft.getInstance();
		return client.options == null ? 1.0 : client.options.damageTiltStrength().get();
	}

	/** Advances the springs, the shake and the sway. Once per rendered frame, game-time delta. */
	public static void frame(double deltaTicks) {
		if (deltaTicks <= 0.0 || ClientState.dying()) {
			return;   // frozen: death inherits the last living pose
		}
		// The spring loses its damping as the head loses the ability to steady
		// itself. Same ramp the look effects use, so it arrives with them.
		double severity = ClientState.active() ? ClientState.ramp(Balance.SYMPTOM_SENSITIVITY, 0.0) : 0.0;
		double rotDamping = ROT_DAMPING * (1.0 - 0.55 * severity);
		double remaining = deltaTicks;
		while (remaining > 1.0e-6) {
			double dt = Math.min(MAX_STEP, remaining);
			remaining -= dt;
			for (int i = 0; i < 3; i++) {
				step(i, ROT_STIFFNESS, rotDamping, dt);
			}
			step(SHOVE_X, SHOVE_STIFFNESS, SHOVE_DAMPING, dt);
			step(SHOVE_Y, SHOVE_STIFFNESS, SHOVE_DAMPING, dt);
		}
		for (int i = 0; i < pos.length; i++) {
			if (Math.abs(pos[i]) < 1.0e-4 && Math.abs(vel[i]) < 1.0e-4) {
				pos[i] = 0.0;
				vel[i] = 0.0;
			}
		}
		if (shake > 0.0) {
			shakePhase += deltaTicks;
			shake = Math.max(0.0, shake - deltaTicks / SHAKE_TICKS);
		}
		heavyHead(deltaTicks);
		time += deltaTicks;
		sway();
	}

	/**
	 * The heavy head: as consciousness fails a fast turn is not followed by the
	 * picture at once. A share of every turn is put into a spring pointing back
	 * the way the aim came, which then catches up and overshoots — the world
	 * swings late and settles, the way it does when you turn your head faster
	 * than it wants to go. The aim itself is exact the whole time; this is the
	 * view-only counterpart of Look Drift, and the two are separate sliders.
	 */
	private static void heavyHead(double deltaTicks) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			aimKnown = false;
			return;
		}
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		if (!aimKnown) {
			lastAimYaw = yaw;
			lastAimPitch = pitch;
			aimKnown = true;
		}
		double deltaYaw = Mth.wrapDegrees(yaw - lastAimYaw);
		double deltaPitch = pitch - lastAimPitch;
		lastAimYaw = yaw;
		lastAimPitch = pitch;
		double intensity = ClientEffectsConfig.get().cameraLag();
		double severity = ClientState.active() ? ClientState.ramp(Balance.SYMPTOM_SENSITIVITY, 0.0) : 0.0;
		double share = severity * Math.min(1.0, intensity) * LAG_SHARE_MAX;
		if (share > 0.0 && client.gui.screen() == null
				&& Math.abs(deltaYaw) < LAG_TELEPORT && Math.abs(deltaPitch) < LAG_TELEPORT) {
			lagPos[LAG_YAW] = Math.clamp(lagPos[LAG_YAW] - deltaYaw * share, -LAG_CLAMP, LAG_CLAMP);
			lagPos[LAG_PITCH] = Math.clamp(lagPos[LAG_PITCH] - deltaPitch * share, -LAG_CLAMP, LAG_CLAMP);
		}
		double remaining = deltaTicks;
		while (remaining > 1.0e-6) {
			double dt = Math.min(MAX_STEP, remaining);
			remaining -= dt;
			for (int i = 0; i < 2; i++) {
				double acceleration = -LAG_STIFFNESS * lagPos[i] - LAG_DAMPING * lagVel[i];
				lagVel[i] += acceleration * dt;
				lagPos[i] += lagVel[i] * dt;
			}
		}
		for (int i = 0; i < 2; i++) {
			if (Math.abs(lagPos[i]) < 1.0e-4 && Math.abs(lagVel[i]) < 1.0e-4) {
				lagPos[i] = 0.0;
				lagVel[i] = 0.0;
			}
		}
	}

	/** The heavy head's current yaw trail, degrees; for the client gametest. */
	public static double lastLagYaw() {
		return lagPos[LAG_YAW];
	}

	/** Semi-implicit Euler on one channel; stable for these constants at MAX_STEP. */
	private static void step(int i, double stiffness, double damping, double dt) {
		double acceleration = -stiffness * pos[i] - damping * vel[i];
		vel[i] += acceleration * dt;
		pos[i] += vel[i] * dt;
	}

	private static void sway() {
		ClientEffectsConfig config = ClientEffectsConfig.get();
		double intensity = config.cameraSway();
		if (intensity <= 0.0 || !ClientState.active()) {
			swayRoll = 0.0;
			swayPitch = 0.0;
			swayX = 0.0;
			return;
		}
		// Starts where the ghosting starts and grows with the square, so it is a
		// hint at 40 and the floor moving at 10.
		double ramp = ClientState.ramp(Balance.SYMPTOM_AFTERIMAGE, 0.0);
		double s = ramp * ramp * intensity;
		// Two slow rolls at mutually prime periods so the horizon never settles
		// into a loop; a slower pitch; a heave on the breath everything else
		// breathes to; a drift of the eye.
		double roll = (Math.sin(time / 23.0) * 0.6 + Math.sin(time / 41.0) * 0.4) * SWAY_ROLL_MAX;
		double pitch = Math.sin(time / 31.0) * SWAY_PITCH_MAX
				+ (ClientState.breath() - 0.5) * 2.0 * SWAY_HEAVE_MAX;
		double drift = Math.sin(time / 37.0) * SWAY_X_MAX;
		// The drunk walk: each footstep rolls and dips the view, unevenly — a
		// second harmonic offset in phase so one foot lands heavier than the
		// other. Rides the walk cycle vanilla already animates, so it is in step
		// with the view bob and the injured-leg pose rather than beside them.
		LocalPlayer player = Minecraft.getInstance().player;
		double lurchRoll = 0.0;
		double lurchDip = 0.0;
		double lurchX = 0.0;
		if (player != null) {
			float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			double step = player.walkAnimation.position(partial);
			double pace = Math.min(1.0, player.walkAnimation.speed(partial));
			double stride = Math.sin(step * Math.PI) + 0.5 * Math.sin(step * Math.PI * 2.0 + 0.7);
			lurchRoll = stride * LURCH_ROLL_MAX * pace;
			lurchDip = Math.abs(Math.cos(step * Math.PI)) * LURCH_DIP_MAX * pace;
			lurchX = Math.sin(step * Math.PI) * LURCH_X_MAX * pace;
		}
		// The nod and the slump ride the fades themselves, not the ramp: a
		// microsleep is an event with edges, and the edges are the whole cue.
		double nod = ClientState.microsleepFade() * NOD_PITCH;
		double slump = ClientState.unconsciousFade();
		swayRoll = s * (roll + lurchRoll) + slump * SLUMP_ROLL * intensity;
		swayPitch = s * (pitch + lurchDip) + (nod + slump * SLUMP_PITCH) * intensity;
		swayX = s * (drift + lurchX);
	}

	/**
	 * Applies the frame's motion to a view pose stack, at the tail of vanilla's
	 * own hurt tilt. Called twice a frame — once for the world, once for the
	 * hand — with identical results, which is what keeps the hand attached to
	 * the head.
	 */
	public static void apply(PoseStack poseStack) {
		double shakeRoll = 0.0;
		double shakePitch = 0.0;
		double shakeYaw = 0.0;
		if (shake > 0.0) {
			double a = shake * shake * 3.2;
			shakeRoll = Math.sin(shakePhase * 2.9) * a;
			shakePitch = Math.cos(shakePhase * 2.3) * a * 0.7;
			shakeYaw = Math.sin(shakePhase * 1.9 + 1.0) * a * 0.5;
		}
		double roll = pos[ROLL] + swayRoll + shakeRoll;
		double pitch = pos[PITCH] + swayPitch + shakePitch + lagPos[LAG_PITCH];
		double yaw = pos[YAW] + shakeYaw + lagPos[LAG_YAW];
		double x = pos[SHOVE_X] + swayX;
		double y = pos[SHOVE_Y];
		if (Math.abs(roll) < 1.0e-4 && Math.abs(pitch) < 1.0e-4 && Math.abs(yaw) < 1.0e-4
				&& Math.abs(x) < 1.0e-5 && Math.abs(y) < 1.0e-5) {
			return;
		}
		// Same conventions as the matrix this rides: ZP rolls the picture, XP
		// positive pitches the camera down, YP positive turns it right.
		applications++;
		poseStack.translate((float) x, (float) y, 0.0F);
		poseStack.mulPose(Axis.ZP.rotationDegrees((float) roll));
		poseStack.mulPose(Axis.XP.rotationDegrees((float) pitch));
		poseStack.mulPose(Axis.YP.rotationDegrees((float) yaw));
	}

	public static void reset() {
		java.util.Arrays.fill(pos, 0.0);
		java.util.Arrays.fill(vel, 0.0);
		shake = 0.0;
		shakePhase = 0.0;
		swayRoll = 0.0;
		swayPitch = 0.0;
		swayX = 0.0;
		java.util.Arrays.fill(lagPos, 0.0);
		java.util.Arrays.fill(lagVel, 0.0);
		aimKnown = false;
	}
}
