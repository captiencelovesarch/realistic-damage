package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The client's view of the server-authoritative state (spec §47). Reads the
 * synced snapshot attachment and smooths the vitals so effects fade instead of
 * stepping. Never decides anything — presentation only.
 */
public final class ClientState {
	private ClientState() {}

	private static double smoothedConsciousness = Balance.CONSCIOUSNESS_MAX;
	private static double smoothedBlood = Balance.BLOOD_MAX;
	/** The concussion flare, eased like the vitals so the throb swells rather than steps (1.7). */
	private static double smoothedFlare;
	/** Rises while unconscious, falls after waking — drives the blackout fade. */
	private static double unconsciousFade;
	/**
	 * The same idea for a microsleep (1.4), but far snappier at both ends. A
	 * blackout that eased in like unconsciousness would be over before it
	 * finished arriving; this one drops like a dropped thread and comes back
	 * almost as fast, which is what makes it read as losing a moment rather than
	 * as a slow fade.
	 */
	private static double microsleepFade;
	/**
	 * The player's own heart (1.5). Owned here rather than by whatever needs it,
	 * because the sound, the vignette thump and the arterial spurts from an open
	 * wound all have to be the same beat — three clocks would drift apart within
	 * seconds and the effect only works while they agree.
	 */
	private static final dev.captience.realisticdamage.systems.Cardio HEART =
			new dev.captience.realisticdamage.systems.Cardio();
	/**
	 * Rises from 0 to 1 over the seconds after death (1.5). Everything the death
	 * screen does — the heart running down, the colour finishing its drain, the
	 * quiet — is a function of this one clock, so the pieces cannot drift apart.
	 */
	private static double deathFade;
	/**
	 * How violent the death was, 0 to 1, from the server's one-shot payload.
	 * Compresses the fade and pre-ages the heart: a bleed-out keeps the whole
	 * sequence, a 50-block landing is a cut with no beats in it. Defaults to 0,
	 * so deaths the server says nothing about get the slow ending.
	 */
	private static double deathViolence;
	/**
	 * What kind of death it was (1.7), from the same payload, for the death
	 * camera's motion. BLOW when the server said nothing — the collapse the death
	 * camera always had.
	 */
	private static dev.captience.realisticdamage.damage.DeathKind deathKind =
			dev.captience.realisticdamage.damage.DeathKind.BLOW;

	/** Eases toward a target, quickly when it worsens and slowly when it recovers. */
	private static double approach(double current, double target, double worseRate, double betterRate) {
		return current + (target - current) * (target < current ? worseRate : betterRate);
	}

