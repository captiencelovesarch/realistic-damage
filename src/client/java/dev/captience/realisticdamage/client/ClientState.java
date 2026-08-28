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
	/** Rises while unconscious, falls after waking — drives the blackout fade. */
	private static double unconsciousFade;
	/** Free-running heartbeat clock, in beats. Whole numbers are the "lub". */
	private static double beatPhase;
	private static long lastBeat = -1;
	private static boolean beatFired;

	@Nullable
	public static BodySnapshot snapshot() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? null : player.getAttached(RDAttachments.SNAPSHOT);
	}

	/** Called each client tick: eases the smoothed values toward the synced ones. */
	public static void tick() {
		BodySnapshot snapshot = snapshot();
		if (snapshot == null) {
			smoothedConsciousness = Balance.CONSCIOUSNESS_MAX;
			smoothedBlood = Balance.BLOOD_MAX;
			unconsciousFade = 0.0;
			return;
		}
		smoothedConsciousness += (snapshot.consciousness() - smoothedConsciousness) * 0.15;
		smoothedBlood += (snapshot.blood() - smoothedBlood) * 0.15;
		double target = snapshot.unconscious() ? 1.0 : 0.0;
		unconsciousFade += (target - unconsciousFade) * 0.12;

		// The heart races as things get worse: a resting 55 bpm up to a hammering
		// 165 when you are nearly gone.
		double bpm = 55.0 + 110.0 * distress();
		beatPhase += bpm / 60.0 / 20.0;
		long beat = (long) beatPhase;
		beatFired = beat != lastBeat;
		lastBeat = beat;
	}

	/**
	 * How much trouble the player is in, 0 to 1 — the worst of near-death health,
	 * blood loss and fading consciousness. Drives both the heartbeat and the
	 * near-death vignette so every dangerous state pulses.
	 */
	public static double distress() {
		if (!active()) {
			return 0.0;
		}
		double fromBlood = smoothedBlood >= Balance.BLOOD_SERIOUS ? 0.0
				: Math.clamp((Balance.BLOOD_SERIOUS - smoothedBlood) / Balance.BLOOD_SERIOUS, 0.0, 1.0);
		double fromMind = ramp(Balance.SYMPTOM_HEARTBEAT, 0.0);
		return Math.max(lowHealth(), Math.max(fromBlood, fromMind));
	}

	/**
	 * The cardiac envelope for the current instant: a sharp "lub", a softer "dub"
	 * a fifth of a beat later, then quiet. Sharp transients are what make it read
	 * as a heartbeat instead of gentle breathing.
	 */
	public static double heartbeatEnvelope() {
		double f = beatPhase - Math.floor(beatPhase);
		double lub = Math.exp(-f / 0.055);
		double dub = f >= 0.20 ? 0.72 * Math.exp(-(f - 0.20) / 0.065) : 0.0;
		return Math.clamp(Math.max(lub, dub), 0.0, 1.0);
	}

	/** True on the single tick a new beat begins — fire the thump sound here. */
	public static boolean beatJustFired() {
		return beatFired;
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

	public static boolean unconscious() {
		BodySnapshot snapshot = snapshot();
		return snapshot != null && snapshot.unconscious();
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
		if (snapshot != null && snapshot.concussion()) {
			return smoothedConsciousness - Balance.CONCUSSION_SYMPTOM_SHIFT;
		}
		return smoothedConsciousness;
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
