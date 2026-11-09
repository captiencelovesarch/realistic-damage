package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.damage.DeathKind;

/**
 * A dying body's muscles (active ragdolls, phase 2): how hard each joint still
 * holds the pose the body was in, and how that lets go over time.
 *
 * <p>Tone is in multiples of each limb's own gravity torque (see
 * {@link HumanoidRagdoll}): at 1 a limb held out sags about half a radian, at
 * 2.5 it barely moves, and the hips at about 1.2 can no longer hold the torso
 * up, so below that a standing body folds at the waist instead of going over
 * in one piece. The {@link #floor} is not a muscle but the stiffness of a joint
 * with nobody in it: enough that it is not a free hinge.
 *
 * <p>Three groups let go separately (the neck, the arms, the legs), each held
 * for a moment ({@code delay}) and then fading ({@code fade}, a time
 * constant), so a collapse has an order to it: in a bleed-out the legs give
 * first and the head is the last thing held.
 *
 * <p>Everything here is chosen by {@link #forDeath} from the body's own record
 * (consciousness, injuries, adrenaline) and the kind of death, and none of it
 * is random. Plain Java, so the table is unit-tested.
 */
public final class Muscles {
	public static final int NECK = 0;
	public static final int ARMS = 1;
	public static final int LEGS = 2;
	private static final int GROUPS = 3;

	/** Tone at the moment of death, per group. */
	public final double[] start = new double[GROUPS];
	/** Seconds each group holds before it starts to let go. */
	public final double[] delay = new double[GROUPS];
	/** Seconds for a group to lose most of its tone once it lets go. */
	public final double[] fade = new double[GROUPS];
	/** The stiffness of a joint with nobody in it. */
	public double floor = 0.12;
	/** Per part ({@link HumanoidRagdoll#HEAD} and so on): 1 is a sound limb, 0 a limb with no muscle left. */
	public final double[] limb = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
	/** Per part: how much of the floor it keeps (a broken limb swings freer than a sound dead one). */
	public final double[] limbFloor = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
	/** Per part: how much wider than normal its joint may go (a broken limb bends where it shouldn't). */
	public final double[] looseness = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
	/**
	 * Whether this death drops the body where it stands ("strings cut"): it then
	 * needs no shove to go over, only a direction.
	 */
	public boolean stringsCut;
	/** How much of a mind was left at death, 0..1: what reflexes (phase 3) have to work with. */
	public double awake = 1.0;

	/** Phase 0's single curve: firm at death, gone in half a second. The default for a body nobody described. */
	public static Muscles standard() {
		Muscles m = new Muscles();
		m.setAll(2.5, 0.0, 0.45);
		return m;
	}

	/**
	 * Muscles that hold, firmly and for as long as anything wants them to: a
	 * body being kept on its feet (the stepper's test body).
	 */
	public static Muscles holding(double tone) {
		Muscles m = new Muscles();
		m.setAll(tone, 1.0e9, 0.45);
		return m;
	}

	/** These muscles, letting go from {@code atSeconds} on over {@code fadeOver} (a time constant). */
	public Muscles lettingGo(double atSeconds, double fadeOver) {
		Muscles m = new Muscles();
		for (int g = 0; g < GROUPS; g++) {
			m.set(g, start[g], atSeconds, fadeOver);
		}
		m.floor = floor;
		System.arraycopy(limb, 0, m.limb, 0, limb.length);
		System.arraycopy(limbFloor, 0, m.limbFloor, 0, limbFloor.length);
		System.arraycopy(looseness, 0, m.looseness, 0, looseness.length);
		m.stringsCut = stringsCut;
		m.awake = awake;
		return m;
	}

	/**
	 * These muscles giving way from {@code atSeconds} on, from the ground up: the
	 * legs go at once, the arms a moment after, the head last. A body let go of
	 * this way sinks and folds; one whose muscles all go together goes over like
	 * a plank.
	 */
	public Muscles crumpling(double atSeconds) {
		Muscles m = lettingGo(atSeconds, 0.3);
		m.set(LEGS, Math.min(start[LEGS], 2.0), atSeconds, 0.2);
		m.set(ARMS, Math.min(start[ARMS], 0.7), atSeconds + 0.2, 0.5);
		m.set(NECK, Math.min(start[NECK], 1.2), atSeconds + 0.4, 0.6);
		return m;
	}

	/** Tone of a part's joint, {@code seconds} after death. */
	public double tone(int part, double seconds) {
		int g = group(part);
		double t = seconds - delay[g];
		double held = t <= 0.0 ? 1.0 : Math.exp(-t / Math.max(1.0e-3, fade[g]));
		return floor * limbFloor[part] + start[g] * limb[part] * held;
	}

	public static int group(int part) {
		return switch (part) {
			case HumanoidRagdoll.HEAD -> NECK;
			case HumanoidRagdoll.RIGHT_ARM, HumanoidRagdoll.LEFT_ARM -> ARMS;
			case HumanoidRagdoll.RIGHT_LEG, HumanoidRagdoll.LEFT_LEG -> LEGS;
			default -> -1;
		};
	}

	private void setAll(double tone, double holdFor, double fadeOver) {
		for (int g = 0; g < GROUPS; g++) {
			start[g] = tone;
			delay[g] = holdFor;
			fade[g] = fadeOver;
		}
	}