	@Nullable
	public static BodySnapshot snapshot() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? null : player.getAttached(RDAttachments.SNAPSHOT);
	}

	/** Called each client tick: eases the smoothed values toward the synced ones. */
	public static void tick() {
		// Dying comes first, because the checks below all treat a dead player as
		// "nothing to do" — which is exactly the snap-to-nothing this replaces.
		// The vitals freeze where death left them; only the fade and the failing
		// heart advance.
		if (dying()) {
			boolean realistic = ClientEffectsConfig.get().realisticDeath;
			// Violence compresses the fade toward a cut...
			double seconds = dev.captience.realisticdamage.damage.DeathMath.fadeSeconds(
					realistic ? Balance.DEATH_FADE_SECONDS_REALISTIC : Balance.DEATH_FADE_SECONDS,
					deathViolence);
			deathFade = Math.min(1.0, deathFade + 1.0 / (seconds * 20.0));
			// ...and pre-ages the heart: an overwhelming death starts the run-down
			// at or past the silent point, so there are no beats at all —
			// consciousness ended before the heart did. The realistic ending keeps
			// what beats remain going past the blackout, because hearing is the
			// last sense to go; the cinematic one stops them while the screen
			// still shows something.
			HEART.setMoribund(Math.max(deathFade, deathViolence), realistic
					? Balance.DEATH_HEART_SILENT_REALISTIC : Balance.DEATH_HEART_SILENT);
			// Pure agonal character: whatever was wrong before, this is the head
			// going now — slow and heavy, not racing.
			HEART.tick(0.0, 0.0, 1.0, breath());
			return;
		}
		if (deathFade > 0.0) {
			// Respawned: a fresh body gets a fresh heart, immediately — the last
			// one's fade must not play out over the new life, and neither may its
			// violence.
			deathFade = 0.0;
			deathViolence = 0.0;
			deathKind = dev.captience.realisticdamage.damage.DeathKind.BLOW;
			HEART.reset();
		}
		BodySnapshot snapshot = snapshot();
		if (snapshot == null) {
			smoothedConsciousness = Balance.CONSCIOUSNESS_MAX;
			smoothedBlood = Balance.BLOOD_MAX;
			smoothedFlare = 0.0;
			unconsciousFade = 0.0;
			microsleepFade = 0.0;
			HEART.reset();
			return;
		}
		// Asymmetric on purpose. Getting hurt is an event and must land at once —
		// a symmetric ease left a limp second after impact where the blow had
		// already happened but nothing felt different. Recovery still eases in
		// gently, which is where smoothing actually earns its keep.
		smoothedConsciousness = approach(smoothedConsciousness, snapshot.consciousness(), 0.85, 0.15);
		smoothedBlood = approach(smoothedBlood, snapshot.blood(), 0.85, 0.15);
		smoothedFlare += (snapshot.concussionFlare() - smoothedFlare) * 0.12;
		double target = snapshot.unconscious() ? 1.0 : 0.0;
		unconsciousFade += (target - unconsciousFade) * (target > unconsciousFade ? 0.45 : 0.12);
		double microTarget = snapshot.microsleeping() ? 1.0 : 0.0;
		microsleepFade += (microTarget - microsleepFade) * (microTarget > microsleepFade ? 0.70 : 0.30);

		HEART.tick(distressFromHealth(), distressFromBlood(), distressFromMind(), breath());
	}

	/**
	 * The breathing cycle, 0 to 1 — one slow inhale and exhale every 90 ticks.
	 *
	 * <p>Shared rather than recomputed per effect so that the washout overlay, the
	 * heart's sinus arrhythmia and anything added later all breathe together. Two
	 * effects on independent breathing clocks drift against each other and the
	 * whole thing stops reading as one body.
	 */
	public static double breath() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return 0.5;
		}
		double period = 90.0;
		double time = client.level.getGameTime() % (long) period;
		return 0.5 - 0.5 * Math.cos(time / period * Math.PI * 2.0);
	}

	/** The player's cardiac clock, for anything that has to land on the beat. */
	public static dev.captience.realisticdamage.systems.Cardio heart() {
		return HEART;
	}

	/** True from the moment of death until the respawn, while the body winds down. */
	public static boolean dying() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.isDeadOrDying();
	}

	/**
	 * How far through dying the player is, 0 (just happened, or alive) to 1
	 * (over). Raw state — consumers scale it by their own intensity settings.
	 */
	public static double deathFade() {
		return deathFade;
	}

	/** The server's one word about the death in progress: how hard it happened. */
	public static void setDeathViolence(double violence) {
		deathViolence = Math.clamp(violence, 0.0, 1.0);
	}

	public static double deathViolence() {
		return deathViolence;
	}

	public static void setDeathKind(dev.captience.realisticdamage.damage.DeathKind kind) {
		deathKind = kind;
	}

	public static dev.captience.realisticdamage.damage.DeathKind deathKind() {
		return deathKind;
	}

	/**
	 * How much trouble the player is in, 0 to 1 — the worst of the three causes.
	 * Still the right number for anything that only cares how bad things are; use
	 * the three components below for anything that cares <em>why</em>.
	 */
	public static double distress() {
		return Math.max(distressFromHealth(), Math.max(distressFromBlood(), distressFromMind()));
	}

	/** Distress from the ordinary health bar. */
	public static double distressFromHealth() {
		return active() ? lowHealth() : 0.0;
	}

	/** Distress from blood loss — the cause behind the greyout and the thready pulse. */
	public static double distressFromBlood() {
		return active() ? dev.captience.realisticdamage.body.Vitals.distressFromBlood(smoothedBlood) : 0.0;
	}

	/** Distress from a fading mind — the cause behind the warping and the slow, heavy beat. */
	public static double distressFromMind() {
		return active()
				? dev.captience.realisticdamage.body.Vitals.distressFromMind(effectiveConsciousness())
				: 0.0;
	}

	/**
	 * The cardiac envelope for the current instant: a sharp first sound, a softer
	 * second a fifth of a second later, then quiet.
	 *
	 * <p>Both the shape and the timing now come from {@link
	 * dev.captience.realisticdamage.systems.Cardio}, which fixed two things. The
	 * decays used to be measured in fractions of a beat, so the thump got shorter
	 * in real time the faster the heart went and a racing pulse turned into a
	 * click track. And the rate was one smooth function of one number, so the beat
	 * could get faster but could never falter.
	 */
	public static double heartbeatEnvelope() {
		return HEART.envelope();
	}

	/** True on the single tick a new beat begins — fire the thump sound here. */
	public static boolean beatJustFired() {
		return HEART.beatJustFired();
	}

	/** How hard the beat in progress landed: weak if ectopic, heavy after a pause. */
	public static double beatAmplitude() {
		return HEART.beatAmplitude();
	}

	public static double consciousness() {
		return smoothedConsciousness;
	}

	public static double blood() {
		return smoothedBlood;
	}

	public static double unconsciousFade() {
		return unconsciousFade;
	}

	public static double microsleepFade() {
		return microsleepFade;
	}

	public static boolean unconscious() {
		BodySnapshot snapshot = snapshot();
		return snapshot != null && snapshot.unconscious();
	}

	/** Blacked out for a moment (1.4) — the stutter before the stop. */
	public static boolean microsleeping() {
		BodySnapshot snapshot = snapshot();
		return snapshot != null && snapshot.microsleeping();
	}

	/** Face-down after a hard landing (1.4). */
	public static boolean knockedDown() {
		BodySnapshot snapshot = snapshot();
		return snapshot != null && snapshot.knockedDown();
	}

	/**
	 * Ramp of a symptom that begins at {@code threshold} and reaches full
	 * strength at {@code full}: 0 above the threshold, 1 at or below full.
	 * Smooth and deterministic — no flicker (spec §24).
	 */
	public static double ramp(double threshold, double full) {
		double consciousness = effectiveConsciousness();
		if (consciousness >= threshold) {
			return 0.0;
		}
		if (consciousness <= full) {
			return 1.0;
		}
		return (threshold - consciousness) / (threshold - full);
	}

	/**
	 * Consciousness as the senses experience it. A concussion makes every symptom
	 * arrive sooner and hit harder (spec §21) by shifting the whole ladder down,
	 * which is what gives "you feel dazed" something to actually feel like.
	 */
	public static double effectiveConsciousness() {
		BodySnapshot snapshot = snapshot();
		if (snapshot == null) {
			return dev.captience.realisticdamage.body.Vitals.effectiveConsciousness(smoothedConsciousness, false);
		}
		return dev.captience.realisticdamage.body.Vitals.effectiveConsciousness(
				smoothedConsciousness, snapshot.concussionGrade(), snapshot.concussion() ? smoothedFlare : 0.0);
	}

	/** 0 none, 1 mild, 2 moderate, 3 severe (1.7). */
	public static int concussionGrade() {
		BodySnapshot snapshot = snapshot();
		return snapshot == null || !snapshot.concussion() ? 0 : Math.max(1, snapshot.concussionGrade());
	}

	/** The concussion flare, smoothed, 0 to 1 (1.7). */
	public static double flare() {
		return concussed() ? Math.clamp(smoothedFlare / Balance.FLARE_MAX, 0.0, 1.0) : 0.0;
	}

	/** The room is spinning (1.7); and which way. */
	public static boolean vertigo() {
		BodySnapshot snapshot = snapshot();
		return snapshot != null && snapshot.vertigo();
	}

	public static boolean vertigoLeft() {
		BodySnapshot snapshot = snapshot();
		return snapshot != null && snapshot.vertigoLeft();
	}

	public static boolean retching() {
		BodySnapshot snapshot = snapshot();
		return snapshot != null && snapshot.retching();
	}

	/** The last-two-hearts phase (1.7). */
	public static dev.captience.realisticdamage.systems.AdrenalineMath.Phase adrenaline() {
		BodySnapshot snapshot = snapshot();
		return snapshot == null ? dev.captience.realisticdamage.systems.AdrenalineMath.Phase.NONE
				: snapshot.adrenaline();
	}

	public static boolean winded() {
		BodySnapshot snapshot = snapshot();
		return snapshot != null && snapshot.winded();
	}

	/** True when the synced state says the player is concussed. */
	public static boolean concussed() {
		BodySnapshot snapshot = snapshot();
		return snapshot != null && snapshot.concussion();
	}

	/**
	 * How close to death the player is on the ordinary health bar, 0 (fine) to 1
	 * (about to die). Read straight from vanilla client state — no sync needed.
	 */
	public static double lowHealth() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return 0.0;
		}
		double max = player.getMaxHealth();
		if (max <= 0.0 || !player.isAlive() || player.getHealth() <= 0.0F) {
			return 0.0;
		}
		double fraction = player.getHealth() / max;
		if (fraction >= Balance.LOW_HP_FRACTION) {
			return 0.0;
		}
		return Math.clamp(1.0 - fraction / Balance.LOW_HP_FRACTION, 0.0, 1.0);
	}

	/**
	 * Whether Realistic Damage effects should show at all right now. Dead players
	 * are excluded: zero health otherwise reads as maximum distress, so the
	 * heartbeat and vignette would keep going on the death screen.
	 */
	public static boolean active() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		return player != null && player.isAlive() && player.getHealth() > 0.0F
				&& !player.isCreative() && !player.isSpectator();
	}
}
