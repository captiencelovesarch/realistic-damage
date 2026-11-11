package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.client.compat.Vr;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.NeckMath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Whiplash, felt (2.0): a stiff neck turns slowly, and quick turns hurt.
 *
 * <p>The cap has to feel like stiffness and not like input lag, so it never
 * stores anything up to play back later. The neck has a little give
 * ({@link #GIVE_SECONDS} of turning at the cap), refilled at the cap's rate;
 * each turn the player asks for takes what the give allows, through a soft
 * knee ({@link NeckMath#give}), and the rest is simply not turned. Slow looks
 * pass untouched, a fast one meets resistance that builds, and the moment you
 * stop pushing there is nothing left over to drift on.
 *
 * <p>Turning that the neck refuses is strain. Strain bleeds away in a quarter
 * of a second, so turning steadily against the cap is uncomfortable rather
 * than painful; a quick, hard turn piles it up past {@link #PAIN_DEGREES} and
 * hurts: a wince of the view, a flash of blur and red at the edges, and in VR
 * a haptic pulse.
 *
 * <p>In VR nothing may turn the view but the player's own head, so the head
 * is never capped. Instead its speed is read (fast head turns hurt the same
 * way), and the smooth and snap turning that Vivecraft does is capped
 * (see {@code VrTurnMixin}).
 */
public final class StiffNeck {
	private StiffNeck() {}

	/** How much give the neck has, as seconds of turning at the cap. */
	private static final double GIVE_SECONDS = 0.06;
	/** Refused turning, degrees, that hurts. */
	private static final double PAIN_DEGREES = 25.0;
	/** How fast strain bleeds away, per second (a time constant of a quarter second). */
	private static final double STRAIN_RELEASE = 4.0;
	/** The least time between two pains, seconds. */
	private static final double PAIN_COOLDOWN = 1.2;
	/** How long the flash of pain takes to fade, seconds. */
	private static final double FLASH_SECONDS = 0.35;
	/** Degrees per Entity.turn unit. */
	private static final double TURN_UNIT = 0.15;

	private static double give = Double.NaN;
	private static long lastNs;
	private static double strain;
	private static double sincePain = PAIN_COOLDOWN;
	private static double flash;
	private static long pains;
	private static double lastCap = Double.POSITIVE_INFINITY;
	private static boolean involuntary;

	private static Vec3 lastHead;
	private static double headStrain;
	private static double vrGive = Double.NaN;
	private static long vrLastNs;
	private static long snapReadyNs;

	/** How stiff your neck is right now, 0 (free) to 1. */
	public static double stiffness() {
		if (!ClientState.active()) {
			return 0.0;
		}
		BodySnapshot snapshot = ClientState.snapshot();
		return snapshot == null ? 0.0 : snapshot.whiplash();
	}

	/**
	 * A turn the mod makes on the player's behalf (a blow's kick, a landing's
	 * dip, the drift of a fading mind): involuntary, so the neck does not
	 * resist it. Those are what happens to the head, not what the player asks
	 * of it.
	 */
	public static void turnInvoluntarily(LocalPlayer player, double xo, double yo) {
		involuntary = true;
		try {
			player.turn(xo, yo);
		} finally {
			involuntary = false;
		}
	}

	/**
	 * One turn the player asked for, in {@code Entity.turn} units (from the
	 * mouse, a controller, or the mouse's momentum): what gets through, or null
	 * to leave it alone.
	 */
	public static double[] filter(double xo, double yo) {
		if (involuntary || Vr.active()) {
			return null;
		}
		double s = stiffness();
		long now = System.nanoTime();
		if (s <= 0.0) {
			give = Double.NaN;
			strain = 0.0;
			lastCap = Double.POSITIVE_INFINITY;
			return null;
		}
		double cap = NeckMath.turnCap(s);
		lastCap = cap;
		double full = cap * GIVE_SECONDS;
		double dt = Double.isNaN(give) ? 0.0 : Math.min(0.25, (now - lastNs) / 1.0e9);
		lastNs = now;
		give = Double.isNaN(give) ? full : Math.min(full, give + cap * dt);
		strain *= Math.exp(-STRAIN_RELEASE * dt);
		double asked = Math.hypot(xo, yo) * TURN_UNIT;
		if (asked < 1.0e-9) {
			return null;
		}
		double given = NeckMath.give(asked, give);
		give -= given;
		strain += asked - given;
		if (strain > PAIN_DEGREES) {
			hurt(Math.min(1.0, strain / (PAIN_DEGREES * 2.4)) * (0.5 + 0.5 * s));
			strain = 0.0;
		}
		if (!ClientEffectsConfig.get().stiffNeck) {
			// Pain only: you feel it, but nothing slows the view.
			return null;
		}
		double k = given / asked;
		return new double[] {xo * k, yo * k};
	}

	/**
	 * Vivecraft turned the world this frame, from {@code before} to {@code after}
	 * degrees (smooth or snap turning): what it may turn to. Smooth turning is
	 * held to the cap like a mouse; a snap goes whole or not at all, and the next
	 * one waits until the neck could have made the last.
	 */
	public static float vrTurn(float before, float after) {
		double s = stiffness();
		long now = System.nanoTime();
		if (s <= 0.0 || !ClientEffectsConfig.get().stiffNeck) {
			vrGive = Double.NaN;
			return after;
		}
		double cap = NeckMath.turnCap(s);
		double full = cap * GIVE_SECONDS;
		double dt = Double.isNaN(vrGive) ? 0.0 : Math.min(0.25, (now - vrLastNs) / 1.0e9);
		vrLastNs = now;
		vrGive = Double.isNaN(vrGive) ? full : Math.min(full, vrGive + cap * dt);
		double delta = after - before;
		double asked = Math.abs(delta);
		if (asked < 1.0e-6) {
			return after;
		}
		if (asked > full * 1.5) {
			// A snap: all of it, or none of it yet.
			if (now - snapReadyNs < 0L) {
				return before;
			}
			snapReadyNs = now + (long) (asked / cap * 1.0e9);
			return after;
		}
		double given = NeckMath.give(asked, vrGive);
		vrGive -= given;
		return before + (float) Math.copySign(given, delta);
	}

	/** Once per client tick: in VR, how fast the head itself is turning. */
	public static void tick() {
		sincePain += 0.05;
		double s = stiffness();
		Vec3 head = s > 0.0 && Vr.active() ? Vr.headDirection() : null;
		if (head == null) {
			lastHead = null;
			headStrain = 0.0;
			return;
		}
		if (lastHead != null) {
			double degrees = Math.toDegrees(Math.acos(Math.clamp(lastHead.dot(head), -1.0, 1.0)));
			double allowed = NeckMath.turnCap(s) * 0.05;
			headStrain = headStrain * Math.exp(-STRAIN_RELEASE * 0.05) + Math.max(0.0, degrees - allowed);
			if (headStrain > PAIN_DEGREES) {
				hurt(Math.min(1.0, headStrain / (PAIN_DEGREES * 2.4)) * (0.5 + 0.5 * s));
				headStrain = 0.0;
			}
		}
		lastHead = head;
	}

	private static void hurt(double strength) {
		if (sincePain < PAIN_COOLDOWN) {
			return;
		}
		sincePain = 0.0;
		pains++;
		flash = Math.max(flash, strength);
		if (!Vr.active()) {
			CameraMotion.flinch(strength * 0.8);
		}
		VrHaptics.pain(strength);
	}

	/** Per frame, from the HUD: the flash fades. */
	public static void frame(double deltaTicks) {
		if (flash > 0.0) {
			flash = Math.max(0.0, flash - deltaTicks * 0.05 / FLASH_SECONDS);
		}
	}

	/** Whether the flash of pain is bright enough to blur the view (never in VR: the vignette says it there). */
	public static boolean blurring() {
		return flash > 0.45 && ClientState.active() && !Vr.active();
	}

	/** The red at the edges as the pain flashes. */
	public static void render(GuiGraphicsExtractor graphics) {
		if (flash <= 0.01 || !ClientState.active()) {
			return;
		}
		float f = (float) flash;
		Lens.vignette(graphics, 0.25F * f, 0.75F * f, 0.75F * f);
	}

	/** Pains so far; for the gametests. */
	public static long pains() {
		return pains;
	}

	/** The cap the last filtered turn met, degrees a second; infinite with a sound neck. For the gametests. */
	public static double lastCap() {
		return lastCap;
	}

	public static void reset() {
		give = Double.NaN;
		strain = 0.0;
		flash = 0.0;
		sincePain = PAIN_COOLDOWN;
		lastHead = null;
		headStrain = 0.0;
		vrGive = Double.NaN;
		lastCap = Double.POSITIVE_INFINITY;
	}
}
