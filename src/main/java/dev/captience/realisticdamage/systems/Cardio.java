package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.config.Balance;

/**
 * One heart (1.5). Stateful, because a beat has to be integrated rather than
 * evaluated: the rate changes while it is running, so where the next beat falls
 * depends on where the last one did.
 *
 * <p>Three things the previous model could not do.
 *
 * <p><b>It could not falter.</b> The rate was a smooth monotonic function of a
 * single distress scalar, which makes a metronome by construction. Real
 * decompensation produces ectopic beats — one arrives early and weak, the heart
 * pauses longer than usual to catch up, and the beat that ends the pause lands
 * harder than any normal one. That "skipped a beat, then a thump" is what
 * people actually mean when they say their heart is irregular, and it is
 * generated here deterministically from the beat index rather than from a random
 * source, so two clients watching the same player see the same arrhythmia.
 *
 * <p><b>It could not be slow.</b> Distress was one number, so every cause of it
 * produced the same racing beat. Blood loss and head trauma do not feel alike:
 * a bled-out heart is fast and faint, a failing one is slow and enormous. The
 * causes are kept separate here and crossfaded, so the beat says what is wrong
 * with you and not merely that something is.
 *
 * <p><b>Its envelope was measured in beats.</b> A decay constant of 0.055
 * <em>beats</em> is 60ms at rest and 20ms at 165bpm, so the thump got tighter
 * and clickier the closer to death you were. Everything here is in seconds.
 */
public final class Cardio {
	/** Ticks since the last beat began — the envelope's clock. */
	private double sinceBeat;
	/**
	 * Progress toward the next beat, 0 to 1, advanced by {@code 1/interval} each
	 * tick — the same accumulator the microsleep system uses, for the same
	 * reason. The first version compared elapsed ticks against an interval it
	 * re-derived from the current rate every tick, and a heart slowing quickly
	 * enough (dying stretches the interval by ~0.7 a tick) pushed the deadline
	 * away faster than the clock could chase it: the beat became a receding
	 * horizon and death was silent from the first frame. Integrating the phase
	 * means a slowing rate slows the approach but can never reverse it.
	 */
	private double phase;
	/** How hard the current beat lands, 1 being ordinary. */
	private double amplitude = 1.0;
	/** How stretched the current beat's envelope is; 1 is the sharp resting shape. */
	private double stretch = 1.0;
	/** The ectopic timing multiplier this beat was given, held so a moving rate cannot lose it. */
	private double intervalScale = 1.0;
	private long beatIndex;
	private boolean beatFired;
	private boolean previousWasEctopic;
	private double bpm = Balance.HEART_REST_BPM;
	/**
	 * How far into dying this heart is, 0 (alive) to 1 (stopped). Stretches the
	 * interval, drains the amplitude and lengthens every beat, so the last few
	 * are slow, enormous and then not there — rather than the heart simply being
	 * switched off between frames, which is what death used to do.
	 */
	private double moribund;
	/**
	 * The point of the dying fade at which this heart falls silent. A parameter
	 * rather than a constant because the two death styles disagree about the
	 * ordering of the senses: the cinematic ending stops the heart while the
	 * screen still shows something, and the realistic one keeps it beating past
	 * the blackout, because hearing is the last sense to go.
	 */
	private double silentAt = Balance.DEATH_HEART_SILENT;

	/**
	 * Advances by one tick.
	 *
	 * @param fromHealth distress from ordinary injury, 0 to 1
	 * @param fromBlood  distress from blood loss, 0 to 1
	 * @param fromMind   distress from fading consciousness, 0 to 1
	 * @param breath     the breathing cycle, 0 to 1, for sinus arrhythmia
	 */
	public void tick(double fromHealth, double fromBlood, double fromMind, double breath) {
		double distress = Math.max(fromHealth, Math.max(fromBlood, fromMind));
		double sum = fromHealth + fromBlood + fromMind;
		double weightHealth = sum <= 1.0e-6 ? 1.0 : fromHealth / sum;
		double weightBlood = sum <= 1.0e-6 ? 0.0 : fromBlood / sum;
		double weightMind = sum <= 1.0e-6 ? 0.0 : fromMind / sum;

		if (moribund > 0.0) {
			// Dying owns the rate outright. It starts fast — the body has just
			// taken whatever killed it — and collapses through and below the
			// living floor, which nothing alive is permitted to do. The 0.7
			// exponent front-loads the collapse so the gaps are already audibly
			// widening by the second or third beat.
			bpm = Balance.DEATH_BPM_START
					+ (Balance.DEATH_BPM_END - Balance.DEATH_BPM_START) * Math.pow(moribund, 0.7);
		} else {
			bpm = Math.clamp(Balance.HEART_REST_BPM + distress * (
					weightHealth * Balance.HEART_POUNDING_GAIN
							+ weightBlood * Balance.HEART_THREADY_GAIN
							+ weightMind * Balance.HEART_AGONAL_GAIN),
					Balance.HEART_MIN_BPM, Balance.HEART_MAX_BPM);
		}

		// Rate rises on the inhale and falls on the exhale. Small, but it is the
		// difference between a pulse and a click track at low distress, where
		// there is no arrhythmia yet to do the job.
		double breathing = 1.0 - Balance.HEART_BREATH_SWING * (breath * 2.0 - 1.0);
		double baseInterval = 20.0 * 60.0 / bpm * breathing;

		sinceBeat += 1.0;
		beatFired = false;
		// The rate a beat approaches at tracks the live interval — including the
		// ectopic scale the pending beat was given — so a racing heart arrives
		// sooner and a slowing one later, without the deadline itself moving.
		phase += 1.0 / Math.max(1.0, baseInterval * intervalScale);
		if (phase >= 1.0) {
			phase -= 1.0;
			sinceBeat = 0.0;
			beatIndex++;
			startBeat(distress, weightBlood, weightMind);
			beatFired = amplitude > 0.05;
		}
	}