	private void set(int g, double tone, double holdFor, double fadeOver) {
		start[g] = tone;
		delay[g] = holdFor;
		fade[g] = fadeOver;
	}

	/**
	 * What the body knew about itself as it died. Parts use the ragdoll's own
	 * indices ({@link HumanoidRagdoll#HEAD} and so on).
	 *
	 * @param kind          what killed it
	 * @param violence      0..1, how hard (see {@code DeathMath})
	 * @param hitPart       where the killing blow landed, or -1
	 * @param consciousness 0..100, the hidden meter
	 * @param unconscious   already out when it died
	 * @param broken        per part, broken
	 * @param limp          per part, worn so far it no longer holds anything ({@code LimpMath.RUNG_RUINED})
	 * @param concussion    concussion grade, 0 for none
	 * @param surge         in an adrenaline surge
	 * @param crash         in the crash after one
	 */
	public record Input(DeathKind kind, double violence, int hitPart, double consciousness, boolean unconscious,
			boolean[] broken, boolean[] limp, int concussion, boolean surge, boolean crash) {
		/** A healthy, conscious body dying of this. */
		public static Input healthy(DeathKind kind, double violence, int hitPart) {
			return new Input(kind, violence, hitPart, 100.0, false, new boolean[HumanoidRagdoll.PARTS],
					new boolean[HumanoidRagdoll.PARTS], 0, false, false);
		}
	}

	/** The muscles a body dies with. */
	public static Muscles forDeath(Input in) {
		Muscles m = new Muscles();
		double v = clamp01(in.violence());
		double awake = in.unconscious() ? 0.0 : clamp01(in.consciousness() / 100.0);
		boolean headHit = in.hitPart() == HumanoidRagdoll.HEAD
				&& (in.kind() == DeathKind.BLOW || in.kind() == DeathKind.PROJECTILE || in.kind() == DeathKind.CRUSH);
		m.stringsCut = awake <= 0.0 || headHit || in.kind() == DeathKind.CRUSH || v >= 0.95;
		m.awake = m.stringsCut ? 0.0 : awake;

		switch (in.kind()) {
			case FREEZE -> {
				// Frozen stiff: it goes over in one piece and stays that way.
				m.setAll(8.0, 3.0, 4.0);
				m.floor = 3.0;
				m.stringsCut = false;
				return m;
			}
			case FADE, VOID -> {
				// Let go rather than struck: the legs first, the head last, over
				// a couple of seconds, so the body sinks and folds.
				m.set(LEGS, 1.6, 0.15, 0.45);
				m.set(ARMS, 1.8, 0.45, 0.7);
				m.set(NECK, 2.2, 0.7, 0.8);
			}
			case DROWNING -> {
				// Soft from the start and slow to go: it drifts.
				m.setAll(0.7, 0.0, 1.6);
			}
			case EXPLOSION -> {
				// The tumble is all physics.
				m.setAll(0.5, 0.0, 0.15);
			}
			case FALL -> {
				// The landing takes it all at once.
				m.setAll(1.0, 0.0, 0.1);
			}
			case SHOCK -> {
				// Locked by the current for a moment, then nothing (phase 3 adds the
				// convulsion).
				m.setAll(4.0, 0.3, 0.25);
			}
			default -> {
				// Struck, shot, burned: it crumples. Harder deaths go slack faster.
				// It goes over in one piece for a moment, then folds at the hips as it
				// falls.
				double tone = 2.4 - 1.2 * v;
				double hold = 0.2 - 0.12 * v;
				m.set(LEGS, tone, hold, 0.35 - 0.15 * v);
				m.set(ARMS, tone * 0.9, hold, 0.45 - 0.15 * v);
				m.set(NECK, tone * 1.1, hold + 0.05, 0.55 - 0.2 * v);
			}
		}

		for (int g = 0; g < GROUPS; g++) {
			m.start[g] *= awake;
		}
		if (m.stringsCut) {
			for (int g = 0; g < GROUPS; g++) {
				m.start[g] = 0.0;
			}
		}
		if (in.surge()) {
			// The surge holds on: more tone, held longer.
			for (int g = 0; g < GROUPS; g++) {
				m.start[g] *= 1.3;
				m.delay[g] = m.delay[g] * 1.5 + 0.1;
				m.fade[g] *= 1.5;
			}
		} else if (in.crash()) {
			for (int g = 0; g < GROUPS; g++) {
				m.start[g] *= 0.7;
				m.fade[g] *= 0.8;
			}
		}
		if (in.concussion() > 0) {
			m.start[NECK] *= Math.max(0.0, 1.0 - 0.25 * in.concussion());
		}

		for (int p = 0; p < HumanoidRagdoll.PARTS; p++) {
			if (p == HumanoidRagdoll.TORSO) {
				continue;
			}
			boolean broken = in.broken() != null && in.broken()[p];
			boolean limp = in.limp() != null && in.limp()[p];
			if (broken) {
				// Nothing holds it, and it bends further than it should.
				m.limb[p] = 0.0;
				m.limbFloor[p] = 0.4;
				m.looseness[p] = 1.3;
			} else if (limp) {
				m.limb[p] = 0.0;
				m.limbFloor[p] = 0.7;
			}
		}
		return m;
	}

	private static double clamp01(double v) {
		return v < 0.0 ? 0.0 : v > 1.0 ? 1.0 : v;
	}
}