	private void startBeat(double distress, double weightBlood, double weightMind) {
		boolean ectopicNow = isEctopic(beatIndex, distress);
		boolean compensating = previousWasEctopic;
		intervalScale = ectopicNow ? Balance.ECTOPY_EARLY_SCALE
				: compensating ? Balance.ECTOPY_PAUSE_SCALE : 1.0;

		double base = ectopicNow ? Balance.ECTOPY_WEAK_AMPLITUDE
				: compensating ? Balance.ECTOPY_THUMP_AMPLITUDE : 1.0;
		// A bled-out pulse is thready: you can feel it racing and barely feel it
		// land. Scaling amplitude rather than rate is what separates the two.
		base *= 1.0 - weightBlood * distress * (1.0 - Balance.HEART_THREADY_AMPLITUDE);
		// A dying heart drains away entirely, and is silent before the fade is —
		// the last stretch of dying happens in silence, on purpose.
		if (moribund > 0.0) {
			base *= Math.clamp(1.0 - moribund / silentAt, 0.0, 1.0);
		}
		amplitude = base;
		stretch = (1.0 + weightMind * distress * (Balance.HEART_AGONAL_STRETCH - 1.0))
				* (1.0 + moribund);
		previousWasEctopic = ectopicNow;
	}

	/**
	 * Whether the given beat misfires. A hash of the beat index rather than a
	 * random draw: the same heart in the same state always produces the same
	 * pattern, which keeps it reproducible without making it periodic — the whole
	 * point being that it must not sound like it is on a schedule.
	 */
	public static boolean isEctopic(long beat, double distress) {
		double chance = ectopyChance(distress);
		if (chance <= 0.0) {
			return false;
		}
		// Never two in a row. Back-to-back ectopics read as the beat breaking
		// down rather than as it stumbling, and there is no compensatory pause
		// between them for either one to land against.
		if (BloodFlow.jitter(beat - 1, 23) < chance) {
			return false;
		}
		return BloodFlow.jitter(beat, 23) < chance;
	}

	/** Share of beats that misfire at this level of distress, 0 below the threshold. */
	public static double ectopyChance(double distress) {
		if (distress <= Balance.ECTOPY_THRESHOLD) {
			return 0.0;
		}
		return Balance.ECTOPY_CHANCE_MAX
				* Math.clamp((distress - Balance.ECTOPY_THRESHOLD) / (1.0 - Balance.ECTOPY_THRESHOLD), 0.0, 1.0);
	}

	/**
	 * The cardiac envelope right now, 0 to 1: a sharp first sound, a softer
	 * second a fifth of a second later, then quiet. Both decays are in seconds,
	 * so the shape of a beat does not change as the rate does.
	 */
	public double envelope() {
		double seconds = sinceBeat / 20.0;
		double lub = Math.exp(-seconds / (Balance.HEART_LUB_DECAY_SECONDS * stretch));
		double delay = Balance.HEART_DUB_DELAY_SECONDS * stretch;
		double dub = seconds >= delay
				? Balance.HEART_DUB_LEVEL * Math.exp(-(seconds - delay)
						/ (Balance.HEART_DUB_DECAY_SECONDS * stretch))
				: 0.0;
		return Math.clamp(amplitude * Math.max(lub, dub), 0.0, 1.0);
	}

	/**
	 * Marks this heart as dying, 0 (alive) to 1 (stopped). Set every tick while
	 * the fade runs; cleared by {@link #reset} or by setting it back to 0.
	 */
	public void setMoribund(double fade) {
		setMoribund(fade, Balance.DEATH_HEART_SILENT);
	}

	/** As above, with the fade fraction at which the heart falls silent. */
	public void setMoribund(double fade, double silentFraction) {
		moribund = Math.clamp(fade, 0.0, 1.0);
		silentAt = Math.clamp(silentFraction, 1.0e-6, 1.0);
	}

	/** True on the one tick a beat begins, and only if it was strong enough to hear. */
	public boolean beatJustFired() {
		return beatFired;
	}

	/** How hard the beat in progress landed, for cues that scale with it. */
	public double beatAmplitude() {
		return amplitude;
	}

	public double bpm() {
		return bpm;
	}

	public long beatIndex() {
		return beatIndex;
	}

	public void reset() {
		sinceBeat = 0.0;
		phase = 0.0;
		amplitude = 1.0;
		stretch = 1.0;
		beatIndex = 0;
		beatFired = false;
		previousWasEctopic = false;
		intervalScale = 1.0;
		bpm = Balance.HEART_REST_BPM;
		moribund = 0.0;
		silentAt = Balance.DEATH_HEART_SILENT;
	}
}
